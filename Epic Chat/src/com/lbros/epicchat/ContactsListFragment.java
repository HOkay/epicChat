package com.lbros.epicchat;

import java.util.ArrayList;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
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
	
	private float pixelDensity;
	private int contactThumbnailSize;
	
	private SharedPreferences preferences;
	private String userId;

	private RelativeLayout fragmentLayout;
	private FragmentActivity parentActivity;
	
	private ContactsListAdapter contactsListAdapter;
	
	private IntentFilter contactsSyncCompleteFilter;
	
	//UI stuff
	GridView listViewContacts;
	ProgressBar progressBarSyncStatusProgress;
	TextView textViewSyncStatusText;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		parentActivity = (FragmentActivity) super.getActivity();
		fragmentLayout = (RelativeLayout) inflater.inflate(R.layout.fragment_contacts_list, container, false);
		
		contactsSyncCompleteFilter = new IntentFilter(MainActivity.intentSignatureContactSyncComplete);
		
		parentActivity.registerReceiver(contactsUpdatedReceiver, contactsSyncCompleteFilter);

		setHasOptionsMenu(true);						//We want the action bar
		
		database = new Database(parentActivity);		//Connect to the SQLite database
		
		preferences = PreferenceManager.getDefaultSharedPreferences(parentActivity);
		userId = preferences.getString("userId", null);
	
		//Setup the UI
		pixelDensity = parentActivity.getResources().getDisplayMetrics().density;
		contactThumbnailSize = (int) (160 * pixelDensity + 0.5f);
		
		listViewContacts = (GridView) fragmentLayout.findViewById(R.id.fragment_contacts_gridview);
		contactsListAdapter = new ContactsListAdapter(parentActivity);					//Create an instance of our custom adapter
		listViewContacts.setAdapter(contactsListAdapter);												//And link it to the list view
		listViewContacts.setOnItemClickListener(contactsListItemClickListener);							//And add a listener to it

		progressBarSyncStatusProgress = (ProgressBar) fragmentLayout.findViewById(R.id.fragment_conversations_sync_status_progress);
		textViewSyncStatusText = (TextView) fragmentLayout.findViewById(R.id.fragment_conversations_sync_status_text);
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
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.menu_contacts_list_fragment, menu);
	}
	
	/**
	 * Called when the options menu or action bar icon is touched 
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.menu_contacts_list_fragment_sync_contacts:
	        SyncContactsTask syncContactsTask = new SyncContactsTask(getActivity());
			syncContactsTask.execute();
			return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}

	/**
	 * An inner class that represents a custom list adapter that is used to show a list of contacts, each with an image and a name
	 * @author Tom
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
			if (view == null){
				LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = vi.inflate(R.layout.fragment_contacts_list_list_item, null);
			}

			ImageView contactImage = (ImageView) view.findViewById(R.id.fragment_contacts_list_list_item_image);
			TextView contactName = (TextView) view.findViewById(R.id.fragment_contacts_list_list_item_text);
			
			//Get the Contact object from the list
			Contact contact = contactsList.get(position);

			if(contact!=null){
				contact.loadImage(contactImage, contactThumbnailSize, contactThumbnailSize);
				contactName.setText(contact.getFullName());                 //Get the contact's full name
			}
			return view;
		}
	}
	
	/**
     * Launches the conversation activity using an Intent, with the provided ID sent as an extra
     * @param conversationId		Conversation ID that will be sent with the new Intent to the Conversation activity
     */
    private void openChat(String conversationId){
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
		public void onItemClick(AdapterView<?> adapterView, View itemView, int index, long arg3) {
			Contact contact = (Contact) contactsListAdapter.getItem(index);
			showUserMenuDialog(contact);
		}
	};
	
	//Displays a dialog box that shows the selected image to the user, along with a text box for adding a caption to the message 
	private void showUserMenuDialog(final Contact contact) {
		//Create the dialog
		final Dialog imagePreviewDialog = new Dialog(parentActivity);
		imagePreviewDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		imagePreviewDialog.setContentView(R.layout.dialog_contact_menu);
		imagePreviewDialog.setOnShowListener(new OnShowListener() {
			@Override
			public void onShow(final DialogInterface dialog) {
				//Increase the amount of background dimming
				WindowManager.LayoutParams params = imagePreviewDialog.getWindow().getAttributes();
				params.dimAmount = 0.8f;
				imagePreviewDialog.getWindow().setAttributes(params);
				ImageView userImage = (ImageView) imagePreviewDialog.findViewById(R.id.dialog_contact_menu_image);
				userImage.setImageBitmap(contact.getImageBitmap(600, 600, null));
				TextView userName = (TextView) imagePreviewDialog.findViewById(R.id.dialog_contact_menu_name);
				userName.setText(contact.getFullName());
				Button buttonChat = (Button) imagePreviewDialog.findViewById(R.id.dialog_contact_menu_button_chat);
				buttonChat.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						String conversationId = contact.getId()+","+userId;
						openChat(conversationId);
						dialog.dismiss();
					}
				});
				Button buttonViewProfile = (Button) imagePreviewDialog.findViewById(R.id.dialog_contact_menu_button_profile);
				buttonViewProfile.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						Intent viewProfileIntent = new Intent(parentActivity, ViewContactProfileActivity.class);
						viewProfileIntent.putExtra("contact", contact);
						startActivity(viewProfileIntent);
						dialog.dismiss();
					}
				});
				Button buttonRemove = (Button) imagePreviewDialog.findViewById(R.id.dialog_contact_menu_button_remove);
				buttonRemove.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						database.deleteContact(contact, true);
						contactsListAdapter.refresh();
						//Send a Broadcast to alert the ConversationsListFragment that a conversation may have been deleted
						//Send a broadcast to indicate a contact sync event has occured
						Intent newMessageIntent = new Intent(MainActivity.intentSignatureConversationsModified);
						parentActivity.sendBroadcast(newMessageIntent, null);
						dialog.dismiss();
						Toast.makeText(parentActivity, "Contact \""+contact.getFullName()+"\" removed", Toast.LENGTH_SHORT).show();
					}
				});
			}
		});
		imagePreviewDialog.show();
	}
	
	/**
	 * Listens for events that cause the list of contacts to change
	 */
	BroadcastReceiver contactsUpdatedReceiver = new BroadcastReceiver(){
	    @Override
	    public void onReceive(Context context, Intent intent){
	    	//Also check the intent for the presence of the syncStatus flag
	    	int syncStatus = intent.getIntExtra("syncStatus", -1);
	    	switch(syncStatus){
	    	case SyncContactsTask.STATUS_SYNC_STARTED:
	    		setSyncProgressIndicatorVisibility(true);
	    		break;
	    	case SyncContactsTask.STATUS_SYNC_NEW_CONTACT:
	    		contactsListAdapter.refresh();
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