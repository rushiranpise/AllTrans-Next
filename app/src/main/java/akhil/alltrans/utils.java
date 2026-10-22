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

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

class utils {
    /* Off by default: the log used to dump the full prefs JSON, including the
       Microsoft/Yandex subscription key, before the Debug preference was read.
       Users can still enable logging from the app's settings. */
    public static boolean Debug = false;

    public static boolean isExpModuleActive(Context context) {

        boolean isExp = false;
        if (context == null) {
            throw new IllegalArgumentException("context must not be null!!");
        }

        try {
            ContentResolver contentResolver = context.getContentResolver();
            Uri uri = Uri.parse("content://me.weishu.exposed.CP/");
            Bundle result;
            try {
                result = contentResolver.call(uri, "active", null, null);
            } catch (RuntimeException e) {
                /* TaiChi/Exposed may be stopped. Previously this started its
                   activity to wake it up, but background activity starts are
                   blocked since Android 10 - just treat the module as
                   inactive. */
                return false;
            }
            if (result == null) {
                result = contentResolver.call(uri, "active", null, null);
            }

            if (result == null) {
                return false;
            }
            isExp = result.getBoolean("active", false);
        } catch (Throwable ignored) {
        }
        return isExp;
    }

    public static List<String> getExpApps(Context context) {
        Bundle result;
        try {
            result = context.getContentResolver().call(Uri.parse("content://me.weishu.exposed.CP/"), "apps", null, null);
        } catch (Throwable e) {
            return Collections.emptyList();
        }

        if (result == null) {
            return Collections.emptyList();
        }
        List<String> list = result.getStringArrayList("apps");
        if (list == null) {
            return Collections.emptyList();
        }
        return list;
    }

    /**
     * Guards the exported content providers. The settings provider may only be
     * queried by the very app whose package is in the URI; the translation
     * provider may only be queried by apps the user enabled in AllTrans. This
     * stops any other app on the device from reading the module settings
     * (including the Microsoft/Yandex subscription key) or using the
     * translation backend for free.
     *
     * @param expectedPackage package the caller must be, or null to accept any
     *                        app that is enabled in AllTrans
     */
    /** Query parameter the settings-provider hook adds to proxied requests. */
    public static final String CALLER_PARAM = "alltrans_caller";

