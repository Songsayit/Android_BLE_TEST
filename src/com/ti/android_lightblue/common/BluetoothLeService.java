package com.ti.android_lightblue.common;

import java.util.List;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class BluetoothLeService extends Service {
	static final String TAG = "BluetoothLeService";

	public static final String ACTION_DATA_NOTIFY = "ti.android.ble.common.ACTION_DATA_NOTIFY";
	public static final String ACTION_DATA_READ = "ti.android.ble.common.ACTION_DATA_READ";
	public static final String ACTION_DATA_WRITE = "ti.android.ble.common.ACTION_DATA_WRITE";
	public static final String ACTION_GATT_CONNECTED = "ti.android.ble.common.ACTION_GATT_CONNECTED";
	public static final String ACTION_GATT_DISCONNECTED = "ti.android.ble.common.ACTION_GATT_DISCONNECTED";
	public static final String ACTION_GATT_SERVICES_DISCOVERED = "ti.android.ble.common.ACTION_GATT_SERVICES_DISCOVERED";
	public static final String EXTRA_ADDRESS = "ti.android.ble.common.EXTRA_ADDRESS";
	public static final String EXTRA_DATA = "ti.android.ble.common.EXTRA_DATA";
	public static final String EXTRA_STATUS = "ti.android.ble.common.EXTRA_STATUS";
	public static final String EXTRA_UUID = "ti.android.ble.common.EXTRA_UUID";
	private static BluetoothLeService mThis = null;
	private final IBinder binder = new LocalBinder();

	private String mBluetoothDeviceAddress;

	private BluetoothGatt mBluetoothGatt = null;
	private BluetoothManager mBluetoothManager = null;
	private BluetoothAdapter mBtAdapter = null;
	private volatile boolean mBusy = false;
	private BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			// TODO Auto-generated method stub
			Log.i(TAG, "onCharacteristicChanged");
			broadcastUpdate(ACTION_DATA_NOTIFY, characteristic, 0);
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			// TODO Auto-generated method stub
			Log.i(TAG, "onCharacteristicRead");
			broadcastUpdate(ACTION_DATA_READ, characteristic, status);
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			// TODO Auto-generated method stub
			Log.i(TAG, "onCharacteristicWrite");
			broadcastUpdate(ACTION_DATA_WRITE, characteristic, status);
		}

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
				int newState) {
			// TODO Auto-generated method stub
			if (BluetoothLeService.this.mBluetoothGatt == null) {
				Log.e(TAG, "mBluetoothGatt not created!");
				return;
			}
			BluetoothDevice device = gatt.getDevice();
			String address = device.getAddress();
			Log.d(TAG, "onConnectionStateChange (" + address + "), newState = " + newState
					+ " status: " + status);
			switch (newState) {
			case BluetoothProfile.STATE_CONNECTED:
				broadcastUpdate(ACTION_GATT_CONNECTED, address, status);
				   Log.i(TAG, "Attempting to start service discovery:" +
	                        mBluetoothGatt.discoverServices());
				break;
			case BluetoothProfile.STATE_DISCONNECTED:
				broadcastUpdate(ACTION_GATT_DISCONNECTED, address, status);
				break;
			case BluetoothProfile.STATE_CONNECTING:
			case BluetoothProfile.STATE_DISCONNECTING:
			default:
				Log.e(TAG, "New state not processed: " + newState);
				break;
			}
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			// TODO Auto-generated method stub
			mBusy = false;
			Log.i(TAG, "onDescriptorRead = ");
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			// TODO Auto-generated method stub
			mBusy = false;
			Log.i(TAG, "onDescriptorWrite + " );
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			// TODO Auto-generated method stub
			BluetoothDevice device = gatt.getDevice();
			Log.i(TAG, "onServicesDiscovered");
			broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED,
					device.getAddress(), status);
		}

	};

	private void broadcastUpdate(String action,
			BluetoothGattCharacteristic characteristic, int status) {
		Intent intent = new Intent(action);
		intent.putExtra(EXTRA_UUID, characteristic.getUuid().toString());
		intent.putExtra(EXTRA_DATA, characteristic.getValue());
		intent.putExtra(EXTRA_STATUS, status);
		sendBroadcast(intent);
		this.mBusy = false;
	}

	private void broadcastUpdate(String action, String address, int status) {
		Intent intent = new Intent(action);
		intent.putExtra(EXTRA_ADDRESS, address);
		intent.putExtra(EXTRA_STATUS, status);
		sendBroadcast(intent);
		this.mBusy = false;
	}

	private boolean checkGatt() {
		if (this.mBtAdapter == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return false;
		}

		if (this.mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothGatt not initialized");
			return false;
		}

		if (this.mBusy) {
			Log.w(TAG, "LeService busy");
			return false;
		}

		return true;
	}

	public static BluetoothGatt getBtGatt() {
		return mThis.mBluetoothGatt;
	}

	public static BluetoothManager getBtManager() {
		return mThis.mBluetoothManager;
	}

	public static BluetoothLeService getInstance() {
		return mThis;
	}

	public void close() {
		if (this.mBluetoothGatt != null) {
			Log.i(TAG, "close");
			this.mBluetoothGatt.close();
			this.mBluetoothGatt = null;
		}
	}

	public boolean connect(String address) {
		if ((this.mBtAdapter == null) || (address == null)) {
			Log.w(TAG,
					"BluetoothAdapter not initialized or unspecified address.");
			return false;
		}

		Log.e(TAG, "address = " + address);
		
		int connectionState;

		if (mBluetoothDeviceAddress != null
				&& address.equals(mBluetoothDeviceAddress)
				&& mBluetoothGatt != null) {

			Log.d(TAG, "Re-use GATT connection.");
			if (mBluetoothGatt.connect()) {

				connectionState = BluetoothProfile.STATE_CONNECTING;
				Log.w(TAG, "Attempt to connect in state: " + connectionState);
				return true;

			} else {

				Log.w(TAG, "GATT re-connect failed.");
				return false;
			}
		}

		final BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
		if (device == null) {
			Log.w(TAG, "Device not found.  Unable to connect.");
			return false;
		}

		Log.d(TAG, "Create a new GATT connection.");

		mBluetoothGatt = device.connectGatt(this, true, this.mGattCallbacks);
		mBluetoothDeviceAddress = address;

		connectionState = mBluetoothManager.getConnectionState(device,
				BluetoothProfile.GATT);
		Log.w(TAG, "Attempt to connect in state: " + connectionState);

		return true;
	}

	public void disconnect(String address) {
		if (mBtAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "disconnect: BluetoothAdapter not initialized");
			return;
		}
		BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
		int connectionState = mBluetoothManager.getConnectionState(device,
				BluetoothProfile.GATT);

		Log.w(TAG, "Attempt to disconnect in state: " + connectionState);
		if (connectionState != BluetoothProfile.STATE_DISCONNECTED) {
			mBluetoothGatt.disconnect();
			Log.i(TAG, "disconnect");
		}
	}

	public int getNumServices() {
		if (mBluetoothGatt == null)
			return 0;
		return mBluetoothGatt.getServices().size();
	}

	public List<BluetoothGattService> getSupportedGattServices() {
		if (this.mBluetoothGatt == null)
			return null;
		return this.mBluetoothGatt.getServices();
	}

	public boolean initialize() {
		Log.d(TAG, "initialize");
		mThis = this;
		if (mBluetoothManager == null) {
			mBluetoothManager = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE));
			if (mBluetoothManager == null) {
				Log.e(TAG, "Unable to initialize BluetoothManager.");
				return false;
			}

		}

		mBtAdapter = mBluetoothManager.getAdapter();
		if (mBtAdapter == null) {
			Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
			return false;
		}

		return true;
	}

	public boolean isNotificationEnabled(
			BluetoothGattCharacteristic characteristic) {

		if (!checkGatt())
			return false;

		BluetoothGattDescriptor clientConfig;
		do {
			clientConfig = characteristic
					.getDescriptor(GattInfo.CLIENT_CHARACTERISTIC_CONFIG);
		} while ((clientConfig == null)
				|| (clientConfig.getValue() != BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE));

		return true;
	}

	public int numConnectedDevices() {
		BluetoothGatt localBluetoothGatt = mBluetoothGatt;
		int num = 0;
		if (localBluetoothGatt != null) {
			List<BluetoothDevice> devList = mBluetoothManager
					.getConnectedDevices(BluetoothProfile.GATT);
			num = devList.size();
		}
		return num;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return this.binder;
	}

	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy() called");
		if (this.mBluetoothGatt != null) {
			this.mBluetoothGatt.close();
			this.mBluetoothGatt = null;
		}
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "Received start id " + startId + ": " + intent);
		return startId;
	}

	public boolean onUnbind(Intent intent) {
		close();
		return super.onUnbind(intent);
	}

	public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (!checkGatt())
			return;

		this.mBusy = true;
		this.mBluetoothGatt.readCharacteristic(characteristic);
	}

	
	public boolean setCharacteristicNotification(
			BluetoothGattCharacteristic characteristic, boolean enable) {
		if (!checkGatt())
			return false;
		BluetoothGattDescriptor clientConfig;
		do {

			if (!mBluetoothGatt.setCharacteristicNotification(characteristic,
					enable)) {
				Log.w(TAG, "setCharacteristicNotification failed");
				return false;
			} else {
				Log.w(TAG, "setCharacteristicNotification ok");
			}

			clientConfig = characteristic.getDescriptor(GattInfo.CLIENT_CHARACTERISTIC_CONFIG);
		} while (clientConfig == null);
		
		
		
		if (enable) {
			Log.i(TAG, "enable notification");
			int prop = characteristic.getProperties();
			if ((prop & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
				clientConfig.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
			} else {
				clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			}
		} else {
			Log.i(TAG, "disable notification");
			clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
			
		}

		mBusy = true;
		return mBluetoothGatt.writeDescriptor(clientConfig);
	}

	public boolean waitIdle(int i) {

		
		// do {
		//
		// } while(this.mBusy);
		//
		// while (true) {
		// i--;
		// if (i <= 0)
		// break;
		// do {
		// if (i <= 0)
		// break;
		// return true;
		// } while (!this.mBusy);
		// try {
		// Thread.sleep(10L);
		// } catch (InterruptedException e) {
		// e.printStackTrace();
		// }
		//
		// }

		return false;
	}

	public boolean writeCharacteristic(
			BluetoothGattCharacteristic characteristic) {
		if (!checkGatt())
			return false;

		this.mBusy = true;
		return this.mBluetoothGatt.writeCharacteristic(characteristic);
	}

	public boolean writeCharacteristic(
			BluetoothGattCharacteristic characteristic, byte b) {
		if (!checkGatt())
			return false;

		byte[] val = new byte[1];
		val[0] = b;
		characteristic.setValue(val);
		this.mBusy = true;
		return this.mBluetoothGatt.writeCharacteristic(characteristic);
	}

	public boolean writeCharacteristic(
			BluetoothGattCharacteristic characteristic, boolean b) {
		if (!checkGatt())
			return false;

		byte[] val = new byte[1];
		byte v = 0;
		if (b) {
			v = 1;
		}
		val[0] = v;
		characteristic.setValue(val);
		this.mBusy = true;
		return this.mBluetoothGatt.writeCharacteristic(characteristic);
	}

	public class LocalBinder extends Binder {
		public LocalBinder() {
		}

		public BluetoothLeService getService() {
			return BluetoothLeService.this;
		}
	}
}
