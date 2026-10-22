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

import java.io.File;

import androidx.preference.PreferenceManager;

class sharedPrefUtils {
    private sharedPrefUtils() {
    }

    static SharedPreferences getSharedPreferences(Context context, String name) {
        /* MODE_WORLD_READABLE is rejected on Android 7+ (API 24+). Read and write
           with MODE_PRIVATE, then widen the file permissions (see
           makeWorldReadable) so XSharedPreferences in hooked apps can still read
           it - the same pattern every modern Xposed module uses. */
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    /**
     * Makes a prefs file readable by the hooked app processes (and therefore by
     * XSharedPreferences) on Android 7+, where MODE_WORLD_READABLE was removed.
     * The shared_prefs directory needs execute+read for other UIDs to traverse
     * into it; the xml file itself only needs read.
     */
    static void makeWorldReadable(Context context, String name) {
        try {
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            if (prefsDir.exists()) {
                prefsDir.setExecutable(true, false);
                prefsDir.setReadable(true, false);
            }
            File prefsFile = new File(prefsDir, name + ".xml");
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        } catch (Throwable ignored) {
        }
    }

    static void syncPrivateToWorldReadable(Context context, String name) {
        /* Preferences live in a single MODE_PRIVATE file now, so there is no
           second "world readable" copy to sync. Just make sure the file on disk
           is readable by the hooked app processes. */
        makeWorldReadable(context, name);
    }

    static void setWorldReadableMode(PreferenceManager preferenceManager) {
        /* Do NOT use MODE_WORLD_READABLE: on Android 7+ it throws
           SecurityException from getSharedPreferences(), which is too late for a
           try/catch here to help. MODE_PRIVATE plus explicit file permissions is
           the supported replacement. */
        try {
            preferenceManager.setSharedPreferencesMode(Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
        }
    }
}
