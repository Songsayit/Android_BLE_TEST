package com.android.bleoad;

import android.bluetooth.BluetoothGattService;
import android.content.Context;

public abstract class IPlatformBleOad {

	protected IBleOadCallback mBleOadCallback;
	protected Context mContext;
	private String mUpdateFilePath;
	protected BleOadManager mBleOadManager;
	private int mFirmwareVersion = 0;
	
	
	public IPlatformBleOad(Context c, BleOadManager mgr) {
		mContext = c;
		mBleOadManager = mgr;
	}
	
	public void setBleOadCallback(IBleOadCallback cb) {
		mBleOadCallback = cb;
	}
	
	public void setUpdateFilePath(String path) {
		mUpdateFilePath = path;
	}
	
	public String getUpdateFilePath() {
		return mUpdateFilePath;
	}
	
	public void setFirmwareVersion(int version) {
		mFirmwareVersion = version;
	}
	
	public int getFirmwareVersion() {
		return mFirmwareVersion;
	}
	
	public abstract void startBleOad();
	public abstract void setBluetoothGattService(BluetoothGattService service);
	public abstract void onReceiveNoti(String uuid, byte[] values);
}
