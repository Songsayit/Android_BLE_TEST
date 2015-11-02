package com.ti.android_lightblue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.android.bleoad.BleOadManager;
import com.android.bleoad.IBleOadCallback;
import com.android.bleoad.IPlatformBleOad;
import com.ti.android_lightblue.common.BluetoothLeService;
import com.ti.android_lightblue.common.GattInfo;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class DeviceControlActivity extends Activity {
	private final static String TAG = DeviceControlActivity.class
			.getSimpleName();
	public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

	private final String WRN_UUID = "00009025-0000-1000-8000-00805f9b34fb";
	private final String TEMPERATURE_UUID = "00002a1c-0000-1000-8000-00805f9b34fb";
	
	///////////////////////////////////////////////////////////////////
	//transport protocols (tp for short)
	private final byte TP_HEADER_VALUE = (byte) 0xAA;
	private final byte TP_CMDS_SET_ALARM_TEMP = (byte)0x81;
	private final byte TP_CMDS_GET_ALARM_TEMP = (byte) 0x82;
	private final byte TP_CMDS_GET_BAT_ADC = (byte) 0x83;
	private final byte TP_CMDS_GET_VERSION = (byte) 0x84;
	private final byte TP_CMDS_GET_TEMPERATURE = (byte) 0x86;
	private final byte TP_CMDS_GET_CALIBRATE_VALUE = (byte) 0x87;
	
	
	//////////////////////////////////////////////////////////////////
	private Context mContext;

	private String mDeviceName;
	private String mDeviceAddress;

	private BluetoothDevice mBluetoothDevice;
	private BluetoothLeService mBluetoothLeService;
	private BluetoothAdapter mBtAdapter;
	
	private BluetoothGattCharacteristic mTempGattChara = null;
	private BluetoothGattCharacteristic mRWGattChara = null;
	
	private TextView mTxtShowTemp;
	private TextView mTxtShowTempuV;
	
	private Button mBtnGetCalibration;
	private TextView mTxtShowCalibration;
	private TextView mTxtConnectedState;
	
	private Button mBtnSetAlarmTemp;
	private EditText mEdtAlarmTemp;
	private CheckBox mChbFahrenhet;
	
	private Button mBtnGetAlarmTemp;
	private TextView mTxtShowAlarmTemp;
	
	
	private Button mBtnGetBatV;
	private TextView mTxtShowBatV;
	
	private Button mBtnGetVersion;
	private TextView mTxtShowVersion;
	
	private TextView mTxtShowLog ;
	private ScrollView mScrollViewShowLog ;
	
	private Button mBtnStartOad;
	private ProgressBar mUpdateBinProgressBar;
	
	private Button mBtnSelectBin;
	private TextView mTxtBinPath;
	
	private BleOadManager mBleOadManager;
	private long mOadTime_us;
	
	private long disconnectCount = 0;

	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName,
				IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service)
					.getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}
			// Automatically connects to the device upon successful start-up
			// initialization.
			mBluetoothLeService.connect(mDeviceAddress);

		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	// Handles various events fired by the Service.
	// ACTION_GATT_CONNECTED: connected to a GATT server.
	// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	// ACTION_DATA_AVAILABLE: received data from the device. This can be a
	// result of read
	// or notification operations.
	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {

				Log.e(TAG, "gatt connected.");
				Toast.makeText(mContext, "connected", Toast.LENGTH_SHORT)
						.show();

				mHandler.obtainMessage(MSG_SHOW_CONN_STATE, 1, 0).sendToTarget();
				
				logAppend("gatt connected--haha.");
				mHandler.removeMessages(MSG_TRY_CONNECT_BLE);
				
			} else if (BluetoothLeService.ACTION_GATT_DISCONNECTED
					.equals(action)) {
				
				mHandler.obtainMessage(MSG_SHOW_CONN_STATE, 0, 0).sendToTarget();
				
				logAppend("==========>gatt disconnected. try to reconnect... cnt = " + (++disconnectCount));
				
//				mHandler.sendEmptyMessageDelayed(MSG_TRY_CONNECT_BLE, 500);

			} else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED
					.equals(action)) {
				// Show all the supported services and characteristics on the
				// user interface.
				displayGattServices(mBluetoothLeService
						.getSupportedGattServices());
				
			} else if (BluetoothLeService.ACTION_DATA_NOTIFY.equals(action)) {
				byte[] values = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
				String uuid = intent.getStringExtra(BluetoothLeService.EXTRA_UUID);
				Log.d(TAG, "ACTION_DATA_NOTIFY uuid = " + uuid);
				
				
				if (uuid.toUpperCase().equals(TEMPERATURE_UUID.toUpperCase())
						|| uuid.toUpperCase().contains(WRN_UUID.toUpperCase())) {
					
					processValues(values);
					
				} else {
					
					mBleOadManager.onReceiveNoti(uuid, values);
				}
			} else if (BluetoothLeService.ACTION_DATA_WRITE.equals(action)) {
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);

		
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,   
				  WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		
		mContext = this;
		
		setContentView(R.layout.activity_main);
		mTxtShowTemp = (TextView)findViewById(R.id.txt_show_temp);
		mTxtShowTempuV = (TextView)findViewById(R.id.txt_show_temp_uV);
		
		mBtnGetCalibration = (Button)findViewById(R.id.btn_get_calibration);
		mTxtShowCalibration = (TextView)findViewById(R.id.txt_show_calibration);
		mTxtConnectedState = (TextView)findViewById(R.id.txt_show_connected_state);
		
		mBtnSetAlarmTemp = (Button)findViewById(R.id.btn_set_alarm_temp);
		mEdtAlarmTemp = (EditText)findViewById(R.id.edt_alarm_temp);
		mChbFahrenhet = (CheckBox)findViewById(R.id.chb_fahrenheit);
		
		mBtnGetAlarmTemp = (Button)findViewById(R.id.btn_get_alarm_temp);
		mTxtShowAlarmTemp = (TextView)findViewById(R.id.txt_show_alarm_temp);
		
		mBtnGetBatV = (Button)findViewById(R.id.btn_get_bat_v);
		mTxtShowBatV = (TextView)findViewById(R.id.txt_show_bat_v);
		
		mBtnGetVersion = (Button)findViewById(R.id.btn_get_version);
		mTxtShowVersion = (TextView)findViewById(R.id.txt_show_version);
		
		mBtnStartOad = (Button)findViewById(R.id.btn_start_oad);
		mUpdateBinProgressBar = (ProgressBar)findViewById(R.id.progressbar_oad);
		
		mBtnSelectBin = (Button)findViewById(R.id.btn_select_bin);
		mTxtBinPath = (TextView)findViewById(R.id.txt_show_bin_path);
		
		mTxtShowLog = (TextView) findViewById(R.id.txt_show_log);
		mScrollViewShowLog = (ScrollView) findViewById(R.id.sv_show_log);
		mScrollViewShowLog.post(new Runnable() {
             @Override
              public void run() {
            	 mScrollViewShowLog.fullScroll(ScrollView.FOCUS_DOWN);
               }
          });

		mDeviceName = getIntent().getStringExtra(EXTRAS_DEVICE_NAME);
		mDeviceAddress = getIntent().getStringExtra(EXTRAS_DEVICE_ADDRESS);

		getActionBar().setTitle(mDeviceName);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
		
		mBtnGetCalibration.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				byte[] val = new byte[4];
				val[0] = TP_HEADER_VALUE;
				val[1] = 0x01;
				val[2] = TP_CMDS_GET_CALIBRATE_VALUE;
				val[3] = checkSum(val, val.length - 1);

				if (mRWGattChara != null && mBluetoothLeService != null) {
					mRWGattChara.setValue(val);
					mBluetoothLeService.writeCharacteristic(mRWGattChara);
				}
				mTxtShowCalibration.setText(null);
			}
			
		});
		
		mBtnSetAlarmTemp.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				byte[] val = new byte[9];
				val[0] = TP_HEADER_VALUE;
				val[1] = 0x06;
				val[2] = TP_CMDS_SET_ALARM_TEMP;
				val[3] = 0;//base temp L
				val[4] = 0;//base temp H
				
				String alarmStr = mEdtAlarmTemp.getText().toString();
				
				float alarm_val = Float.parseFloat(alarmStr);
				Float f = Float.valueOf(alarm_val * 100);
				int alarmTemp = f.intValue();
				Log.e(TAG, "alarmTemp = " + alarmTemp);
				val[5] = (byte)(alarmTemp & 0xff);//alarm temp L
				val[6] = (byte)(alarmTemp >> 8 & 0xff);//alarm temp H
				
				byte isFahrenheit = 0;
				if (mChbFahrenhet.isChecked()) {
					isFahrenheit = 1;
				}
				val[7] = isFahrenheit; //is Fahrenheit
				val[8] = checkSum(val, val.length - 1);
				
				
				StringBuilder strB = new StringBuilder();
				for (int i = 0; i < val.length; i++) {
					strB.append(Integer.toHexString(val[i]));
					strB.append(":");
				}
				Log.e(TAG, "SetAlarm :values = " + strB.toString());
				
				if (mRWGattChara != null && mBluetoothLeService != null) {
					mRWGattChara.setValue(val);
					mBluetoothLeService.writeCharacteristic(mRWGattChara);
				}
			}
		});
		
		mBtnGetAlarmTemp.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				byte[] val = new byte[4];
				val[0] = TP_HEADER_VALUE;
				val[1] = 0x01;
				val[2] = TP_CMDS_GET_ALARM_TEMP;
				val[3] = checkSum(val, val.length - 1);

				if (mRWGattChara != null && mBluetoothLeService != null) {
					mRWGattChara.setValue(val);
					mBluetoothLeService.writeCharacteristic(mRWGattChara);
				}
				
				mTxtShowAlarmTemp.setText(null);
			}
		});
		
		mBtnGetBatV.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				byte[] val = new byte[4];
				val[0] = TP_HEADER_VALUE;
				val[1] = 0x01;
				val[2] = TP_CMDS_GET_BAT_ADC;
				val[3] = checkSum(val, val.length - 1);

				if (mRWGattChara != null && mBluetoothLeService != null) {
					mRWGattChara.setValue(val);
					mBluetoothLeService.writeCharacteristic(mRWGattChara);
				}
				
				mTxtShowBatV.setText(null);
			}
		});
		
		mBtnGetVersion.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				byte[] val = new byte[4];
				val[0] = TP_HEADER_VALUE;
				val[1] = 0x01;
				val[2] = TP_CMDS_GET_VERSION;
				val[3] = checkSum(val, val.length - 1);

				if (mRWGattChara != null && mBluetoothLeService != null) {
					mRWGattChara.setValue(val);
					mBluetoothLeService.writeCharacteristic(mRWGattChara);
				}
				
				mTxtShowVersion.setText(null);
			}
		});
		
		mBtnStartOad.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				mBleOadManager.setUpdateFilePath(mTxtBinPath.getText().toString());
				mBleOadManager.startBleOad();
				mOadTime_us = SystemClock.elapsedRealtime();
				
				
			}
		});
		
		mBtnSelectBin.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				showDialog(openfileDialogId);
			}
		});
		
		mBleOadManager = new BleOadManager(mContext) {

			@Override
			public boolean setCharacteristicNotification(
					BluetoothGattCharacteristic characteristic, boolean enable) {
				// TODO Auto-generated method stub
				if (mBluetoothLeService != null) {
					return mBluetoothLeService.setCharacteristicNotification(characteristic, enable);
				}
				
				return false;
			}

			@Override
			public boolean writeCharacteristic(
					BluetoothGattCharacteristic characteristic) {
				// TODO Auto-generated method stub
				if (characteristic != null && mBluetoothLeService != null) {
					return mBluetoothLeService.writeCharacteristic(characteristic);
				}
				
				return false;
			}
			
		};
		
	}
	
	static private int openfileDialogId = 0;
	
	@Override
	protected Dialog onCreateDialog(int id) {
		// TODO Auto-generated method stub
		if(id==openfileDialogId){
			Map<String, Integer> images = new HashMap<String, Integer>();
			// 下面几句设置各文件类型的图标， 需要你先把图标添加到资源文件夹
			images.put(OpenFileDialog.sRoot, R.drawable.filedialog_root);	// 根目录图标
			images.put(OpenFileDialog.sParent, R.drawable.filedialog_folder_up);	//返回上一层的图标
			images.put(OpenFileDialog.sFolder, R.drawable.filedialog_folder);	//文件夹图标
			images.put("bin", R.drawable.filedialog_file);	//wav文件图标
			images.put(OpenFileDialog.sEmpty, R.drawable.filedialog_root);
			Dialog dialog = OpenFileDialog.createDialog(id, this, "打开文件", new CallbackBundle() {
				@Override
				public void callback(Bundle bundle) {
					String filepath = bundle.getString("path");
					mHandler.obtainMessage(MSG_SET_BIN_PATH, filepath).sendToTarget();
				}
			}, 
			".bin;",
			images);
			return dialog;
		}
		return null;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		mHandler.sendEmptyMessageDelayed(MSG_TRY_CONNECT_BLE, 10);
		
	}

	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mGattUpdateReceiver);
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		unbindService(mServiceConnection);
		mBluetoothLeService = null;
	}

	private void displayGattServices(List<BluetoothGattService> gattServices) {
		if (gattServices == null)
			return;
		
		int type = mBleOadManager.init(gattServices, mBleOadCallback);
		
		String uuid = null;
		BluetoothGattService theGattService = null;
		// Loops through available GATT Services.
		for (BluetoothGattService gattService : gattServices) {
			uuid = gattService.getUuid().toString();
			
			Log.e(TAG, "uuid = " + uuid);
			if (GattInfo.isBtSigUuid(gattService.getUuid())) {
				if (uuid.contains("1809")) {
					theGattService = gattService;
					break;
				}
			}
		}

		if (theGattService == null)
			return;

		List<BluetoothGattCharacteristic> gattCharacteristics = theGattService
				.getCharacteristics();

		for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
			uuid = gattCharacteristic.getUuid().toString();
			Log.e(TAG, "chara uuid = " + uuid);

			int prop = gattCharacteristic.getProperties();
			Log.e(TAG, "prop = " + getPropertyDescription(prop));
			
			if (uuid.toUpperCase().equals(TEMPERATURE_UUID.toUpperCase())) {
				mTempGattChara = gattCharacteristic;
			}
			if (uuid.toUpperCase().equals(WRN_UUID.toUpperCase())) {
				mRWGattChara = gattCharacteristic;
			}
		}
		
		mHandler.sendEmptyMessage(MSG_HANDLER_TempGattChara);
		mHandler.sendEmptyMessageDelayed((MSG_HANDLER_RWGattChara), 100);
	}

	private String getPropertyDescription(int prop) {
		String str = new String();

//		if ((prop & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
//			str = str + "R";
//		}
		
		if ((prop & 0x2) > 0)
			str = str + "R";
		if ((prop & 0x8) > 0)
			str = str + "W";
		if ((prop & 0x10) > 0)
			str = str + "N";
		if ((prop & 0x20) > 0)
			str = str + "I";
		if ((prop & 0x4) > 0)
			str = str + "*";
		if ((prop & 0x1) > 0)
			str = str + "B";
		if ((prop & 0x80) > 0)
			str = str + "E";
		if ((prop & 0x40) > 0)
			str = str + "S";
		return str;
	}

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
		intentFilter
				.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_NOTIFY);
		intentFilter.addAction(BluetoothLeService.ACTION_DATA_WRITE);
		return intentFilter;
	}
	
	private void processValues(byte[] values) {
		
		StringBuilder strB = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			String hex = Integer.toHexString(values[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
			strB.append(hex.toUpperCase() + ": ");
		}
		Log.i(TAG, "ble values = " + strB.toString());
		
		if (values.length <= 2) {
			Log.e(TAG, "values.length " + values.length + " is smaller than 2");
			return;
		}
		
		int pos = 0;
		byte header = values[pos++];
		byte len = values[pos++];
		
		if (values.length <= len + 2) {
			Log.e(TAG, "values.length " + values.length + " is smaller than (or equal) " + (len + 2));
			return;
		}
		
		byte cmd = values[pos++];
		byte theCrcSum = values[len + 2];
		if (header != TP_HEADER_VALUE) {
			Log.e(TAG, "header " + header + " is not ours type!!!");
			return;
		}
		byte crcSum = checkSum(values, len + 2);
		if (crcSum != theCrcSum) {
			Log.e(TAG, "crcSum " + crcSum + " is not equals value crcSum( " + theCrcSum +")");
			return;
		}
		switch (cmd) {
		case TP_CMDS_GET_TEMPERATURE:
			int temp = 0;
			int temp_adc = 0;
			//1 means cmd; len = cmd + content
			int isFah = values[pos++];
			
			for (int i = 0; i < 2; i++) {
				temp += ((values[pos + i] & 0xff) << (i * 8));
			} 
			pos += 2;
			for (int i = 0; i < 4; i++) {
				temp_adc += ((values[pos + i] & 0xff) << (i * 8));
			}
			DecimalFormat fmt = new DecimalFormat("0.##");
			StringBuilder sb = new StringBuilder();
			sb.append(fmt.format(temp/100.0));
			mHandler.obtainMessage(MSG_SHOW_TEMP, isFah, 0, sb.toString()).sendToTarget();
			
			mHandler.obtainMessage(MSG_SHOW_TEMP_UV, temp_adc, 0).sendToTarget();
			break;

		case TP_CMDS_GET_CALIBRATE_VALUE:
			int isCalibration = values[pos++];
			int caliVal = 0;
			//2 means cmd and calibration
			for (int i = 0; i < len - 2; i++) {
				caliVal += ((values[pos + i] & 0xff) << (i * 8));
			}
			
			mHandler.obtainMessage(MSG_SHOW_CALI, isCalibration, caliVal).sendToTarget();
			break;
			
		case TP_CMDS_GET_ALARM_TEMP:
			int isFahrenheit = values[pos++];
			int alarm_temp = 0;
			//1 means cmd; len = cmd + content
			for (int i = 0; i < len - 2; i++) {
				alarm_temp += ((values[pos + i] & 0xff) << (i * 8));
			}
			
			mHandler.obtainMessage(MSG_SHOW_ALARM_TEMP, alarm_temp, isFahrenheit).sendToTarget();
			
			break;
		case TP_CMDS_GET_BAT_ADC:
			int bat_adc = 0 ;
			for (int i = 0; i < len - 1; i++) {
				bat_adc += ((values[pos + i] & 0xff) << (i * 8));
			}
			mHandler.obtainMessage(MSG_SHOW_BAT_ADC, bat_adc, 0).sendToTarget();
			
			break;
			
		case TP_CMDS_GET_VERSION:
			int version = 0;
			for (int i = 0; i < len - 1; i++) {
				version += ((values[pos + i] & 0xff) << (i * 8));
			}
			
			mHandler.obtainMessage(MSG_SHOW_VERSION, version, 0).sendToTarget();
			
			break;
			
		default:
			break;
		}
	}

	private byte checkSum(byte[] pack, int len) {

		int i = 0;
		int sum = 0;
		for(i = 0; i < len; i++) {
			sum += pack[i];
		}

		return (byte)(sum & 0x0000ff);
	}
	
	
	private final int MSG_HANDLER_TempGattChara = 1;
	private final int MSG_HANDLER_RWGattChara = 2;
	private final int MSG_SHOW_TEMP = 3;
	private final int MSG_SHOW_CALI = 4;
	private final int MSG_SHOW_CONN_STATE = 5;
	private final int MSG_SHOW_ALARM_TEMP = 6;
	private final int MSG_SHOW_TEMP_UV = 7;
	private final int MSG_SHOW_BAT_ADC = 8;
	private final int MSG_SHOW_VERSION = 9;
	private final int MSG_TRY_CONNECT_BLE = 80;
	private final int MSG_SET_BIN_PATH = 90;
	private final int MSG_BIN_FILE_FAIL = 91;
	private final int TO_V_VALUE = 1000000;
	private Handler mHandler = new Handler(){
		public void handleMessage(android.os.Message msg) {
			final int what = msg.what;
			switch (what) {
			case MSG_HANDLER_TempGattChara:
				if (mTempGattChara != null) {
					Log.e(TAG, "set temp gatt chara enable");
					if (!mBluetoothLeService.setCharacteristicNotification(mTempGattChara, true)) {
						mHandler.sendEmptyMessageDelayed((MSG_HANDLER_TempGattChara), 500);
					}
				}
				break;
			case MSG_HANDLER_RWGattChara:
				if (mRWGattChara != null) {
					Log.e(TAG, "set rw gatt chara enable");
					if (!mBluetoothLeService.setCharacteristicNotification(mRWGattChara, true)) {
						mHandler.sendEmptyMessageDelayed((MSG_HANDLER_RWGattChara), 500);
					}
				}
				break;
				
			case MSG_SHOW_TEMP:
				String temp = (String) msg.obj;
				int isFah = msg.arg1;
				String strC = "℃";
				if (isFah > 0) {
					strC = "℉";
				}
				mTxtShowTemp.setText(temp + " " + strC);
				break;
				
			case MSG_SHOW_CALI:
				int isCal = msg.arg1;
				String isCalStr = (isCal == 0) ? "NO" : "YES";
				int calVal = msg.arg2;
//				StringBuilder calSB = new StringBuilder();
//				calSB.append(calVal / TO_V_VALUE);
//				calSB.append(".");
//				calSB.append(calVal % TO_V_VALUE);
				
//				float f_cal = (float)((double)calVal/TO_V_VALUE);
//				BigDecimal   b_cal   =   new   BigDecimal(f_cal);  
//				float   f1_cal   =   b_cal.setScale(6,   BigDecimal.ROUND_HALF_UP).floatValue();  
//				
//				String calShow = String.format(getResources().getString(R.string.set_calibration),
//						isCalStr, Float.toString(f1_cal));
				
				String calShow = String.format(getResources().getString(R.string.set_calibration),
						isCalStr, (calVal+ ""));
				mTxtShowCalibration.setText(calShow);
				break;
				
			case MSG_SHOW_CONN_STATE:
				int isConn = msg.arg1;
				String str = getResources().getString(R.string.state_disconnected);
				if (isConn == 1) {
					str = getResources().getString(R.string.state_connected);
				}
				mTxtConnectedState.setText(str);
				break;
				
			case MSG_SHOW_ALARM_TEMP:
				int alarm_val = msg.arg1;
//				StringBuilder alarmSB = new StringBuilder();
//				alarmSB.append(alarm_val/100);
//				alarmSB.append(".");
//				alarmSB.append(alarm_val % 100);
				
				float f_alarm = (float)((double)alarm_val/100);
				BigDecimal   b_alarm   =   new   BigDecimal(f_alarm);  
				float   f1_alarm   =   b_alarm.setScale(2,   BigDecimal.ROUND_HALF_UP).floatValue();  
				
				mTxtShowAlarmTemp.setText("报警值是: " + Float.toString(f1_alarm) + " V, " + "isFah = " + msg.arg2);
				break;
			case MSG_SHOW_TEMP_UV:
				int temp_uV = msg.arg1;
				
//				float f_adc = (float)((double)temp_adc/TO_V_VALUE);;
//				BigDecimal   b   =   new   BigDecimal(f_adc);  
//				float   f1   =   b.setScale(6,   BigDecimal.ROUND_HALF_UP).floatValue();  
				mTxtShowTempuV.setText(temp_uV + " uV");
				break;
			case MSG_SHOW_BAT_ADC:
				int bat_uV = msg.arg1;
				float f_adc = (float)((double)bat_uV/TO_V_VALUE);;
				BigDecimal   b   =   new   BigDecimal(f_adc);  
				float   f1   =   b.setScale(6,   BigDecimal.ROUND_HALF_UP).floatValue(); 
				mTxtShowBatV.setText("battery vol = " + Float.toString(f1) + "V");
				break;
				
			case MSG_SHOW_VERSION:
				
				int version = msg.arg1;
				int H = (byte) ((version >> 8) & 0xff);
				int L = (byte) ((version) & 0xff);
				String ver = "V" + H + "." + L;
				mTxtShowVersion.setText(ver);
				
				break;
				
			case MSG_TRY_CONNECT_BLE:
				if (mBluetoothLeService != null) {
					final boolean result = mBluetoothLeService.connect(mDeviceAddress);
					Log.d(TAG, "Connect request result=" + result);
				}
				break;
			case MSG_SET_BIN_PATH:
				String path = (String)msg.obj;
				mTxtBinPath.setText(path);
				Log.e(TAG, "path is " + mTxtBinPath.getText().toString());
				break;
			case MSG_BIN_FILE_FAIL:
				Toast.makeText(mContext, "failed to set bin file.", Toast.LENGTH_SHORT).show();
				break;
				
			default:
				break;
			}
		};
	};
	
	private IBleOadCallback mBleOadCallback = new IBleOadCallback() {
		
		@Override
		public void onTransferInPercent(int percent) {
			// TODO Auto-generated method stub
			mUpdateBinProgressBar.setProgress(percent);
			if (percent == 100) {
				long time = SystemClock.elapsedRealtime();
				long useTime = time - mOadTime_us;
				Toast.makeText(mContext, "use time " + useTime + "us", Toast.LENGTH_LONG).show();
			}
		}
		
		@Override
		public void onError(int reason) {
			// TODO Auto-generated method stub
			Log.e(TAG, "reason = " +  reason);
			if (reason == IBleOadCallback.ERROR_READ_BIN_FILE) {
				mHandler.sendEmptyMessage(MSG_BIN_FILE_FAIL);
			}
		}
	};
	
	private String getTime() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSS--> ");  
		String t=format.format(new Date());  
		return t; 
	}
	 private void logAppend(String string) {
		 StringBuilder sb = new StringBuilder(mTxtShowLog.getText());
		 sb.append("\n");
		 sb.append(getTime());
		 sb.append(string);
		 sb.append("\n");
		 mTxtShowLog.setText(sb.toString());
	 }
	 
}
