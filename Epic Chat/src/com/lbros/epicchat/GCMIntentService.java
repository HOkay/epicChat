package com.lbros.epicchat;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;

public class GCMIntentService extends GCMBaseIntentService{
	private final static String TAG =  "GCMIntentService";

	private final int ID_DOWNLOAD_USER_IMAGE = 1;
	private final int ID_DOWNLOAD_RESOURCE = 2;

	private Database database = new Database(this);

	private SharedPreferences preferences;

	private Message messageWaiting = null;
	
	private Contact contactWaiting = null;
	private Message contactWaitingMessage = null;

	private Resource resourceWaiting = null;

	@Override
	protected void onRegistered(Context context, String regId) {
		Log.d(TAG, "Registered with GCM, ID: "+regId);
		preferences = PreferenceManager.getDefaultSharedPreferences(this);	//Load the preferences
		try {
			String deviceNameEncoded = URLEncoder.encode(getDeviceName(), "utf-8");
			preferences = PreferenceManager.getDefaultSharedPreferences(this);	//Load the preferences
			String userId = preferences.getString("userId", null);
			Log.d(TAG, "USERID: "+userId);
			String UUID = preferences.getString("UUID", null);
			//String gcmId = preferences.getString("gcmId", null);
			String serverAddress = preferences.getString("serverAddress", null);
			String request = serverAddress+"deviceRegister.php?uuid="+UUID+"&userId="+userId+"&gcmId="+regId+"&deviceName="+deviceNameEncoded;
			String response = HTTP.doHttpGet(request, 5);
			Log.d(TAG, "Response: "+response);
			SharedPreferences.Editor editor = preferences.edit();
			editor.putString("gcmId", regId);
			editor.commit();
		}
		catch (UnsupportedEncodingException e) {
		}
	}

	@Override
	protected void onUnregistered(Context context, String regId) {
		//Get all the info we need from the shared preferences. This eliminates the need for static variables
		preferences = PreferenceManager.getDefaultSharedPreferences(this);	//Load the preferences
		String userId = preferences.getString("userId", null);
		String UUID = preferences.getString("UUID", null);
		//String gcmId = preferences.getString("gcmId", null);
		String serverAddress = preferences.getString("serverAddress", null);
		Log.d(TAG, "Unegistered with GCM, ID: "+regId);
		String request = serverAddress+"deviceUnregister.php?uuid="+UUID+"&userId="+userId+"&gcmId="+regId;
		String response = HTTP.doHttpGet(request, 5);
		Log.d(TAG, "Response: "+response);
	}
	
