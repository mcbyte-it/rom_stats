/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.romstats;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;

public class PreviewActivity extends PreferenceActivity {

    private static final String UNIQUE_ID = "preview_id";
    private static final String DEVICE = "preview_device";
    private static final String VERSION = "preview_version";
    private static final String COUNTRY = "preview_country";
    private static final String CARRIER = "preview_carrier";
    private static final String ROMNAME = "preview_romname";
    private static final String ROMVERSION = "preview_romversion";

    private Preference mId;
    private Preference mDevice;
    private Preference mVersion;
    private Preference mCountry;
    private Preference mCarrier;
    private Preference mRomName;
    private Preference mRomVersion;
    
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preview_data);
		PreferenceScreen prefSet = getPreferenceScreen();

        mId = (Preference) prefSet.findPreference(UNIQUE_ID);
        mDevice = (Preference) prefSet.findPreference(DEVICE);
        mVersion = (Preference) prefSet.findPreference(VERSION);
        mCountry = (Preference) prefSet.findPreference(COUNTRY);
        mCarrier = (Preference) prefSet.findPreference(CARRIER);
        mRomName = (Preference) prefSet.findPreference(ROMNAME);
        mRomVersion = (Preference) prefSet.findPreference(ROMVERSION);

        mId.setSummary(Utilities.getUniqueID(getApplicationContext()));
        mDevice.setSummary(Utilities.getDevice());
        mVersion.setSummary(Utilities.getModVersion());
        mCountry.setSummary(Utilities.getCountryCode(getApplicationContext()));
        mCarrier.setSummary(Utilities.getCarrier(getApplicationContext()));
        mRomName.setSummary(Utilities.getRomName());
        mRomVersion.setSummary(Utilities.getRomVersion());
	}
	
}