package com.lbros.epicchat;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Receives a broadcast intent from the OS when the user dismisses a notification from the notification drawer
 * (either by swiping it away or by touching the clear all notifications button)
 * @author Tom
 *
 */
public class NotificationDismissed extends BroadcastReceiver {	
	/**
	 * Called by the OS when the notification is dismissed. Handles the removal of all messages from the pending messages
	 * database table that are associated with this particular notification
	 * @param context		The context of the notification
	 * @param intent		The intent that was attached to the notification when it was created 
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		//Extract the user id associated with the message
		String conversationId = intent.getStringExtra("conversationId");
		if(conversationId!=null){		//We successfully got the conversation ID
			Database database = new Database(context);
			Conversation conversation = database.getConversation(conversationId);
			if(conversation!=null){
				database.deletePendingMessagesFromConversation(conversation);
				//Send a new broadcast intent that will refresh the UI in the conversation list, if that screen is being shown. This has to be done here as the PendingIntent we received here does not support ordered broadcasts
				//Send a broadcast to indicate a new message has arrived
				Intent newMessageIntent = new Intent(MainActivity.intentSignatureRemovePendingMessages);
				context.sendOrderedBroadcast(newMessageIntent, null);
			}
			//One last thing to check is if the notification that sent this broadcast is still being displayed. Thismay be the case if the user touched the "Ignore" button in the notification
			NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.cancel("com.lbros.newMessage."+conversationId, MainActivity.NOTIFICATION_NEW_MESSAGE);
		}
	}
}