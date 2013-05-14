/*
 * Displays a chat with another user
 */

package com.lbros.epicchat;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import android.app.ActionBar;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;

public class ViewConversationsActivity extends FragmentActivity {
	//private final String TAG = "ViewConversationsActivity";

	public final static String EXTRA_CONVERSATION_ID = "conversationId";
	
	private ActionBar actionBar;
	
	private ViewPager conversationPager;
	private PagerTabStrip conversationPagerTabStrip;
	private ConversationPagerAdapter conversationPagerAdapter;
	
	private Database database;
	
	private NotificationManager notificationManager;
	
	private List<ViewConversationFragment> conversationFragments;
	
	private ArrayList<Conversation> conversationsList;
	private ArrayList<String> conversationIds;
	private String requestedConversationId = "";
	
	private SharedPreferences preferences;
	private String localUserId = null;

	/**
	 * Called when the activity is created. Handles all UI setup
	 */
	protected void onCreate(Bundle SavedInstanceState){
		super.onCreate(SavedInstanceState);
		setContentView(R.layout.activity_view_conversations);		
		//Connect to the SQLite database
		database = new Database(this);
		
		//Get the conversation ID and the resource ID of the image to show first form the Intent extras
		Intent intent = getIntent();
		Bundle intentData = intent.getExtras();
		if(intentData!=null){
			requestedConversationId = intentData.getString(EXTRA_CONVERSATION_ID, null);
			//Check if the conversation exists. If not, the user has most likely requested a new conversation, so add one to the database
	    	Conversation conversation = database.getConversation(requestedConversationId);
	    	if(conversation==null){				//Create a new conversation in this case
	    		String[] conversationUsers = requestedConversationId.split(",");
	    		String conversationImagePath = database.getContact(conversationUsers[0]).getImagePath();
	    		Conversation newConversation = new Conversation(requestedConversationId, conversationImagePath);
	    		database.addConversation(newConversation);
	    		conversation = newConversation;
	    	}
		}

		//Get the local user's ID from the preferences. We can't use the static USER_ID field in MainActivity.java as that class may not exist when this activity is invoked from a notification
		preferences = PreferenceManager.getDefaultSharedPreferences(this);
		localUserId = preferences.getString("userId", null);
		
		//Get the notification manager
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		//Set up the action bar
		actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		conversationFragments = new Vector<ViewConversationFragment>();
		
		conversationsList = database.getAllConversations(null);
		
		//Check the intent action
		boolean actionSend = false;
		String action = intent.getAction();
		if(action==null){
			//Do nothing
		}
		else if(action.equals(Intent.ACTION_SEND)){
			actionSend = true;
		}
		
		//Build a list of conversation IDs, this will be used for searching later on
		conversationIds = new ArrayList<String>();
		int nImages = conversationsList.size();
		String tempId = null;
		for(int i=0; i<nImages; i++){
			tempId = conversationsList.get(i).getId();
			conversationIds.add(tempId);
			//Check the intent action. If it is ACTION_SEND and the conversation ID matches the one that we are creating a fragment for, we should pass the intent through to the fragment
			Intent fragmentIntent = null;
			if(actionSend && requestedConversationId!=null && requestedConversationId.equals(tempId)){
				fragmentIntent = intent;
			}
			conversationFragments.add(ViewConversationFragment.newInstance(conversationsList.get(i), fragmentIntent));
		}

		conversationPager = (ViewPager) findViewById(R.id.activity_view_conversations_viewpager);
		conversationPagerAdapter = new ConversationPagerAdapter(getSupportFragmentManager());
		conversationPager.setAdapter(conversationPagerAdapter);
		conversationPager.setPageMargin(20);
		conversationPager.setOnPageChangeListener(imageChangedListener);		//This listener listens for when the contents of this pager change
		//conversationPager.setPageTransformer(false, new ZoomOutPageTransformer(0.95f, 1.0f));
		conversationPagerTabStrip = (PagerTabStrip) findViewById(R.id.activity_view_conversations_viewpager_tab_strip);
		conversationPagerTabStrip.setDrawFullUnderline(false);					//Makes the tab indicator fill the whole width
		conversationPagerTabStrip.setTabIndicatorColor(0xFFFF8800);
		
		//Get the index of the requested conversation, this is the one we should show
		int conversationIndex = conversationIds.indexOf(requestedConversationId);
		if(conversationIndex==0){
			updateActionBar(conversationsList.get(0));	//Update the action bar manually if the index is 0, as the OnPageChangeListener will not fire until the undex is actually changed
			conversationFragments.get(0).setVisibility(true);
			clearNotificationAndPendingMessages(conversationsList.get(0));
		}
		else{
			conversationPager.setCurrentItem(conversationIndex, true);
		}
	}
	
	private void updateActionBar(Conversation conversation){
		//We want to show who sent the message and at what time
		String conversationId = conversation.getId();
		if(conversationId!=null){				//True if we got the sender ID
	    	//Also update the image
			Bitmap conversationImage = conversation.getImageBitmap(100, 100, 8);
	    	Drawable imageDrawable = new BitmapDrawable(getResources(), conversationImage);
	    	actionBar.setIcon(imageDrawable);
	    	String userFirstNames = getUserList(conversation, false);
			actionBar.setTitle(userFirstNames);
		}
	}
	
	private String getUserList(Conversation conversation, boolean includeLocalUser){
		String[] conversationUsers = conversation.getId().split(",");
    	//Use these IDs to get the first names of the users
		int nUsers = conversationUsers.length;			
		String userFirstNames = null;
		String tempId;
		String tempFirstName;
		Contact tempContact;
		for(int i=0; i<nUsers; i++){
			tempId = conversationUsers[i];
			//One of the users will be the local user, so ignore this
			if(!tempId.equals(localUserId) || includeLocalUser){
				//Get the first name of the user
				tempContact = database.getContact(tempId);
				tempFirstName = tempContact.getFirstName();
				if(userFirstNames==null){		//True if this is the first name in the list
					userFirstNames = tempFirstName;
				}
				else{
					userFirstNames+= ", "+tempFirstName;
				}
			}
		}
		return userFirstNames;
	}
	
	public class ConversationPagerAdapter extends FragmentStatePagerAdapter {
		Resource imageBeingShown;

        public ConversationPagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public int getCount() {
            return conversationsList.size();
        }
        
        @Override
		public CharSequence getPageTitle(int position) {
			return getUserList(conversationsList.get(position), false);
		}

        @Override
        public ViewConversationFragment getItem(int position) {
        	return conversationFragments.get(position);
        }
    }
	
	private ViewPager.OnPageChangeListener imageChangedListener = new OnPageChangeListener() {
		@Override
		public void onPageSelected(int position) {
			Conversation conversation = conversationsList.get(position);
			updateActionBar(conversation);
			clearNotificationAndPendingMessages(conversation);
			int nConversations = conversationPagerAdapter.getCount();
    		for(int i=0; i<nConversations; i++){
    			if(i==position){		//This conversation became visible
    				conversationPagerAdapter.getItem(i).setVisibility(true);
    			}
    			else{					//This conversation became invisible
    				conversationPagerAdapter.getItem(i).setVisibility(false);
    			}
    		}
		}

		@Override
		public void onPageScrollStateChanged(int arg0) {}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2) {}
	};

	protected void clearNotificationAndPendingMessages(Conversation conversation) {
		notificationManager.cancel("com.lbros.newMessage."+conversation.getId(), 0x01);
		database.deletePendingMessagesFromConversation(conversation);				//Also remove any pending messages sent from this conversation from the database
	}
}