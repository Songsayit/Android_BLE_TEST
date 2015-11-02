package com.android.bleoad;

public interface IBleOadCallback {
	
	
	public final int ERROR_TRANSFER 		= 10;
	public final int ERROR_VERSION  		= 11;
	public final int ERROR_READ_BIN_FILE 	= 20;
	/**
	 * 
	 * @param reason
	 * 		<p>10:Transfer Error
	 * 		<p>11:Version Error(maybe firmware is newest)
	 * 		<p>20:failed to Read update bin file or bin file does not exist!!
	 */
	public void onError(int reason);
	/**
	 * 
	 * @param percent
	 * 		0~100, 100 means download over
	 */
	public void onTransferInPercent(int percent);
}
