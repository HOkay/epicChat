package com.lbros.epicchat;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

/**
 * Class used for downloading a file from a server on the background thread, which may be configured to send status events to a Handler object at various
 * stages during the download. Alternatively, it may be run as a "fire and forget" task (i.e. no status events are returned)
 * @author Tom
 *
 */
public class DownloadFileTask extends AsyncTask<Void, Void, Boolean>{
	private final String TAG = "DownloadImageTask";
	private String remoteLocation;
	private String savePath;
	
	private Integer uniqueId = null;
	
	private InputStream inputStream;
	private FileOutputStream outputStream;
	private Handler handler;
	
	//Message codes. Accessible from other classes
	public static final int EVENT_DOWNLOAD_STARTED = 0x01;
	public static final int EVENT_DOWNLOAD_SUCCEEDED = 0x02;
	public static final int EVENT_DOWNLOAD_FAILED = 0x03;
	
	private boolean messageDownloaded = false;
	
	/**
	 * Constructor
	 * @param newRemoteLocation		The URL of the image to retrieve, including 'http://'
	 * @param newSavePath			The path to save the image on the local device
	 */
	public DownloadFileTask(String newRemoteLocation, String newSavePath){
		remoteLocation = newRemoteLocation;
		savePath = newSavePath;
	}
	
	/**
	 * Handles the download of the image on the background thread
	 */
	@Override
	protected Boolean doInBackground(Void... arg) {
		boolean returnCode = true;
		Log.d(TAG, "Downloading from: "+remoteLocation+" to: "+savePath);
		if(handler!=null){
			handler.sendEmptyMessage(EVENT_DOWNLOAD_STARTED);		//Send an empty message to the Handler
		}
		try { 
			URL imageUrl = new URL(remoteLocation);
			inputStream = imageUrl.openStream();	//Open a stream from the URL specified
			try {    			    			
				outputStream = new FileOutputStream(savePath);		//Open an output stream 
				byte[] buffer = new byte[1024];
				int bytesRead = 0;
				while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) >= 0) {   	//While the stream has bytes left, write them to the file
					outputStream.write(buffer, 0, bytesRead);
				}
				messageDownloaded = true;
			}
			finally{
				try{
					outputStream.flush();
					outputStream.close();     //Close both streams before exiting
				}
				catch(NullPointerException e){
					Log.e(TAG, "Error downloading image: "+e.toString());
					returnCode = false;
				}
			} 
		}
		catch (MalformedURLException e) {
			Log.e(TAG, "Error downloading image: "+e.toString());
			returnCode = false;
		}
		catch (IOException e) {
			Log.e(TAG, "Error downloading image: "+e.toString());
			returnCode = false;
		} 
		finally {
			try {
				inputStream.close();
			}
			catch(IOException e) {
				Log.e(TAG, "Error downloading image: "+e.toString());
				returnCode = false;
			}
			catch(NullPointerException e){
				Log.e(TAG, "Error downloading image: "+e.toString());
				returnCode = false;
			}
		}
		Log.d(TAG, "Download complete");
		return returnCode;
	}
	
	/**
	 * Called on the UI thread when doInBackground() has finished executing
	 * @param result	Boolean representing whether or not the download was successfull
	 */
	@Override
	protected void onPostExecute(Boolean result){
		if(result!=null && result){		//Download was successful
			if(handler!=null){
				android.os.Message message = handler.obtainMessage(EVENT_DOWNLOAD_SUCCEEDED);
				Bundle messageData = new Bundle();
				if(uniqueId!=null){
					messageData.putInt("uniqueId", uniqueId);				//Send the unique ID back to the handler
				}
				message.setData(messageData);
				handler.dispatchMessage(message);
			}
		}
		else{			//Download failed
			if(handler!=null){
				handler.sendEmptyMessage(EVENT_DOWNLOAD_FAILED);		//Send an empty message to the Handler
			}
		}
	}
	
	/**
	 * Set the Handler that the task should sent events to. This method does not have to be called if you do not wish to recieve events
	 * @param newHandler
	 */
	public void setHandler(Handler newHandler) {
		handler = newHandler;
	}
	
	/**
	 * Sets the ID of associated with this task. This is not required for normal operation, however if multiple instances of this task are created, this ID may be retrieved to help identify the task
	 * @param newUniqueId		The unique ID to give this task
	 */
	public void setUniqueId(Integer newUniqueId) {
		uniqueId = newUniqueId;
	}
	
	/**
	 * Returns the ID set by setId(), or null if an ID was not set 
	 * @return
	 */
	public Integer getUniqueId(){
		return uniqueId;
	}
	
	/**
	 * Returns a Bitmap of the contact's image ready for use with ImageView objects, or null if a bitmap could not be produced for some reason
	 * @param width		The width of the image
	 * @param height	The height of the image
	 * @return			A Bitmap image, with dimensions as specified, or original size if not
	 */
	public Bitmap getImageBitmap(Integer width, Integer height){
		Bitmap imageBitmap = null;
		if(messageDownloaded){		//Load the bitmap if the message downloaded successfully
			FileInputStream imageInputStream;
			try {
				imageInputStream = new FileInputStream(savePath);
				imageBitmap = BitmapFactory.decodeStream(imageInputStream);
				if(width!=null && height!=null){
					imageBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
				}
			}
			catch (FileNotFoundException e) {
				Log.e(TAG, "Error opening image input stream: "+e.toString());
			}
		}
		return imageBitmap;
	}
}