    /**
     * Resolves the package a UID belongs to. {@code preferredPackage} is
     * returned when it is one of the UID's packages (apps can share a UID).
     */
    public static String resolvePackageForUid(Context context, int uid, String preferredPackage) {
        try {
            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            if (packages == null || packages.length == 0) {
                return null;
            }
            if (preferredPackage != null) {
                for (String pkg : packages) {
                    if (pkg.equals(preferredPackage)) {
                        return pkg;
                    }
                }
                return null;
            }
            return packages[0];
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isTrustedCaller(Context context, String expectedPackage) {
        return isTrustedCaller(context, expectedPackage, null);
    }

    /**
     * Guards the exported content providers. The settings provider may only be
     * queried by the very app whose package is in the URI; the translation
     * provider may only be queried by apps the user enabled in AllTrans. This
     * stops any other app on the device from reading the module settings
     * (including the Microsoft/Yandex subscription key) or using the
     * translation backend for free.
     *
     * The settings-provider proxy hook runs inside system_server, so requests
     * from UID 1000 carry the original package in {@link #CALLER_PARAM}; that
     * value is trusted only because system_server resolved it from the real
     * calling UID before clearing the calling identity.
     *
     * @param expectedPackage package the caller must be, or null to accept any
     *                        app that is enabled in AllTrans
     * @param proxiedCaller   package forwarded by the system_server proxy
     */
    public static boolean isTrustedCaller(Context context, String expectedPackage, String proxiedCaller) {
        try {
            int uid = Binder.getCallingUid();
            if (uid == Process.myUid()) {
                return true;
            }
            if (uid == Process.SYSTEM_UID) {
                if (proxiedCaller == null || proxiedCaller.isEmpty()) {
                    /* Older module versions run the settings-provider proxy hook
                       without the caller parameter (the hook lives in
                       system_server and only reloads on reboot). Accept those
                       requests for compatibility; the provider returns a
                       sanitized global map for them. */
                    return true;
                }
                if (expectedPackage != null) {
                    return proxiedCaller.equals(expectedPackage);
                }
                SharedPreferences prefs = sharedPrefUtils.getSharedPreferences(context, "AllTransPref");
                return prefs.getBoolean(proxiedCaller, false);
            }
            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            if (packages == null) {
                return false;
            }
            SharedPreferences prefs = sharedPrefUtils.getSharedPreferences(context, "AllTransPref");
            for (String pkg : packages) {
                if (expectedPackage != null) {
                    if (pkg.equals(expectedPackage)) {
                        return true;
                    }
                } else if (prefs.getBoolean(pkg, false)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            debugLog("Caller check failed: " + Log.getStackTraceString(t));
        }
        return false;
    }

    public static void debugLog(String str) {
        if (Debug) {
            if (str.length() > 3900) {
                Log.i("AllTrans", "AllTrans: " + str.substring(0, 3900));
                debugLog(str.substring(3900));
            } else {
                Log.i("AllTrans", "AllTrans: " + str);
            }
        }
    }

    public static void tryHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback){
        try{
            findAndHookMethod(clazz, methodName, parameterTypesAndCallback);
        } catch (Throwable e){
            utils.debugLog("Cannot hook method - " + clazz.getCanonicalName() + " - " + methodName + Log.getStackTraceString(e));
        }
    }

    public static void tryHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback){
        try{
            findAndHookMethod(className, classLoader, methodName, parameterTypesAndCallback);
        } catch (Throwable e){
            utils.debugLog("Cannot hook method - " + className + " - " + methodName + Log.getStackTraceString(e));
        }
    }

    public static String XMLUnescape(String s) {
        String retVal = s.replaceAll("&amp;", "&");
        retVal = retVal.replaceAll("&quot;", "\"");
        retVal = retVal.replaceAll("&apos;", "'");
        retVal = retVal.replaceAll("&lt;", "<");
        retVal = retVal.replaceAll("&gt;", ">");
        retVal = retVal.replaceAll("&#xD;", "\r");
        retVal = retVal.replaceAll("&#xA;", "\n");

        retVal = retVal.replaceAll("&amp;", "&");
        retVal = retVal.replaceAll("&quot;", "\"");
        retVal = retVal.replaceAll("&apos;", "'");
        retVal = retVal.replaceAll("&lt;", "<");
        retVal = retVal.replaceAll("&gt;", ">");
        retVal = retVal.replaceAll("&#xD;", "\r");
        retVal = retVal.replaceAll("&#xA;", "\n");
        return retVal;
    }

    // Taken from Apache Commons Lang 2.6 at -
    // https://commons.apache.org/proper/commons-lang/javadocs/api-2.6/src-html/org/apache/commons/lang/StringEscapeUtils.html#line.146
    public static String javaScriptEscape(String str) {
        if (str == null) {
            return null;
        }
        StringWriter writer = new StringWriter(str.length() * 2);
        int sz;
        sz = str.length();
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);

            // handle unicode
            if (ch > 0xfff) {
                writer.write("\\u" + hex(ch));
            } else if (ch > 0xff) {
                writer.write("\\u0" + hex(ch));
            } else if (ch > 0x7f) {
                writer.write("\\u00" + hex(ch));
            } else if (ch < 32) {
                switch (ch) {
                    case '\b':
                        writer.write('\\');
                        writer.write('b');
                        break;
                    case '\n':
                        writer.write('\\');
                        writer.write('n');
                        break;
                    case '\t':
                        writer.write('\\');
                        writer.write('t');
                        break;
                    case '\f':
                        writer.write('\\');
                        writer.write('f');
                        break;
                    case '\r':
                        writer.write('\\');
                        writer.write('r');
                        break;
                    default:
                        if (ch > 0xf) {
                            writer.write("\\u00" + hex(ch));
                        } else {
                            writer.write("\\u000" + hex(ch));
                        }
                        break;
                }
            } else {
                switch (ch) {
                    case '\'':
                        writer.write('\\');
                        writer.write('\'');
                        break;
                    case '"':
                        writer.write('\\');
                        writer.write('"');
                        break;
                    case '\\':
                        writer.write('\\');
                        writer.write('\\');
                        break;
                    case '/':
                        writer.write('\\');
                        writer.write('/');
                        break;
                    default:
                        writer.write(ch);
                        break;
                }
            }
        }
        return writer.toString();
    }

    private static String hex(char ch) {
        return Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
    }
}
