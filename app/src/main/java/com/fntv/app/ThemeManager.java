package com.fntv.app;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/** 外观：跟随系统 / 白天 / 夜间 */
public class ThemeManager {
    public static final int SYSTEM = 0;
    public static final int DAY = 1;
    public static final int NIGHT = 2;

    private static final String PREFS = "fntv_prefs";
    private static final String KEY = "theme_mode";

    public static int getMode(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, SYSTEM);
    }

    public static void setMode(Context c, int mode) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply();
        apply(mode);
    }

    public static void applySaved(Context c) {
        apply(getMode(c));
    }

    public static void apply(int mode) {
        switch (mode) {
            case DAY:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case NIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public static String label(int mode) {
        switch (mode) {
            case DAY: return "白天";
            case NIGHT: return "夜间";
            default: return "跟随系统";
        }
    }
}
