package com.lbros.epicchat;

import java.util.ArrayList;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

public class NotificationCreator extends BroadcastReceiver{
	private final String TAG = "NotificationCreator";
	
	private final int notificationLightOffDuration = 2000;
	private final int notificationLightOnDuration = 500;
	private final int notificationLightColour = 0x0000FF;
	
	private NotificationManager notificationManager;
	private NotificationCompat.Builder notificationBuilder;		//Use the NotificationCompat class to ensure compatibility with pre 4.1 devices
	//private Notification notification;
	
	private Database database;
	private SharedPreferences preferences;
	
	/**
	 * Called by the OS when a message broadcast intent is allowed to propagate down to this receiver. This method handles the generation of a multi-line notification,
	 * which shows either a single message (which may expand if neccessary), or an 'inbox-style' list of messages, much like the Gmail app does
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		Message message = (Message) intent.getSerializableExtra("message");
		
		database = new Database(context);
		
		if(message.getType()==Message.MESSAGE_TYPE_ACK){	//If the message is an ACK, update the status of the specified message and exit
			database.updateMessageStatus(message.getContents(null).toString(), Message.MESSAGE_STATUS_ACK_RECIPIENT);
			return;											//Don't want to do any more processing as this is just an ACK
		}
		
		//Add this message to pendingMessages table, if it is not a message from the same user on another device. If that is the case, exit straightaway
		preferences = PreferenceManager.getDefaultSharedPreferences(context);
		String localUserId = preferences.getString("userId", null);
		if(message.getSenderId().equals(localUserId)){		//This message was sent by the same user from another device, so no need to add it to the list of pending messages
			return;
		}
		
		database.addPendingMessage(message);

		//Now retrieve a list of all messages that have not been addressed by the user
		ArrayList<Message> pendingMessages = database.getPendingMessages(null);
		int nPendingMessages = pendingMessages.size();
		
		//Do the common notification stuff
		
		//Get the notification manager
		notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		
		CharSequence textLine = null;
		String senderName;
		Message tempMessage;
		String fromUser;
		String conversationId;
		
		//A two level ArrayList that will contain the Message objects grouped by user id
		ArrayList<ArrayList<Message>> messageList = new ArrayList<ArrayList<Message>>();
		
		ArrayList<String> uniqueUserIds = new ArrayList<String>();
		
		//Iterate from the newest message to the oldest, this will keep the newest message on top of the list, and the '...' underneath the bottom one, if it is neeed
		for(int i=nPendingMessages-1; i>=0; i--){
			tempMessage = pendingMessages.get(i);
			fromUser = tempMessage.getSenderId();
			
			//Build a list of unique user ids. This is used later to determine what kind of notification to send
			if(!uniqueUserIds.contains(fromUser)){		//This user id is not in the list, so add it
				uniqueUserIds.add(fromUser);
				messageList.add(new ArrayList<Message>());	//Add a new empty array list to this list
			}
			//Work out which ArrayList the Message should be placed in
			int listNumber = uniqueUserIds.indexOf(fromUser);
			messageList.get(listNumber).add(tempMessage);
		}
		
		int nUniqueUserIds = uniqueUserIds.size();
		ArrayList<Message> messageGroup;
		Message messageInGroup;
		String messageSentString;
		
		//Loop through each group of messages
		for(int i=0; i<nUniqueUserIds; i++){
			//Build the notification, step by step
			notificationBuilder = new NotificationCompat.Builder(context);
			notificationBuilder.setPriority(Notification.PRIORITY_HIGH);
			notificationBuilder.setLights(notificationLightColour, notificationLightOnDuration, notificationLightOffDuration);
			notificationBuilder.setSmallIcon(R.drawable.note_icon);		//Small icon, goes in the notification bar
			//notificationBuilder.setWhen(0);								//Don't want to show the time
			
			messageGroup = messageList.get(i);
			int nMessagesInGroup = messageGroup.size();
			
			Message firstMessage = messageGroup.get(0);
			fromUser = firstMessage.getSenderId();	
			
			Contact senderContact = database.getContact(fromUser);
			if(senderContact!=null){
				conversationId = firstMessage.getConversationId();

				//Retrieve the sender's profile picture from the database and add it to the notification
				//Get the dimensions the image should be
				Resources resources = context.getResources();
				int notificationIconWidth = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
				int notificationIconHeight = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
				
				Bitmap senderProfilePicture = senderContact.getImageBitmap(notificationIconWidth, notificationIconHeight, null);
				
				notificationBuilder.setLargeIcon(senderProfilePicture);			//Large icon, goes to the left of the notification
				
				senderName = senderContact.getFirstName();
				String nMessagesString = nMessagesInGroup+" new message";
				if(nMessagesInGroup!=1){
					nMessagesString+= 's';
				}			

				notificationBuilder.setContentTitle(senderName);				//Title
				
				//Check how many messages there are on the group. This affects whether we show a standard, big text or inbox style notification
				if(nMessagesInGroup==1){		//Single message, so standard or big text
					//Get first and only message
					messageInGroup = messageGroup.get(0);
					//Get the timestamp of the message in HH:MM form
					messageSentString = "Sent at: "+messageInGroup.getFormattedTime();

					//Some parts of the message depend on the message type
					int messageType = messageInGroup.getType();
					switch(messageType){
					case Message.MESSAGE_TYPE_TEXT:
						textLine = messageInGroup.getContents(null);
						notificationBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(textLine).setSummaryText(messageSentString));		//In this case we should also allow the notification to be expanded to view the entire text
						//notificationBuilder.setSubText(messageSentString);
						notificationBuilder.setContentText(textLine);		//Short one line text
						notificationBuilder.setTicker(senderName+": "+messageInGroup.getContents(100));
						break;
					case Message.MESSAGE_TYPE_IMAGE:
						//Get the bitmap of the image, so we can create a "big picture" notification
						//The message is a JSON object containing several fields, one of which is the file name which we will use to load the bitmap
						String caption = "Image";
						String resourceId = null;
						try {
							JSONObject messageJSON = new JSONObject(messageInGroup.getContents(null).toString());
							//Get the image from the path
							String fileName = messageJSON.getString("fileName");
							String imagePath = MainActivity.DIRECTORY_RESOURCES+fileName;
							Bitmap originalImage = BitmapFactory.decodeFile(imagePath);
							//Scale the image to an appropriate size
							//The area the picture will be displayed in will be up to 256dp, which for xhdpi screens is 512px
							int maxHeight = 512;
							int width = originalImage.getWidth();
							int height = originalImage.getHeight();
							Bitmap scaledImage;
							if(height>maxHeight){		//Image needs to be scaled
								float aspectRatio = (float) width / (float) height;
								int newWidth = (int) (aspectRatio * maxHeight);
								scaledImage = Bitmap.createScaledBitmap(originalImage, newWidth, maxHeight, false);
							}
							else{
								scaledImage = originalImage;
							}
							if(messageJSON.has("caption")){			//If there is a caption, grab it
								Log.d(TAG, "USING CAPTION");
								caption = messageJSON.getString("caption");
							}
							else{									//If not, use the file name instead
								Log.d(TAG, "USING FILENAME");
								caption = fileName;
							}
							notificationBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(scaledImage).setSummaryText(caption));
							resourceId = messageJSON.getString("resourceId");
						}
						catch (JSONException e) {
							Log.e(TAG, "Error reading image JSON: "+e.toString());
						}
						notificationBuilder.setContentText(caption);		//Short one line text for when the notification is not expanded
						notificationBuilder.setTicker(senderName+": "+caption+" (image)");
						
						//Add a button that allows the user to view the image directly in the gallery instead of navigating
						Intent showChatInGalleryIntent = new Intent(context, ViewConversationImageGalleryActivity.class);
						showChatInGalleryIntent.putExtra("conversationId", conversationId);		//This is needed by the conversation image gallery in order to load the set of images in the conversation's gallery
						showChatInGalleryIntent.putExtra("resourceId", resourceId);				//This is needed by the conversation image gallery in order to set the initially displayed image
						String action = "com.lbros.newMessage."+conversationId+".viewInGallery";
						showChatInGalleryIntent.setAction(action);
						showChatInGalleryIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);		//Specifies that the ViewConversationsActivity should be stopped if it is running and recreated. This avoids having many separate copies of this Activity in the back stack
						PendingIntent showImageInGallery = PendingIntent.getActivity(context, 0, showChatInGalleryIntent, PendingIntent.FLAG_UPDATE_CURRENT);
						notificationBuilder.addAction(android.R.drawable.ic_menu_gallery, "View", showImageInGallery);
						break;
					case Message.MESSAGE_TYPE_INVALID:
					default:
						break;
					}
				}
				else{
					//Set the notification to 'inbox style'
					NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
					
					//Loop through the messages in the group and add them to a notification 
					for(int j=0; j<nMessagesInGroup; j++){
						//Get the message in question
						messageInGroup = messageGroup.get(j);
						//Get the timestamp of the message
						messageSentString = messageInGroup.getFormattedTime();
						
						//Some parts of the message depend on the message type
						int messageType = messageInGroup.getType();
						switch(messageType){
						case Message.MESSAGE_TYPE_TEXT:
							textLine = messageInGroup.getContents(100);
							break;
						case Message.MESSAGE_TYPE_IMAGE:
							textLine = "Image";
							break;
						case Message.MESSAGE_TYPE_INVALID:
						default:
							textLine = "";
							break;
						}
						inboxStyle.addLine(textLine);
						inboxStyle.setSummaryText(nMessagesString);
					}
					notificationBuilder.setStyle(inboxStyle);
					notificationBuilder.setContentText(nMessagesString);
					notificationBuilder.setTicker(textLine);
				}
				//Create various Intents for the notification. There are three standard intents: view, reply, and mark as read
				String action = "com.lbros.newMessage."+conversationId;

				//The view intent is sent when the main body of the notification is touched
				Intent viewChatIntent = new Intent(context, ViewConversationsActivity.class);
				viewChatIntent.putExtra("conversationId", conversationId);
				viewChatIntent.setAction(action+".view");
				viewChatIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);		//Specifies that the ViewConversationsActivity should be stopped if it is running and recreated. This avoids having many separate copies of this Activity in the back stack
				
				//The reply intent is sent when the reply button is touched
				Intent replyChatIntent = new Intent(context, ViewConversationsActivity.class);
				replyChatIntent.putExtra("conversationId", conversationId);
				replyChatIntent.putExtra("showKeyboard", true);		//This parameter informs the receiving activity it should show the keyboard
				replyChatIntent.setAction(action+".reply");
				replyChatIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);		//Specifies that the ViewConversationsActivity should be stopped if it is running and recreated. This avoids having many separate copies of this Activity in the back stack
				
				//The delete intent is sent when the notification is dismissed by the user
				Intent deletePendingMessagesIntent = new Intent(context, NotificationDismissed.class);
				deletePendingMessagesIntent.putExtra("conversationId", conversationId);
				deletePendingMessagesIntent.setAction("com.lbros.epicChat.removePendingMessages"+conversationId);
				
				//Create PendingIntents from our Intents
				PendingIntent viewChatIntentPending = PendingIntent.getActivity(context, 0, viewChatIntent, PendingIntent.FLAG_UPDATE_CURRENT);
				PendingIntent replyChatIntentPending = PendingIntent.getActivity(context, 0, replyChatIntent, PendingIntent.FLAG_UPDATE_CURRENT);
				PendingIntent deletePendingMessagesIntentPending = PendingIntent.getBroadcast(context, 0, deletePendingMessagesIntent, 0);
	
				//Add the PendingIntents to the notification
				notificationBuilder.setContentIntent(viewChatIntentPending);
				//notificationBuilder.setDeleteIntent(deletePendingMessagesIntentPending);		//If the message is swiped away or cleared using the clear all button, remove the pending messages from the database

				//Add a button that allows the user to reply
				notificationBuilder.addAction(android.R.drawable.ic_menu_edit, "Reply", replyChatIntentPending);
				
				//Add a button that allows the user to ignore the notification (clearing the list of pending messages for this conversation)
				notificationBuilder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Clear", deletePendingMessagesIntentPending);
	
				notificationBuilder.setOnlyAlertOnce(true);
	
				Notification notification = notificationBuilder.build();					//Build the notification
				
				notification.flags |= Notification.FLAG_AUTO_CANCEL;		//We want it to be removed when touched
				
				//notification.defaults |= Notification.DEFAULT_LIGHTS;		//Use the user-defined values for lights, sounds and vibration
				notification.defaults |= Notification.DEFAULT_SOUND;
				notification.defaults |= Notification.DEFAULT_VIBRATE;
				
				//Send the notification to the OS. The first parameter is a unique tag that we can use to cancel this notification programatically if required
				notificationManager.notify(action, MainActivity.NOTIFICATION_NEW_MESSAGE, notification);
			}
		}
	}
}