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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.util.Log;

public class AnonymousStats extends PreferenceActivity implements
		DialogInterface.OnClickListener, DialogInterface.OnDismissListener,
		Preference.OnPreferenceChangeListener {

	private static final String VIEW_STATS = "pref_view_stats";

	private static final String PREF_UNINSTALL = "pref_uninstall_romstats";

	protected static final String ANONYMOUS_OPT_IN = "pref_anonymous_opt_in";
	protected static final String ANONYMOUS_OPT_OUT_PERSIST = "pref_anonymous_opt_out_persist";
	protected static final String ANONYMOUS_FIRST_BOOT = "pref_anonymous_first_boot";
	protected static final String ANONYMOUS_LAST_CHECKED = "pref_anonymous_checked_in";
	protected static final String ANONYMOUS_ALARM_SET = "pref_anonymous_alarm_set";

	private CheckBoxPreference mEnableReporting;
	private CheckBoxPreference mPersistentOptout;
	private Preference mViewStats;
	private Preference btnUninstall;

	private Dialog mOkDialog;
	private boolean mOkClicked;

	private SharedPreferences mPrefs;

	public static SharedPreferences getPreferences(Context context) {
		return context.getSharedPreferences(Utilities.SETTINGS_PREF_NAME, 0);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.anonymous_stats);

		mPrefs = getPreferences(this);

		PreferenceScreen prefSet = getPreferenceScreen();
		mPrefs = this.getSharedPreferences(Utilities.SETTINGS_PREF_NAME, 0);
		mEnableReporting = (CheckBoxPreference) prefSet.findPreference(ANONYMOUS_OPT_IN);
		mPersistentOptout = (CheckBoxPreference) prefSet.findPreference(ANONYMOUS_OPT_OUT_PERSIST);
		mViewStats = (Preference) prefSet.findPreference(VIEW_STATS);
		btnUninstall = prefSet.findPreference(PREF_UNINSTALL);

		boolean firstBoot = mPrefs.getBoolean(ANONYMOUS_FIRST_BOOT, true);
        if (mEnableReporting.isChecked() && firstBoot) {
        	Log.d(Utilities.TAG, "First app start, set params and report immediately");
            mPrefs.edit().putBoolean(ANONYMOUS_FIRST_BOOT, false).apply();
            mPrefs.edit().putLong(ANONYMOUS_LAST_CHECKED, 1).apply();
            ReportingServiceManager.launchService(this);
        }
        
		// show Uninstall button if RomStats is installed as User App
		try {
			PackageManager pm = getPackageManager();
			ApplicationInfo appInfo = pm.getApplicationInfo(getPackageName(), 0);

            //Log.d(Utilities.TAG, "App is installed in: " + appInfo.sourceDir);
            //Log.d(Utilities.TAG, "App is system: " + (appInfo.flags & ApplicationInfo.FLAG_SYSTEM));
            

			if ((appInfo.sourceDir.startsWith("/data/app/"))
					&& (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
				// it is a User app
				btnUninstall.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
							@Override
							public boolean onPreferenceClick(Preference pref) {
								uninstallSelf();
								return true;
							}
						});
			} else {
				prefSet.removePreference(btnUninstall);
			}

		} catch (Exception e) {
			prefSet.removePreference(btnUninstall);
		}

		// Cancel notification on app open, in case it doesn't AutoCancel
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		nm.cancel(Utilities.NOTIFICATION_ID);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
		if (preference == mEnableReporting) {
			if (mEnableReporting.isChecked()) {
				// Display the confirmation dialog
				mOkClicked = false;
				if (mOkDialog != null) {
					mOkDialog.dismiss();
				}
				mOkDialog = new AlertDialog.Builder(this)
						.setMessage(this.getResources().getString(R.string.anonymous_statistics_warning))
						.setTitle(R.string.anonymous_statistics_warning_title)
						.setPositiveButton(android.R.string.yes, this)
						.setNeutralButton(getString(R.string.anonymous_learn_more), this)
						.setNegativeButton(android.R.string.no, this)
						.show();
				mOkDialog.setOnDismissListener(this);
			} else {
				// Disable reporting
				mPrefs.edit().putBoolean(ANONYMOUS_OPT_IN, false).apply();
			}
		} else if (preference == mPersistentOptout) {
			if (mPersistentOptout.isChecked()) {
				try {
					File sdCard = Environment.getExternalStorageDirectory();
					File dir = new File (sdCard.getAbsolutePath() + "/.ROMStats");
					dir.mkdirs();
					File cookieFile = new File(dir, "optout");
					
					FileOutputStream optOutCookie = new FileOutputStream(cookieFile);
					OutputStreamWriter oStream = new OutputStreamWriter(optOutCookie);
		            oStream.write("true");
		            oStream.close();
		            optOutCookie.close();
		            Log.d(Utilities.TAG, "Persistent Opt-Out cookie written successfully");
				} catch (IOException e) {
					Log.e(Utilities.TAG, "Unable to write persistent optout cookie", e);
				}
			} else {
				try {
					File sdCard = Environment.getExternalStorageDirectory();
					File dir = new File (sdCard.getAbsolutePath() + "/.ROMStats");
					File cookieFile = new File(dir, "optout");
					cookieFile.delete();
					Log.d(Utilities.TAG, "Persistent Opt-Out cookie removed successfully");
				} catch (Exception e) {
					Log.w(Utilities.TAG, "Unable to write persistent optout cookie", e);
				}				
			}
		} else if (preference == mViewStats) {
			// Display the stats page
			Uri uri = Uri.parse(Utilities.getStatsUrl() + "stats");
			startActivity(new Intent(Intent.ACTION_VIEW, uri));
		} else {
			// If we didn't handle it, let preferences handle it.
			return super.onPreferenceTreeClick(preferenceScreen, preference);
		}
		return true;
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		return false;
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		if (!mOkClicked) {
			mEnableReporting.setChecked(false);
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			mOkClicked = true;
			mPrefs.edit().putBoolean(ANONYMOUS_OPT_IN, true).apply();
			
			mPersistentOptout.setChecked(false);
			try {
				File sdCard = Environment.getExternalStorageDirectory();
				File dir = new File (sdCard.getAbsolutePath() + "/.ROMStats");
				File cookieFile = new File(dir, "optout");
				cookieFile.delete();
				Log.d(Utilities.TAG, "Persistent Opt-Out cookie removed successfully");
			} catch (Exception e) {
				Log.w(Utilities.TAG, "Unable to write persistent optout cookie", e);
			}
			
			ReportingServiceManager.launchService(this);
		} else if (which == DialogInterface.BUTTON_NEGATIVE) {
			mEnableReporting.setChecked(false);
		} else {
			Uri uri = Uri.parse("http://www.cyanogenmod.com/blog/cmstats-what-it-is-and-why-you-should-opt-in");
			startActivity(new Intent(Intent.ACTION_VIEW, uri));
		}
	}

	public void uninstallSelf() {
		Intent intent = new Intent(Intent.ACTION_DELETE);
		intent.setData(Uri.parse("package:" + getPackageName()));
		startActivity(intent);
	}

}
