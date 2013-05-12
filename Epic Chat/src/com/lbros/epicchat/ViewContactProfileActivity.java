package com.lbros.epicchat;
import java.util.ArrayList;
import java.util.Iterator;

import android.os.Bundle;
import android.app.ActionBar;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class ViewContactProfileActivity extends Activity {
	//private final String TAG = "ViewContactInformationActivity";
	
	public static final String ACTION_PREFIX = "com.lbros.newContact.";
	public static final String ACTION_SUFFIX_NEW_CONTACT_CONFIRM = ".confirm";
	public static final String ACTION_SUFFIX_NEW_CONTACT_ADD = ".add";
	public static final String ACTION_SUFFIX_NEW_CONTACT_BLOCK = ".block";
	
	private Database database;
	
	private NotificationManager notificationManager;
	
	private Contact contact;
	private Message message;

	//UI
	private ActionBar actionBar;
	
	private TextView contactName, contactEmail, contactPhone;
	private ImageView contactImage;
	
	private RelativeLayout confirmContactPanel;
	private TextView confirmContactInfo;
	private Button buttonAddContact, buttonBlockContact;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		database = new Database(this);
		
		notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		
		//Load the UI elements
		setContentView(R.layout.activity_view_contact_information);
		contactName = (TextView) findViewById(R.id.activity_view_contact_information_contact_name);
		contactEmail = (TextView) findViewById(R.id.activity_view_contact_information_contact_email);
		contactPhone = (TextView) findViewById(R.id.activity_view_contact_information_contact_phone);
		contactImage = (ImageView) findViewById(R.id.activity_view_contact_information_contact_image);
		
		confirmContactPanel = (RelativeLayout) findViewById(R.id.activity_view_contact_information_confirm_contact_panel);
		confirmContactInfo = (TextView) findViewById(R.id.activity_view_contact_information_confirm_contact_panel_info);
		buttonAddContact = (Button) findViewById(R.id.activity_view_contact_information_button_add_contact);
		buttonBlockContact = (Button) findViewById(R.id.activity_view_contact_information_button_block_contact);
		buttonAddContact.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addNewContact();
			}
		});
		buttonBlockContact.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				blockNewContact();
			}
		});
		
		//Set up the action bar
		actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		Intent intent = getIntent();
		Bundle intentData = intent.getExtras();
		if(intentData!=null){
			contact = (Contact) intentData.getSerializable("contact");
			message = (Message) intentData.getSerializable("message");
			if(contact==null){		//Cannot continue if there was no valid Contact passed to this activity
				finish();
			}
			else{
				loadContactInfo();
				String action = intent.getAction();
				if(action==null){
					//Do nothing
				}
				else if(action.endsWith(ACTION_SUFFIX_NEW_CONTACT_CONFIRM)){	//User touched the main body of the new contact notification
					showNewContactPanel();
				}
				else if(action.endsWith(ACTION_SUFFIX_NEW_CONTACT_ADD)){		//User touched 'Add' in the new contact notification
					showNewContactPanel();
					addNewContact();
					notificationManager.cancel(ACTION_PREFIX+contact.getId(), MainActivity.NOTIFICATION_NEW_CONTACT);	//Clear the notification
				}
				else if(action.endsWith(ACTION_SUFFIX_NEW_CONTACT_BLOCK)){		//User touched 'Block' in the new contact notification
					blockNewContact();
					notificationManager.cancel(ACTION_PREFIX+contact.getId(), MainActivity.NOTIFICATION_NEW_CONTACT);	//Clear the notification
				}
			}
		}
		else{
			finish();
		}
	}

	private void loadContactInfo() {
		contactName.setText(contact.getFullName());
		contactEmail.setText(contact.getId());
		String phoneNumber = contact.getPhoneNumber();
		if(phoneNumber!=null && !phoneNumber.equals("null")){
			contactPhone.setText(phoneNumber);
		}
		String imagePath = contact.getImagePath();
		if(imagePath!=null){
			contactImage.setImageBitmap(contact.getImageBitmap(null, null, null));   					//Sync load the image
			//new Utils.LoadBitmapAsync(imagePath, contactImage, null, null, true, null).execute();		//Async load the image
		}
		actionBar.setTitle(contact.getFullName());		//Also update the title of the action bar
	}

	private void showNewContactPanel() {		
		confirmContactPanel.setVisibility(View.VISIBLE);
	}

	private void addNewContact() {
		database.addContact(contact);
		//Send a broadcast to indicate a contact sync event has occured. This will cause any activities and fragment displaying contact information to refresh themselves
		Intent newContactIntent = new Intent(MainActivity.intentSignatureContactSyncComplete);
		newContactIntent.putExtra("syncStatus", SyncContactsTask.STATUS_SYNC_COMPLETE);
		sendBroadcast(newContactIntent, null);
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
			sendBroadcast(newMessageIntent, null);
			
			//Show a message to indicate the Contact was added successfully
			contactAddedSuccessfully();
			
			//Finally, launch the conversation
			Intent launchConversationIntent = new Intent(this, ViewConversationsActivity.class);
			launchConversationIntent.putExtra("conversationId", conversationId);
			launchConversationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			//startActivity(launchConversationIntent);
		}
	}

	private void contactAddedSuccessfully() {
		confirmContactInfo.setText("Contact added successfully");
		buttonAddContact.setVisibility(View.INVISIBLE);
		buttonBlockContact.setVisibility(View.INVISIBLE);
	}

	private void blockNewContact() {
		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.view_contact_information, menu);
		return true;
	}
	
	/**
	 * Called when the options menu or action bar icon is touched 
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case android.R.id.home:		//App icon in action bar clicked, so go to the main activity	            
	        	finish();
	            return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
}