package com.ti.android_lightblue.common;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.xmlpull.v1.XmlPullParserException;

import android.content.res.XmlResourceParser;

public class GattInfo {
	public static final UUID CC_SERVICE_UUID;
	public static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID
			.fromString("00002902-0000-1000-8000-00805f9b34fb");
	public static final UUID OAD_SERVICE_UUID = UUID
			.fromString("f000ffc0-0451-4000-b000-000000000000");
	private static Map<String, String> mDescrMap;
	private static Map<String, String> mNameMap;
	private static final String uuidBtSigBase = "0000****-0000-1000-8000-00805f9b34fb";
	private static final String uuidTiBase = "f000****-0451-4000-b000-000000000000";

	static {
		CC_SERVICE_UUID = UUID
				.fromString("f000ccc0-0451-4000-b000-000000000000");
		mNameMap = new HashMap();
		mDescrMap = new HashMap();
	}


	public static String getDescription(UUID uuid) {
		String str = toShortUuidStr(uuid);
		return (String) mDescrMap.get(str.toUpperCase());
	}

	public static boolean isBtSigUuid(UUID u) {
		String us = u.toString();
		String r = toShortUuidStr(u);
		return us.replace(r, "****").equals(
				"0000****-0000-1000-8000-00805f9b34fb");
	}

	public static boolean isTiUuid(UUID u) {
		String us = u.toString();
		String r = toShortUuidStr(u);
		return us.replace(r, "****").equals(
				"f000****-0451-4000-b000-000000000000");
	}

	private static String toShortUuidStr(UUID u) {
		return u.toString().substring(4, 8);
	}

	private static String uuidToName(String uuidStr16) {
		return (String) mNameMap.get(uuidStr16);
	}

	public static String uuidToName(UUID uuid) {
		String str = toShortUuidStr(uuid);
		return uuidToName(str.toUpperCase());
	}

	public static String uuidToString(UUID u) {
		String uuidStr = null;
		if (isBtSigUuid(u))
			uuidStr = toShortUuidStr(u);
		
		if (uuidStr != null)
			return uuidStr.toUpperCase();
		
		return null;
	}
}