	@Override
	protected void onMessage(Context context, Intent intent){ 
		preferences = PreferenceManager.getDefaultSharedPreferences(this);	//Load the preferences
		String userId = preferences.getString("userId", null);
		boolean messageReadyForBroadcast = false;
		//Extract the data we need
		String messageTypeString = intent.getStringExtra("messageType");		//Type of message as provided by the sending device. Use trim() to remove whitespace
		int messageType = -1;
		if(messageTypeString!=null){	//True if we got a type from the Intent
			try{
				messageType = Integer.parseInt(messageTypeString);
			}
			catch (NumberFormatException e){
				Log.e(TAG, "Error retrieving type from message: "+e.toString());
			}
		}

		if(messageType!=-1){			//True if the message has a valid type and was parsed to an integer successfully
			String messageId = intent.getStringExtra("messageId");
			int timestamp = 0;
			String conversation = null;
			String sender = null;
			String messageContents = null;
			
			if(messageId==null){		//True if the message ID could not be retreived
				return;
			}
			else if(database.getMessage(messageId)!=null && messageType!=Message.MESSAGE_TYPE_ACK){		//True if this message is already in the database and the message is not an ACK
				//Just send an ACK and exit
				String senderDeviceId = intent.getStringExtra("senderDevice");
				sendMessageACK(senderDeviceId, messageId);
				return;
			}
			
			//Extract the message data and build the message in a switch, where each case adds different information to the message
			switch(messageType){
			case Message.MESSAGE_TYPE_ACK:		//For an ACK, most of the data will be null or 0
				messageContents = intent.getStringExtra("messageId");
				messageReadyForBroadcast = true;		//Set this flag, which is used at the end of this method
				break;
			case Message.MESSAGE_TYPE_TEXT:		//For a text message, all the fields should have valid values
				try{
					timestamp = Integer.parseInt(intent.getStringExtra("sentTimestamp"));
				}
				catch (NumberFormatException e){
					Log.e(TAG, "Error retrieving timestamp from message: "+e.toString());
				}
				conversation = Conversation.sortConversationId(intent.getStringExtra("destinationIds"));
				sender = intent.getStringExtra("sender");
				Log.d(TAG, "SENDER A: "+sender);
				messageContents = intent.getStringExtra("messageText");
				messageContents = messageContents.replace("\\", "");		//Remove escaping slashes that are added by the server
				messageReadyForBroadcast = true;		//Set this flag, which is used at the end of this method
				break;
			case Message.MESSAGE_TYPE_IMAGE:		//For an image message, all the fields should have valid values, and the contents field will be a JSON object, containing the resource ID of the image as it is stored on the server, and a Base64-encoded string containing a thumbnail
				try{
					timestamp = Integer.parseInt(intent.getStringExtra("sentTimestamp"));
				}
				catch (NumberFormatException e){
					Log.e(TAG, "Error retrieving timestamp from message: "+e.toString());
				}
				conversation = Conversation.sortConversationId(intent.getStringExtra("destinationIds"));
				sender = intent.getStringExtra("sender");
				Log.d(TAG, "SENDER: "+sender);
				try {
					messageContents = URLDecoder.decode(intent.getStringExtra("messageText"), "utf-8");
				} catch (Exception e1) {
					Log.e(TAG, "Error decoding text: "+e1.toString());
					break;
				}
				messageContents = messageContents.replace("\\", "");		//Remove escaping slashes that are added by the server
				//Get the file name from the JSON in the message's contents
				//The message is a JSON object containing several fields, one of which is the thumbnail
				String fileName = null;
				String resourceId = null;
				String imageCaption = null;
				try {
					Log.d(TAG, "CONTENTS: "+messageContents);
					JSONObject messageJSON = new JSONObject(messageContents);
					fileName = messageJSON.getString("fileName");
					resourceId = messageJSON.getString("resourceId");
					if(messageJSON.has("caption")){
						imageCaption = messageJSON.getString("caption");
					}
				}
				catch (JSONException e) {
					Log.e(TAG, "Error reading image JSON: "+e.toString());
				}
				if(fileName!=null && resourceId!=null){		//True if we have the data we need
					//Use a DownloadFileTask to download the image for us
					String serverAddress = preferences.getString("serverAddress", null);
					String remoteAddress = serverAddress+"/downloadResource.php?resourceId="+resourceId;
					String savePath = MainActivity.DIRECTORY_RESOURCES+fileName;
					//When the file has finished downloading, an entry in the resources table will be created for it. We can create the object now and it can be added when the download is complete
					resourceWaiting = new Resource(resourceId, Resource.TYPE_IMAGE, timestamp, conversation, sender, savePath, imageCaption);
					DownloadFileTask downloadImage = new DownloadFileTask(remoteAddress, savePath);
					downloadImage.setHandler(downloadImageHandler);
					downloadImage.setUniqueId(ID_DOWNLOAD_RESOURCE);
					downloadImage.execute();			//Kick the tyres Goose!
				}
				break;
			default:
				break;
			}
			
			Message message = new Message(messageId, timestamp, messageType, Message.MESSAGE_STATUS_ACK_RECIPIENT, conversation, sender, messageContents);
			
			if(messageReadyForBroadcast){		//True if this message requires no more processing (i.e. downloading of high-res images, videos, etc etc)
				//Some actions are only taken if the message is not an ACK, such as storing the message in the database or sending an ACK
				if(messageType!=Message.MESSAGE_TYPE_ACK){
					//Check if the sender is in our list of contacts. If not, we will create a notification where the local user can choose whether or not to add the contact
					Contact senderContact = database.getContact(sender);
					if(senderContact!=null){				//Message is from one of our contacts, so continue
						//Add the message to the database
						database.addMessage(message);
					}
					else{		//Message not from one of our contacts, offer the local user the choice to add them or not
						contactWaitingMessage = message;		//Save this message for later
						new RetrieveContactDetails(context, sender).execute();		//This starts the process of retrieving the contact's details from the server
					}
				}
				//Send a broadcast to indicate a new message has arrived
				Intent newMessageIntent = new Intent(MainActivity.intentSignatureNewMessageReceived);
				newMessageIntent.putExtra("message", message);
				context.sendOrderedBroadcast(newMessageIntent, null);
			}
			else{								//This message is not ready yet, but will be soon (after downloading it's extra data), so store it in this global
				messageWaiting = message;
			}
			//Always send the ACK immediately, even if the message is not ready for broadcast. This has to go here as the ACKs are always sent immediately
			if(messageType!=Message.MESSAGE_TYPE_ACK){
				//Check if the sender id matches the user id in use on this device. If it does not, then we need to send an ACK back to the originating device, and create a status bar notification
		        if(!sender.equals(userId)){
		        	String senderDeviceId = intent.getStringExtra("senderDevice");
		        	sendMessageACK(senderDeviceId, messageId);	//Use the unique message id and the sender's device id to send an acknowledgement back to the sender
		        }
			}
		}
    }
	
