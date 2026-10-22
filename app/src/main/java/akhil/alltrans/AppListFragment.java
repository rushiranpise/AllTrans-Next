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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

@SuppressWarnings("ALL")
public class AppListFragment extends Fragment {

    private static SharedPreferences settings;
    private FragmentActivity context;
    private RecyclerView recyclerView;
    private EditText searchApps;
    private TextView noAppsFound;
    private PackageManager pm;

    private final List<ApplicationInfo> userPackages = new ArrayList<>();
    private final List<ApplicationInfo> systemPackages = new ArrayList<>();
    private AppsAdapter adapter;
    private int currentTab = 0;
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.apps_list, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        context = this.getActivity();
        //noinspection ConstantConditions
        settings = sharedPrefUtils.getSharedPreferences(this.getActivity(), "AllTransPref");

        //noinspection ConstantConditions
        View view = getView();
        recyclerView = view.findViewById(R.id.apps_list);
        searchApps = view.findViewById(R.id.searchApps);
        noAppsFound = view.findViewById(R.id.noAppsFound);
        MaterialButtonToggleGroup appTabs = view.findViewById(R.id.appTabs);
        pm = context.getPackageManager();

        adapter = new AppsAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);

        searchApps.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s == null ? "" : s.toString();
                applyFilter();
            }
        });

        appTabs.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            currentTab = checkedId == R.id.tab_system ? 1 : 0;
            applyFilter();
        });
        appTabs.check(R.id.tab_user);

        new PrepareAdapter().execute();
    }

    /** Rebuilds the visible list from the selected tab and the search query. */
    private void applyFilter() {
        if (adapter == null || pm == null) {
            return;
        }
        List<ApplicationInfo> base = currentTab == 1 ? systemPackages : userPackages;
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        List<ApplicationInfo> filtered = new ArrayList<>();
        for (ApplicationInfo ai : base) {
            if (query.isEmpty()) {
                filtered.add(ai);
                continue;
            }
            String label = pm.getApplicationLabel(ai).toString().toLowerCase(Locale.ROOT);
            if (label.contains(query) || ai.packageName.toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(ai);
            }
        }
        adapter.updateData(filtered);
        if (noAppsFound != null) {
            noAppsFound.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    /** Enabled apps first, then alphabetically by label. */
    private void sortApps(List<ApplicationInfo> packages) {
        Collections.sort(packages, new Comparator<ApplicationInfo>() {
            public int compare(ApplicationInfo a, ApplicationInfo b) {
                if (settings.contains(a.packageName) && !settings.contains(b.packageName))
                    return -1;
                if (!settings.contains(a.packageName) && settings.contains(b.packageName))
                    return 1;
                String labelA = pm.getApplicationLabel(a).toString().toLowerCase();
                String labelB = pm.getApplicationLabel(b).toString().toLowerCase();
                return labelA.compareTo(labelB);
            }
        });
    }

    private void openApp(ApplicationInfo app) {
        LocalPreferenceFragment localPreferenceFragment = new LocalPreferenceFragment();
        localPreferenceFragment.applicationInfo = app;
        if (context instanceof MainActivity) {
            ((MainActivity) context).setToolbarTitle(pm.getApplicationLabel(app).toString());
        }
        context.getSupportFragmentManager().beginTransaction()
                .replace(R.id.toReplace, localPreferenceFragment)
                .addToBackStack(null)
                .commitAllowingStateLoss();
    }

    private void toggleApp(ApplicationInfo app, boolean enabled) {
        String packageName = app.packageName;
        if (enabled) {
            settings.edit().putBoolean(packageName, true).apply();
            sharedPrefUtils.getSharedPreferences(context, packageName)
                    .edit().putBoolean("LocalEnabled", true).apply();
        } else if (settings.contains(packageName)) {
            settings.edit().remove(packageName).apply();
            sharedPrefUtils.getSharedPreferences(context, packageName)
                    .edit().putBoolean("LocalEnabled", false).apply();
        }
    }

    //TODO: Check this does not mess things up.
    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        setTargetFragment(null, -1);
    }

    private class AppsAdapter extends RecyclerView.Adapter<AppsAdapter.ViewHolder> {

        private final List<ApplicationInfo> items = new ArrayList<>();

        void updateData(List<ApplicationInfo> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ApplicationInfo app = items.get(position);
            holder.title.setText(pm.getApplicationLabel(app));
            holder.packageName.setText(app.packageName);
            holder.icon.setImageDrawable(pm.getApplicationIcon(app));

            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(settings.contains(app.packageName));
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) ->
                    toggleApp(app, isChecked));
            holder.itemView.setOnClickListener(v -> openApp(app));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView packageName;
            final ImageView icon;
            final MaterialCheckBox checkBox;

            ViewHolder(View view) {
                super(view);
                title = view.findViewById(R.id.firstLine);
                packageName = view.findViewById(R.id.secondLine);
                icon = view.findViewById(R.id.icon);
                checkBox = view.findViewById(R.id.checkBox);
            }
        }
    }

    private class PrepareAdapter extends AsyncTask<Void, Void, Void> {
        ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            dialog = new ProgressDialog(context);
            dialog.setMessage(getString(R.string.loading));
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            dialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
            final PackageManager pm = context.getPackageManager();
            //get a list of installed apps.
            final List<ApplicationInfo> packages = getInstalledApplications(context);
            if (utils.isExpModuleActive(context)) {
                List<String> packagNames = utils.getExpApps(context);
                ListIterator<ApplicationInfo> iter = packages.listIterator();
                while (iter.hasNext()) {
                    if (!packagNames.contains(iter.next().packageName)) {
                        iter.remove();
                    }
                }
            }

            /* Split into the User / System tabs. FLAG_SYSTEM marks preinstalled
               apps (including their updates), everything else is a user app. */
            List<ApplicationInfo> user = new ArrayList<>();
            List<ApplicationInfo> system = new ArrayList<>();
            for (ApplicationInfo ai : packages) {
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    system.add(ai);
                } else {
                    user.add(ai);
                }
            }
            sortApps(user);
            sortApps(system);
            userPackages.clear();
            userPackages.addAll(user);
            systemPackages.clear();
            systemPackages.addAll(system);
            return null;
        }

        protected void onPostExecute(Void result) {
            applyFilter();
            dialog.dismiss();
        }

        List<ApplicationInfo> getInstalledApplications(Context context) {
            final PackageManager pm = context.getPackageManager();
            try {
                return pm.getInstalledApplications(PackageManager.GET_META_DATA);
            } catch (Throwable ignored) {
                //we don't care why it didn't succeed. We'll do it using an alternative way instead
            }
            // use fallback:
            Process process;
            List<ApplicationInfo> result = new ArrayList<>();
            BufferedReader bufferedReader = null;
            try {
                process = Runtime.getRuntime().exec("pm list packages");
                bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    final String packageName = line.substring(line.indexOf(':') + 1);
                    final ApplicationInfo applicationInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
                    result.add(applicationInfo);
                }
                process.waitFor();
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                if (bufferedReader != null)
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
            return result;
        }
    }
}
