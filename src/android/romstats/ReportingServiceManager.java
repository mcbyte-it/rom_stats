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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;

public class ReportingServiceManager extends BroadcastReceiver {

	private static final long MILLIS_PER_HOUR = 60L * 60L * 1000L;
	private static final long MILLIS_PER_DAY = 24L * MILLIS_PER_HOUR;

	// UPDATE_INTERVAL days is set in the build.prop file
	// private static final long UPDATE_INTERVAL = 1L * MILLIS_PER_DAY;

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			Log.d(Utilities.TAG, "[onReceive] BOOT_COMPLETED");
			
			SharedPreferences prefs = AnonymousStats.getPreferences(context);
			
			Log.d(Utilities.TAG, "[onReceive] Check prefs exist: " + prefs.contains(AnonymousStats.ANONYMOUS_OPT_IN));
			if (!prefs.contains(AnonymousStats.ANONYMOUS_OPT_IN)) {
				Log.d(Utilities.TAG, "[onReceive] New install, check 'persistent cookie' on Boot");
				
				File sdCard = Environment.getExternalStorageDirectory();
				File dir = new File (sdCard.getAbsolutePath() + "/.ROMStats");
				File cookieFile = new File(dir, "optout");
				
				if (cookieFile.exists()) {
					Log.d(Utilities.TAG, "[onReceive] persistent cookie exists, Disable everything");
					
					prefs.edit().putBoolean(AnonymousStats.ANONYMOUS_OPT_IN, false).apply();
					prefs.edit().putBoolean(AnonymousStats.ANONYMOUS_FIRST_BOOT, false).apply();
					
					SharedPreferences mainPrefs = PreferenceManager.getDefaultSharedPreferences(context);
					mainPrefs.edit().putBoolean(AnonymousStats.ANONYMOUS_OPT_IN, false).apply();
					mainPrefs.edit().putBoolean(AnonymousStats.ANONYMOUS_OPT_OUT_PERSIST, true).apply();
					
					return;
				}
			};
			
			setAlarm(context, 0);
		} else {
			Log.d(Utilities.TAG, "[onReceive] CONNECTIVITY_CHANGE");
			launchService(context);
		}
	}

	public static void setAlarm(Context context, long millisFromNow) {
		SharedPreferences prefs = AnonymousStats.getPreferences(context);
		
        //prefs.edit().putBoolean(AnonymousStats.ANONYMOUS_ALARM_SET, false).apply();
		
		//boolean firstBoot = prefs.getBoolean(AnonymousStats.ANONYMOUS_FIRST_BOOT, true);
		
		// get OPT_IN pref, defaults to true (new behavior)
		boolean optedIn = prefs.getBoolean(AnonymousStats.ANONYMOUS_OPT_IN, true);

		// If we want the old behavior, re-read OPT_IN but default to false 
		if (Utilities.askFirstBoot()) {
			optedIn = prefs.getBoolean(AnonymousStats.ANONYMOUS_OPT_IN, false);
			Log.d(Utilities.TAG, "[setAlarm] AskFirstBoot, optIn=" + optedIn);
		}
        
		if (!optedIn) {
			return;
		}

		long UPDATE_INTERVAL = Long.valueOf(Utilities.getTimeFrame()) * MILLIS_PER_DAY;

		if (millisFromNow <= 0) {
			long lastSynced = prefs.getLong(AnonymousStats.ANONYMOUS_LAST_CHECKED, 0);
			if (lastSynced == 0) {
				// never synced, so let's fake out that the last sync was just now.
				// this will allow the user tFrame time to opt out before it will start
				// sending up anonymous stats.
				lastSynced = System.currentTimeMillis();
				prefs.edit().putLong(AnonymousStats.ANONYMOUS_LAST_CHECKED, lastSynced).apply();
				Log.d(Utilities.TAG, "[setAlarm] Set alarm for first sync.");
			}
			millisFromNow = (lastSynced + UPDATE_INTERVAL) - System.currentTimeMillis();
		}

		Intent intent = new Intent(ConnectivityManager.CONNECTIVITY_ACTION);
		intent.setClass(context, ReportingServiceManager.class);

		AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + millisFromNow,
				PendingIntent.getBroadcast(context, 0, intent, 0));
		Log.d(Utilities.TAG, "[setAlarm] Next sync attempt in : " + millisFromNow / MILLIS_PER_HOUR + " hours");
		
        //prefs.edit().putBoolean(AnonymousStats.ANONYMOUS_ALARM_SET, true).apply();
	}

	public static void launchService(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo networkInfo = cm.getActiveNetworkInfo();
		Log.d(Utilities.TAG, "[launchService] networkInfo: " + networkInfo);
		if (networkInfo == null || !networkInfo.isConnected()) {
			return;
		}
		
		SharedPreferences prefs = AnonymousStats.getPreferences(context);
		
		boolean optedIn = prefs.getBoolean(AnonymousStats.ANONYMOUS_OPT_IN, true);
		if (!optedIn) {
			return;
		}
		
		boolean firstBoot = prefs.getBoolean(AnonymousStats.ANONYMOUS_FIRST_BOOT, true);
		if (Utilities.askFirstBoot() && firstBoot) {
			Log.d(Utilities.TAG, "[launchService] AskUserFirst & firstBoot -> prompt user");
			
			// promptUser is called through a service because it cannot be called from a BroadcastReceiver
			Intent intent = new Intent();
			intent.setClass(context, ReportingService.class);
			intent.putExtra("promptUser", true);
			context.startService(intent);
			return;
		}
		
		long lastSynced = prefs.getLong(AnonymousStats.ANONYMOUS_LAST_CHECKED, 0);
		if (lastSynced == 0) {
			setAlarm(context, 0);
			return;
		}
		
		long UPDATE_INTERVAL = Long.valueOf(Utilities.getTimeFrame()) * MILLIS_PER_DAY;
		
		long timeLeft = System.currentTimeMillis() - lastSynced;
		if (timeLeft < UPDATE_INTERVAL) {
			Log.d(Utilities.TAG, "Waiting for next sync : " + timeLeft / MILLIS_PER_HOUR + " hours");
			return;
		}

		Intent intent = new Intent();
		intent.setClass(context, ReportingService.class);
		context.startService(intent);
	}
}