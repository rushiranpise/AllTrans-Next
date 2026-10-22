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
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Material 3 replacement for {@link SwitchPreferenceCompat}: same behaviour and
 * XML attributes, but renders a MaterialSwitch instead of a platform Switch.
 */
public class MaterialSwitchPreference extends SwitchPreferenceCompat {

    public MaterialSwitchPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_switch_material);
    }

    public MaterialSwitchPreference(Context context) {
        super(context);
        setLayoutResource(R.layout.preference_switch_material);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        MaterialSwitch materialSwitch = (MaterialSwitch) holder.findViewById(R.id.material_switch);
        if (materialSwitch == null) {
            return;
        }
        materialSwitch.setOnCheckedChangeListener(null);
        materialSwitch.setChecked(isChecked());
        materialSwitch.setOnCheckedChangeListener((buttonView, checked) -> setChecked(checked));
    }
}
