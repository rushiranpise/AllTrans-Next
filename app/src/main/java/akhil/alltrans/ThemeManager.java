/*
 * Copyright 2017 Akhil Kedia
 * This file is part of AllTrans.
 *
 * AllTrans is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AllTrans is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AllTrans. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package akhil.alltrans;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import android.app.Activity;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

/**
 * Applies the selected appearance (follow system / light / dark / AMOLED black)
 * and Material You dynamic colors on Android 12+.
 */
class ThemeManager {

    static final String KEY = "AppTheme";
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String AMOLED = "amoled";

    private ThemeManager() {
    }

    static String getMode(Context context) {
        SharedPreferences prefs = sharedPrefUtils.getSharedPreferences(context, "AllTransPref");
        String mode = prefs.getString(KEY, SYSTEM);
        return mode == null ? SYSTEM : mode;
    }

    static boolean isDark(Context context) {
        int nightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Must be called before {@code super.onCreate()} in the activity. */
    static void apply(Activity activity) {
        String mode = getMode(activity);
        switch (mode) {
            case LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case DARK:
            case AMOLED:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }

        if (AMOLED.equals(mode)) {
            /* Pure black surfaces: skip dynamic colors, they would replace the
               black background roles with wallpaper-derived colors. */
            activity.setTheme(R.style.Theme_AllTrans_Amoled);
        } else {
            activity.setTheme(R.style.Theme_AllTrans);
            /* Only follow Material You in system mode. When the appearance is
               forced to light or dark, DynamicColors reads the SYSTEM palette
               (which may be the opposite mode) and produces dark surfaces or
               light-on-light text. The static light/dark palettes are always
               consistent with the forced mode. */
            if (SYSTEM.equals(mode) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                DynamicColors.applyToActivityIfAvailable(activity);
            }
        }
    }
}
