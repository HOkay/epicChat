package com.lbros.epicchat;

import java.util.ArrayList;
import java.util.Iterator;

import com.google.android.apps.dashclock.api.DashClockExtension;
import com.google.android.apps.dashclock.api.ExtensionData;

import android.content.Intent;
import android.util.Log;

public class DashClockPlugin extends DashClockExtension {
    private final String TAG = "ExampleExtension";
    
    private Database database;
    
    @Override
    protected void onInitialize(boolean isReconnect){
    	setUpdateWhenScreenOn(true);			//Makes DashClock update our plugin when the screen is turned on
    	addWatchContentUris(new String[]{"content://com.lbros.epicchat.PendingMessages"});
    	Log.d(TAG, "INIT");
    }
    
    @Override
    protected void onUpdateData(int reason) {
    	Log.d(TAG, "UPDATE, REASON: "+reason);
    	database = new Database(this);

    	ArrayList<Message> pendingMessages = database.getPendingMessages(null);		//Get all pending messages
    	
    	int nPendingMessages = pendingMessages.size();
    	
    	ExtensionData extensionData = new ExtensionData();
    	
    	boolean showNotification = false;
    	
		if(nPendingMessages>0){			//True if there is at least one unread message
			showNotification = true;
    		String expandedTitle = nPendingMessages+" unread message";
    		if(nPendingMessages!=1){
    			expandedTitle+= 's';
    		}
    		
    		ArrayList<String> conversationIds = new ArrayList<String>();
    		
    		Iterator<Message> iterator = pendingMessages.iterator();
    		Message tempMessage;
    		String conversationId;
    		while(iterator.hasNext()){				//Loop through all unread messages, building a list of unique conversation Ids
    			tempMessage = iterator.next();
    			conversationId = tempMessage.getConversationId();
    			if(!conversationIds.contains(conversationId)){		//True if this is a new conversation Id
    				conversationIds.add(conversationId);
    			}
    		}
    		
    		String expandedBody = null;
			
    		int nConversations = conversationIds.size();
    		if(nConversations==1){			//Only one converation
    			Message firstMessage = pendingMessages.get(nPendingMessages - 1);
    			String senderId = firstMessage.getSenderId();
    			Contact sender = database.getContact(senderId);
    			
    			//The body will be the sender's name, plus some of the message (we will clip it to fit)
    			expandedBody = sender.getFirstName()+": ";
    			int charsUsed = expandedBody.length() + 5;		//Plus 5 is for the colon, space, and the three possible dots
    			expandedBody+= firstMessage.getContents(70 - charsUsed);
    		}
    		else{			//More than one conversation
    			expandedBody = "In "+nConversations+" conversations";
    		}
    		
    		extensionData.icon(R.drawable.note_icon);				//Use the small white icon
    		extensionData.status(nPendingMessages+"");				//Small title should be just the number
            extensionData.expandedTitle(expandedTitle);				//Large title should have the number of unread messages
            extensionData.expandedBody(expandedBody);				//And the large body describes the conversations they belong to
            Intent showConversation = new Intent(this, ViewConversationsActivity.class);
            //This intent will launch the view conversations activity. The conversation to view is conversation to which the first pending message belongs
            showConversation.putExtra(ViewConversationsActivity.EXTRA_CONVERSATION_ID, pendingMessages.get(0).getConversationId());
            extensionData.clickIntent(showConversation);
    	}
    	
		extensionData.visible(showNotification);							//Show the item if there are messages to display
		// Publish the extension data update.
        publishUpdate(extensionData);
    }
}