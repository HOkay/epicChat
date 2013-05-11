package com.lbros.epicchat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Class used for uploading a file to a server on the background thread, which may be configured to send status events to a Handler object at various
 * stages during the upload. Alternatively, it may be run as a "fire and forget" task (i.e. no status events are returned)
 * @author Tom
 *
 */
public class UploadFileTask extends AsyncTask<Void, Integer, Boolean>{
	private final String TAG = "UploadFileTask";
	private String remoteLocation;
	private String filePath;
	
	private FileInputStream fileInputStream;
	private Handler handler;
	private String tag = null;
	
	private boolean fileExists = false;
	private int fileLength;
	
	private int bufferLength = 4096;		//Default upload chunk size
	
	private int bytesUploaded = 0;
	
	float total, uploaded;
	
	long lastUiUpdateTime = 0;
	long minTimeBetweenUiUpdates = 100;		//Time in ms between updates of the notification. This is to ensure the event does not get spammed. Default is 100 ms, which is 10FPS
	
	//Message codes. Accessible from other classes
	public static final int EVENT_FILE_LOADED = 0x01;
	public static final int EVENT_UPLOAD_STARTED = 0x02;
	public static final int EVENT_UPLOAD_PROGRESS_UPDATED = 0x03;
	public static final int EVENT_UPLOAD_SUCCEEDED = 0x04;
	public static final int EVENT_UPLOAD_FAILED = 0x05;
	
	//Field names for data fields sent in the event messages. Accessible from other classes
	public static final String EXTRA_RESPONSE_STRING = "responseString";
	public static final String EXTRA_BYTES_UPLOADED = "bytesUploaded";
	public static final String EXTRA_BYTES_TOTAL = "bytesTotal";
	public static final String EXTRA_TAG = "tag";
	
	public Integer resourceId = null;
	
	String responseString = null;
	
	JSONObject responseJSON = null;
	
	/**
	 * Constructor
	 * @param newRemoteLocation		The URL that the file should be sent to, including 'http://'
	 * @param newFilePath			The file's path on the local device
	 */
	public UploadFileTask(String newRemoteLocation, String newFilePath){
		remoteLocation = newRemoteLocation;
		filePath = newFilePath;
	}
	
	/**
	 * Set the Handler that the task should sent events to. This method does not have to be called if you do not wish to recieve events
	 * @param newHandler
	 */
	public void setHandler(Handler newHandler) {
		handler = newHandler;
	}
	
	/**
	 * Sets the chunk size to use during the upload. If not set, the chunk size is 4KB
	 * @param newSize			The new chunk size, in bytes
	 */
	public void setUploadChunkSize(int newSize) {
		bufferLength = newSize;
	}
	
	/**
	 * Sets the minimum time between EVENT_UPLOAD_PROGRESS_UPDATED events being sent. This can be set to a higher value to reduce the frequency
	 * at which updates are received. If not set, the interval is 100ms, which sends 10 updates per second, or 10 FPS
	 * @param newInterval			The new interval, in ms
	 */
	public void setMinimumProgressUpdateInterval(long newInterval) {
		minTimeBetweenUiUpdates = newInterval;
	}
	
	/**
	 * Adds a tag to the task, which will be sent with any event message as a String called "tag".
	 * This is useful if you create multiple instances of this task and want to know which instance sent the message
	 * Calling this method is optional.
	 * @param newTag			The tag to set
	 */
	public void setTag(String newTag) {
		tag = newTag;
	}
	
