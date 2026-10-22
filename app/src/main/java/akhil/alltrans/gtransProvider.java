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
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.languageid.IdentifiedLanguage;
import com.google.mlkit.nl.languageid.LanguageIdentification;
import com.google.mlkit.nl.languageid.LanguageIdentifier;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class gtransProvider extends ContentProvider {
    private static final float POSSIBLE_LANGUAGE_CONFIDENCE_THRESHOLD = 0.50f;
    private Map<String, Translator> translatorClients;
    private Map<String, Boolean> translatorModelsReady;
    private LanguageIdentifier languageIdentifier;

    @Override
    public boolean onCreate() {
        utils.debugLog("Creating new Content Provider for gTrans!!");
        translatorClients = Collections.synchronizedMap(new HashMap<>());
        translatorModelsReady = Collections.synchronizedMap(new HashMap<>());
        languageIdentifier = LanguageIdentification.getClient();
        return true;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        utils.debugLog("Got URI as - " + uri.toString());
        /* Only apps enabled in AllTrans may use the translation backend. */
        if (!utils.isTrustedCaller(this.getContext(), null,
                uri.getQueryParameter(utils.CALLER_PARAM))) {
            utils.debugLog("gtransProvider: rejected caller");
            String original = uri.getQueryParameter("text");
            return buildCursorWithTranslation(original == null ? "" : original);
        }
        String fromLanguage = uri.getQueryParameter("from");
        String toLanguage = uri.getQueryParameter("to");
        String tobeTrans = uri.getQueryParameter("text");
        utils.debugLog("gtransProvider query from=" + fromLanguage + " to=" + toLanguage
                + " textLen=" + (tobeTrans == null ? 0 : tobeTrans.length()));
        if (toLanguage == null || toLanguage.trim().isEmpty() || tobeTrans == null || tobeTrans.trim().isEmpty()) {
            return buildCursorWithTranslation(tobeTrans == null ? "" : tobeTrans);
        }
        if (fromLanguage == null || fromLanguage.trim().isEmpty()) {
            fromLanguage = "auto";
        }

        String effectiveFromLanguage = fromLanguage;
        if ("auto".equals(fromLanguage)) {
            effectiveFromLanguage = detectSourceLanguage(tobeTrans);
            if (effectiveFromLanguage == null) {
                utils.debugLog("Could not auto-detect source language, returning original text");
                return buildCursorWithTranslation(tobeTrans);
            }
            utils.debugLog("Auto-detected source language as - " + effectiveFromLanguage);
        }

        if (effectiveFromLanguage.equals(toLanguage)) {
            return buildCursorWithTranslation(tobeTrans);
        }

        String hashKey = effectiveFromLanguage + "##" + toLanguage;
        Translator translator;
        if (translatorClients.containsKey(hashKey)){
            translator = translatorClients.get(hashKey);
        } else {
            TranslatorOptions options =
                    new TranslatorOptions.Builder()
                            .setSourceLanguage(effectiveFromLanguage)
                            .setTargetLanguage(toLanguage)
                            .build();
            translator = Translation.getClient(options);
            translatorClients.put(hashKey, translator);
        }

        assert translator != null;
        assert tobeTrans != null;
        if (!ensureTranslatorModelReady(hashKey, translator)) {
            return buildCursorWithTranslation(tobeTrans);
        }

        Task<String> task = translator.translate(tobeTrans);
        String translatedString = tobeTrans;
        try {
            translatedString = Tasks.await(task);
        } catch (Throwable e) {
            utils.debugLog(Log.getStackTraceString(e));
            Log.e("AllTrans", "AllTrans: translate() failed for pair " + hashKey, e);
        }
        if (translatedString == null || translatedString.isEmpty()) {
            translatedString = tobeTrans;
        }
        return buildCursorWithTranslation(translatedString);
    }

    private boolean ensureTranslatorModelReady(@NonNull String hashKey, @NonNull Translator translator) {
        if (Boolean.TRUE.equals(translatorModelsReady.get(hashKey))) {
            return true;
        }

        synchronized (translatorModelsReady) {
            if (Boolean.TRUE.equals(translatorModelsReady.get(hashKey))) {
                return true;
            }
            try {
                Tasks.await(translator.downloadModelIfNeeded());
                translatorModelsReady.put(hashKey, true);
                return true;
            } catch (Throwable e) {
                utils.debugLog("Model download failed for pair " + hashKey + " : " + Log.getStackTraceString(e));
                Log.e("AllTrans", "AllTrans: model download failed for pair " + hashKey, e);
                return false;
            }
        }
    }

    @Nullable
    private String detectSourceLanguage(@NonNull String sourceText) {
        try {
            if (isTextTooShortForReliableDetection(sourceText)) {
                String scriptLanguage = detectSourceLanguageFromScript(sourceText);
                if (scriptLanguage != null) {
                    utils.debugLog("Auto-detect using Unicode script evidence as - " + scriptLanguage
                            + " for short text: " + sourceText);
                    return scriptLanguage;
                }

                String confidentCandidateLanguage = detectSourceLanguageFromCandidates(sourceText);
                if (confidentCandidateLanguage != null) {
                    return confidentCandidateLanguage;
                }

                String appLocaleLanguage = detectSourceLanguageFromAppLocale();
                if (appLocaleLanguage != null) {
                    utils.debugLog("Auto-detect using app locale because text is too short: " + sourceText);
                    return appLocaleLanguage;
                }
            }

            String mostLikelyLanguage = Tasks.await(languageIdentifier.identifyLanguage(sourceText));
            String normalizedMostLikelyLanguage = normalizeForTranslateLanguage(mostLikelyLanguage);
            if (normalizedMostLikelyLanguage != null) {
                return normalizedMostLikelyLanguage;
            }

            String scriptLanguage = detectSourceLanguageFromScript(sourceText);
            if (scriptLanguage != null) {
                utils.debugLog("Auto-detect fallback using Unicode script evidence as - " + scriptLanguage);
                return scriptLanguage;
            }

            String confidentCandidateLanguage = detectSourceLanguageFromCandidates(sourceText);
            if (confidentCandidateLanguage != null) {
                return confidentCandidateLanguage;
            }

            String appLocaleLanguage = detectSourceLanguageFromAppLocale();
            if (appLocaleLanguage != null) {
                return appLocaleLanguage;
            }
            return null;
        } catch (Throwable e) {
            utils.debugLog(Log.getStackTraceString(e));
            return null;
        }
    }

    private boolean isTextTooShortForReliableDetection(@NonNull String sourceText) {
        int letterCount = 0;
        for (int i = 0; i < sourceText.length(); i++) {
            if (Character.isLetter(sourceText.charAt(i))) {
                letterCount++;
                if (letterCount >= 3) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private String detectSourceLanguageFromScript(@NonNull String sourceText) {
        try {
            if (containsUnicodeScript(sourceText, Character.UnicodeScript.HIRAGANA)
                    || containsUnicodeScript(sourceText, Character.UnicodeScript.KATAKANA)) {
                return "ja";
            }
            if (containsUnicodeScript(sourceText, Character.UnicodeScript.HANGUL)) {
                return "ko";
            }
            if (containsUnicodeScript(sourceText, Character.UnicodeScript.HAN)) {
                return "zh";
            }
            return null;
        } catch (Throwable e) {
            utils.debugLog(Log.getStackTraceString(e));
            return null;
        }
    }

    private boolean containsUnicodeScript(@NonNull String text, @NonNull Character.UnicodeScript script) {
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (Character.UnicodeScript.of(codePoint) == script) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    @Nullable
    private String detectSourceLanguageFromCandidates(@NonNull String sourceText) {
        try {
            List<IdentifiedLanguage> possibleLanguages = Tasks.await(languageIdentifier.identifyPossibleLanguages(sourceText));
            String bestLanguage = null;
            float bestConfidence = -1.0f;
            for (IdentifiedLanguage identifiedLanguage : possibleLanguages) {
                if (identifiedLanguage == null) {
                    continue;
                }
                float confidence = identifiedLanguage.getConfidence();
                if (confidence < POSSIBLE_LANGUAGE_CONFIDENCE_THRESHOLD) {
                    continue;
                }
                String normalizedLanguage = normalizeForTranslateLanguage(identifiedLanguage.getLanguageTag());
                if (normalizedLanguage == null) {
                    continue;
                }
                if (confidence > bestConfidence) {
                    bestConfidence = confidence;
                    bestLanguage = normalizedLanguage;
                }
            }
            if (bestLanguage != null) {
                utils.debugLog("Auto-detect fallback using identifyPossibleLanguages picked " + bestLanguage
                        + " with confidence " + bestConfidence);
            }
            return bestLanguage;
        } catch (Throwable e) {
            utils.debugLog(Log.getStackTraceString(e));
            return null;
        }
    }

    @Nullable
    private String detectSourceLanguageFromAppLocale() {
        try {
            Context context = getContext();
            if (context == null) {
                return null;
            }
            Configuration configuration = context.getResources().getConfiguration();
            Locale locale;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (configuration.getLocales().size() == 0) {
                    return null;
                }
                locale = configuration.getLocales().get(0);
            } else {
                locale = configuration.locale;
            }
            if (locale == null) {
                return null;
            }
            String localeTag;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                localeTag = locale.toLanguageTag();
            } else {
                localeTag = locale.getLanguage();
            }
            String normalizedLanguage = normalizeForTranslateLanguage(localeTag);
            if (normalizedLanguage != null) {
                utils.debugLog("Auto-detect fallback using app locale as - " + normalizedLanguage);
            }
            return normalizedLanguage;
        } catch (Throwable e) {
            utils.debugLog(Log.getStackTraceString(e));
            return null;
        }
    }

    @Nullable
    private String normalizeForTranslateLanguage(@Nullable String languageTag) {
        if (languageTag == null) {
            return null;
        }
        String trimmedLanguageTag = languageTag.trim();
        if (trimmedLanguageTag.isEmpty() || "und".equals(trimmedLanguageTag)) {
            return null;
        }
        String normalizedLanguage = TranslateLanguage.fromLanguageTag(trimmedLanguageTag);
        if (normalizedLanguage != null) {
            return normalizedLanguage;
        }
        int separatorIndex = trimmedLanguageTag.indexOf('-');
        if (separatorIndex > 0) {
            String shortLanguage = trimmedLanguageTag.substring(0, separatorIndex);
            return TranslateLanguage.fromLanguageTag(shortLanguage);
        }
        return null;
    }

    @NonNull
    private Cursor buildCursorWithTranslation(@NonNull String translatedString) {
        String[] cols = {"translate"};
        MatrixCursor cursor = new MatrixCursor(cols);
        MatrixCursor.RowBuilder builder = cursor.newRow();
        builder.add(translatedString);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
