package com.android.bleoad;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.os.Handler;
import android.util.Log;

public class NordicBleOad extends IPlatformBleOad {

	private final String TAG = "NordicBleOad";
	
	private final String WRITE_UUID = "00009024-0000-1000-8000-00805f9b34fb";
	private BluetoothGattCharacteristic mWritableChara = null;
	
	////////////////////////////////////////////////////////
	private final byte TP_CMDS_RESULT_FAIL = (byte) 0xff;  // 0XFF IS FAILED.
	
	
	private final byte TP_CMDS_DFU_START = (byte) 0x93;
	private final byte TP_CMDS_DFU_DLFW = (byte) 0x96;
	
	private static final byte DFU_SEND_PACK_TAG = (byte)0xd1;
	private static final byte DFU_REV_PACK_TAG = (byte)0xc6;
	
	private final int BLOCK_SIZE = 18;
	private byte[] mUpdateBuffer;
	private ArrayList<byte[]> mSendPackageList = new ArrayList<byte[]>();
	private int mSendPackageIndex = 0;
	
	/*
	 * this is cmd and flag about DFU.
	 */
	private final byte TP_DFU_REQ_DATA	= (byte) 0x10;
	private final byte TP_DFU_PACKET_RECVED= (byte) 0x13;
	///////////////////////////////////////////////////////
	
	
	private final int MSG_SET_NOTI = 20000;
	private final int MSG_WR_CHARA = MSG_SET_NOTI + 1;
	private final int MSG_DFU_SEND_PACK = MSG_SET_NOTI + 10;
	private final int MSG_TRANSFER_PERCENT = MSG_SET_NOTI + 11;
	private final int MSG_ERR_READ_BIN_FILE = MSG_SET_NOTI + 100;
	private final int MSG_ERR_TRANSFER = MSG_ERR_READ_BIN_FILE + 1;
	private Handler mHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {

			final int what = msg.what;
			boolean ok = false;
			switch (what) {
			case MSG_SET_NOTI:
				ok = mBleOadManager.setCharacteristicNotification(mWritableChara, true);
				if (!ok) {
					mHandler.sendEmptyMessageDelayed(MSG_SET_NOTI, 1*1000);
				}
				break;
			case MSG_WR_CHARA:
				
				ok = mBleOadManager.writeCharacteristic(mWritableChara);
				if (!ok) {
					mHandler.sendEmptyMessageDelayed(MSG_WR_CHARA, 50);
				}
				
				break;
			case MSG_TRANSFER_PERCENT:
				if (mBleOadCallback != null) {
					int percent = (mHadSentPackLength * 100) / mUpdateBuffer.length;
					mBleOadCallback.onTransferInPercent(0 == percent ? 1 : percent);
				}
				break;
			case MSG_ERR_READ_BIN_FILE:
				if (mBleOadCallback != null) {
					mBleOadCallback.onError(IBleOadCallback.ERROR_READ_BIN_FILE);
				}
				break;
			case MSG_DFU_SEND_PACK:
				sendBuffer(mSendPackageIndex);
				break;
			case MSG_ERR_TRANSFER:
				if (mBleOadCallback != null) {
					mBleOadCallback.onError(IBleOadCallback.ERROR_TRANSFER);
				}
			default:
				break;
			}
		};
	};
	
	public NordicBleOad(Context c, BleOadManager mgr) {
		super(c, mgr);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void startBleOad() {
		// TODO Auto-generated method stub
		byte[] val = new byte[3];
		val[0] = DFU_SEND_PACK_TAG;
		val[1] = (byte) TP_CMDS_DFU_START;
		val[2] = 0x01; // request to dfu.

		Log.i(TAG, "startBleOad val = " + Utils.toHexString(val));
		
		if (mWritableChara != null) {
			mWritableChara.setValue(val);
			mHandler.sendEmptyMessage(MSG_WR_CHARA);
		}
		
	}

	@Override
	public void setBluetoothGattService(BluetoothGattService service) {
		// TODO Auto-generated method stub
		if (service == null)
			return;
		List<BluetoothGattCharacteristic> gattCharacteristics = service
				.getCharacteristics();
		
		for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
			String uuid = gattCharacteristic.getUuid().toString();

			if (uuid.toUpperCase().equals(WRITE_UUID.toUpperCase())) {
				mWritableChara = gattCharacteristic;
				break;
			}
		}
		
		if (mWritableChara != null) {
			mHandler.sendEmptyMessage(MSG_SET_NOTI);
		}
		
	}

	@Override
	public void onReceiveNoti(String uuid, byte[] values) {
		// TODO Auto-generated method stub
		if (WRITE_UUID.toUpperCase().equals(uuid.toUpperCase())) {
			processValues(values);
		}
		
	}
	
	private int mHadSentPackLength = 0;
	
	private void sendBuffer(int index) {
		if (mSendPackageList.isEmpty()) {
			Log.e(TAG, "mSendPackageList is empty!!!");
			return;
		}
		
		byte[] buffer = mSendPackageList.get(index);
		
		mWritableChara.setValue(buffer);
		mHandler.sendEmptyMessage(MSG_WR_CHARA);
		
		mHadSentPackLength += (buffer.length - 2);
		
		mHandler.sendEmptyMessage(MSG_TRANSFER_PERCENT);
	}
	
	private void processValues(byte[] values) {
		
		int pos = 0;
		byte tag = values[pos++];
		if (tag != DFU_REV_PACK_TAG) {
			Log.e(TAG, "tag (" + tag + ") is not " + DFU_REV_PACK_TAG);
			return;
		}
		
		byte cmd = values[pos++];
		if (cmd != TP_CMDS_DFU_DLFW) {
			Log.e(TAG, "cmd (" + cmd + ") is not " + TP_CMDS_DFU_DLFW);
			return;
		}
		
		byte sub_cmd = values[pos++];
		if (sub_cmd == TP_CMDS_RESULT_FAIL) {
			Log.e(TAG, "result is fail");
			mSendPackageList.clear();
			mHandler.removeMessages(MSG_DFU_SEND_PACK);
			return;
		}
		
		if( TP_DFU_REQ_DATA == sub_cmd ) {
			int offset = bytesToInt( values , pos );
			pos += 4;
			int length = bytesToInt( values , pos );
			Log.e(TAG, "TP_CMDS_DFU_DLFW ,offset=" + offset + ",length=" + length);
			
			byte[] readBuffer = getUpdateBuffer(offset, length);
			if (readBuffer == null) {
				Log.e(TAG, "readBuffer is null");
				return;
			}
			mHadSentPackLength = offset;
			mSendPackageList.clear();
			for (int i = 0; i < readBuffer.length / BLOCK_SIZE; i++) {
				byte[] packBuffer = new byte[BLOCK_SIZE + 2];
				packBuffer[0] = DFU_SEND_PACK_TAG;
				packBuffer[1] = TP_CMDS_DFU_DLFW;
				System.arraycopy(readBuffer, i*BLOCK_SIZE, packBuffer, 2, BLOCK_SIZE);
				mSendPackageList.add(packBuffer);
			}
			int remainder = readBuffer.length % BLOCK_SIZE;
			if (remainder > 0) {
				byte[] packBuffer = new byte[remainder + 2];
				packBuffer[0] = DFU_SEND_PACK_TAG;
				packBuffer[1] = TP_CMDS_DFU_DLFW;
				System.arraycopy(readBuffer, readBuffer.length - remainder, packBuffer, 2, remainder);
				mSendPackageList.add(packBuffer);
			}
			mSendPackageIndex = 0;
			mHandler.sendEmptyMessage(MSG_DFU_SEND_PACK);
			
		} else if (TP_DFU_PACKET_RECVED == sub_cmd) {
			if (!(mSendPackageList.isEmpty())
					&& mSendPackageList.size() > (++mSendPackageIndex)) {
				mHandler.sendEmptyMessage(MSG_DFU_SEND_PACK);
			}
		}
	}

	private byte[] getUpdateBuffer(int offset, int length) {
		if (mUpdateBuffer == null) {
			mUpdateBuffer = readUpdateFile();
		}
		if (mUpdateBuffer == null || (offset + length) > mUpdateBuffer.length) {
			return null;
		}
		
		byte[] buffer = new byte[length];
		System.arraycopy(mUpdateBuffer, offset, buffer, 0, length);
		return buffer;
		
	}
	
	private byte[] readUpdateFile() {
		
		String updateFilePath = getUpdateFilePath();
		if (updateFilePath == null) {
			mHandler.sendEmptyMessage(MSG_ERR_READ_BIN_FILE);
			return null;
		}
		InputStream updateFileInputStream;
		byte[] buffer = null;
		
		try {
			updateFileInputStream = new FileInputStream(updateFilePath);
			
			int available = updateFileInputStream.available();
			
			buffer = new byte[available];
			int readed = updateFileInputStream.read(buffer);
			
			updateFileInputStream.close();
			
			if( available != readed ){
				buffer = null;
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
		}
		
		return buffer;
	}
	
	public static int bytesToInt(byte[] ary, int offset) {  
		int value;    
		value = (ary[offset]&0xFF)   
				| ((ary[offset+1]<<8) & 0xFF00)  
				| ((ary[offset+2]<<16)& 0xFF0000)   
				| ((ary[offset+3]<<24) & 0xFF000000);  
			return value;  
	}  
	
	public static void InttoByte(byte[] ary, int value) {  
		ary[0] = (byte) (value & 0xff);
		ary[1] = (byte) ((value >> 8 ) & 0xff);
		ary[2] = (byte) ((value >> 16 ) & 0xff);
		ary[3] = (byte) ((value >> 24 ) & 0xff);
	}  
	
}
