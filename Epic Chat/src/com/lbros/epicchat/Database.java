package com.lbros.epicchat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * A global database class that holds all persistent data that cannot be stored in the Shared Preferences
 * @author Tom
 *
 */
public class Database extends SQLiteOpenHelper {
	private final static String TAG =  "Database";
	
	private Context context;
	 
    //Database Version
    private static final int DATABASE_VERSION = 14;
 
    //Database Name
    private static final String DATABASE_NAME = "epicChatDatabase";
 
    //Table names
    private final String TABLE_MESSAGES = "messages";
    private final String TABLE_PENDING_MESSAGES = "pendingMessages";
    private final String TABLE_CONTACTS = "contacts";
    private final String TABLE_CONVERSATIONS = "conversations";
    private final String TABLE_RESOURCES = "resources";
    private final String TABLE_GAMES = "games";
    		   
    //Shared column names
    private final String KEY_ID = "id";
    private final String KEY_TIMESTAMP = "timestamp";

    //Messsages table column names
    private final String KEY_USER_LIST = "userList";
    private final String KEY_FROM_USER = "fromUser";
    private final String KEY_CONTENTS = "contents";
    private final String KEY_MESSAGE_TYPE = "type";
    private final String KEY_STATUS = "status";
    
    //Contacts table column names
    private final String KEY_FIRST_NAME = "firstName";
    private final String KEY_LAST_NAME = "lastName";
    private final String KEY_PHONE_NUMBER = "phoneNumber";
    private final String KEY_IMAGE_PATH = "imagePath";
    
    //Resources column names
    private final String KEY_TYPE = "type";
    private final String KEY_CONVERSATION_ID = "conversationId";
    private final String KEY_PATH = "path";
    private final String KEY_TEXT = "text";
    
    //Games column names
    private final String KEY_GENRE = "genre";
    private final String KEY_RELEASE_DATE = "releaseDate";
    private final String KEY_SHORT_NAME = "shortName";
    private final String KEY_LONG_NAME = "longName";
    private final String KEY_DESCRIPTION = "description";
    private final String KEY_RATING = "rating";
    
    //Creation statements for each of the tables
    private String createTableMessages = "CREATE TABLE IF NOT EXISTS "+TABLE_MESSAGES+" ("+KEY_ID+" TEXT PRIMARY KEY,"+KEY_TIMESTAMP+" INTEGER,"+KEY_USER_LIST+" TEXT,"+KEY_FROM_USER+" TEXT, "+KEY_CONTENTS+" TEXT, "+KEY_MESSAGE_TYPE+" INTEGER , "+KEY_STATUS+" INTEGER)";
    private String createTablePendingMessages = "CREATE TABLE IF NOT EXISTS "+TABLE_PENDING_MESSAGES+" ("+KEY_ID+" TEXT PRIMARY KEY,"+KEY_TIMESTAMP+" INTEGER,"+KEY_USER_LIST+" TEXT,"+KEY_FROM_USER+" TEXT, "+KEY_CONTENTS+" TEXT, "+KEY_MESSAGE_TYPE+" INTEGER)";
    private String createTableContacts = "CREATE TABLE IF NOT EXISTS "+TABLE_CONTACTS+" ("+KEY_ID+" TEXT PRIMARY KEY,"+KEY_FIRST_NAME+" TEXT,"+KEY_LAST_NAME+" TEXT,"+KEY_PHONE_NUMBER+" TEXT, "+KEY_IMAGE_PATH+" TEXT)";
    private String createTableConversations = "CREATE TABLE IF NOT EXISTS "+TABLE_CONVERSATIONS+" ("+KEY_ID+" TEXT PRIMARY KEY, "+KEY_IMAGE_PATH+" TEXT)";
    private String createTableResources = "CREATE TABLE IF NOT EXISTS "+TABLE_RESOURCES+" ("+KEY_ID+" TEXT PRIMARY KEY, "+KEY_TYPE+" INTEGER, "+KEY_TIMESTAMP+" INTEGER, "+KEY_CONVERSATION_ID+" TEXT, "+KEY_PATH+" TEXT, "+KEY_FROM_USER+" TEXT, "+KEY_TEXT+" TEXT)";
    private String createTableGames = "CREATE TABLE IF NOT EXISTS "+TABLE_GAMES+" ("+KEY_ID+" TEXT PRIMARY KEY, "+KEY_GENRE+" INTEGER, "+KEY_SHORT_NAME+" TEXT, "+KEY_LONG_NAME+" TEXT, "+KEY_DESCRIPTION+" TEXT, "+KEY_RATING+" INTEGER, "+KEY_RELEASE_DATE+" INTEGER, "+KEY_IMAGE_PATH+" TEXT)";

    /**
     * Constructor for the Database. Upgrading between database versions is handled by the system
     * @param newContext		The context of the activity or service that instantiated the Database
     */
    public Database(Context newContext) {
        super(newContext, DATABASE_NAME, null, DATABASE_VERSION);
        context = newContext;
    }

	/**
	 * Called when the database is created. Handles creation of the various tables
	 */
    @Override
    public void onCreate(SQLiteDatabase database) {
    	//Create the various tables we will use
        database.execSQL(createTableMessages);
        database.execSQL(createTablePendingMessages);
        database.execSQL(createTableContacts);
        database.execSQL(createTableConversations);
        database.execSQL(createTableResources);
        database.execSQL(createTableGames);
    }
 
