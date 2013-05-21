package com.lbros.epicchat;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * This Fragment displays a list of all contacts the user has added
 * @author Tom
 *
 */
public class ChooseConversationActivity extends Activity {
	//private final String TAG = "ChooseContactActivity";

	//Intent extra keys
	public static final String EXTRA_MODE = "operationMode";
	public static final String EXTRA_TITLE = "title";
	public static final String EXTRA_SUBTITLE = "subTitle";
	
	public static final String EXTRA_CONVERSATION = "conversation";
	public static final String EXTRA_CONTACT_LIST = "conversationList";
	
	//The two modes this Activity may operate in	
	public static final int MODE_SINGLE_CONTACT = 1;
	public static final int MODE_MULTIPLE_CONTACTS = 2;

	private Database database;
	private SharedPreferences preferences;
	
	private String localUserId;

	private int mode;
	private String pageTitle = null;
	private String pageSubtitle = null;

	private ArrayList<String> contactsSelectedList;
	private ContactsListAdapter contactsListAdapter;
	
	//private IntentFilter contactsSyncCompleteFilter;
	
	//UI stuff
	private ActionBar actionBar;

	private final int COLOUR_BLUE_NOT_SELECTED = 0xFF0099CC;
	private final int COLOUR_GREEN_SELECTED = 0xFF99CC00;
	private GridView listViewContacts;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setResult(RESULT_CANCELED);					//In case the user backs out of this activity
		
		database = new Database(this);
		preferences = PreferenceManager.getDefaultSharedPreferences(this);	//Load the preferences
		localUserId = preferences.getString("userId", null);
		
		actionBar = getActionBar();
		
		setContentView(R.layout.activity_choose_conversation);

		contactsSelectedList = new ArrayList<String>();

		//Setup the UI
		listViewContacts = (GridView) findViewById(R.id.activity_choose_contact_gridview);
		contactsListAdapter = new ContactsListAdapter(this);					//Create an instance of our custom adapter
		listViewContacts.setAdapter(contactsListAdapter);									//And link it to the list view
		listViewContacts.setOnItemClickListener(contactsListItemClickListener);				//And add a listener to it
		
		//Set the mode, which determines how the GridViews onItemClickListener behaves
		Intent intent = getIntent();
		mode = intent.getIntExtra(EXTRA_MODE, MODE_SINGLE_CONTACT);		//If not set, the mode defaults to single user (one contact may be selected)
		//If the page title and / or subtitle were provided, set them now
		pageTitle = intent.getStringExtra(EXTRA_TITLE);
		pageSubtitle = intent.getStringExtra(EXTRA_SUBTITLE);
		if(pageTitle!=null){
			actionBar.setTitle(pageTitle);
		}
		if(pageSubtitle!=null){
			actionBar.setSubtitle(pageSubtitle);
		}
	}
	
	public void onResume(){
		super.onResume();
		contactsListAdapter.refresh();
	}
	
	public void onPause(){
		super.onPause();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.menu_choose_contact, menu);
		if(mode==MODE_MULTIPLE_CONTACTS){		//Only want this menu to be displayed if we are in multiple contact selection mode
			return true;
		}
		else{
			return false;
		}
	}
	
	/**
	 * Called when the options menu or action bar icon is touched 
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case R.id.menu_choose_contact_done:					//The done button was touched, so finish up and return the list of contacts currently selected            
	            if(contactsSelectedList.size()>0){				//At least one contact is selected
	            	//Convert the list of user Ids into a conversation ID
	            	String conversationId = localUserId;
	            	Iterator<String> iterator = contactsSelectedList.iterator();
	            	while(iterator.hasNext()){
	            		conversationId+= ','+iterator.next();
	            	}
	            	Conversation chosenConversation = new Conversation(conversationId, null);
	            	Intent result = new Intent();
	            	result.putExtra(EXTRA_CONVERSATION, chosenConversation);			//Return this conversation object to the calling activity
	            	setResult(RESULT_OK, result);
	            }
	            finish();
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
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
		
		public int getCount() {
			return contactsList.size();
		}

		public Object getItem(int position) {
			return contactsList.get(position);
		}

		public long getItemId(int position) {
			return position;
		}
		
		public void refresh(){
			//Get the list of contacts from the database
			contactsList = database.getAllContacts(false);
			notifyDataSetChanged();
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			if (view==null){
				LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = vi.inflate(R.layout.activity_choose_contact_list_item, null);
			}
			
			//Get the Contact object from the list
			Contact contact = contactsList.get(position);

			ImageView contactImage = (ImageView) view.findViewById(R.id.activity_choose_contact_list_item_image);
			TextView contactName = (TextView) view.findViewById(R.id.activity_choose_contact_list_item_text);
			
			//If the contact is selected, give the heading text a green background
			if(mode==MODE_MULTIPLE_CONTACTS && contactsSelectedList.contains(contact.getId())){
				contactName.setBackgroundColor(COLOUR_GREEN_SELECTED);
			}
			else{		//Othrwise the normal background colour is applied
				contactName.setBackgroundColor(COLOUR_BLUE_NOT_SELECTED);
			}
			
			contactImage.setImageBitmap(contact.getImageBitmap(320, 320, null));
			contactName.setText(contact.getFullName());                 //Get the contact's full name

			return view;
		}
	}
	
	/**
	 * Listens for clicks on the contacts list
	 */
	private OnItemClickListener contactsListItemClickListener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> adapterView, View itemView, int index, long arg3) {		//When a contact is selected, return the relevant contact object to the calling activity 
			final Contact contact = (Contact) contactsListAdapter.getItem(index);
			//This method behaves differently, depending on the mode
			if(mode==MODE_SINGLE_CONTACT){			//Single Contact mode, so just return with this contact
				//Add the Contact object to the return Intent and finish the activity
				Conversation chosenConversation = new Conversation(contact.getId()+','+localUserId, null);
				Intent returnIntent = new Intent();
				returnIntent.putExtra(EXTRA_CONVERSATION, chosenConversation);
				setResult(RESULT_OK, returnIntent);
				finish();
			}
			else if(mode==MODE_MULTIPLE_CONTACTS){
				String contactId = contact.getId();
				boolean contactAlreadyInSelection = contactsSelectedList.contains(contactId);
				if(contactAlreadyInSelection){		//Contact was already selected, so deselect it 
					contactsSelectedList.remove(contactId);		//Remove it from the list
				}
				else{								//Contact was not already selected, so select it
					contactsSelectedList.add(contactId);		//Add it to the list
				}
				contactsListAdapter.refresh();
			}
		}
	};
}