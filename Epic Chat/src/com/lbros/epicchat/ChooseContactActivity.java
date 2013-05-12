package com.lbros.epicchat;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
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
public class ChooseContactActivity extends Activity {
	//private final String TAG = "ChooseContactActivity";

	private Database database;
	
	ArrayList<Contact> contactsList;
	ContactsListAdapter contactsListAdapter;
	
	IntentFilter contactsSyncCompleteFilter;
	
	//UI stuff
	GridView listViewContacts;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setResult(RESULT_CANCELED);					//In case the user backs out of this activity
		
		setContentView(R.layout.activity_choose_contact);
		database = new Database(this);		//Connect to the SQLite database
	
		//Get the list of contacts from the database
		contactsList = database.getAllContacts(false);

		//Setup the UI
		listViewContacts = (GridView) findViewById(R.id.activity_choose_contact_gridview);
		contactsListAdapter = new ContactsListAdapter(contactsList, this);					//Create an instance of our custom adapter
		listViewContacts.setAdapter(contactsListAdapter);									//And link it to the list view
		listViewContacts.setOnItemClickListener(contactsListItemClickListener);				//And add a listener to it
	}
	
	public void onResume(){
		super.onResume();
		contactsListAdapter.notifyDataSetChanged();
	}
	
	public void onPause(){
		super.onPause();
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
		ContactsListAdapter (ArrayList<Contact> newContactsList, Context newContext){
			contactsList = newContactsList;
			context = newContext;
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
			
			//Add the Contact object to the return Intent and finish the activity
			Intent returnIntent = new Intent();
			returnIntent.putExtra("contact", contact);
			setResult(RESULT_OK, returnIntent);
			finish();
		}
	};
}