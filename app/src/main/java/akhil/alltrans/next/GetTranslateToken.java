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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.Semaphore;

import okhttp3.Cache;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

//class GetTranslateToken implements Callback {
class GetTranslateToken {
    private static final String MODULE_PACKAGE = "akhil.alltrans.next";
    private static final String SETTINGS_PROXY_CALL_URI = "content://settings/system";
    private static final String SETTINGS_PROXY_CALL_METHOD_TRANSLATE = "alltransProxyTranslate";
    private static final String SETTINGS_PROXY_BUNDLE_FROM = "alltrans_from_lang";
    private static final String SETTINGS_PROXY_BUNDLE_TO = "alltrans_to_lang";
    private static final String SETTINGS_PROXY_BUNDLE_TEXT = "alltrans_text";
    private static final String SETTINGS_PROXY_BUNDLE_TRANSLATION = "alltrans_translation";
    private static final Semaphore available = new Semaphore(1, true);
    //    private static String userCredentials;
//    private static long lastExpireTime = 0;
    private static OkHttpClient httpsClient;
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");
    public GetTranslate getTranslate;

    private static Cache createHttpsClientCache() {
        int cacheSize = 1024 * 1024; // 1 MiB
        File cacheDirectory = new File(alltrans.context.getCacheDir(), "AllTransHTTPsCache");
        return new Cache(cacheDirectory, cacheSize);
    }

    public void doAll() {
        available.acquireUninterruptibly();
        if (httpsClient == null) {
            Cache cache = createHttpsClientCache();
            httpsClient = new OkHttpClient.Builder()
                    .cache(cache).build();
        }
        available.release();
        doInBackground();
//        if (PreferenceList.EnableYandex)
//            doInBackground();
//        else {
//            available.acquireUninterruptibly();
//            long time = System.currentTimeMillis();
//            if (time > lastExpireTime) {
//                utils.debugLog("In Thread " + Thread.currentThread().getId() + "  Entering get new token for string : " + getTranslate.stringToBeTrans);
//                getNewToken();
//            } else {
//                available.release();
//                doInBackground();
//            }
//        }
    }

//    private void getNewToken() {
//        try {
//            MediaType mediaType = MediaType.parse("application/jwt");
//            RequestBody body = RequestBody.create(mediaType, "");
//
//            Request request = new Request.Builder()
//                    .url("https://api.cognitive.microsoft.com/sts/v1.0/issueToken")
//                    .post(body)
//                    .addHeader("Ocp-Apim-Subscription-Key", PreferenceList.SubscriptionKey)
//                    .addHeader("Content-Type", "application/json")
//                    .addHeader("Accept", "application/jwt")
//                    .cacheControl(CacheControl.FORCE_NETWORK)
//                    .build();
//
//            httpsClient.newCall(request).enqueue(this);
//
//        } catch (Throwable e) {
//            Log.e("AllTrans", "AllTrans: Got error in getting new token as : " + Log.getStackTraceString(e));
//        }
//    }

