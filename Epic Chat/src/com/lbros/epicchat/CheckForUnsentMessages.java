package com.lbros.epicchat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gcm.GCMRegistrar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;

public class CheckForUnsentMessages extends BroadcastReceiver {
	private final String TAG = "CheckForUnsentMessages";
	@Override
	public void onReceive(final Context context, Intent intent) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);		//Need this for the server address
		final String serverAddress = preferences.getString("serverAddress", null);
		final String gcmId = GCMRegistrar.getRegistrationId(context);

		//Connect to the database and get a list of any unAcked messages
		Log.d(TAG, "Checking for unAcked messages");				
		Database database = new Database(context);
		final ArrayList<Message> unAckedMessages = database.getUnAckedMessages();		//Do this in onReceive() as the CPU is guaranteed to stay awake until onReceive() finishes
		database.close();

		//We do the sending of messages in a background thread
		Thread sendUnAckedMessagesThread = new Thread(new Runnable(){
			public void run(){
				int nMessages = unAckedMessages.size();
				if(nMessages>0){			//At least one message to resend
					//Acquire a wakelock so this Thread is not terminated before sending is complete
					PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
					WakeLock wakelock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "epicChat.sendingUnAckedMessages");
					wakelock.acquire();
					//Loop through each message
					for(int i=0; i<nMessages; i++){
						sendMessage(unAckedMessages.get(i));
					}
					wakelock.release();
				}
			}

			private boolean sendMessage(Message message){
				boolean responseCode = false;
				//Extract the data from the message
				final String messageId = message.getId();
				int timestamp = message.getTimestamp();
				int messageType = message.getType();
				String fromUser = message.getSenderId();
				String toUsers = message.getConversationId();
				String messageContents = message.getContents(null);
				String messageTextEncoded;
				try {
					messageTextEncoded = URLEncoder.encode(messageContents, "utf-8");
				} catch (UnsupportedEncodingException e) {
					messageTextEncoded = "URLEncoder failed: "+e.toString();
				}
				String request = serverAddress+"sendMessage.php";
				String parameters = "messageId="+messageId+"&sentTimestamp="+timestamp+"&messageType="+messageType+"&fromUser="+fromUser+"&toUsers="+toUsers+"&messageText="+messageTextEncoded+"&senderDeviceId="+gcmId;
				Log.d(TAG, "PARAMS: "+parameters);
				String response = HTTP.doHttpPost(request, parameters, 5);
				if(response!=null){
					try {
						JSONObject responseJSON = new JSONObject(response);
						if(responseJSON.has("ack")){			//If we got an ACK back then the message was sent
							responseCode = true;
						}
					}
					catch (JSONException e) {
						Log.e(TAG, "Error reading response: "+e.toString());
					}
				}
				return responseCode;
			}
		});
		sendUnAckedMessagesThread.start();		//Start the thread. It will acquire a wakelock if neccessary, so it will run until completion
	}
}