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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Material 3 replacements for the AppCompat dialogs that the AndroidX
 * preference framework shows for list and edit-text preferences.
 */
class MaterialPreferenceDialogs {

    private MaterialPreferenceDialogs() {
    }

    /** Replaces the default dialogs for every list / edit-text preference. */
    static void bindAll(PreferenceScreen screen) {
        if (screen != null) {
            bindGroup(screen);
        }
    }

    private static void bindGroup(PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference preference = group.getPreference(i);
            if (preference instanceof PreferenceGroup) {
                bindGroup((PreferenceGroup) preference);
            } else if (preference instanceof ListPreference) {
                preference.setOnPreferenceClickListener(p -> {
                    showList((ListPreference) p);
                    return true;
                });
            } else if (preference instanceof EditTextPreference) {
                preference.setOnPreferenceClickListener(p -> {
                    showEditText((EditTextPreference) p);
                    return true;
                });
            }
        }
    }

    private static void showList(ListPreference preference) {
        CharSequence[] entries = preference.getEntries();
        CharSequence[] values = preference.getEntryValues();
        if (entries == null || values == null) {
            return;
        }
        int checked = preference.findIndexOfValue(preference.getValue());
        new MaterialAlertDialogBuilder(preference.getContext())
                .setTitle(preference.getTitle())
                .setSingleChoiceItems(entries, checked, (dialog, which) -> {
                    preference.setValue(values[which].toString());
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showEditText(EditTextPreference preference) {
        Context context = preference.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_text, null);
        TextInputLayout layout = view.findViewById(R.id.dialog_edit_layout);
        TextInputEditText input = view.findViewById(R.id.dialog_edit_text);
        layout.setHint(preference.getTitle());
        input.setText(preference.getText());

        new MaterialAlertDialogBuilder(context)
                .setTitle(preference.getTitle())
                .setView(view)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    CharSequence text = input.getText();
                    preference.setText(text == null ? "" : text.toString());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Non-cancelable Material 3 progress dialog. */
    static AlertDialog showProgress(Context context, CharSequence message) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null);
        ((TextView) view.findViewById(R.id.progress_message)).setText(message);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(false)
                .create();
        dialog.show();
        return dialog;
    }
}