	@Override
	protected void onError(Context arg0, String errorId) {
		
	}
	
	@Override 
	protected boolean onRecoverableError(Context context, String errorId){
		return true;
	}
	
	class RetrieveContactDetails extends AsyncTask<Void, Void, Void>{
		private Context context = null;
		private String contactId = null;
		private SharedPreferences preferences;
		
		public RetrieveContactDetails(Context newContext, String newContactId){
			context = newContext;
			contactId = newContactId;
			preferences = PreferenceManager.getDefaultSharedPreferences(context);
		}

		@Override
		protected Void doInBackground(Void... params) {
			//Send the data to the server using an HTTP POST request
			String serverAddress = preferences.getString("serverAddress", null);
			String request = serverAddress+"checkUserIds.php";
			String response = HTTP.doHttpPost(request, "idList="+contactId, 5);
			if(response!=null){
				processContactInformation(response);
			}
			return null;
		}
		
		/**
		 * Enters new contacts into the database
		 * @param serverResponse		A JSON-encoded String containing the information of the contacts to be added
		 * @return						Boolean, true if one or more contacts were added to the database, false otherwise
		 */
		private boolean processContactInformation(String serverResponse){
			int nContacts = 0;
			try{
				JSONArray contactsJSON = new JSONArray(serverResponse);
				nContacts = contactsJSON.length();
				JSONObject contactJSON;
				String contactId;
				String contactFirstName;
				String contactLastName;
				String contactPhoneNumber;
				
				DownloadFileTask downloadContactImage;
				String contactImagePath;
				
				//Get the contact's details from the response
				contactJSON = contactsJSON.getJSONObject(0);
				contactId = contactJSON.getString("id");
				contactFirstName = contactJSON.getString("firstName");
				contactLastName = contactJSON.getString("lastName");
				contactPhoneNumber = contactJSON.getString("devicePhoneNumber");
				
				//Download the image for the contact from the server
				contactImagePath = MainActivity.DIRECTORY_USER_IMAGES+contactId+".jpg";
				
				String serverAddress = preferences.getString("serverAddress", null);
				downloadContactImage = new DownloadFileTask(serverAddress+"getUserImage.php?userId="+contactId+"&size=500", contactImagePath);
				downloadContactImage.setHandler(downloadImageHandler);
				downloadContactImage.setUniqueId(ID_DOWNLOAD_USER_IMAGE);		//Add the loop index to this task, this helps us keep track of 
				downloadContactImage.execute();				//Execute the task asynchronously. If there is a previous copy of this task running (i.e. from a previous iteration of this loop) then execution will wait for it to finish
				
				contactWaiting = new Contact(contactId, contactFirstName, contactLastName, contactPhoneNumber, contactImagePath);
				
			}
			catch(JSONException e){
				Log.e(TAG, "Error processing server response: "+e.toString());
				return false;
			}
			if(nContacts>0){
				return true;
			}
			else{
				return false;
			}
		}
	}
	
	/**
	 * Sends an ACK back to the specific device that sent the message. This ACK contains the unique id of the message that was received
	 * @param senderDeviceId	The GCM ID of the device that sent the original message, which is where we want to send the ACK to
	 * @param messageId			The unique ID of the message we received
	 */
	private void sendMessageACK(final String senderDeviceId, final String messageId) {
		Thread sendACKThread = new Thread(new Runnable(){
			public void run() {
				String serverAddress = preferences.getString("serverAddress", null);
				String request = serverAddress+"sendACK.php?destinationDevice="+senderDeviceId+"&messageId="+messageId;
				HTTP.doHttpGet(request, 5);				
			}
		});
		sendACKThread.start();
	}

	/**
	 * Retrieves the device's name (manufacturer and model), or null if not available
	 * @return			A String containing both the device's manufacturer and model
	 */
    private String getDeviceName(){
    	return android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL;
    }

    final Handler downloadImageHandler = new Handler(){
    	public void handleMessage(android.os.Message message){
			//Switch based on the code of the message
			switch(message.what){
			case DownloadFileTask.EVENT_DOWNLOAD_SUCCEEDED:		//Received when the image has been downloaded from the server
				int taskId = message.getData().getInt("uniqueId", -1);
				if(taskId==-1){
					//Do nothing
				}
				else if(taskId==ID_DOWNLOAD_RESOURCE){
					//Add the message to the database
					database.addMessage(messageWaiting);
					//Send a broadcast to indicate a new message has arrived
					Intent newMessageIntent = new Intent(MainActivity.intentSignatureNewMessageReceived);
					newMessageIntent.putExtra("message", messageWaiting);
					sendOrderedBroadcast(newMessageIntent, null);
					database.addResource(resourceWaiting);
					requestMediaScan(resourceWaiting.getPath());
				}
				else if(taskId==ID_DOWNLOAD_USER_IMAGE){	//Download of the new contact's image has completed, we are now ready to create a notification to alert the user
					if(contactWaiting!=null){
						createNewContactChoiceNotification(contactWaiting, contactWaitingMessage);
						requestMediaScan(contactWaiting.getImagePath());
					}
				}
				break;
			default:
				break;
			}
		}
    };

