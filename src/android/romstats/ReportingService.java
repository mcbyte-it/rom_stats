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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.analytics.tracking.android.GoogleAnalytics;
import com.google.analytics.tracking.android.Tracker;

public class ReportingService extends Service {

	private StatsUploadTask mTask;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
    	boolean canReport = true;
        if (intent.getBooleanExtra("promptUser", false)) {
        	Log.d(Utilities.TAG, "Prompting user for opt-in.");
            promptUser();
            canReport = false;
        }
        
        String RomStatsUrl = Utilities.getStatsUrl();
        if (RomStatsUrl == null || RomStatsUrl.isEmpty()) {
        	Log.e(Utilities.TAG, "This ROM is not configured for ROM Statistics.");
        	canReport = false;
        }
        
        if (canReport) {
	    	Log.d(Utilities.TAG, "User has opted in -- reporting.");
	    	
	        if (mTask == null || mTask.getStatus() == AsyncTask.Status.FINISHED) {
	            mTask = new StatsUploadTask();
	            mTask.execute();
	        }
        }

        return Service.START_REDELIVER_INTENT;
    }
    
    private class StatsUploadTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
    		String deviceId = Utilities.getUniqueID(getApplicationContext());
    		String deviceName = Utilities.getDevice();
    		String deviceVersion = Utilities.getModVersion();
    		String deviceCountry = Utilities.getCountryCode(getApplicationContext());
    		String deviceCarrier = Utilities.getCarrier(getApplicationContext());
    		String deviceCarrierId = Utilities.getCarrierId(getApplicationContext());
    		String RomName = Utilities.getRomName();
    		String RomVersion = Utilities.getRomVersion();

    		String RomStatsUrl = Utilities.getStatsUrl();
    		
    		Log.d(Utilities.TAG, "SERVICE: Report URL=" + RomStatsUrl);
    		Log.d(Utilities.TAG, "SERVICE: Device ID=" + deviceId);
    		Log.d(Utilities.TAG, "SERVICE: Device Name=" + deviceName);
    		Log.d(Utilities.TAG, "SERVICE: Device Version=" + deviceVersion);
    		Log.d(Utilities.TAG, "SERVICE: Country=" + deviceCountry);
    		Log.d(Utilities.TAG, "SERVICE: Carrier=" + deviceCarrier);
    		Log.d(Utilities.TAG, "SERVICE: Carrier ID=" + deviceCarrierId);
    		Log.d(Utilities.TAG, "SERVICE: ROM Name=" + RomName);
    		Log.d(Utilities.TAG, "SERVICE: ROM Version=" + RomVersion);

			if (Utilities.getGaTracking() != null) {
				Log.d(Utilities.TAG, "Reporting to Google Analytics is enabled");
				
				GoogleAnalytics ga = GoogleAnalytics.getInstance(ReportingService.this);
				Tracker tracker = ga.getTracker(Utilities.getGaTracking());
				tracker.sendEvent(deviceName, deviceVersion, deviceCountry, null);
				tracker.sendEvent("checkin", deviceName, RomVersion, null);
				tracker.close();
			}
    		
            // report to the cmstats service
            HttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(RomStatsUrl + "submit");
            boolean success = false;

            try {
                List<NameValuePair> kv = new ArrayList<NameValuePair>(5);
    			kv.add(new BasicNameValuePair("device_hash", deviceId));
    			kv.add(new BasicNameValuePair("device_name", deviceName));
    			kv.add(new BasicNameValuePair("device_version", deviceVersion));
    			kv.add(new BasicNameValuePair("device_country", deviceCountry));
    			kv.add(new BasicNameValuePair("device_carrier", deviceCarrier));
    			kv.add(new BasicNameValuePair("device_carrier_id", deviceCarrierId));
    			kv.add(new BasicNameValuePair("rom_name", RomName));
    			kv.add(new BasicNameValuePair("rom_version", RomVersion));

                httpPost.setEntity(new UrlEncodedFormEntity(kv));
                httpClient.execute(httpPost);

                success = true;
            } catch (IOException e) {
                Log.w(Utilities.TAG, "Could not upload stats checkin", e);
            }

            return success;
        }
        
        @Override
		protected void onPostExecute(Boolean result) {
			final Context context = ReportingService.this;
			long interval;

			if (result) {
				final SharedPreferences prefs = AnonymousStats.getPreferences(context);
				prefs.edit().putLong(AnonymousStats.ANONYMOUS_LAST_CHECKED, System.currentTimeMillis()).apply();
				// use set interval
				interval = 0;
			} else {
				// error, try again in 3 hours
				interval = 3L * 60L * 60L * 1000L;
			}

			ReportingServiceManager.setAlarm(context, interval);
			stopSelf();
		}
	}

	private void promptUser() {
		NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		Intent mainActivity = new Intent(getApplicationContext(), AnonymousStats.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, mainActivity, 0);

		Notification notification = new NotificationCompat.Builder(getBaseContext())
				.setSmallIcon(R.drawable.ic_launcher)
				.setTicker(getString(R.string.notification_ticker))
				.setContentTitle(getString(R.string.notification_title))
				.setContentText(getString(R.string.notification_desc))
				.setWhen(System.currentTimeMillis())
				.setContentIntent(pendingIntent)
				.setAutoCancel(true)
				.build();

		nm.notify(Utilities.NOTIFICATION_ID, notification);
	}
 
}