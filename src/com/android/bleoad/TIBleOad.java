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
import android.os.Message;
import android.util.Log;

public class TIBleOad extends IPlatformBleOad {

	private static final String TAG = "TIBleOad";
	
	private final String UUID_OAD_IMG_IDENTIFY = "f000ffc1-0451-4000-b000-000000000000";
	private final String UUID_OAD_IMG_BLOCK = "f000ffc2-0451-4000-b000-000000000000";
	
	//Types
	private final int EFL_OAD_IMG_TYPE_APP        = 1;
	private final int EFL_OAD_IMG_TYPE_STACK      = 2;
	private final int EFL_OAD_IMG_TYPE_NP         = 3;
	
	private final int HAL_FLASH_WORD_SIZE = 4;
	private final int OAD_BLOCK_SIZE = 16;
	
	private final int FLASH_START_ADDRESS = 0x1000;
	
	private int mUpdateBinLength = 0;
	private ArrayList<byte[]> mUpdateBlockList = new ArrayList<byte[]>();
	
	
	private BluetoothGattCharacteristic mImgIdentifyGattChara;
	private BluetoothGattCharacteristic mImgBlockGattChara;
	
	//小尾端存放
	private class ImageMetadata {
		
		final static int CRC_SIZE = 2;
		final static int VER_SIZE = 2;
		final static int LEN_SIZE = 2;
		final static int UID_SIZE = 4;
		final static int ADDR_SIZE = 2;
		final static int TYPE_SIZE = 1;
		final static int STATE_SIZE = 1;
		final static int IMG_META_DATA_SIZE = CRC_SIZE*2 + VER_SIZE + LEN_SIZE 
									+ UID_SIZE + ADDR_SIZE + TYPE_SIZE + STATE_SIZE;
		
		int crc;
		int crcShadow;
		int version;
		int length;
		byte[] uid = new byte[UID_SIZE];
		int startAddr;
		int imgType;
		int state;
		
		
		public byte[] getImgMetaData() {
			byte[] metaData = new byte[IMG_META_DATA_SIZE];
			int len = 0;
			System.arraycopy(getCrc(), 0, metaData, len, CRC_SIZE);
			len += CRC_SIZE;
			
			System.arraycopy(getCrcShadow(), 0, metaData, len, CRC_SIZE);
			len += CRC_SIZE;
			
			System.arraycopy(getVersion(), 0, metaData, len, VER_SIZE);
			len += VER_SIZE;
			
			System.arraycopy(getLength(), 0, metaData, len, LEN_SIZE);
			len += LEN_SIZE;
			
			System.arraycopy(getUid(), 0, metaData, len, UID_SIZE);
			len += UID_SIZE;
			
			System.arraycopy(getAddr(), 0, metaData, len, ADDR_SIZE);
			len += ADDR_SIZE;
			
			System.arraycopy(getImgType(), 0, metaData, len, TYPE_SIZE);
			len += TYPE_SIZE;
			
			System.arraycopy(getState(), 0, metaData, len, STATE_SIZE);
			len += STATE_SIZE;
			
			return metaData;
		}
		
		public void setCrc(int crc) {
			this.crc = crc;
		}
		public byte[] getCrc() {
			return Utils.intToByteArray(crc, CRC_SIZE);
		}
		
		public byte[] getCrcShadow() {
			return Utils.intToByteArray(crcShadow, CRC_SIZE);
		}
		public void setCrcShadow(int crcShadow) {
			this.crcShadow = crcShadow;
		}
		
		public byte[] getVersion() {
			return Utils.intToByteArray(version, VER_SIZE);
		}
		public void setVersion(int ver) {
			this.version = ver;
		}
		
		public byte[] getLength() {
			return Utils.intToByteArray(length, LEN_SIZE);
		}
		public void setLength(int len) {
			this.length = len;
		}
		
		public byte[] getUid() {
			return uid;
		}
		public void setUid(byte[] uid) {
			
			for (int i = 0; i < UID_SIZE; i++) {
				this.uid[i] = 0;
			}
			
			for (int i = 0; i < uid.length && i < UID_SIZE; i++) {
				this.uid[i] = uid[i];
			}
		}
		
		public byte[] getAddr() {
			return Utils.intToByteArray(startAddr, ADDR_SIZE);
		}
		public void setAddr(int addr) {
			this.startAddr = addr;
		}
		
		public byte[] getImgType() {
			return Utils.intToByteArray(imgType, TYPE_SIZE);
		}
		public void setImgType(int imgType) {
			this.imgType = imgType;
		}
		
		public byte[] getState() {
			return Utils.intToByteArray(state, STATE_SIZE);
		}
		public void setState(int state) {
			this.state = state;
		}
	}
	
	
	private final int MSG_SET_IMG_IDENTIFY_NOTI = 10000;
	private final int MSG_SET_IMG_BLOCK_NOTI = MSG_SET_IMG_IDENTIFY_NOTI + 1;
	private final int MSG_WR_IMG_IDENTIFY = MSG_SET_IMG_IDENTIFY_NOTI + 2;
	private final int MSG_WR_IMG_BLOCK = MSG_SET_IMG_IDENTIFY_NOTI + 3;
	private final int MSG_TRANSFER_PERCENT = MSG_SET_IMG_IDENTIFY_NOTI + 4;
	private final int MSG_SEND_IMG_IDENTIFY = MSG_SET_IMG_IDENTIFY_NOTI + 5;
	
