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
import android.os.Bundle;
import android.view.View;

import com.codemybrainsout.ratingdialog.RatingDialog;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.multidex.MultiDex;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class MainActivity extends FragmentActivity implements View.OnClickListener {
    //public static String TAG = "alltrans";
    private static String uniqueID = null;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPrefUtils.syncPrivateToWorldReadable(getApplicationContext(), "AllTransPref");

        final RatingDialog ratingDialog = new RatingDialog.Builder(this)
                .threshold(4)
                .session(6)
                .title(getString(R.string.feedback_title))
                .positiveButtonText(getString(R.string.feedback_positiveButtonText))
                .negativeButtonText(getString(R.string.feedback_negativeButtonText))
                .formTitle(getString(R.string.feedback_formTitle))
                .formHint(getString(R.string.feedback_formHint))
                .formSubmitText(getString(R.string.feedback_formSubmitText))
                .formCancelText(getString(R.string.feedback_formCancelText))
                .onRatingBarFormSumbit(new RatingDialog.Builder.RatingDialogFormListener() {
                    @Override
                    public void onFormSubmitted(String feedback) {
                        try {
                            if (uniqueID == null) {
                                SharedPreferences sharedPrefs = sharedPrefUtils.getSharedPreferences(
                                        getApplicationContext(), "PREF_UNIQUE_ID");
                                uniqueID = sharedPrefs.getString("PREF_UNIQUE_ID", null);
                                if (uniqueID == null) {
                                    uniqueID = UUID.randomUUID().toString();
                                    SharedPreferences.Editor editor = sharedPrefs.edit();
                                    editor.putString("PREF_UNIQUE_ID", uniqueID);
                                    editor.apply();
                                }
                            }

                            OkHttpClient client = new OkHttpClient().newBuilder().build();
                            MediaType mediaType = MediaType.parse("application/json");
                            Gson gson = new Gson();
                            Map<String, String> map = new HashMap<>();
                            map.put("body", uniqueID + " ---- " + feedback);
                            String resquestBody = gson.toJson(map);

                            RequestBody body = RequestBody.create(resquestBody, mediaType);
                            Request request = new Request.Builder()
                                    .url("https://api.github.com/gists/fbe99628496b1d349347d3212c837d8d/comments")
                                    .method("POST", body)
                                    .addHeader("Authorization", "token "+getString(R.string.github_key))
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("accept", "*/*")
                                    .build();
                            client.newCall(request).enqueue(new Callback() {
                                @Override
                                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                                }
                                @Override
                                public void onResponse(@NotNull Call call, @NotNull Response response) {
                                }
                            });

                        } catch (Throwable ignored) {
                        }

                    }
                }).build();

        ratingDialog.show();

        SharedPreferences settings = sharedPrefUtils.getSharedPreferences(this, "AllTransPref");
        utils.Debug = settings.getBoolean("Debug", false);

        setContentView(R.layout.activity_main);

        /* Android 15+ (targetSdk 35) draws the app edge-to-edge: the content is
           laid out behind the status and navigation bars. Pad the root view by
           the system bar insets so the toolbar and list stay fully visible. */
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        View rootView = findViewById(R.id.main_root);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        insetsController.setAppearanceLightStatusBars(true);
        insetsController.setAppearanceLightNavigationBars(true);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.toReplace, new AppListFragment())
                .commitAllowingStateLoss();

        Toolbar toolbar = findViewById(R.id.titleBar);
        toolbar.setTitle(getString(R.string.app_name) + " - " + getString(R.string.translate_anywhere));

        TabLayout tabLayout = findViewById(R.id.tabs);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.toReplace, new AppListFragment())
                                .commitAllowingStateLoss();
                        break;
                    case 1:
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.toReplace, new GlobalPreferencesFragment())
                                .commitAllowingStateLoss();
                        break;
                    case 2:
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.toReplace, new InstructionsFragment())
                                .commitAllowingStateLoss();
                        break;
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {

            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
    }

    @Override
    public void onClick(View v) {

        //startActivity(new Intent(getApplicationContext(), GlobalPreferenceActivity.class));

    }

}

