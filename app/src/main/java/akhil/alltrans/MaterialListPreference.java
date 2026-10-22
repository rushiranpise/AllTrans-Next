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

import androidx.preference.ListPreference;

/**
 * ListPreference whose dialog is a Material 3 dialog. Overriding onClick()
 * replaces the framework dialog instead of opening it alongside.
 */
public class MaterialListPreference extends ListPreference {

    public MaterialListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaterialListPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        MaterialPreferenceDialogs.showList(this);
    }
}
