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
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
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

    private Toolbar toolbar;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.apply(this);
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
        boolean lightBars = !ThemeManager.isDark(this);
        insetsController.setAppearanceLightStatusBars(lightBars);
        insetsController.setAppearanceLightNavigationBars(lightBars);

        toolbar = findViewById(R.id.titleBar);
        bottomNavigationView = findViewById(R.id.bottom_nav);

        getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            updateNavigationState();
            if (bottomNavigationView != null
                    && getSupportFragmentManager().getBackStackEntryCount() == 0) {
                setToolbarTitle(titleForSelection(bottomNavigationView.getSelectedItemId()));
            }
        });

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_apps) {
                showRootScreen(new AppListFragment(), getString(R.string.apps_to_translate));
            } else if (id == R.id.nav_settings) {
                showRootScreen(new GlobalPreferencesFragment(), getString(R.string.global_settings));
            }
            return true;
        });

        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_apps);
        } else {
            updateNavigationState();
        }
    }

    private void showRootScreen(Fragment fragment, String title) {
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.toReplace, fragment)
                .commitAllowingStateLoss();
        /* Set the title last: popBackStack triggers the back-stack listener,
           which would otherwise restore the previous selection's title. */
        setToolbarTitle(title);
        updateNavigationState();
    }

    private String titleForSelection(int id) {
        if (id == R.id.nav_settings) {
            return getString(R.string.global_settings);
        }
        return getString(R.string.apps_to_translate);
    }

    /** Shows the back arrow and hides the bottom bar while a detail screen is open. */
    private void updateNavigationState() {
        boolean detail = getSupportFragmentManager().getBackStackEntryCount() > 0;
        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(detail ? View.GONE : View.VISIBLE);
        }
        if (toolbar != null) {
            if (detail) {
                toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
                toolbar.setNavigationOnClickListener(
                        v -> getOnBackPressedDispatcher().onBackPressed());
            } else {
                toolbar.setNavigationIcon((android.graphics.drawable.Drawable) null);
                toolbar.setNavigationOnClickListener(null);
            }
        }
    }

    /** Used by the per-app screen to show which app is being configured. */
    public void setToolbarTitle(String title) {
        if (toolbar != null) {
            toolbar.setTitle(title);
        }
    }

    @Override
    public void onClick(View v) {

        //startActivity(new Intent(getApplicationContext(), GlobalPreferenceActivity.class));

    }

}

