package com.ti.android_lightblue;

import android.util.Log;

public class GetFirmwareVersionNew {

	private static final String TAG = "GetFirmwareVersionNew";
	
	private static final byte TP_HEADER_VALUE = (byte) 0xAA;
	private static final byte TP_CMDS_SET_ALARM_TEMP = (byte)0x81;
	private static final byte TP_CMDS_GET_ALARM_TEMP = (byte) 0x82;
	private static final byte TP_CMDS_GET_BAT_ADC = (byte) 0x83;
//	private final byte TP_CMDS_GET_VERSION = (byte) 0x84;
	private static final byte TP_CMDS_GET_TEMPERATURE = (byte) 0x86;
	private static final byte TP_CMDS_GET_CALIBRATE_VALUE = (byte) 0x87;
	private static final byte TP_CMDS_GET_VERSION_NEW = (byte) 0x88;
	
	
	public static String processGetFirmwareVersionNew(byte[] values) {
		String version = "";
		if (values.length <= 2) {
			Log.e(TAG, "values.length " + values.length + " is smaller than 2");
			return version;
		}
		
		int pos = 0;
		byte header = values[pos++];
		byte len = values[pos++];
		
		if (values.length <= len + 2) {
			Log.e(TAG, "values.length " + values.length + " is smaller than (or equal) " + (len + 2));
			return version;
		}
		
		byte cmd = values[pos++];
		byte theCrcSum = values[len + 2];
		if (header != TP_HEADER_VALUE) {
			Log.e(TAG, "header " + header + " is not ours type!!!");
			return version;
		}
		byte crcSum = checkSum(values, len + 2);
		if (crcSum != theCrcSum) {
			Log.e(TAG, "crcSum " + crcSum + " is not equals value crcSum( " + theCrcSum +")");
			return version;
		}
		
		if (cmd != TP_CMDS_GET_VERSION_NEW) {
			Log.e(TAG, "cmd( " + cmd + ") is not 0x88" );
			return version;
		}
		
		if (len - 1 != 5) {
			Log.e(TAG, "cmd( " + cmd + "): ValueLen(" + (len-1) + ") is not 5" );
			return version;
		}
		
		////////////////////////////////////////////
		byte[] versionByte = new byte[len - 1];
		
		System.arraycopy(values, pos, versionByte, 0, len - 1);
//		Log.e(TAG, "versionByte = " + toHexString(versionByte));
		version = getFirmwareVersionNew(versionByte);
		/////////////////////////////////////////////
		
		return version;
	}
	
	
	public static String getFirmwareVersionNew(byte[] versionByte) {
		String versionStr = "";
		if (versionByte.length != 5) {
			Log.e(TAG, " ValueLen(" + versionByte.length + ") is not 5!!!" );
			return versionStr;
		}
		
		char modelName = (char)versionByte[0];
		byte modelNumber = versionByte[1];
		char ICName = (char)versionByte[2];
		byte versionH = versionByte[3];
		byte versionL = versionByte[4];
		if (versionL % 10 == 0) {
			versionL /= 10;
		}
		versionStr = "" + modelName + modelNumber +"_" + ICName + versionH + "." + versionL;
		
		return versionStr;
	}

	public static byte checkSum(byte[] pack, int len) {

		int i = 0;
		int sum = 0;
		for(i = 0; i < len; i++) {
			sum += pack[i];
		}

		return (byte)(sum & 0x0000ff);
	}
	
	
	public static String toHexString(byte[] b) {

		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < b.length; i++) {
			String hex = Integer.toHexString(b[i] & 0xFF);
			if (hex.length() == 1) {
				hex = '0' + hex;
			}
			sb.append(hex.toUpperCase() + " ");
		}
		
		return sb.toString();
	}
	
}