	/**
	 * Handles the download of the image on the background thread
	 */
	@Override
	protected Boolean doInBackground(Void... arg) {
		boolean returnCode = true;
		File file = null;
		try{		
			file = new File(filePath);				//Get the file object identified by the filename provided
			fileLength = (int) file.length();	//Get the file length. Because this is an int, 2GB is the maximum file size
			if(!file.exists()){
				fileExists = false;
				Log.e(TAG, "File does not exist, path: "+filePath);
				return false;
			}
			else{
				fileExists = true;
			}
			byte[] buffer = new byte[bufferLength];
			fileInputStream = new FileInputStream(file);
			
			Log.d(TAG, "Opening connection to: "+remoteLocation);
			URL url = new URL(remoteLocation);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();	//Make a new URL connection
			connection.setReadTimeout(20000);	//Set a timeout of 10 seconds for reading the response from the server
			connection.setDoOutput(true);		//We need to output data
			//connection.setDoInput(true);		//We need to recieve data
			connection.setRequestMethod("POST");	//POST form method
			//connection.setRequestProperty("Content-encoding", "deflate");
			connection.setRequestProperty("Content-type", "application/octet-stream");

			connection.setRequestProperty("Content-Language", "en-GB");
			//connection.setChunkedStreamingMode(bufferLength);
			
			long bytesUploaded = 0;		//Keep a local counter of the bytes we have transferred, this is used after the stream writing is completed
			
			OutputStream outputStream = connection.getOutputStream();		//Open an  output stream, this is the raw file data, uncompressed
			//DeflaterOutputStream compressedOutputStream = new DeflaterOutputStream(outputStream);
			
			//Send a message to the handler that the upload has just started
			if(handler!=null){
				Message message = handler.obtainMessage(EVENT_UPLOAD_STARTED);
				Bundle dataBundle = new Bundle();
				dataBundle.putInt(EXTRA_BYTES_TOTAL, fileLength);
				if(tag!=null){
					dataBundle.putString(EXTRA_TAG, tag);
				}
				message.setData(dataBundle);
				handler.sendMessage(message);
			}
			
			for (int i = 0; i < fileLength; i += bufferLength){			//Upload the data in a loop, split into sections of bufferLength length
				if (fileLength - i >= bufferLength){					//True if the end of the file will not be reached during this read
					fileInputStream.read(buffer, 0, bufferLength);		//Read into the buffer until the buffer is full
					outputStream.write(buffer, 0, bufferLength);		//Read from the contents of the buffer and write the bytes we read to a compressed output stream
					bytesUploaded+= bufferLength;
					publishProgress((int) bytesUploaded);									//Inform the UI there is an update
				}
				else{														//True if this will be the last read from this file. In this case, we do the same as above, but only read until the end fo the file, meaning the buffer may not be full after the read
					fileInputStream.read(buffer, 0, fileLength - i);
					outputStream.write(buffer, 0, fileLength - i);
					bytesUploaded+= fileLength - i;
					publishProgress((int) bytesUploaded);
				}
			}
			Log.d(TAG, "File uploaded");
			//Check that all the bytes were uploaded. If they were not, it means the connection was interrupted or closed by the server
			if(bytesUploaded<fileLength){		//True if not all the bytes were uploaded
				publishProgress(fileLength);	//Make up the difference in the progress, so that any progress bars stay in sync and do not lose any bytes. This is purely a cosmetic feature
			}
	
			//Close the streams
			fileInputStream.close();				
			
			//compressedOutputStream.flush();
			//compressedOutputStream.close();

			outputStream.flush();
			outputStream.close();
	
			//Read the response from the server
			StringBuilder builder = new StringBuilder();
			InputStream inputStream = connection.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
			String line;
			while ((line = reader.readLine()) != null) {	//Read data from the stream while it is available
				builder.append(line);   
			}
			
			connection.disconnect();	//Close the connection to free up resources
			
			responseString = builder.toString();
			inputStream.close();
			Log.d(TAG, "Server response: "+responseString);
		
			responseJSON = new JSONObject(responseString);
			if(responseJSON.has("error")){			//True if a parameter was incorrect
				Log.e(TAG, "Error : "+responseJSON.getString("error"));
				return false;			//An error occurred, return false
			}
			else if(responseJSON.has("warning")){	//True if the server returned a warning
				Log.e(TAG, "Warning : "+responseJSON.getString("warning"));
				return false;			//An error occurred, return false			
			}
			else{
				return true;
			}			
		}
		catch (MalformedURLException e){
			Log.e(TAG, "Error uploading image '"+file.getName()+"': "+e.toString());
			returnCode = false;
		}
		catch (SocketTimeoutException e){
			Log.e(TAG, "Error uploading image '"+file.getName()+"': "+e.toString());
			returnCode = false;
		}
		catch (IOException e){
			Log.e(TAG, "Error uploading image '"+file.getName()+"': "+e.toString());
			returnCode = false;
		} catch (JSONException e) {
			Log.e(TAG, "Error uploading image '"+file.getName()+"': "+e.toString());
			returnCode = false;		
		}		
		return returnCode;
	}
	
