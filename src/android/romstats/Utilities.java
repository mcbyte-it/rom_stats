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

import java.math.BigInteger;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.Locale;

import android.content.Context;
import android.os.SystemProperties;
import android.telephony.TelephonyManager;

public class Utilities {
	public static final String SETTINGS_PREF_NAME = "ROMStats";
	public static final String TAG = "ROMStats";
	public static final int NOTIFICATION_ID = 1;

	// For the Unique ID, I still use the IMEI or WiFi MAC address
	// CyanogenMod switched to use the Settings.Secure.ANDROID_ID
	// This is because the ANDROID_ID could change on hard reset, while IMEI remains equal
	public static String getUniqueID(Context ctx) {
		TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);

		String device_id = digest(tm.getDeviceId());
		if (device_id == null) {
			String wifiInterface = SystemProperties.get("wifi.interface");
			try {
				String wifiMac = new String(NetworkInterface.getByName(wifiInterface).getHardwareAddress());
				device_id = digest(wifiMac);
			} catch (Exception e) {
				device_id = null;
			}
		}

		return device_id;
	}

	public static String getStatsUrl() {
		String returnUrl = SystemProperties.get("ro.romstats.url");

		if (returnUrl.isEmpty()) {
			return null;
		}
		
		// if the last char of the link is not /, add it
		if (!returnUrl.substring(returnUrl.length() - 1).equals("/")) {
			returnUrl += "/";
		}

		return returnUrl;
	}

	public static String getCarrier(Context ctx) {
		TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
		String carrier = tm.getNetworkOperatorName();
		if ("".equals(carrier)) {
			carrier = "Unknown";
		}
		return carrier;
	}

	public static String getCarrierId(Context ctx) {
		TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
		String carrierId = tm.getNetworkOperator();
		if ("".equals(carrierId)) {
			carrierId = "0";
		}
		return carrierId;
	}

	public static String getCountryCode(Context ctx) {
		TelephonyManager tm = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
		String countryCode = tm.getNetworkCountryIso();
		if (countryCode.equals("")) {
			countryCode = "Unknown";
		}
		return countryCode;
	}

	public static String getDevice() {
		return SystemProperties.get("ro.product.model");
	}

	public static String getModVersion() {
		return SystemProperties.get("ro.build.display.id");
	}

	public static String getRomName() {
		return SystemProperties.get("ro.romstats.name");
	}

	public static String getRomVersion() {
		return SystemProperties.get("ro.romstats.version");
	}
	
	public static long getTimeFrame() {
		String tFrameStr = SystemProperties.get("ro.romstats.tframe", "7");
		return Long.valueOf(tFrameStr);
	}

	public static String digest(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			return new BigInteger(1, md.digest(input.getBytes())).toString(16).toUpperCase(Locale.US);
		} catch (Exception e) {
			return null;
		}
	}
	
	public static String getGaTracking() {
		String gaTracking = SystemProperties.get("ro.romstats.ga");
		
		if (gaTracking.isEmpty()) {
			return null;
		}
		
		return gaTracking;
	}
	
	/**
	 * Gets the Ask First value
	 * 0: RomStats will behave like CMStats, starts reporting automatically after the tframe (default)
	 * 1: RomStats will behave like the old CMStats, asks the user on first boot
	 * 
	 * @return boolean
	 */
	public static boolean askFirstBoot() {
		String askFirst = SystemProperties.get("ro.romstats.askfirst", "0");
		
		if ("0".equals(askFirst)) {
			return false;
		} else {
			return true;
		}
	}
	
	
}