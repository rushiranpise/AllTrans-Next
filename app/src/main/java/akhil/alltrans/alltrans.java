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


import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.findClass;


public class alltrans implements IXposedHookLoadPackage {
    public static final Semaphore cacheAccess = new Semaphore(1, true);
    public static final Semaphore hookAccess = new Semaphore(1, true);
    @SuppressLint("StaticFieldLeak")
    public static final DrawTextHookHandler drawTextHook = new DrawTextHookHandler();
    @SuppressLint("StaticFieldLeak")
    public static final NotificationHookHandler notifyHook = new NotificationHookHandler();
    @SuppressLint("StaticFieldLeak")
    public static final ToastHookHandler toastHook = new ToastHookHandler();
    @SuppressLint("StaticFieldLeak")
    public static final VirtWebViewOnLoad virtWebViewOnLoad = new VirtWebViewOnLoad();
    public static HashMap<String, String> cache = new HashMap<>();
    @SuppressLint("StaticFieldLeak")
//    TODO: Maybe change to using WeakReference?
    public static Context context = null;
    public static Class baseRecordingCanvas = null;
    public static boolean settingsHooked = false;
    private static final String SETTINGS_PROXY_CALL_METHOD_PREF = "alltransProxyProviderURI";
    private static final String SETTINGS_PROXY_CALL_METHOD_TRANSLATE = "alltransProxyTranslate";
    private static final String SETTINGS_PROXY_BUNDLE_PACKAGE = "alltrans_package_name";
    private static final String SETTINGS_PROXY_BUNDLE_GLOBAL = "alltrans_global_pref";
    private static final String SETTINGS_PROXY_BUNDLE_LOCAL = "alltrans_local_pref";
    private static final String SETTINGS_PROXY_BUNDLE_FROM = "alltrans_from_lang";
    private static final String SETTINGS_PROXY_BUNDLE_TO = "alltrans_to_lang";
    private static final String SETTINGS_PROXY_BUNDLE_TEXT = "alltrans_text";
    private static final String SETTINGS_PROXY_BUNDLE_TRANSLATION = "alltrans_translation";


    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {

        if ("com.android.providers.settings".equals(lpparam.packageName)
                || "android".equals(lpparam.packageName)
                || "system".equals(lpparam.packageName)) {
            XposedBridge.log("AllTrans: got settings provider host package " + lpparam.packageName);
            if (!settingsHooked) {
                try {
                    hookSettings(lpparam);
                    settingsHooked = true;
                } catch (Throwable e) {
                    XposedBridge.log("AllTrans: settings hook install failed in host " + lpparam.packageName);
                    XposedBridge.log(Log.getStackTraceString(e));
                }
            }
        }

        // TODO: Comment this line later
        XposedBridge.log("in package beginning : " + lpparam.packageName);

        try {
            baseRecordingCanvas = findClass("android.graphics.BaseRecordingCanvas", lpparam.classLoader);
        } catch (Throwable e){
            XposedBridge.log("Cannot find baseRecordingCanvas");
        }

//        Hook Application onCreate
        appOnCreateHookHandler appOnCreateHookHandler = new appOnCreateHookHandler();
        utils.tryHookMethod(Application.class, "onCreate", appOnCreateHookHandler);

//        Possibly change to android.app.Instrumentation.newActivity()
//        or XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook()
        AttachBaseContextHookHandler attachBaseContextHookHandler = new AttachBaseContextHookHandler();
        utils.tryHookMethod(ContextWrapper.class, "attachBaseContext", Context.class, attachBaseContextHookHandler);

    }