	/**
	 * Runs on the UI thread when publishProgress() is called
	 * @param progress		The number of bytes uploaded so far
	 */
	@Override
	protected void onProgressUpdate(Integer... progress){
		bytesUploaded = progress[0];
		
		//long timeSinceLastUpdate = System.currentTimeMillis() - lastUiUpdateTime;
		//Log.d(TAG, "SINCE: "+timeSinceLastUpdate);
		//if(timeSinceLastUpdate>=minTimeBetweenUiUpdates){
			//Send a message to the class that called this class, to inform it that the sync progress has changed
			if(handler!=null){
				Message message = handler.obtainMessage(EVENT_UPLOAD_PROGRESS_UPDATED);
				Bundle dataBundle = new Bundle();
				dataBundle.putInt(EXTRA_BYTES_UPLOADED, bytesUploaded);
				dataBundle.putInt(EXTRA_BYTES_TOTAL, fileLength);
				if(tag!=null){
					dataBundle.putString(EXTRA_TAG, tag);
				}
				message.setData(dataBundle);
				handler.sendMessage(message);
			}
			//lastUiUpdateTime = System.currentTimeMillis();
		//}
	}
	
	/**
	 * Called on the UI thread when doInBackground() has finished executing
	 * @param result	Boolean representing whether or not the download was successfull
	 */
	@Override
	protected void onPostExecute(Boolean result){
		if(result!=null && result){		//Download was successful
			if(handler!=null){
				android.os.Message message = handler.obtainMessage(EVENT_UPLOAD_SUCCEEDED);
				if(responseString!=null){
					Bundle dataBundle = new Bundle();
					dataBundle.putString(EXTRA_RESPONSE_STRING, responseString);	//Send the response back to the handler
					if(tag!=null){
						dataBundle.putString(EXTRA_TAG, tag);
					}
					message.setData(dataBundle);
				}
				handler.dispatchMessage(message);
			}
		}
		else{							//Download failed
			if(handler!=null){
				android.os.Message message = handler.obtainMessage(EVENT_UPLOAD_FAILED);
				Bundle dataBundle = new Bundle();
				if(responseString!=null){
					dataBundle.putString(EXTRA_RESPONSE_STRING, responseString);	//Send the response back to the handler if we got one
				}
				if(tag!=null){
					dataBundle.putString(EXTRA_TAG, tag);
				}
				message.setData(dataBundle);
				handler.dispatchMessage(message);
			}
		}
	}

	/**
	 * Returns the size of the file in bytes, or null if the file does not exist
	 * @return
	 */
	public Integer getFileSize(){
		if(fileExists){
			return fileLength;
		}
		else{
			return null;
		}
	}
	
	/**
	 * Returns the server response as a string if it has been set (i.e. after the server responded to the request), or null otherwise
	 * @return	String containing the server's response
	 */
	public String getResponseString(){
		return responseString;
	}
	
	/**
	 * Returns the server response as a JSONObject if it has been set (i.e. after the server responded to the request), or null otherwise
	 * @return	JSONObject containing the server's response
	 */
	public JSONObject getResponseJSON(){
		return responseJSON;
	}
}