    /**
     * Called when the database is upgraded. Handles any changes we define
     */
    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        //Changes are handled in a for loop, with each change represented as an interation
    	String updateOperation = null;
    	for(int i=oldVersion+1; i<=newVersion; i++){
    		switch(i){
    		case 4:				//Version 4 adds the contacts table
    			updateOperation = createTableContacts;
    	        break;
    		case 5:				//Version 5 adds the image location column to the contacts table
    			updateOperation = "ALTER TABLE "+TABLE_CONTACTS+" ADD COLUMN "+KEY_IMAGE_PATH+" TEXT";
    			break;
    		case 6:				//Version 6 adds the conversations table
    			updateOperation = createTableConversations;
    			break;
    		case 7:				//Version 7 adds a message type field to the messages table
    			updateOperation = "ALTER TABLE "+TABLE_MESSAGES+" ADD COLUMN "+KEY_MESSAGE_TYPE+" INTEGER";
    			break;
    		case 8:				//Version 8 adds a message type field to the pending messages table
    			updateOperation = "ALTER TABLE "+TABLE_PENDING_MESSAGES+" ADD COLUMN "+KEY_MESSAGE_TYPE+" INTEGER";
    			break;
    		case 9:				//Version 9 adds a status field to the messages table
    			updateOperation = "ALTER TABLE "+TABLE_MESSAGES+" ADD COLUMN "+KEY_STATUS+" INTEGER";
    			break;
    		case 10:			//Version 10 adds the resources table
    			updateOperation = createTableResources;
    			break;
    		case 11:			//Version 11 adds the from user field to the resources table
    			updateOperation = "ALTER TABLE "+TABLE_RESOURCES+" ADD COLUMN "+KEY_FROM_USER+" TEXT";
    			break;
    		case 12:			//Version 12 adds a text field to the resources table
    			updateOperation = "ALTER TABLE "+TABLE_RESOURCES+" ADD COLUMN "+KEY_TEXT+" TEXT";
    			break;
    		case 13:			//Version 13 doesn't exist (I screwed up the versions somehow)
    			break;
    		case 14:			//Versaion 14 adds the games table
    			updateOperation = createTableGames;
    			break;
    		default:
    			updateOperation = null;
    			break;
    		}
    		if(updateOperation!=null){
    			database.execSQL(updateOperation);
    		}
    	}
    	Log.d(TAG, "Database upgraded to version "+newVersion);
    	onCreate(database);
    }
    
    /**
     * Adds a message to the messages table
     * @param message		The Message to add to the database
     */
    public void addMessage(Message message){
    	SQLiteDatabase database = this.getWritableDatabase();	//Connect to the database
    	
    	//We will store this message in a row in the database, which is represented by a set of values contained in a ContentValues object
    	ContentValues row = new ContentValues();
        row.put(KEY_ID, message.getId()); 						//Message ID
        row.put(KEY_TIMESTAMP, message.getTimestamp()); 		//Message timestamp. Always in GMT, because we bloody invented time
        row.put(KEY_MESSAGE_TYPE, message.getType()); 			//Message type
        row.put(KEY_STATUS, message.getStatus()); 				//Message status
        row.put(KEY_USER_LIST, message.getConversationId()); 	//Set of users in the conversation
        row.put(KEY_FROM_USER, message.getSenderId()); 			//The user who sent the message
        row.put(KEY_CONTENTS, message.getContents(null).toString());		//The actual text
  
        //Insert the row
        database.insert(TABLE_MESSAGES, null, row);
        database.close(); 										//Close the database connection
        
        //Check if there exists a conversation for this message. If this is the first message and was sent from another user, there will not be, so a conversation must be created
        String conversationId = message.getConversationId();
		Conversation messageConversation = getConversation(conversationId);
		if(messageConversation==null){		//True if the conversation does not already exist, so let's add it
			Contact senderContact = getContact(message.getSenderId());
			if(senderContact!=null){
				messageConversation = new Conversation(conversationId, senderContact.getImagePath());
				addConversation(messageConversation);
			}
		}
    }
    
    /**
     * Updates the status of a message in the messages table
     * @param messageId		The unique id of the message to update
     * @param newStatus		The new status of the message. Possible values are defined as fields in the Message class
     */
    public void updateMessageStatus(String messageId, int newStatus){
    	SQLiteDatabase database = this.getWritableDatabase();	//Connect to the database
    	
    	String selector = KEY_ID+"=\""+messageId+"\"";
    	
    	//We will store this message in a row in the database, which is represented by a set of values contained in a ContentValues object
    	ContentValues row = new ContentValues();
        row.put(KEY_STATUS, newStatus); 						//The new status of the message        
  
        //Update the row
        database.update(TABLE_MESSAGES, row, selector, null);
        database.close(); 										//Close the database connection
    }
    
    /**
     * Updates the status of a message in the messages table
     * @param messageId		The unique id of the message to update
     * @param newContents	The new contents of the message
     */
    public void updateMessageContents(String messageId, String newContents){
    	SQLiteDatabase database = this.getWritableDatabase();	//Connect to the database
    	
    	String selector = KEY_ID+"=\""+messageId+"\"";
    	
    	//We will store this message in a row in the database, which is represented by a set of values contained in a ContentValues object
    	ContentValues row = new ContentValues();
        row.put(KEY_CONTENTS, newContents); 					//The new contents of the message        
  
        //Update the row
        database.update(TABLE_MESSAGES, row, selector, null);
        database.close(); 										//Close the database connection
    }
    
    /**
     * Retrieves a single message from the database using its unique id. Useful for retrieving single messages for forwarding to others, etc etc
     * @param messageId		The unique id of the message to retrieve
     * @return				A message object representing the message
     */
    public Message getMessage(String messageId){
    	SQLiteDatabase database = this.getReadableDatabase();	//Connect to the database
    	
    	Message message = null;
    	String[] columnsToRetrieve = {KEY_ID, KEY_TIMESTAMP, KEY_MESSAGE_TYPE, KEY_STATUS, KEY_USER_LIST, KEY_FROM_USER, KEY_CONTENTS};
    	 
        Cursor cursor = database.query(TABLE_MESSAGES, columnsToRetrieve, KEY_ID + "=?", new String[] { messageId },  null, null, null, null);
        if(cursor.moveToFirst()){		//True if we got a result from the database
            message = new Message(cursor.getString(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4), cursor.getString(5), cursor.getString(6));
        }
        database.close(); 										//Close the database connection
        return message;
    }
    
   	/**
   	 * Returns an ArrayList containing all the Message objects that belong to the specified conversation. Messages are sorted with respect to their sent timestamps, with the latest message first in the list
   	 * @param conversation		The id of the conversation
   	 * @param startFrom			The first result to return. If 0 or null then the first result returned is the first overall result
   	 * @param limit				How many messages to return. May be null, in which case all messages are returned
   	 * @return					An ArrayList of Message objects
   	 */
    public ArrayList<Message> getMessagesByConversation(String conversation, Integer startFrom, Integer limit, boolean reverseOrder){
    	ArrayList<Message> messageList = new ArrayList<Message>();
    	SQLiteDatabase database = this.getReadableDatabase();		//Connect to the database

    	String[] columnsToRetrieve = {KEY_ID, KEY_TIMESTAMP, KEY_MESSAGE_TYPE, KEY_STATUS, KEY_USER_LIST, KEY_FROM_USER, KEY_CONTENTS};
    	
    	//If a start point or limit was provided, set it in a string, ready for passing to the query
    	
    	//Handle null values
    	if(startFrom==null){
    		startFrom = 0;
    	}    	
    	if(limit==null){
    		limit = Integer.MAX_VALUE;
    	}

    	String limitText = startFrom+", "+limit;
   	 	Cursor cursor = database.query(TABLE_MESSAGES, columnsToRetrieve, KEY_USER_LIST + "=?", new String[] { conversation },  null, null, KEY_TIMESTAMP+" DESC", limitText);
        if (cursor.moveToFirst()){		//True if there is at least one result to process
        	do {						//Loop through
        		Message message = new Message(cursor.getString(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4), cursor.getString(5), cursor.getString(6));
            	messageList.add(message);
            } while (cursor.moveToNext());
        }
        database.close(); 										//Close the database connection
        
        if(reverseOrder){			//Reverse the list if requested. This is not the same as reversing the sort order in the database query, as this simply reverses the set of results returned from the database
        	Collections.reverse(messageList);
        }
        
        //Return the list of messages, which is empty if none were found
        return messageList;
    }
    
    /**
     * Returns an ArrayList containing all Messages that have not had ACKs recieved 
     * @return		An ArrayList of Message objects
     */
    public ArrayList<Message> getUnAckedMessages(){
    	ArrayList<Message> messageList = new ArrayList<Message>();
    	SQLiteDatabase database = this.getReadableDatabase();		//Connect to the database

    	String[] columnsToRetrieve = {KEY_ID, KEY_TIMESTAMP, KEY_MESSAGE_TYPE, KEY_STATUS, KEY_USER_LIST, KEY_FROM_USER, KEY_CONTENTS};
    	
    	//This array contains a list of message statuses that indicate some sort of failure
    	String[] selection = new String[]{Message.MESSAGE_STATUS_FAILED+"", Message.MESSAGE_STATUS_PENDING+"", Message.MESSAGE_STATUS_ACK_SERVER+""};

    	Cursor cursor = database.query(TABLE_MESSAGES, columnsToRetrieve, KEY_STATUS+"=? OR "+KEY_STATUS+"=? OR "+KEY_STATUS+"=?", selection,  null, null, KEY_TIMESTAMP+" DESC", null);
        if (cursor.moveToFirst()){		//True if there is at least one result to process
        	do {						//Loop through
        		Message message = new Message(cursor.getString(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4), cursor.getString(5), cursor.getString(6));
            	messageList.add(message);
            } while (cursor.moveToNext());
        }
        database.close(); 										//Close the database connection
    	return messageList;
    }
    
    /**
     * Removes a Message from the database
     * @param message						The Message to remove
     * @param alsoDeletePendingMessage		If true, this message will also be removed from the pending messages table, if it is found there
     */
    @SuppressWarnings("unused")
	private void deleteMessage(Message message, boolean alsoDeletePendingMessage) {
    	SQLiteDatabase database = this.getWritableDatabase();		//Connect to the database
    	database.execSQL("DELETE FROM "+TABLE_MESSAGES+" WHERE "+KEY_ID+"=\""+message.getId()+"\"");	//Delete the Message from the table
        database.close();
        //Check if we should remove any conversations this Contact belongs to
    	if(alsoDeletePendingMessage){
    		deletePendingMessage(message);
    	}
	}
    
    /**
     * Removes all messages from the specified Conversation from the messages table
     * @param conversation		The Conversation
     */
    public void deleteMessagesFromConversation(Conversation conversation){
    	SQLiteDatabase database = this.getReadableDatabase();		//Connect to the database
    	database.execSQL("DELETE FROM "+TABLE_MESSAGES+" WHERE "+KEY_USER_LIST+"=\""+conversation.getId()+"\"");	//Delete all items in the table that match the provided conversation id
        database.close();
    }

    /**
     * Adds a Message object to the pendingMessages table, which contains a list of messages that have been delivered to the device but have not been read.
     * This list is used to generate 'inbox style' notifications which can show up to 5 separate lines of text, one per message. 
     * @param pendingMessage		A message object representing the message that has not been read yet
     */
    public void addPendingMessage(Message pendingMessage){
    	SQLiteDatabase database = this.getWritableDatabase();	//Connect to the database
    	
    	//We will store this message in a row in the database, which is represented by a set of values contained in a ContentValues object
    	ContentValues row = new ContentValues();
        row.put(KEY_ID, pendingMessage.getId()); 						//Message ID
        row.put(KEY_TIMESTAMP, pendingMessage.getTimestamp()); 			//Message timestamp
        row.put(KEY_MESSAGE_TYPE, pendingMessage.getType()); 			//Message type
        row.put(KEY_USER_LIST, pendingMessage.getConversationId()); 	//Set of users in the conversation
        row.put(KEY_FROM_USER, pendingMessage.getSenderId()); 			//The user who sent the message
        row.put(KEY_CONTENTS, pendingMessage.getContents(null).toString());			//The actual text
  
        //Insert the row
        database.insert(TABLE_PENDING_MESSAGES, null, row);
        database.close(); 										//Close the database connection
    }

    /**
     * Deletes a single Message from the pending messages table
     * @param message
     */
	private void deletePendingMessage(Message message) {
		SQLiteDatabase database = this.getWritableDatabase();		//Connect to the database
    	database.execSQL("DELETE FROM "+TABLE_PENDING_MESSAGES+" WHERE "+KEY_ID+"=\""+message.getId()+"\"");	//Delete the Message from the table
        database.close();
	}
    
    /**
     * Returns an ArrayList containing all the Message objects are in the pendingMessagesTable
     * @param conversationId	The conversation ID. If set, only pending message from this conversation will be returned. If null, all pending messages are returned
     * @return					An ArrayList of Message objects
     */
    public ArrayList<Message> getPendingMessages(String conversationId){
    	ArrayList<Message> pendingMessageList = new ArrayList<Message>();
    	String[] columnsToRetrieve = {KEY_ID, KEY_TIMESTAMP, KEY_MESSAGE_TYPE, KEY_USER_LIST, KEY_FROM_USER, KEY_CONTENTS};
      	
    	String selector = null;
    	if(conversationId!=null){		//True if a conversation ID was provided
    		selector = KEY_USER_LIST+" = \""+conversationId+"\"";
    	}
    	
    	SQLiteDatabase database = this.getReadableDatabase();	//Connect to the database
    	
    	//Simply get all messages in the table
        Cursor cursor = database.query(TABLE_PENDING_MESSAGES, columnsToRetrieve, selector, null,  null, null, null, null);
        if (cursor.moveToFirst()){		//True if there is at least one result to process
        	do {						//Loop through
        		Message message = new Message(cursor.getString(0), cursor.getInt(1), cursor.getInt(2), Message.MESSAGE_STATUS_NOT_SET, cursor.getString(3), cursor.getString(4), cursor.getString(5));
            	pendingMessageList.add(message);
            } while (cursor.moveToNext());
        }
        database.close(); 										//Close the database connection
        //Return the list of messages, or null if none were found    	
    	return pendingMessageList;
    }
    
    /**
     * Removes all messages from the pendingMessages table
     */
    public void emptyPendingMessages(){
    	SQLiteDatabase database = this.getReadableDatabase();		//Connect to the database
    	database.execSQL("DELETE FROM "+TABLE_PENDING_MESSAGES);	//Delete all items in the table
        database.close();
    }
    
    /**
     * Removes all messages from the specified Conversation from the pending messages table
     * @param conversation		The Conversation
     */
    public void deletePendingMessagesFromConversation(Conversation conversation){
    	SQLiteDatabase database = this.getReadableDatabase();		//Connect to the database
    	database.execSQL("DELETE FROM "+TABLE_PENDING_MESSAGES+" WHERE "+KEY_USER_LIST+"=\""+conversation.getId()+"\"");	//Delete all items in the table that match the provided user id
        database.close();
    }
    
    /**
     * Adds a contact to the database
     * @param contact		A Contact object containing the contact's details
     */
    public void addContact(Contact contact){    	
    	SQLiteDatabase database = this.getWritableDatabase();	//Connect to the database
    	
    	String contactId = contact.getId();			//Cache this here to reduce method calls as we use it multiple times
    	
    	//First check if this contact is already added
    	Contact existingContact = getContact(contactId);
    	database = this.getWritableDatabase();					//Reconnect to the database as we already connected to it when we called getContact() on the previous line
    	if(existingContact==null){		//True if the contact does not already exist    	
	    	//We will store this contact in a row in the database, which is represented by a set of values contained in a ContentValues object
	    	ContentValues row = new ContentValues();
	        row.put(KEY_ID, contactId); 								//Contact ID
	        row.put(KEY_FIRST_NAME, contact.getFirstName()); 			//First name
	        row.put(KEY_LAST_NAME, contact.getLastName()); 				//Last name
	        row.put(KEY_PHONE_NUMBER, contact.getPhoneNumber()); 		//Phone number (optional)
	        row.put(KEY_IMAGE_PATH, contact.getImagePath()); 			//Image path (optional)
	  
	        //Insert the row
	        if(database.insert(TABLE_CONTACTS, null, row)!=-1){
	        	Log.d(TAG, "Contact added, ID: "+contactId);
	        }
	        else{
	        	Log.e(TAG, "Failed to add contact, ID: "+contactId);
	        }
    	}
    	else{							//Contact is already int the database, output a log message
    		Log.d(TAG, "Contact with ID: "+contactId+" already exists");
    	}
        database.close(); 											//Close the database connection
    }
    
    /**
     * Deletes a contact from the database
     * @param userId						The Contact to delete
     * @param alsoDeleteConversations		Whether any conversations involving this Contact should also be removed
     */
    public void deleteContact(Contact contact, boolean alsoDeleteConversations){
    	SQLiteDatabase database = this.getWritableDatabase();		//Connect to the database
    	database.execSQL("DELETE FROM "+TABLE_CONTACTS+" WHERE "+KEY_ID+"=\""+contact.getId()+"\"");	//Delete the contact from the table that matches the provided Contact's ID
        database.close();
        //Check if we should remove any conversations this Contact belongs to
    	if(alsoDeleteConversations){
    		ArrayList<Conversation> relevantConversations = getAllConversations(contact);
    		Iterator<Conversation> iterator = relevantConversations.iterator();
    		Conversation conversation = null;
    		while(iterator.hasNext()){
    			conversation = iterator.next();
    			deleteConversation(conversation, true);		//Delete this conversation and any messages associated with it
    		}
    	}
    }
    
    /**
     * Retrieves a single contact from the database
     * @param userId		The ID of the contact to retrieve details for
     * @return				A Contact object containing all the contact's information, or null if no contact was found with the specified ID
     */
    public Contact getContact(String userId){
    	Contact contact = null;    	
    	String[] columnsToRetrieve = {KEY_ID, KEY_FIRST_NAME, KEY_LAST_NAME, KEY_PHONE_NUMBER, KEY_IMAGE_PATH};
    	SQLiteDatabase database = this.getReadableDatabase();	//Connect to the database
 
        Cursor cursor = database.query(TABLE_CONTACTS, columnsToRetrieve, KEY_ID + "=?", new String[] { userId },  null, null, null, null);
        if (cursor.moveToFirst()){		//True if we got a result from the database
            contact = new Contact(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4));
        }
        database.close(); 										//Close the database connection
    	return contact;
    }
    
    /**
     * Retrieves all available Contacts from the database
     * @param includeLocalUser   Whether or not the details of the local user should be included in the list of Contacts returned
     * @return		An ArrayList containing a Contact object for each contact found in the database, or null if no contacts were found
     */
    public ArrayList<Contact> getAllContacts(boolean includeLocalUser){
    	ArrayList<Contact> contactsList = new ArrayList<Contact>();
    	String[] columnsToRetrieve = {KEY_ID, KEY_FIRST_NAME, KEY_LAST_NAME, KEY_PHONE_NUMBER, KEY_IMAGE_PATH};
      	
    	SQLiteDatabase database = this.getReadableDatabase();	//Connect to the database
    	
    	SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);    	
    	String localUserId = preferences.getString("userId", null);
    	
    	if(localUserId==null){		//Can't continue of the user ID is not set
    		return contactsList;
    	}
    	
    	//Simply get all messages in the table
        Cursor cursor = database.query(TABLE_CONTACTS, columnsToRetrieve, null, null,  null, null, KEY_LAST_NAME+" ASC", null);
        if (cursor.moveToFirst()){		//True if there is at least one result to process
        	do {						//Loop through
        		if(!cursor.getString(0).equals(localUserId) || includeLocalUser){		//Add the contact if it is not the local user's ID, OR if the user's ID is to be included in the contacts list returned
	                Contact contact = new Contact(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4));           
	            	contactsList.add(contact);
        		}
            } while (cursor.moveToNext());
        }
        database.close(); 										//Close the database connection
        //Return the list of contacts, or null if none were found    	
    	return contactsList;
    }
    
    /**
     * Adds a Conversation object to the conversation table
     * @param conversation		A Conversation object containing the ID and image path of the conversation
     */
    public void addConversation(Conversation conversation){
    	SQLiteDatabase database = this.getWritableDatabase();	//Connect to the database
    	
    	String conversationId = conversation.getId();			//Cache this here to reduce method calls as we use it multiple times
    	
    	//First check if this contact is already added
    	Conversation existingConversation = getConversation(conversationId);
    	database = this.getWritableDatabase();					//Reconnect to the database as we already connected to it when we called getConversation() on the previous line
    	if(existingConversation==null){		//True if the contact does not already exist    	
	    	//We will store this conversation in a row in the database, which is represented by a set of values contained in a ContentValues object
	    	ContentValues row = new ContentValues();
	        row.put(KEY_ID, conversation.getId()); 							//Conversation ID        
	        row.put(KEY_IMAGE_PATH, conversation.getImagePath()); 			//Image path (optional)
	  
	        //Insert the row
	        if(database.insert(TABLE_CONVERSATIONS, null, row)!=-1){
	        	Log.d(TAG, "Conversation added, ID: "+conversationId);
	        	//Send a broadcast to indicate a conversation was added
	        	Intent newConversationIntent = new Intent(MainActivity.intentSignatureConversationsModified);
	        	context.sendBroadcast(newConversationIntent);
	        }
    	}
    	else{								//Conversation is already int the database, output a log message
    		Log.d(TAG, "Conversation with ID: "+conversationId+" already exists");
    	}
        database.close(); 										//Close the database connection
    }
    
    /**
     * Deletes a conversation from the database
     * @param conversation			The Conversation to delete
     * @param alsoDeleteMessages	Whether any Messages belonging to this Conversation should also be removed
     */
    public void deleteConversation(Conversation conversation, boolean alsoDeleteMessages){
    	SQLiteDatabase database = this.getWritableDatabase();		//Connect to the database
    	database.execSQL("DELETE FROM "+TABLE_CONVERSATIONS+" WHERE "+KEY_ID+"=\""+conversation.getId()+"\"");	//Delete the conversation from the table that matches the provided conversation ID
        database.close();
        //Check if we should remove any conversations this Contact belongs to
    	if(alsoDeleteMessages){
    		deleteMessagesFromConversation(conversation);
    		deletePendingMessagesFromConversation(conversation);
    	}
    }
    
    /**
     * Retrieves a single conversation from the database
     * @param conversationId		The ID of the conversation to retrieve details for
     * @return						A Conversation object containing all the conversation's information, or null if no conversation was found with the specified ID
     */
    public Conversation getConversation(String conversationId){
    	//First, sort the conversation ID alphabetically
    	String sortedConversationId = Conversation.sortConversationId(conversationId);
    	Conversation conversation = null;
    	String[] columnsToRetrieve = {KEY_ID, KEY_IMAGE_PATH};
    	SQLiteDatabase database = this.getReadableDatabase();	//Connect to the database
    	Cursor cursor = database.query(TABLE_CONVERSATIONS, columnsToRetrieve, KEY_ID + "=?", new String[] { sortedConversationId },  null, null, null, null);
        if (cursor.moveToFirst()){		//True if we got a result from the database
            conversation = new Conversation(cursor.getString(0), cursor.getString(1));           
        }
        database.close(); 										//Close the database connection
    	return conversation;
    }

    /**
     * Retrieves all available Conversations from the database
     * @param containingContact		If not null, only conversations involving this Contact will be returned
     * @return						An ArrayList containing a Contact object for each contact found in the database, or null if no contacts were found
     */
    public ArrayList<Conversation> getAllConversations(Contact containingContact){
    	ArrayList<Conversation> conversationsList = new ArrayList<Conversation>();
    	String[] columnsToRetrieve = {KEY_ID, KEY_IMAGE_PATH};
      	
    	SQLiteDatabase database = this.getReadableDatabase();	//Connect to the database
    	
    	ArrayList<String> userIds;

    	//Simply get all messages in the table
        Cursor cursor = database.query(TABLE_CONVERSATIONS, columnsToRetrieve, null, null,  null, null, null, null);
        if (cursor.moveToFirst()){		//True if there is at least one result to process
        	do {						//Loop through
        		Conversation conversation = new Conversation(cursor.getString(0), cursor.getString(1));
        		if(containingContact==null){		//No Contact provided, so just add the conversation to the return list
        			conversationsList.add(conversation);
        		}
        		else{
        			userIds = conversation.getUserList();
        			if(userIds.contains(containingContact.getId())){		//Add to the list if the conversation contains this user
        				conversationsList.add(conversation);
        			}
        		}
            } while (cursor.moveToNext());
        }
        database.close(); 										//Close the database connection
        //Return the list of contacts, or null if none were found    	
    	return conversationsList;
    }
    
    /**
     * Adds a resource to the database
     * @param resource		A Resource object containing the resource's details
     */
    public void addResource(Resource resource){    	
    	SQLiteDatabase database = this.getWritableDatabase();	//Connect to the database
    	
    	String resourceId = resource.getId();			//Cache this here to reduce method calls as we use it multiple times
    	
    	//First check if this contact is already added
    	Resource existingResource = getResource(resourceId);
    	database = this.getWritableDatabase();					//Reconnect to the database as we already connected to it when we called getResource() on the previous line
    	if(existingResource==null){		//True if the resource does not already exist    	
	    	//We will store this resource in a row in the database, which is represented by a set of values contained in a ContentValues object
	    	ContentValues row = new ContentValues();
	        row.put(KEY_ID, resourceId); 								//Resource ID
	        row.put(KEY_TYPE, resource.getType()); 						//Type
	        row.put(KEY_TIMESTAMP, resource.getTimestamp()); 			//Timestamp
	        row.put(KEY_CONVERSATION_ID, resource.getConversationId()); //Conversation ID
	        row.put(KEY_FROM_USER, resource.getFromUser()); 			//Sender ID
	        row.put(KEY_PATH, resource.getPath()); 						//Path
	        row.put(KEY_TEXT, resource.getText()); 						//Text
	  
	        //Insert the row
	        if(database.insert(TABLE_RESOURCES, null, row)!=-1){
	        	Log.d(TAG, "Resource added, ID: "+resourceId);
	        }
	        else{
	        	Log.e(TAG, "Failed to add Resource, ID: "+resourceId);
	        }
    	}
    	else{							//Resource is already int the database, output a log message
    		Log.d(TAG, "Resource with ID: "+resourceId+" already exists");
    	}
        database.close(); 											//Close the database connection
    }
    
    /**
     * Deletes a resource from the database
     * @param resourceId	The ID of the resource to delete
     */
    public void deleteResource(String resourceId){
    	SQLiteDatabase database = this.getReadableDatabase();		//Connect to the database
    	database.execSQL("DELETE FROM "+TABLE_RESOURCES+" WHERE "+KEY_ID+"=\""+resourceId+"\"");	//Delete the resources from the table that matches the provided id
        database.close();
    }
    
    /**
     * Retrieves a single resource from the database
     * @param resourceId	The ID of the resource to retrieve details for
     * @return				A Resource object containing all the resource's information, or null if no resource was found with the specified ID
     */
    public Resource getResource(String resourceId){
    	Resource resource = null;    	
    	String[] columnsToRetrieve = {KEY_ID, KEY_TYPE, KEY_TIMESTAMP, KEY_CONVERSATION_ID, KEY_FROM_USER, KEY_PATH, KEY_TEXT};
    	SQLiteDatabase database = this.getReadableDatabase();	//Connect to the database
 
        Cursor cursor = database.query(TABLE_RESOURCES, columnsToRetrieve, KEY_ID + "=?", new String[] { resourceId },  null, null, null, null);
        if (cursor.moveToFirst()){								//True if we got a result from the database
        	resource = new Resource(cursor.getString(0), cursor.getInt(1), cursor.getInt(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6));
        }
        database.close(); 										//Close the database connection
    	return resource;
    }
    
    /**
   	 * Returns an ArrayList containing all the Resource objects that belong to the specified conversation. Resources are sorted with respect to their timestamps, with the latest resource first in the list
   	 * @param conversationId	The id of the conversation
   	 * @param startFrom			The first result to return. If 0 or null then the first result returned is the first overall result
   	 * @param limit				How many resources to return. May be null, in which case all messages are returned
   	 * @return					An ArrayList of Resource objects
   	 */
    public ArrayList<Resource> getResourcesByConversation(String conversationId, Integer startFrom, Integer limit){
    	SQLiteDatabase database = this.getReadableDatabase();		//Connect to the database
    	
    	ArrayList<Resource> resourceList = new ArrayList<Resource>();
    	
    	String[] columnsToRetrieve = {KEY_ID, KEY_TYPE, KEY_TIMESTAMP, KEY_CONVERSATION_ID, KEY_FROM_USER, KEY_PATH, KEY_TEXT};
    	
    	//If a start point or limit was provided, set it in a string, ready for passing to the query
    	
    	//Handle null values
    	if(startFrom==null){
    		startFrom = 0;
    	}    	
    	if(limit==null){
    		limit = Integer.MAX_VALUE;
    	}
    	
    	String limitText = startFrom+", "+limit;
   	 	Cursor cursor = database.query(TABLE_RESOURCES, columnsToRetrieve, KEY_CONVERSATION_ID + "=?", new String[] { conversationId },  null, null, KEY_TIMESTAMP+" ASC", limitText);
        if (cursor.moveToFirst()){		//True if there is at least one result to process
        	do {						//Loop through
        		Resource resource = new Resource(cursor.getString(0), cursor.getInt(1), cursor.getInt(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6));
        		resourceList.add(resource);
            } while (cursor.moveToNext());
        }
        database.close(); 										//Close the database connection
        //Return the list of resources, or null if none were found
        return resourceList;
    }

    /**
     * Adds a game to the database
     * @param game		A Game object containing the game's details
     */
    public void addGame(Game game){    	
    	SQLiteDatabase database = this.getWritableDatabase();	//Connect to the database
    	
    	String gameId = game.getId();			//Cache this here to reduce method calls as we use it multiple times
    	
    	//First check if this game is already added
    	Game existingGame = getGame(gameId);
    	database = this.getWritableDatabase();					//Reconnect to the database as we already connected to it when we called getGame() on the previous line
    	if(existingGame==null){		//True if the contact does not already exist    	
	    	//We will store this contact in a row in the database, which is represented by a set of values contained in a ContentValues object
	    	ContentValues row = new ContentValues();
	        row.put(KEY_ID, gameId); 							//Game ID
	        row.put(KEY_GENRE, game.getGenre());				//Genre
	        row.put(KEY_SHORT_NAME, game.getShortName()); 		//Short name
	        row.put(KEY_LONG_NAME, game.getLongName()); 		//Long name
	        row.put(KEY_DESCRIPTION, game.getDescription()); 	//Description
	        row.put(KEY_RATING, game.getRating()); 				//Rating
	        row.put(KEY_RELEASE_DATE, game.getReleaseDate()); 	//Release date
	        row.put(KEY_IMAGE_PATH, game.getImagePath()); 		//Image path (optional)
	  
	        //Insert the row
	        if(database.insert(TABLE_GAMES, null, row)!=-1){
	        	Log.d(TAG, "Game added, ID: "+gameId);
	        }
	        else{
	        	Log.e(TAG, "Failed to add game, ID: "+gameId);
	        }
    	}
    	else{							//Game is already int the database, output a log message
    		Log.d(TAG, "Game with ID: "+gameId+" already exists");
    	}
        database.close(); 											//Close the database connection
    }
    
    /**
     * Deletes a game from the database
     * @param gameId	The ID of the game to delete
     */
    public void deleteGame(Game game){
    	SQLiteDatabase database = this.getReadableDatabase();		//Connect to the database
    	database.execSQL("DELETE FROM "+TABLE_GAMES+" WHERE "+KEY_ID+"=\""+game.getId()+"\"");	//Delete the game from the table that matches the provided game id
        database.close();
    }
    
    /**
     * Retrieves a single game from the database
     * @param gameId		The ID of the game to retrieve details for
     * @return				A Game object containing all the game's information, or null if no game was found with the specified ID
     */
    public Game getGame(String gameId){
    	Game game = null;    	
    	String[] columnsToRetrieve = {KEY_ID, KEY_GENRE, KEY_SHORT_NAME, KEY_LONG_NAME, KEY_IMAGE_PATH, KEY_RATING, KEY_RELEASE_DATE, KEY_IMAGE_PATH};
    	SQLiteDatabase database = this.getReadableDatabase();	//Connect to the database
 
        Cursor cursor = database.query(TABLE_GAMES, columnsToRetrieve, KEY_ID + "=?", new String[] { gameId },  null, null, null, null);
        if (cursor.moveToFirst()){		//True if we got a result from the database
            game = new Game(cursor.getString(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5), cursor.getInt(6), cursor.getString(7));
        }
        database.close(); 										//Close the database connection
    	return game;
    }
    
    /**
     * Retrieves all available Games from the database
     * @return		An ArrayList containing a Game object for each game found in the database, or null if no games were found
     */
    public ArrayList<Game> getAllGames(Integer startFrom, Integer limit){
    	ArrayList<Game> gamesList = new ArrayList<Game>();
    	String[] columnsToRetrieve = {KEY_ID, KEY_GENRE, KEY_SHORT_NAME, KEY_LONG_NAME, KEY_IMAGE_PATH, KEY_RATING, KEY_RELEASE_DATE, KEY_IMAGE_PATH};
    	
    	//If a start point or limit was provided, set it in a string, ready for passing to the query
    	
    	//Handle null values
    	if(startFrom==null){
    		startFrom = 0;
    	}    	
    	if(limit==null){
    		limit = Integer.MAX_VALUE;
    	}
    	
    	String limitText = startFrom+", "+limit;
      	
    	SQLiteDatabase database = this.getReadableDatabase();	//Connect to the database
    	
    	//Simply get all messages in the table
        Cursor cursor = database.query(TABLE_GAMES, columnsToRetrieve, null, null,  null, null, null, limitText);
        if (cursor.moveToFirst()){		//True if there is at least one result to process
        	do {						//Loop through        		
	            Game game = new Game(cursor.getString(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5), cursor.getInt(6), cursor.getString(7));
	            gamesList.add(game);
        		
            } while (cursor.moveToNext());
        }
        database.close(); 										//Close the database connection
        //Return the list of games, or null if none were found    	
    	return gamesList;
    }
    
    /**
   	 * Returns an ArrayList containing all Games belonging to the specified genre. Games are sorted by release date, with the latest first in the list
   	 * @param genre				The genre of games to return
   	 * @param startFrom			The first result to return. If 0 or null then the first result returned is the first overall result
   	 * @param limit				How many games to return. May be null, in which case all games starting from the start point are returned
   	 * @return					An ArrayList of Game objects
   	 */
    public ArrayList<Game> getGamesByGenre(Integer genre, Integer startFrom, Integer limit){
    	SQLiteDatabase database = this.getReadableDatabase();		//Connect to the database
    	
    	ArrayList<Game> gamesList = new ArrayList<Game>();
    	
    	String[] columnsToRetrieve = {KEY_ID, KEY_GENRE, KEY_SHORT_NAME, KEY_LONG_NAME, KEY_IMAGE_PATH, KEY_RATING, KEY_RELEASE_DATE, KEY_IMAGE_PATH};
    	
    	//If a start point or limit was provided, set it in a string, ready for passing to the query
    	
    	//Handle null values
    	if(startFrom==null){
    		startFrom = 0;
    	}    	
    	if(limit==null){
    		limit = Integer.MAX_VALUE;
    	}
    	
    	String limitText = startFrom+", "+limit;
   	 	Cursor cursor = database.query(TABLE_GAMES, columnsToRetrieve, KEY_GENRE + "=?", new String[] { genre+"" },  null, null, KEY_RELEASE_DATE+" ASC", limitText);
        if (cursor.moveToFirst()){		//True if there is at least one result to process
        	do {						//Loop through
        		Game game = new Game(cursor.getString(0), cursor.getInt(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5), cursor.getInt(6), cursor.getString(7));
        		gamesList.add(game);
            } while (cursor.moveToNext());
        }
        database.close(); 										//Close the database connection
        //Return the list of games, or an empty list if none were found
        return gamesList;
    }
}