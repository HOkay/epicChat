package com.lbros.epicchat;

import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * This Fragment displays a list of all contacts the user has added
 * @author Tom
 *
 */
public class ContactsListFragment extends Fragment {
	//private final String TAG = "ContactsListFragment";

	private Database database;
	
	private SharedPreferences preferences;
	private String userId;

	private RelativeLayout fragmentLayout;
	private FragmentActivity parentActivity;
	
	ArrayList<Contact> contactsList;
	ContactsListAdapter contactsListAdapter;
	
	IntentFilter contactsSyncCompleteFilter;
	
	//UI stuff
	ListView listViewContacts;
	ImageButton buttonSyncContacts;
	ProgressBar progressBarSyncStatusProgress;
	TextView textViewSyncStatusText;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		parentActivity = (FragmentActivity) super.getActivity();
		fragmentLayout = (RelativeLayout) inflater.inflate(R.layout.fragment_contacts_list, container, false);
		
		contactsSyncCompleteFilter = new IntentFilter(MainActivity.intentSignatureContactSyncComplete);
		
		parentActivity.registerReceiver(contactsUpdatedReceiver, contactsSyncCompleteFilter);

		database = new Database(parentActivity);		//Connect to the SQLite database
		
		preferences = PreferenceManager.getDefaultSharedPreferences(parentActivity);
		userId = preferences.getString("userId", null);
	
		//database.deleteContact("elizaleja@gmail.com");
		
		

		//Setup the UI
		listViewContacts = (ListView) fragmentLayout.findViewById(R.id.fragment_contacts_listview);
		contactsListAdapter = new ContactsListAdapter(parentActivity);					//Create an instance of our custom adapter
		listViewContacts.setAdapter(contactsListAdapter);												//And link it to the list view
		listViewContacts.setOnItemClickListener(contactsListItemClickListener);							//And add a listener to it
		
		buttonSyncContacts = (ImageButton) fragmentLayout.findViewById(R.id.fragment_conversations_button_sync);
		progressBarSyncStatusProgress = (ProgressBar) fragmentLayout.findViewById(R.id.fragment_conversations_sync_status_progress);
		textViewSyncStatusText = (TextView) fragmentLayout.findViewById(R.id.fragment_conversations_sync_status_text);
		
		buttonSyncContacts.setOnClickListener(new View.OnClickListener() {			
			@Override
			public void onClick(View v) {		//When this button is touched, start a new contacts sync task
				SyncContactsTask syncContactsTask = new SyncContactsTask(getActivity());
				syncContactsTask.execute();
			}
		});

