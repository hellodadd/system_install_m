package com.system.android.sysoperation.installer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.FileProvider;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;

import com.system.android.sysoperation.installer.util.AssetUtil;
import com.system.android.sysoperation.installer.util.DownloadsUtil;
import com.system.android.sysoperation.installer.util.InstallZipUtil;
import com.system.android.sysoperation.installer.util.InstallZipUtil.SysOperationProp;
import com.system.android.sysoperation.installer.util.NotificationUtil;
import com.system.android.sysoperation.installer.util.RepoLoader;

public class SysOperationApp extends Application implements ActivityLifecycleCallbacks {
    public static final String TAG = "SysOperationInstaller";

    @SuppressLint("SdCardPath")
    private static final String BASE_DIR_LEGACY = "/data/data/com.system.android.sysoperation.installer/";

    public static final String BASE_DIR = Build.VERSION.SDK_INT >= 24
            ? "/data/user_de/0/com.system.android.sysoperation.installer/" : BASE_DIR_LEGACY;

    public static final String ENABLED_MODULES_LIST_FILE = SysOperationApp.BASE_DIR + "conf/enabled_modules.list";

    private static final String[] XPOSED_PROP_FILES = new String[]{
            "/su/sysoperation/sysoperation.prop", // official systemless
            "/system/sysoperation.prop",    // classical
    };

    public static int WRITE_EXTERNAL_PERMISSION = 69;
    private static SysOperationApp mInstance = null;
    private static Thread mUiThread;
    private static Handler mMainHandler;
    private boolean mIsUiLoaded = false;
    private SharedPreferences mPref;
    private SysOperationProp mSysOperationProp;

    public static SysOperationApp getInstance() {
        return mInstance;
    }

    public static void runOnUiThread(Runnable action) {
        if (Thread.currentThread() != mUiThread) {
            mMainHandler.post(action);
        } else {
            action.run();
        }
    }

    public static void postOnUiThread(Runnable action) {
        mMainHandler.post(action);
    }

    // This method is hooked by SysOperationBridge to return the current version
    public static int getActiveSysOperationVersion() {
        return -10;
    }

    public static int getInstalledSysOperationVersion() {
        SysOperationProp prop = getSysOperationProp();
        return prop != null ? prop.getVersionInt() : -1;
    }

    public static SysOperationProp getSysOperationProp() {
        synchronized (mInstance) {
            return mInstance.mSysOperationProp;
        }
    }

    public static SharedPreferences getPreferences() {
        return mInstance.mPref;
    }

    public static void installApk(Context context, DownloadsUtil.DownloadInfo info) {
        Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = FileProvider.getUriForFile(context, "com.system.android.sysoperation.installer.fileprovider", new File(info.localFilename));
            installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(new File(info.localFilename));
        }
        installIntent.setDataAndType(uri, DownloadsUtil.MIME_TYPE_APK);
        installIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.getApplicationInfo().packageName);
        context.startActivity(installIntent);
    }

    public static String getDownloadPath() {
        return getPreferences().getString("download_location", Environment.getExternalStorageDirectory() + "/SysOperationInstaller");
    }

    public void onCreate() {
        super.onCreate();
        mInstance = this;
        mUiThread = Thread.currentThread();
        mMainHandler = new Handler();

        mPref = PreferenceManager.getDefaultSharedPreferences(this);
        reloadSysOperationProp();
        createDirectories();
        NotificationUtil.init();
        AssetUtil.removeBusybox();

        registerActivityLifecycleCallbacks(this);
    }

    private void createDirectories() {
        FileUtils.setPermissions(BASE_DIR, 00711, -1, -1);
        mkdirAndChmod("conf", 00771);
        mkdirAndChmod("log", 00777);

        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Method deleteDir = FileUtils.class.getDeclaredMethod("deleteContentsAndDir", File.class);
                deleteDir.invoke(null, new File(BASE_DIR_LEGACY, "bin"));
                deleteDir.invoke(null, new File(BASE_DIR_LEGACY, "conf"));
                deleteDir.invoke(null, new File(BASE_DIR_LEGACY, "log"));
            } catch (ReflectiveOperationException e) {
                Log.w(SysOperationApp.TAG, "Failed to delete obsolete directories", e);
            }
        }
    }

    private void mkdirAndChmod(String dir, int permissions) {
        dir = BASE_DIR + dir;
        new File(dir).mkdir();
        FileUtils.setPermissions(dir, permissions, -1, -1);
    }

    public void reloadSysOperationProp() {
        SysOperationProp prop = null;

        for (String path : XPOSED_PROP_FILES) {
            File file = new File(path);
            if (file.canRead()) {
                FileInputStream is = null;
                try {
                    is = new FileInputStream(file);
                    prop = InstallZipUtil.parseSysOperationProp(is);
                    break;
                } catch (IOException e) {
                    Log.e(SysOperationApp.TAG, "Could not read " + file.getPath(), e);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }

        synchronized (this) {
            mSysOperationProp = prop;
        }
    }

    // TODO find a better way to trigger actions only when any UI is shown for the first time
    @Override
    public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (mIsUiLoaded)
            return;

        RepoLoader.getInstance().triggerFirstLoadIfNecessary();
        mIsUiLoaded = true;
    }

    @Override
    public synchronized void onActivityResumed(Activity activity) {
    }

    @Override
    public synchronized void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