	private final int MSG_ERR_READ_BIN_FILE = MSG_SET_IMG_IDENTIFY_NOTI + 100;
	private final int MSG_ERR_TRANSFER = MSG_ERR_READ_BIN_FILE + 1;
	private final int MSG_ERR_VERSION = MSG_ERR_READ_BIN_FILE + 2;
	private Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			
			if (mBleOadManager == null 
					|| mImgIdentifyGattChara == null
					|| mImgBlockGattChara == null) {
				return;
			}
			
			final int what = msg.what;
			boolean ok = false;
			switch (what) {
			case MSG_SET_IMG_IDENTIFY_NOTI:
				ok = mBleOadManager.setCharacteristicNotification(mImgIdentifyGattChara, true);
				if (!ok) {
					mHandler.sendEmptyMessageDelayed(MSG_SET_IMG_IDENTIFY_NOTI, 1*1000);
				}
				break;
			case MSG_SET_IMG_BLOCK_NOTI:
				ok = mBleOadManager.setCharacteristicNotification(mImgBlockGattChara, true);
				if (!ok) {
					mHandler.sendEmptyMessageDelayed(MSG_SET_IMG_BLOCK_NOTI, 1*1000);
				}
				break;
			case MSG_WR_IMG_IDENTIFY:
				
				ok = mBleOadManager.writeCharacteristic(mImgIdentifyGattChara);
				if (!ok) {
					mHandler.sendEmptyMessageDelayed(MSG_WR_IMG_IDENTIFY, 200);
				}
				
				break;
			case MSG_SEND_IMG_IDENTIFY:
				
				sendImgIdentify();
				
				break;
			case MSG_WR_IMG_BLOCK:
				ok = mBleOadManager.writeCharacteristic(mImgBlockGattChara);
				if (!ok) {
					mHandler.sendEmptyMessageDelayed(MSG_WR_IMG_BLOCK, 200);
				}
				
				break;
			case MSG_TRANSFER_PERCENT:
				int numBlk = msg.arg1;
				if (mBleOadCallback != null) {
					int percent = (numBlk*100) / (mUpdateBlockList.size()-1);
//					mBleOadCallback.onTransferInPercent(0==percent ? 1 : percent);
					mBleOadCallback.onTransferInPercent(percent);
				}
				
				break;
				
			case MSG_ERR_READ_BIN_FILE:
				if (mBleOadCallback != null) {
					mBleOadCallback.onError(IBleOadCallback.ERROR_READ_BIN_FILE);
				}
				break;
			case MSG_ERR_TRANSFER:
				if (mBleOadCallback != null) {
					mBleOadCallback.onError(IBleOadCallback.ERROR_TRANSFER);
				}
				break;
			case MSG_ERR_VERSION:
				if (mBleOadCallback != null) {
					mBleOadCallback.onError(IBleOadCallback.ERROR_VERSION);
				}
				break;
			default:
				break;
			}
		}
		
	};
	
	public TIBleOad(Context c, BleOadManager mgr) {
		super(c, mgr);
		// TODO Auto-generated constructor stub
	}

	private boolean isSendStopForStart = false;
	
	@Override
	public void startBleOad() {
		// TODO Auto-generated method stub
		isSendStopForStart = true;
		sendStopForStart();
		mHandler.sendEmptyMessageDelayed(MSG_SEND_IMG_IDENTIFY, 500);
	}
	
	private void sendImgIdentify() {
		
		boolean readFileOk = readUpdateFile();
		if (!readFileOk) {
			mHandler.sendEmptyMessage(MSG_ERR_READ_BIN_FILE);
			Log.e(TAG, "ERROR: fail to read update bin file.");
			return;
		}
		
		int crc = crcCalImg();
		
		ImageMetadata metaData = new ImageMetadata();
		metaData.setCrc(crc);
		metaData.setCrcShadow(crc);
		metaData.setVersion(getFirmwareVersion());
		metaData.setLength( mUpdateBinLength / HAL_FLASH_WORD_SIZE);
		metaData.setUid(new byte[]{'A', 'A', 'C', 'D'});
		metaData.setAddr(FLASH_START_ADDRESS / HAL_FLASH_WORD_SIZE);
		metaData.setImgType(EFL_OAD_IMG_TYPE_APP);
		metaData.setState(0xFF);
		
		byte[] data = metaData.getImgMetaData();
		
		Log.i(TAG, Utils.toHexString(data)); 
		
		mImgIdentifyGattChara.setValue(data);
		
		mHandler.sendEmptyMessage(MSG_WR_IMG_IDENTIFY);
	}
	
	private void sendStopForStart() {
		
		byte[] retValues = new byte[2 + OAD_BLOCK_SIZE];
		int blkNum =  0xfffe;
		retValues[0] = Utils.LO_UINT16(blkNum);
		retValues[1] = Utils.HI_UINT16(blkNum);
		mImgBlockGattChara.setValue(retValues);
		mHandler.sendEmptyMessage(MSG_WR_IMG_BLOCK);
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

			if (uuid.toUpperCase().equals(UUID_OAD_IMG_IDENTIFY.toUpperCase())) {
				mImgIdentifyGattChara = gattCharacteristic;
			} else if (uuid.toUpperCase().equals(UUID_OAD_IMG_BLOCK.toUpperCase())) {
				mImgBlockGattChara = gattCharacteristic;
			}
		}
		
		if (mImgIdentifyGattChara != null) {
			mHandler.sendEmptyMessage(MSG_SET_IMG_IDENTIFY_NOTI);
		}
		if (mImgBlockGattChara != null) {
			mHandler.sendEmptyMessage(MSG_SET_IMG_BLOCK_NOTI);
		}
	}
	
	@Override
	public void onReceiveNoti(String uuid, byte[] values) {
		// TODO Auto-generated method stub
		
		if (UUID_OAD_IMG_BLOCK.toUpperCase().equals(uuid.toUpperCase())) {
			
			int blkNum = Utils.BUILD_UINT16(values[0], values[1]);
			
			if (isSendStopForStart) {
				if (blkNum != 0) {
					return;
				}
				isSendStopForStart = false;
			}
			
			
			if (blkNum != 0xffff && blkNum < mUpdateBlockList.size()) {
				
				byte[] block = mUpdateBlockList.get(blkNum);
				byte[] retValues = new byte[2 + OAD_BLOCK_SIZE];
				retValues[0] = Utils.LO_UINT16(blkNum);
				retValues[1] = Utils.HI_UINT16(blkNum);
				for (int i = 0; i < block.length; i++) {
					retValues[2+i] = block[i];
				}
				
				mImgBlockGattChara.setValue(retValues);
				mHandler.sendEmptyMessage(MSG_WR_IMG_BLOCK);
				
				mHandler.obtainMessage(MSG_TRANSFER_PERCENT, blkNum, 0).sendToTarget();
				
			} else {
				
				mHandler.sendEmptyMessage(MSG_ERR_TRANSFER);
			}
			
		} else if (UUID_OAD_IMG_IDENTIFY.toUpperCase().equals(uuid.toUpperCase())) {
			
			mHandler.sendEmptyMessage(MSG_ERR_VERSION);
		}
	}
	
	private boolean readUpdateFile() {
		
		String updateFilePath = getUpdateFilePath();
		if (updateFilePath == null) {
			return false;
		}
		InputStream updateFileInputStream;
		mUpdateBlockList.clear();
		
		try {
			updateFileInputStream = new FileInputStream(updateFilePath);
			
			mUpdateBinLength = updateFileInputStream.available();
			if (mUpdateBinLength <= 0 )
				return false;
			
			int remainder = mUpdateBinLength % OAD_BLOCK_SIZE;
			if ( remainder != 0) {
				mUpdateBinLength += (OAD_BLOCK_SIZE - remainder);
			}
			
			byte[] arrayOfByte = null;
			while (true) {
				arrayOfByte = new byte[OAD_BLOCK_SIZE];
				int i = updateFileInputStream.read(arrayOfByte);
				if (i != -1) {
					mUpdateBlockList.add(arrayOfByte);
				} else {
					updateFileInputStream.close();
					break;
				}
			}
			
			return true;
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			
		}
		return false;
	}
	
	 /*********************************************************************
	 * @fn          crc16
	 *
	 * @brief       Run the CRC16 Polynomial calculation over the byte parameter.
	 *
	 * @param       crc - Running CRC calculated so far.
	 * @param       val - Value on which to run the CRC16.
	 *
	 * @return      crc - Updated for the run.
	 */
	private static int crc16(int crc, int val)
	{
	  int poly = 0x1021;
	  int cnt;

	  for (cnt = 0; cnt < 8; cnt++, val <<= 1)
	  {
		  int msb = ((crc & 0x8000) != 0) ? 1 : 0;

	    crc <<= 1;
	    
	    if ((val & 0x80) != 0)
	    {
	      crc |= 0x0001;
	    }
	    
	    if (msb != 0)
	    {
	      crc ^= poly;
	    }
	  }

	  return crc;
	}
	
	int crcCalImg() {
		
		int imageCRC = 0;
		
		for (int i = 0; i < mUpdateBlockList.size(); i++) {
			byte[] buf = mUpdateBlockList.get(i);
			int len = buf.length;
			for (int idx = 0; idx < len; idx++) {
				imageCRC = crc16(imageCRC, buf[idx]);
			}
		}
		
		// IAR note explains that poly must be run with value zero for each byte of 
		// the crc.
		imageCRC = crc16(imageCRC, 0);
		imageCRC = crc16(imageCRC, 0);
	  
		// Return the CRC calculated over the image.
		return imageCRC;  
	}
	
}