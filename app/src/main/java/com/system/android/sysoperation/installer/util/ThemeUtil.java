package com.system.android.sysoperation.installer.util;

import android.content.Context;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;

import com.system.android.sysoperation.installer.R;
import com.system.android.sysoperation.installer.SysOperationApp;
import com.system.android.sysoperation.installer.SysOperationBaseActivity;

public final class ThemeUtil {
	private static int[] THEMES = new int[] {
			R.style.Theme_SysOperationInstaller_Light,
			R.style.Theme_SysOperationInstaller_Dark,
			R.style.Theme_SysOperationInstaller_Dark_Black, };

	private ThemeUtil() {
	}

	public static int getSelectTheme() {
		int theme = SysOperationApp.getPreferences().getInt("theme", 0);
		return (theme >= 0 && theme < THEMES.length) ? theme : 0;
	}

	public static void setTheme(SysOperationBaseActivity activity) {
		activity.mTheme = getSelectTheme();
		activity.setTheme(THEMES[activity.mTheme]);
	}

	public static void reloadTheme(SysOperationBaseActivity activity) {
		int theme = getSelectTheme();
		if (theme != activity.mTheme)
			activity.recreate();
    }

    public static int getThemeColor(Context context, int id) {
        Theme theme = context.getTheme();
        TypedArray a = theme.obtainStyledAttributes(new int[] { id });
		int result = a.getColor(0, 0);
		a.recycle();
		return result;
	}
}
