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

package akhil.alltrans.next;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import de.robv.android.xposed.XC_MethodHook;

class ToastHookHandler extends XC_MethodHook {
    private static final String SETTINGS_PROXY_CALL_URI = "content://settings/system";
    private static final String SETTINGS_PROXY_CALL_METHOD_TRANSLATE = "alltransProxyTranslate";
    private static final String SETTINGS_PROXY_BUNDLE_FROM = "alltrans_from_lang";
    private static final String SETTINGS_PROXY_BUNDLE_TO = "alltrans_to_lang";
    private static final String SETTINGS_PROXY_BUNDLE_TEXT = "alltrans_text";
    private static final String SETTINGS_PROXY_BUNDLE_TRANSLATION = "alltrans_translation";

    @Override
    protected void beforeHookedMethod(MethodHookParam methodHookParam) {
        try {
            if (methodHookParam.args == null || methodHookParam.args.length == 0) {
                return;
            }
            int textArgIndex = -1;
            CharSequence originalText = null;
            for (int i = 0; i < methodHookParam.args.length; i++) {
                Object arg = methodHookParam.args[i];
                if (arg instanceof CharSequence && SetTextHookHandler.isNotWhiteSpace(arg.toString())) {
                    textArgIndex = i;
                    originalText = (CharSequence) arg;
                    break;
                }
            }
            if (textArgIndex < 0 || originalText == null) {
                return;
            }

            String sourceText = originalText.toString();
            String translatedText = translateSynchronously(sourceText);
            if (!TextUtils.isEmpty(translatedText) && !sourceText.equals(translatedText)) {
                methodHookParam.args[textArgIndex] = translatedText;
                utils.debugLog("Translated toast text with len="
                        + sourceText.length() + " -> " + translatedText.length());
            }
        } catch (Throwable e) {
            Log.e("AllTrans", "AllTrans: toast hook failed: " + Log.getStackTraceString(e));
        }
    }

    @Override
    protected void afterHookedMethod(MethodHookParam methodHookParam) {
        try {
            if (!"makeText".equals(methodHookParam.method.getName())) {
                return;
            }
            if (methodHookParam.args == null || methodHookParam.args.length < 2) {
                return;
            }
            if (!(methodHookParam.args[0] instanceof Context) || !(methodHookParam.args[1] instanceof Integer)) {
                return;
            }

            Context context = (Context) methodHookParam.args[0];
            int resId = (Integer) methodHookParam.args[1];
            CharSequence sourceText = context.getText(resId);
            if (!SetTextHookHandler.isNotWhiteSpace(sourceText == null ? null : sourceText.toString())) {
                return;
            }

            String translatedText = translateSynchronously(sourceText.toString());
            if (TextUtils.isEmpty(translatedText) || sourceText.toString().equals(translatedText)) {
                return;
            }

            Object result = methodHookParam.getResult();
            if (result instanceof Toast) {
                ((Toast) result).setText(translatedText);
                utils.debugLog("Translated toast resource text with len="
                        + sourceText.length() + " -> " + translatedText.length());
            }
        } catch (Throwable e) {
            Log.e("AllTrans", "AllTrans: toast after-hook failed: " + Log.getStackTraceString(e));
        }
    }

    private String translateSynchronously(String sourceText) {
        String cached = getCachedTranslation(sourceText);
        if (cached != null) {
            return cached;
        }

        String fromLanguage = PreferenceList.TranslateFromLanguage;
        String toLanguage = PreferenceList.TranslateToLanguage;
        if (TextUtils.isEmpty(fromLanguage)) {
            fromLanguage = "auto";
        }
        if (TextUtils.isEmpty(toLanguage)) {
            toLanguage = "en";
        }
        if (!"auto".equals(fromLanguage) && fromLanguage.equals(toLanguage)) {
            return sourceText;
        }

        String translated = queryTranslationUsingSettingsCall(fromLanguage, toLanguage, sourceText);
        if (TextUtils.isEmpty(translated)) {
            translated = queryTranslationUsingDirectProvider(fromLanguage, toLanguage, sourceText);
        }
        if (TextUtils.isEmpty(translated)) {
            return sourceText;
        }

        translated = utils.XMLUnescape(translated);
        if (PreferenceList.Caching) {
            alltrans.cacheAccess.acquireUninterruptibly();
            alltrans.cache.put(sourceText, translated);
            alltrans.cache.put(translated, translated);
            alltrans.cacheAccess.release();
        }
        return translated;
    }

    private String getCachedTranslation(String sourceText) {
        if (!PreferenceList.Caching) {
            return null;
        }
        alltrans.cacheAccess.acquireUninterruptibly();
        try {
            if (alltrans.cache.containsKey(sourceText)) {
                return alltrans.cache.get(sourceText);
            }
            return null;
        } finally {
            alltrans.cacheAccess.release();
        }
    }

    private String queryTranslationUsingSettingsCall(String fromLanguage, String toLanguage, String sourceText) {
        try {
            if (alltrans.context == null) {
                return null;
            }
            Bundle extras = new Bundle();
            extras.putString(SETTINGS_PROXY_BUNDLE_FROM, fromLanguage);
            extras.putString(SETTINGS_PROXY_BUNDLE_TO, toLanguage);
            extras.putString(SETTINGS_PROXY_BUNDLE_TEXT, sourceText);
            Bundle result = alltrans.context.getContentResolver().call(
                    Uri.parse(SETTINGS_PROXY_CALL_URI),
                    SETTINGS_PROXY_CALL_METHOD_TRANSLATE,
                    null,
                    extras
            );
            if (result == null) {
                return null;
            }
            return result.getString(SETTINGS_PROXY_BUNDLE_TRANSLATION);
        } catch (Throwable e) {
            utils.debugLog("Toast settings-call translation failed: " + Log.getStackTraceString(e));
            return null;
        }
    }

    private String queryTranslationUsingDirectProvider(String fromLanguage, String toLanguage, String sourceText) {
        Cursor cursor = null;
        try {
            if (alltrans.context == null) {
                return null;
            }
            Uri uri = new Uri.Builder()
                    .scheme("content")
                    .authority("akhil.alltrans.next.gtransProvider")
                    .appendQueryParameter("from", fromLanguage)
                    .appendQueryParameter("to", toLanguage)
                    .appendQueryParameter("text", sourceText)
                    .build();
            cursor = alltrans.context.getContentResolver().query(uri, null, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            int columnIndex = cursor.getColumnIndex("translate");
            if (columnIndex < 0) {
                return null;
            }
            return cursor.getString(columnIndex);
        } catch (Throwable e) {
            utils.debugLog("Toast direct-provider translation failed: " + Log.getStackTraceString(e));
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
