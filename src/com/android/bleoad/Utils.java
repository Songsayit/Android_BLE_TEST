package com.android.bleoad;

public class Utils {
	public static byte[] intToByteArray(int iSource, int iArrayLen) {
		byte[] bLocalArr = new byte[iArrayLen];
		for (int i = 0; (i < 4) && (i < iArrayLen); i++) {
			bLocalArr[i] = (byte) (iSource >> 8 * i & 0xFF);
		}
		return bLocalArr;
	}

	public static int byteToInt(byte[] bRefArr) {
		int iOutcome = 0;
		byte bLoop;

		for (int i = 0; i < bRefArr.length; i++) {
			bLoop = bRefArr[i];
			iOutcome += (bLoop & 0xFF) << (8 * i);
		}
		return iOutcome;
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
	
	
	public static int BUILD_UINT16(byte loByte, byte hiByte) { 
		return ((int)(((loByte) & 0x00FF) + (((hiByte) & 0x00FF) << 8)));
	}
	
	public static byte HI_UINT16(int a) {
		return (byte) (((a) >> 8) & 0xFF);
	}
	
	public static byte LO_UINT16(int a) {
		return (byte) ((a) & 0xFF);
	}
	
	
	
}
