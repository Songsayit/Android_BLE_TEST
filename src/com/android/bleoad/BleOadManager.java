package com.android.bleoad;

import java.util.List;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

public abstract class BleOadManager {
	private final static String TAG = "BleOadManager";
	private final static String OAD_UUID_TI_SERVICE = "f000ffc0-0451-4000-b000-000000000000";
	private final static String UUID_COMMON_SERVICE = "00001809-0000-1000-8000-00805f9b34fb";
	private final static String OAD_UUID_NORDIC_GATT = "00009024-0000-1000-8000-00805f9b34fb";
	
	public final static int BLE_OAD_TYPE_TI = 100;
	public final static int BLE_OAD_TYPE_NODIC = 101;
	public final static int BLE_OAD_TYPE_UNKOWN = 199;
	
	private final Context mContext;
	private IPlatformBleOad mBleOad;
	
	public BleOadManager(Context c) {
		mContext = c;
	}
	/**
	 * 
	 * @param gattServices
	 * @return
	 * type:<p> TI is <{@link #BLE_OAD_TYPE_TI}>
	 * 		<p> NODIC is <{@link #BLE_OAD_TYPE_NODIC}>
	 */
	
	public int getBleOadType(List<BluetoothGattService> gattServices) {
		int type = BLE_OAD_TYPE_UNKOWN;
		for (BluetoothGattService gattService : gattServices) {
			String uuid = gattService.getUuid().toString();
			
			if (uuid == null) {
				continue;
			}
			
			if (uuid.toUpperCase().equals(OAD_UUID_TI_SERVICE.toUpperCase())) {
				
				type = BLE_OAD_TYPE_TI;
				
			} else if (uuid.toUpperCase().equals(UUID_COMMON_SERVICE.toUpperCase())) {
				List<BluetoothGattCharacteristic> gattCharacteristics = gattService
						.getCharacteristics();

				for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
					String gattUuid = gattCharacteristic.getUuid().toString();
					
					if (gattUuid.toUpperCase().equals(OAD_UUID_NORDIC_GATT.toUpperCase())) {
						
						type = BLE_OAD_TYPE_NODIC;
						break;
					}
				}
 			}
		}
			
		return type;
	}
	
	/**
	 * 
	  * MUST be done
	 * @param gattServices
	 * 		<p><strong>Please send me all GATT services, it will get the  interested services!
	 * @param cb
	 * 		<p> Get something to show in your UI
	 * @return
	 * 		<p> TI is <{@link #BLE_OAD_TYPE_TI}>
	 * 		<p> NODIC is <{@link #BLE_OAD_TYPE_NODIC}>
	 */
	public int init(List<BluetoothGattService> gattServices, IBleOadCallback cb) {
		int type = BLE_OAD_TYPE_UNKOWN;
		for (BluetoothGattService gattService : gattServices) {
			String uuid = gattService.getUuid().toString();
			
			if (uuid == null) {
				continue;
			}
			
			if (uuid.toUpperCase().equals(OAD_UUID_TI_SERVICE.toUpperCase())) {
				
				type = BLE_OAD_TYPE_TI;
				
			} else if (uuid.toUpperCase().equals(UUID_COMMON_SERVICE.toUpperCase())) {
				List<BluetoothGattCharacteristic> gattCharacteristics = gattService
						.getCharacteristics();

				for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
					String gattUuid = gattCharacteristic.getUuid().toString();
					
					if (gattUuid.toUpperCase().equals(OAD_UUID_NORDIC_GATT.toUpperCase())) {
						
						type = BLE_OAD_TYPE_NODIC;
						break;
					}
				}
 			}
			
			if (type == BLE_OAD_TYPE_TI) {
				
				mBleOad = new TIBleOad(mContext, this);
				
			} else if (type == BLE_OAD_TYPE_NODIC) {
				
				mBleOad = new NordicBleOad(mContext, this);
				
			}
			
			if (mBleOad != null) {
				mBleOad.setBluetoothGattService(gattService);
				mBleOad.setBleOadCallback(cb);
				break;
			}
			
		}
		
		if (mBleOad == null) {
			
			Log.e(TAG, "ERROR: mBleOad failed to init");
		}
		
		return type;
		
	}
	
	/**
	 * when {@link #init} is ok to be done, then start OAD 
	 */
	public void startBleOad() {
		if (mBleOad == null) {
			Log.e(TAG, "ERROR:mBleOad is not inited");
			return;
		}
		mBleOad.startBleOad();
	}
	
	/**
	 * Please give me the values of ACTION_DATA_NOTIFY
	 * @param uuid
	 * 		<P> IMPORTANT: get it from BLE to check if the uuid is our OAD's
	 * @param values
	 * 		<P> IMPORTANT: get the values from ble
	 */
	public void onReceiveNoti(String uuid, byte[] values) {
		if (mBleOad == null) {
			Log.e(TAG, "ERROR:mBleOad is not inited");
			return;
		}
		mBleOad.onReceiveNoti(uuid, values);
	}
	
	/**
	 * 
	 * @param updataFilePath
	 * 		<p>the path of update bin file
	 * 		<p>if not set, try to use the bin file in asserts. BUT it is for test.
	 */
	
	public void setUpdateFilePath(String updataFilePath) {
		if (mBleOad == null) {
			Log.e(TAG, "ERROR:mBleOad is not inited");
			return;
		}
		mBleOad.setUpdateFilePath(updataFilePath);
	}
	
	public String getUpdateFilePath() {
		if (mBleOad == null) {
			Log.e(TAG, "ERROR:mBleOad is not inited");
			return null;
		}
		return mBleOad.getUpdateFilePath();
	}
	
	/**
	 * 
	 * @param version
	 * 			<p>if version of firmware is greater than current bin file, refuse to update.
	 * 			<p>if version is 0, always to update.
	 */
	public void setFirmwareVersion(int version) {
		if (mBleOad == null) {
			Log.e(TAG, "ERROR:mBleOad is not inited");
			return;
		}
		mBleOad.setFirmwareVersion(version);
	}
	
	public int getFirmwareVersion() {
		if (mBleOad == null) {
			Log.e(TAG, "ERROR:mBleOad is not inited");
			return -1;
		}
		return mBleOad.getFirmwareVersion();
	}
	
	public abstract boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable);
	public abstract boolean writeCharacteristic(BluetoothGattCharacteristic characteristic);
}
