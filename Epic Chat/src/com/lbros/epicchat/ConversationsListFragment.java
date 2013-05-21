package com.lbros.epicchat;

import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ConversationsListFragment extends Fragment {
	//private final String TAG = "ConversationsListFragment";
	
	private final int ACTION_CREATE_GROUP = 1;
	
	boolean userIdSet = false;
	
	private Database database;
	
	private float pixelDensity;
	private int conversationThumbnailSize;
	
	private SharedPreferences preferences;
	private String userId;
	
	private RelativeLayout fragmentLayout;
	private FragmentActivity parentActivity;

	private ArrayList<Conversation> conversationsList;
	private ConversationsListAdapter conversationsListAdapter;
	
	private IntentFilter messageReceivedFilter, pendingMessagesClearedFilter, conversationsModifiedFilter;
	
	//UI stuff
	private ListView listViewConversations;
	
	//Actions used in the message popup menu
	private final int POPUP_MENU_ACTION_DELETE = 1;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		parentActivity = (FragmentActivity) super.getActivity();
		fragmentLayout = (RelativeLayout) inflater.inflate(R.layout.fragment_conversations_list, container, false);

		messageReceivedFilter = new IntentFilter(MainActivity.intentSignatureNewMessageReceived);
		pendingMessagesClearedFilter = new IntentFilter(MainActivity.intentSignatureRemovePendingMessages);
		conversationsModifiedFilter = new IntentFilter(MainActivity.intentSignatureConversationsModified);
		messageReceivedFilter.setPriority(-5);			//This reciever should have a lower priority than the notification receiver, so that the pending message database's contents are correct before this receiver is woken up
		pendingMessagesClearedFilter.setPriority(-5);	//Ditto
		
		setHasOptionsMenu(true);						//We want the action bar
		
		database = new Database(parentActivity);		//Connect to the SQLite database
		
		preferences = PreferenceManager.getDefaultSharedPreferences(parentActivity);
		userId = preferences.getString("userId", null);
		
		//Setup the UI
		pixelDensity = parentActivity.getResources().getDisplayMetrics().density;
      	conversationThumbnailSize = (int) (60 * pixelDensity + 0.5f);
		
		listViewConversations = (ListView) fragmentLayout.findViewById(R.id.fragment_conversations_listview);

		conversationsListAdapter = new ConversationsListAdapter(parentActivity);		//Create an instance of our custom adapter

		listViewConversations.setAdapter(conversationsListAdapter);											//And link it to the list view

		parentActivity.registerReceiver(conversationsUpdatedReceiver, messageReceivedFilter);
		parentActivity.registerReceiver(conversationsUpdatedReceiver, pendingMessagesClearedFilter);
		parentActivity.registerReceiver(conversationsUpdatedReceiver, conversationsModifiedFilter);
		
		return fragmentLayout;
	}
	
	public void onResume(){
		super.onResume();
		conversationsListAdapter.refresh();
	}
	
	public void onPause(){
		super.onPause();
		
	}
	
	public void onDestroy(){
		super.onDestroy();
		parentActivity.unregisterReceiver(conversationsUpdatedReceiver);
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.menu_conversations_list_fragment, menu);
	}
	
	/**
	 * Called when the options menu or action bar icon is touched 
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.menu_conversations_list_fragment_create_group:
	    	Intent createGroupIntent = new Intent(parentActivity, ChooseConversationActivity.class);
	    	createGroupIntent.putExtra(ChooseConversationActivity.EXTRA_MODE, ChooseConversationActivity.MODE_MULTIPLE_CONTACTS);		//We want to pick multiple contacts
	    	createGroupIntent.putExtra(ChooseConversationActivity.EXTRA_TITLE, "Create group");									//Set the page title
	    	createGroupIntent.putExtra(ChooseConversationActivity.EXTRA_SUBTITLE, "Select contacts to add");						//Set the page subtitle
	    	startActivityForResult(createGroupIntent, ACTION_CREATE_GROUP);
	    	return true;
	    	default:
	            return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent returnedIntent) {
		super.onActivityResult(requestCode, resultCode, returnedIntent);
	    switch(requestCode) { 
	    case ACTION_CREATE_GROUP:			//We have returned from the user creating a group
	    	if(resultCode==Activity.RESULT_OK){
	    		//Retrieve the new conversation
	    		Conversation chosenConversation = (Conversation) returnedIntent.getSerializableExtra(ChooseConversationActivity.EXTRA_CONVERSATION);
	    		if(chosenConversation!=null){
	    			openChatWithUser(chosenConversation.getId());
	    		}
	    	}
	    	break;
	    }
	}

    /**
     * Launches the conversation activity using an Intent, with the provided ID sent as an extra
     * @param conversationId		Conversation ID that will be sent with the new Intent to the Conversation activity
     */
    private void openChatWithUser(String conversationId){
    	Intent openChatIntent = new Intent(parentActivity, ViewConversationsActivity.class);
    	openChatIntent.putExtra(ViewConversationsActivity.EXTRA_CONVERSATION_ID, conversationId);
    	startActivity(openChatIntent);
    }
    
    /**
	 * An inner class that represents a custom list adapter that is used to show a list of ongoing conversations, each with an image and a name
	 * @author Tom
	 *
	 */
	public class ConversationsListAdapter extends BaseAdapter {
		private ArrayList<Conversation> conversationsList;
		Context context;
		
		/**
		 * Constructor
		 * @param newConversationsList	An ArrayList of Conversation objects that this adapter will use
		 * @param newContext			The context of the activity that instantiated this adapter
		 */
		ConversationsListAdapter (Context newContext){
			context = newContext;
			refresh();
		}
		
		private void refresh() {
			//Get the list of contacts from the database
			conversationsList = database.getAllConversations(null);
			notifyDataSetChanged();
		}

		public int getCount() {
			return conversationsList.size();
		}

		public Object getItem(int position) {
			return conversationsList.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view == null){
				LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = vi.inflate(R.layout.fragment_conversations_list_list_item, null);
			}

			ImageView conversationImage = (ImageView) view.findViewById(R.id.fragment_conversations_list_list_item_image);
			TextView conversationName = (TextView) view.findViewById(R.id.fragment_conversations_list_list_item_text);
			TextView conversationLastMessage = (TextView) view.findViewById(R.id.fragment_conversations_list_list_item_subtext);
			TextView conversationUnreadMessageCounter = (TextView) view.findViewById(R.id.fragment_conversations_list_list_item_counter);
			
			//Get the Contact object from the list
			Conversation conversation = conversationsList.get(position);
			
			//Retrieve the list of user IDs contained in this conversation
			final String conversationId = conversation.getId();
			String[] conversationUsers = conversationId.split(",");
			
			//Use these IDs to get the first names of the users
			int nUsers = conversationUsers.length;			
			String userFirstNames = null;
			String tempId;
			String tempFirstName;
			Contact tempContact;
			for(int i=0; i<nUsers; i++){
				tempId = conversationUsers[i];
				//One of the users will be the local user, so ignore this
				if(!tempId.equals(userId)){
					//Get the first name of the user
					tempContact = database.getContact(tempId);
					if(tempContact!=null){
						tempFirstName = tempContact.getFirstName();
						if(userFirstNames==null){		//True if this is the first name in the list
							userFirstNames = tempFirstName;
						}
						else{
							userFirstNames+= ", "+tempFirstName;
						}
					}
				}
			}
			conversation.loadImage(conversationImage, conversationThumbnailSize, conversationThumbnailSize);
			conversationName.setText(userFirstNames);               				//Get the conversation's full name
			
			//Get the contents of the latest message in this particular conversation
			ArrayList<Message> latestMessagesInConversation = database.getMessagesByConversation(conversationId, 0, 1, false);
			if(latestMessagesInConversation.size()>0){		//True if there is a recent message to display
				Message latestMessage = latestMessagesInConversation.get(0);
				//The recent message may be plain text, or it may be an image, so the text we display should reflect this
				CharSequence messageDescription;
				switch(latestMessage.getType()){
				case Message.MESSAGE_TYPE_TEXT:
					messageDescription = latestMessage.getContents(null);
					break;
				case Message.MESSAGE_TYPE_IMAGE:
					messageDescription = "Image";
					break;
				case Message.MESSAGE_TYPE_INVALID:
				default:
					messageDescription = "";
					break;
				}
				conversationLastMessage.setVisibility(View.VISIBLE);					//Show the text and set its value
				conversationLastMessage.setText(messageDescription);
			}
			else{
				conversationLastMessage.setVisibility(View.INVISIBLE);					//Hide the text
			}
			
			//Get the number of pending messages in this conversation
			ArrayList<Message> pendingMessagesInConversation = database.getPendingMessages(conversationId);
			if(pendingMessagesInConversation.size()>0){	//True if there is at least one pending message in this conversation
				int nPendingMessagesInConversation = pendingMessagesInConversation.size();
				conversationUnreadMessageCounter.setVisibility(View.VISIBLE);					//Show the counter and set its value
				conversationUnreadMessageCounter.setText(""+nPendingMessagesInConversation);
			}
			else{
				conversationUnreadMessageCounter.setVisibility(View.INVISIBLE);					//Hide the counter
			}
			
			//A reference to the item's wrapper layout
			RelativeLayout itemLayout = (RelativeLayout) view.findViewById(R.id.fragment_conversations_list_list_item_wrapper);

			//Add an on click listener to this layout
			itemLayout.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					openChatWithUser(conversationId);
				}
			});
			createPopupMenu(itemLayout, conversation);
			return view;
		}
	}
	
	/**
	 * Listens for new messages or when messages have been dismissed from the notification bar. When it is run, it simply triggers a refresh of the list view's adapter
	 */
	BroadcastReceiver conversationsUpdatedReceiver = new BroadcastReceiver(){
	    @Override
	    public void onReceive(Context context, Intent intent){
	    	conversationsListAdapter.refresh();
	    }
	};

	public void createPopupMenu(RelativeLayout itemLayout, final Conversation conversation) {
		final PopupMenu popupMenu = new PopupMenu(parentActivity, itemLayout);
		Menu menu = popupMenu.getMenu();
		//Add various items to the menu
		menu.add(Menu.NONE, POPUP_MENU_ACTION_DELETE, Menu.NONE, "Delete");
		popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			//Called when a popup menu item is touched
			@Override
			public boolean onMenuItemClick(MenuItem menuItem) {
				String toastText = null;
				switch(menuItem.getItemId()){
				case POPUP_MENU_ACTION_DELETE:
					database.deleteConversation(conversation, true);
					conversationsListAdapter.refresh();
					toastText = "Conversation deleted";
					break;
				default:
					break;
				}
				if(toastText!=null){
					Toast.makeText(parentActivity, toastText, Toast.LENGTH_SHORT).show();
				}
				return false;
			}
		});
		itemLayout.setOnLongClickListener(new OnLongClickListener() {
			//Called when list row is long-pressed
			public boolean onLongClick(View arg0) {
				popupMenu.show();
				return false;
			}
		});
	}
}