    private void doInBackground() {

        try {
            if (PreferenceList.TranslatorProvider.equals("g")){
                String fromLanguage = PreferenceList.TranslateFromLanguage;
                String toLanguage = PreferenceList.TranslateToLanguage;
                if (fromLanguage == null || fromLanguage.trim().isEmpty()) {
                    fromLanguage = "auto";
                }
                if (toLanguage == null || toLanguage.trim().isEmpty()) {
                    toLanguage = "en";
                }

                Uri uri = new Uri.Builder().scheme("content")
                        .authority("akhil.alltrans.next.gtransProvider")
                        .appendQueryParameter("from", fromLanguage)
                        .appendQueryParameter("to", toLanguage)
                        .appendQueryParameter("text", getTranslate.stringToBeTrans).build();
                String translatedSring = null;
                translatedSring = queryTranslationUsingSettingsCall(fromLanguage, toLanguage, getTranslate.stringToBeTrans);
                if (translatedSring == null) {
                    Cursor cursor = queryTranslationCursor(uri);
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndex("translate");
                        if (columnIndex >= 0) {
                            translatedSring = cursor.getString(columnIndex);
                        }
                    }
                    if (cursor != null) {
                        cursor.close();
                    }
                }

                if (translatedSring == null) {
                    throw new JSONException("Null or Empty Cursor from Google");
                }
                Request mockRequest = new Request.Builder().url("https://some-url.com").build();
                Response response = new Response.Builder()
                        .request(mockRequest)
                        .code(200)
                        .message("")
                        .protocol(Protocol.HTTP_2)
                        .body(ResponseBody.create(translatedSring, null))
                        .build();
                getTranslate.onResponse(null, response);
            }
            else if (PreferenceList.TranslatorProvider.equals("y")) {
                String baseURL = "https://translate.yandex.net/api/v1.5/tr/translate?";
                String keyURL = "key=" + PreferenceList.SubscriptionKey;
                String textURL = "&text=" + URLEncoder.encode(getTranslate.stringToBeTrans, "UTF-8");
                String languageURL;
                if ("auto".equals(PreferenceList.TranslateFromLanguage)) {
                    languageURL = "&lang=" + PreferenceList.TranslateToLanguage;
                } else {
                    languageURL = "&lang=" + PreferenceList.TranslateFromLanguage + "-" + PreferenceList.TranslateToLanguage;
                }
                String fullURL = baseURL + keyURL + textURL + languageURL;

                Request request = new Request.Builder()
                        .url(fullURL)
                        .get()
                        .build();

                utils.debugLog("In Thread " + Thread.currentThread().getId() + "  Enqueuing Request for new translation for : " + getTranslate.stringToBeTrans);
                httpsClient.newCall(request).enqueue(getTranslate);
            } else {
                String baseURL = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0";
                String languageURL = "&to=" + PreferenceList.TranslateToLanguage;
                if (!PreferenceList.TranslateFromLanguage.equals("auto")){
                    languageURL += "&from=" + PreferenceList.TranslateFromLanguage;
                }
                String fullURL = baseURL + languageURL;

                String requestBodyJson = new JSONArray().put(new JSONObject().put("Text", getTranslate.stringToBeTrans)).toString();
                RequestBody body = RequestBody.create(requestBodyJson, JSON_MEDIA_TYPE);

                Request request = new Request.Builder()
                        .url(fullURL)
                        .method("POST", body)
                        .addHeader("Ocp-Apim-Subscription-Key", PreferenceList.SubscriptionKey)
                        .addHeader("Content-Type", "application/json; charset=UTF-8")
                        .build();

                utils.debugLog("In Thread " + Thread.currentThread().getId() + "  Enqueuing Request for new translation for : " + getTranslate.stringToBeTrans);
                httpsClient.newCall(request).enqueue(getTranslate);
            }
        } catch (Throwable e) {
            Log.e("AllTrans", "AllTrans: Got error in getting translation as : " + Log.getStackTraceString(e));
            if (getTranslate.canCallOriginal) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> getTranslate.originalCallable.callOriginalMethod(getTranslate.stringToBeTrans, getTranslate.userData), PreferenceList.Delay);
            }
        }
    }

    private Cursor queryTranslationCursor(Uri directUri) {
        Cursor cursor = queryCursor(alltrans.context.getContentResolver(), directUri, "direct gtransProvider");
        if (cursor != null) {
            return cursor;
        }

        // Use module context as a package-visibility fallback for Android 11+.
        return queryWithModuleContext(directUri);
    }

    private String queryTranslationUsingSettingsCall(String fromLanguage, String toLanguage, String sourceText) {
        try {
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
            utils.debugLog("Settings call gtrans fallback failed: " + Log.getStackTraceString(e));
            return null;
        }
    }

    private Cursor queryWithModuleContext(Uri uri) {
        try {
            Context moduleContext = alltrans.context.createPackageContext(
                    MODULE_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE
            );
            return moduleContext.getContentResolver().query(uri, null, null, null, null);
        } catch (Throwable e) {
            utils.debugLog("Module-context gtrans query failed for uri=" + uri + " error="
                    + Log.getStackTraceString(e));
            return null;
        }
    }

    private Cursor queryCursor(ContentResolver resolver, Uri uri, String strategyLabel) {
        try {
            return resolver.query(uri, null, null, null, null);
        } catch (Throwable e) {
            utils.debugLog("gtrans query failed for " + strategyLabel + " uri=" + uri + " error="
                    + Log.getStackTraceString(e));
            return null;
        }
    }

//    public void onResponse(Call call, Response response) {
//        try {
//            String result = response.body().string();
//            response.body().close();
//            utils.debugLog("Got request result as : " + result);
//            userCredentials = result;
//            utils.debugLog("In Thread " + Thread.currentThread().getId() + "  Set User Credentials as : " + userCredentials);
//            lastExpireTime = System.currentTimeMillis() + 550000;
//        } catch (java.io.IOException e) {
//            Log.e("AllTrans", "AllTrans: Got error in getting token as : " + Log.getStackTraceString(e));
//        } finally {
//            available.release();
//            doInBackground();
//        }
//    }
//
//    @Override
//    public void onFailure(Call call, IOException e) {
//        Log.e("AllTrans", "AllTrans: Got error in getting token as : " + Log.getStackTraceString(e));
//        available.release();
//        doInBackground();
//    }

}
