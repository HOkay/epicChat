package com.lbros.epicchat;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * @author Tom
 *
 */
public class ContactNotificationHandler extends BroadcastReceiver {
	private final String TAG = "ContactNotificationHandler";
	
	private Database database;
	private NotificationManager notificationManager;
	
	/**
	 * Called by the OS when an "Add contact" or "Block contact" broadcast is sent by any class
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		//The action will tell us what to do
		String action = intent.getAction();
		if(action==null){
			//Nothing we can do in this case
		}
		else{
			//The intent we receive should also have a contact attached to it, and optionally a message
			Contact contact = (Contact) intent.getSerializableExtra("contact");
			Message message = (Message) intent.getSerializableExtra("message");

			database = new Database(context);
			notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			
			if(contact!=null){
				if(action.endsWith("add")){		//There is a new Contact to add to the database
					database.addContact(contact);
					//Send a broadcast to indicate a contact sync event has occured. This will cause any activities and fragment displaying contact information to refresh themselves
					Intent newContactIntent = new Intent(MainActivity.intentSignatureContactSyncComplete);
					newContactIntent.putExtra("syncStatus", SyncContactsTask.STATUS_SYNC_COMPLETE);
					context.sendBroadcast(newContactIntent, null);
					//Now let's check if there was a message sent with this Broadcast
					if(message!=null){
						//First check if the conversation to which the message belongs exists on this device
						String conversationId = message.getConversationId();
						Conversation messageConversation = database.getConversation(conversationId);
						if(messageConversation==null){		//Conversation does not exist, let's create it
							messageConversation = new Conversation(conversationId, contact.getImagePath());
							database.addConversation(messageConversation);
						}
						//Any messages sent by this contact up to this point have been stored in the pending messages table. We can use this to add all these messages to the conversation.
						//This means that if the contact sent more than one message before the local user added them as a contact, all this messages will be visible 
						ArrayList<Message> pendingMessages = database.getPendingMessages(conversationId);
						Iterator<Message> iterator = pendingMessages.iterator();
						
						while(iterator.hasNext()){
							database.addMessage(iterator.next());		//Add each message
						}
						
						//Send a broadcast to indicate a contact sync event has occured
						Intent newMessageIntent = new Intent(MainActivity.intentSignatureConversationsModified);
						context.sendBroadcast(newMessageIntent, null);
						//Finally, launch the conversation
						Intent launchConversationIntent = new Intent(context, ViewConversationsActivity.class);
						launchConversationIntent.putExtra("conversationId", conversationId);
						launchConversationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						context.startActivity(launchConversationIntent);
					}
				}
				else if(action.endsWith("block")){		//There is a new Contact to add to the block list
					
				}
				//Cancel the notification
				String tag = "com.lbros.newContact."+contact.getId();
				notificationManager.cancel(tag, MainActivity.NOTIFICATION_NEW_CONTACT);
			}
		}
	}
}
/* HELLO MARTYN */