    private void hookSettings(final LoadPackageParam lpparam) throws Throwable {

        XposedBridge.log("AllTrans: Trying to hook settings ");
        // https://android.googlesource.com/platform/frameworks/base/+/master/packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java
        @SuppressLint("PrivateApi") Class<?> clsSet = Class.forName("com.android.providers.settings.SettingsProvider", false, lpparam.classLoader);

        XposedBridge.log("AllTrans: Hooking all query overloads in settings provider ");
        XposedBridge.hookAllMethods(clsSet, "query", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (param.args == null || param.args.length == 0 || !(param.args[0] instanceof Uri)) {
                        return;
                    }
                    Uri uri = (Uri) param.args[0];
                    if (uri.toString().contains("alltransProxyProviderURI")){

                        XposedBridge.log("AllTrans: got projection xlua ");
                        final int callerUid = Binder.getCallingUid();
                        long ident = Binder.clearCallingIdentity();
                        try {
                            Method mGetContext = param.thisObject.getClass().getMethod("getContext");
                            Context context = (Context) mGetContext.invoke(param.thisObject);

                            XposedBridge.log("AllTrans: Trying to allow blocking ");
                            XposedHelpers.callStaticMethod(Binder.class, "allowBlockingForCurrentThread");

                            XposedBridge.log("AllTrans: Old URI " + uri.toString());
                            String new_uri_string = uri.toString().replace("content://settings/system/alltransProxyProviderURI/", "content://akhil.alltrans.");
                            /* Vouch for the real caller: the provider rejects
                               plain system_server calls, since any app could
                               otherwise use this proxy to read the settings. */
                            String callerPkg = utils.resolvePackageForUid(context, callerUid, uri.getLastPathSegment());
                            if (callerPkg != null) {
                                new_uri_string += (new_uri_string.contains("?") ? "&" : "?")
                                        + utils.CALLER_PARAM + "=" + Uri.encode(callerPkg);
                            }
                            Uri new_uri = Uri.parse(new_uri_string);
                            XposedBridge.log("AllTrans: New URI " + new_uri.toString());

                            Cursor cursor = context.getContentResolver().query(new_uri, null, null, null, null);
                            param.setResult(cursor);

                            XposedBridge.log("AllTrans: setting query result");
                        } catch (Throwable ex) {
                            XposedBridge.log(Log.getStackTraceString(ex));
                            param.setResult(null);
                        } finally {
                            try {
                                XposedHelpers.callStaticMethod(Binder.class, "defaultBlockingForCurrentThread");
                            } catch (Throwable ignored) {
                            }
                            Binder.restoreCallingIdentity(ident);
                        }
                    }
                } catch (Throwable ex) {
                    XposedBridge.log(Log.getStackTraceString(ex));
                }
            }
        });

        XposedBridge.log("AllTrans: Hooking all call overloads in settings provider ");
        XposedBridge.hookAllMethods(clsSet, "call", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (param.args == null) {
                        return;
                    }

                    String methodName = null;
                    String packageName = null;
                    Bundle extras = null;

                    for (int i = 0; i < param.args.length; i++) {
                        Object arg = param.args[i];
                        if (!(arg instanceof String)) {
                            continue;
                        }
                        String stringArg = (String) arg;
                        if (!SETTINGS_PROXY_CALL_METHOD_PREF.equals(stringArg)
                                && !SETTINGS_PROXY_CALL_METHOD_TRANSLATE.equals(stringArg)) {
                            continue;
                        }
                        methodName = stringArg;
                        // Method signature can be [method, arg, extras] or [authority, method, arg, extras].
                        int argIndex = i + 1;
                        if (argIndex < param.args.length && param.args[argIndex] instanceof String) {
                            packageName = (String) param.args[argIndex];
                        }
                        int extrasIndex = i + 2;
                        if (extrasIndex < param.args.length && param.args[extrasIndex] instanceof Bundle) {
                            extras = (Bundle) param.args[extrasIndex];
                        }
                        break;
                    }

                    if (methodName == null) {
                        return;
                    }

                    if (SETTINGS_PROXY_CALL_METHOD_PREF.equals(methodName)) {
                        if ((packageName == null || packageName.isEmpty()) && extras != null) {
                            packageName = extras.getString(SETTINGS_PROXY_BUNDLE_PACKAGE);
                        }
                        if (packageName == null || packageName.isEmpty()) {
                            return;
                        }

                        final int callerUid = Binder.getCallingUid();
                        long ident = Binder.clearCallingIdentity();
                        try {
                            Method mGetContext = param.thisObject.getClass().getMethod("getContext");
                            Context context = (Context) mGetContext.invoke(param.thisObject);

                            XposedHelpers.callStaticMethod(Binder.class, "allowBlockingForCurrentThread");
                            String callerPkg = utils.resolvePackageForUid(context, callerUid, packageName);
                            Uri newUri = Uri.parse("content://akhil.alltrans.sharedPrefProvider/" + packageName
                                    + (callerPkg != null ? "?" + utils.CALLER_PARAM + "=" + Uri.encode(callerPkg) : ""));
                            Cursor cursor = context.getContentResolver().query(newUri, null, null, null, null);
                            if (cursor == null || !cursor.moveToFirst()) {
                                if (cursor != null) {
                                    cursor.close();
                                }
                                param.setResult(null);
                                return;
                            }

                            int columnIndex = cursor.getColumnIndex("sharedPreferences");
                            if (columnIndex < 0) {
                                cursor.close();
                                param.setResult(null);
                                return;
                            }
                            String globalPref = cursor.getString(columnIndex);
                            String localPref = globalPref;
                            if (cursor.moveToNext()) {
                                columnIndex = cursor.getColumnIndex("sharedPreferences");
                                if (columnIndex >= 0) {
                                    localPref = cursor.getString(columnIndex);
                                }
                            }
                            cursor.close();

                            Bundle out = new Bundle();
                            out.putString(SETTINGS_PROXY_BUNDLE_GLOBAL, globalPref);
                            out.putString(SETTINGS_PROXY_BUNDLE_LOCAL, localPref);
                            param.setResult(out);
                        } catch (Throwable ex) {
                            XposedBridge.log(Log.getStackTraceString(ex));
                            param.setResult(null);
                        } finally {
                            try {
                                XposedHelpers.callStaticMethod(Binder.class, "defaultBlockingForCurrentThread");
                            } catch (Throwable ignored) {
                            }
                            Binder.restoreCallingIdentity(ident);
                        }
                        return;
                    }

                    if (SETTINGS_PROXY_CALL_METHOD_TRANSLATE.equals(methodName)) {
                        if (extras == null) {
                            param.setResult(null);
                            return;
                        }
                        String fromLanguage = extras.getString(SETTINGS_PROXY_BUNDLE_FROM, "auto");
                        String toLanguage = extras.getString(SETTINGS_PROXY_BUNDLE_TO, "en");
                        String sourceText = extras.getString(SETTINGS_PROXY_BUNDLE_TEXT);
                        if (sourceText == null || sourceText.isEmpty()) {
                            param.setResult(null);
                            return;
                        }

                        final int callerUid = Binder.getCallingUid();
                        long ident = Binder.clearCallingIdentity();
                        try {
                            Method mGetContext = param.thisObject.getClass().getMethod("getContext");
                            Context context = (Context) mGetContext.invoke(param.thisObject);

                            XposedHelpers.callStaticMethod(Binder.class, "allowBlockingForCurrentThread");
                            String callerPkg = utils.resolvePackageForUid(context, callerUid, null);
                            Uri uri = new Uri.Builder()
                                    .scheme("content")
                                    .authority("akhil.alltrans.gtransProvider")
                                    .appendQueryParameter(utils.CALLER_PARAM, callerPkg == null ? "" : callerPkg)
                                    .appendQueryParameter("from", fromLanguage)
                                    .appendQueryParameter("to", toLanguage)
                                    .appendQueryParameter("text", sourceText)
                                    .build();
                            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
                            if (cursor == null || !cursor.moveToFirst()) {
                                if (cursor != null) {
                                    cursor.close();
                                }
                                param.setResult(null);
                                return;
                            }

                            int columnIndex = cursor.getColumnIndex("translate");
                            if (columnIndex < 0) {
                                cursor.close();
                                param.setResult(null);
                                return;
                            }
                            String translated = cursor.getString(columnIndex);
                            cursor.close();
                            if (translated == null) {
                                param.setResult(null);
                                return;
                            }
                            Bundle out = new Bundle();
                            out.putString(SETTINGS_PROXY_BUNDLE_TRANSLATION, translated);
                            param.setResult(out);
                        } catch (Throwable ex) {
                            XposedBridge.log(Log.getStackTraceString(ex));
                            param.setResult(null);
                        } finally {
                            try {
                                XposedHelpers.callStaticMethod(Binder.class, "defaultBlockingForCurrentThread");
                            } catch (Throwable ignored) {
                            }
                            Binder.restoreCallingIdentity(ident);
                        }
                    }
                } catch (Throwable ex) {
                    XposedBridge.log(Log.getStackTraceString(ex));
                }
            }
        });
    }
}