		return fragmentLayout;
	}
	
	public void onResume(){
		super.onResume();
		contactsListAdapter.notifyDataSetChanged();
		parentActivity.registerReceiver(contactsUpdatedReceiver, contactsSyncCompleteFilter);
	}
	
	public void onPause(){
		super.onPause();
		parentActivity.unregisterReceiver(contactsUpdatedReceiver);
	}

	/**
	 * An inner class that represents a custom list adapter that is used to show a list of contacts, each with an image and a name
	 * @author Tom
	 *
	 */
	public class ContactsListAdapter extends BaseAdapter {
		private ArrayList<Contact> contactsList;
		Context context;
		
		/**
		 * Constructor
		 * @param newContactsList		An ArrayList of Contact objects that this adapter will use
		 * @param newContext			The context of the activity that instantiated this adapter
		 */
		ContactsListAdapter (Context newContext){
			context = newContext;
			refresh();
		}
		
		private void refresh() {
			//Get the list of contacts from the database
			contactsList = database.getAllContacts(false);
			notifyDataSetChanged();
		}

		public int getCount() {
			return contactsList.size();
		}

		public Object getItem(int position) {
			return contactsList.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view == null)
			{
				LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = vi.inflate(R.layout.fragment_contacts_list_list_item, null);
			}

			ImageView contactImage = (ImageView) view.findViewById(R.id.fragment_contacts_list_list_item_image);
			TextView contactName = (TextView) view.findViewById(R.id.fragment_contacts_list_list_item_text);
			
			//Get the Contact object from the list
			Contact contact = contactsList.get(position);

			if(contact!=null){
				Bitmap bitmap = contact.getImageBitmap(120, 120, 6);
				if(bitmap!=null){
					contactImage.setImageBitmap(bitmap);
				}
				contactName.setText(contact.getFullName());                 //Get the contact's full name
			}

			return view;
		}
	}
	
	/**
     * Launches the conversation activity using an Intent, with the provided ID sent as an extra
     * @param conversationId		Conversation ID that will be sent with the new Intent to the Conversation activity
     */
    private void openChatWithUser(String conversationId){
    	Intent openChatIntent = new Intent(parentActivity, ViewConversationsActivity.class);
    	openChatIntent.putExtra("conversationId", conversationId);
    	startActivity(openChatIntent);
    }

	/**
	 * Sets the visibility of the "Syncing contacts" progress indicator and text
	 * @param visible			Boolean, if true the indicator will be shown, false and it will be hidden
	 */
	protected void setSyncProgressIndicatorVisibility(boolean visible) {
		int visibility;
		if(visible){
			visibility = View.VISIBLE;
		}
		else{
			visibility = View.INVISIBLE;
		}
		progressBarSyncStatusProgress.setVisibility(visibility);
		textViewSyncStatusText.setVisibility(visibility);
	}
    
	
	/**
	 * Listens for clicks on the contacts list
	 */
	private OnItemClickListener contactsListItemClickListener = new OnItemClickListener() {
		private final int MENU_ITEM_OPEN_CHAT = 0;
		private final int MENU_ITEM_VIEW_PROFILE = 1;
		private final int MENU_ITEM_REMOVE_CONTACT = 2;
		@Override
		public void onItemClick(AdapterView<?> adapterView, View itemView, int index, long arg3) {
			final Contact contact = (Contact) contactsListAdapter.getItem(index);
			
			final String contactId = contact.getId();
			final String contactFullName = contact.getFullName();
			AlertDialog.Builder contextDialogBuilder = new AlertDialog.Builder(getActivity());
			contextDialogBuilder.setTitle(contactFullName);
			//Set the icon
			Bitmap contactImage = contact.getImageBitmap(100, 100, null);
	    	Drawable imageDrawable = new BitmapDrawable(getResources(), contactImage);
	    	contextDialogBuilder.setIcon(imageDrawable);
			String[] menuOptions = new String[]{"Chat", "View profile", "Remove"};
			contextDialogBuilder.setItems(menuOptions, new OnClickListener() {				
				@Override
				public void onClick(DialogInterface arg0, int index) {
					switch(index){
					case MENU_ITEM_OPEN_CHAT:		//First item is the "Chat" button
						String conversationId = contactId+","+userId;
						openChatWithUser(conversationId);
						break;
					case MENU_ITEM_VIEW_PROFILE:		//First item is the "Chat" button
						Intent viewProfileIntent = new Intent(parentActivity, ViewContactProfileActivity.class);
						viewProfileIntent.putExtra("contact", contact);
						startActivity(viewProfileIntent);
						break;
					case MENU_ITEM_REMOVE_CONTACT:		//First item is the "Chat" button
						database.deleteContact(contact, true);
						contactsListAdapter.refresh();
						//Send a Broadcast to alert the ConversationsListFragment that a conversation may have been deleted
						//Send a broadcast to indicate a contact sync event has occured
						Intent newMessageIntent = new Intent(MainActivity.intentSignatureConversationsModified);
						parentActivity.sendBroadcast(newMessageIntent, null);
						Toast.makeText(parentActivity, "Contact \""+contactFullName+"\" removed", Toast.LENGTH_SHORT).show();
						break;
					default:
						break;
					}
				}
			});
			contextDialogBuilder.setNegativeButton("Back", new OnClickListener() {				
				@Override
				public void onClick(DialogInterface dialog, int arg1) {
					dialog.cancel();
				}
			});
			AlertDialog contextDialog = contextDialogBuilder.create();
			contextDialog.show();
		}
	};
	
	/**
	 * Listens for events that cause the list of contacts to change
	 */
	BroadcastReceiver contactsUpdatedReceiver = new BroadcastReceiver(){
	    @Override
	    public void onReceive(Context context, Intent intent){
	    	contactsListAdapter.refresh();
	    	//Also check the intent for the presence of the syncStatus flag
	    	int syncStatus = intent.getIntExtra("syncStatus", -1);
	    	switch(syncStatus){
	    	case SyncContactsTask.STATUS_SYNC_STARTED:
	    		setSyncProgressIndicatorVisibility(true);
	    		break;	    		
	    	case SyncContactsTask.STATUS_SYNC_COMPLETE:
	    		setSyncProgressIndicatorVisibility(false);
	    		break;	
	    	case -1:
	    	default:
	    		break;
	    	}
	    }
	};
}