	private void createNewContactChoiceNotification(Contact newContact, Message newMessage) {
		Context context = getApplicationContext();
		
		String action = ViewContactProfileActivity.ACTION_PREFIX+newContact.getId();
		
		//Get the notification manager
		NotificationManager	notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		final int notificationLightOffDuration = 2000;
		final int notificationLightOnDuration = 500;
		final int notificationLightColour = 0xFF0000;
		//Build the notification, step by step
		NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
		notificationBuilder.setLights(notificationLightColour, notificationLightOnDuration, notificationLightOffDuration);
		notificationBuilder.setSmallIcon(R.drawable.note_icon);		//Small icon, goes in the notification bar
		notificationBuilder.setWhen(0);								//Don't want to show the time
		
		//Retrieve the sender's profile picture from the database and add it to the notification
		//Get the dimensions the image should be
		Resources resources = context.getResources();
		int notificationIconWidth = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
		int notificationIconHeight = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
		
		Bitmap senderProfilePicture = newContact.getImageBitmap(notificationIconWidth, notificationIconHeight, null);
		
		notificationBuilder.setLargeIcon(senderProfilePicture);			//Large icon, goes to the left of the notification
		
		Spanned messageTitle = Html.fromHtml("<font color='#cccccc'>New contact: </font>"+newContact.getFullName());
		
		notificationBuilder.setContentTitle(messageTitle);
		notificationBuilder.setPriority(Notification.PRIORITY_HIGH);
		notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(newMessage.getContents(null)));		//Allow the notification to be expanded to view the entire text
		
		notificationBuilder.setSubText(newContact.getId());
		
		//Add an intent to the notification
		Intent confirmContactIntent = new Intent(context, ViewContactProfileActivity.class);
		confirmContactIntent.putExtra("contact", newContact);
		if(newMessage!=null){
			confirmContactIntent.putExtra("message", newMessage);
		}
		confirmContactIntent.setAction(action+ViewContactProfileActivity.ACTION_SUFFIX_NEW_CONTACT_CONFIRM);
		PendingIntent confirmContactIntentPending = PendingIntent.getActivity(context, 0, confirmContactIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		notificationBuilder.setContentIntent(confirmContactIntentPending);
		
		//Add a button that allows the user to add the contact
		Intent addContactIntent = new Intent(context, ViewContactProfileActivity.class);
		addContactIntent.putExtra("contact", newContact);
		if(newMessage!=null){
			addContactIntent.putExtra("message", newMessage);
		}
		addContactIntent.setAction(action+ViewContactProfileActivity.ACTION_SUFFIX_NEW_CONTACT_ADD);
		PendingIntent addContactIntentPending = PendingIntent.getActivity(context, 0, addContactIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		notificationBuilder.addAction(android.R.drawable.ic_menu_add, "Add", addContactIntentPending);
		
		//Add a button that allows the user to add the contact
		Intent ignoreContactIntent = new Intent(context, ViewContactProfileActivity.class);
		ignoreContactIntent.putExtra("contact", newContact);
		if(newMessage!=null){
			addContactIntent.putExtra("message", newMessage);
		}ignoreContactIntent.setAction(action+ViewContactProfileActivity.ACTION_SUFFIX_NEW_CONTACT_BLOCK);
		PendingIntent ignoreContactIntentPending = PendingIntent.getActivity(context, 0, ignoreContactIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		notificationBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Ignore", ignoreContactIntentPending);
		
		Notification notification = notificationBuilder.build();	//Build the notification
		
		notification.flags |= Notification.FLAG_AUTO_CANCEL;		//We want it to be removed when touched
		
		notification.defaults |= Notification.DEFAULT_SOUND;
		notification.defaults |= Notification.DEFAULT_VIBRATE;
		
		//Send the notification to the OS. The first parameter is a unique tag that we can use to cancel this notification programma	tically if required
		notificationManager.notify(action, MainActivity.NOTIFICATION_NEW_CONTACT, notification);
	}

	/**
	 * Requests the OS to rescan the provided path and add any media found to the system media database
	 * @param filePath			The path of the directory or file to scan
	 */
	protected void requestMediaScan(String path) {
		sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://"+path)));		
	}
}