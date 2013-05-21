package com.lbros.epicchat;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Class that handles the syncing of the user's contact details . This is done by checking the list of contacts on the local device for Google
 * email addresses. The set of matching addresses found is then sent to the server, which will return details for each matching contact that it 
 * found in the database
 * @author Tom
 *
 */
public class SyncContactsTask extends AsyncTask<Void, Void, Boolean>{
	private final String TAG = "SyncContactsTask";

	//Codes used with the broadcast intent
	/**
	 * Signals that a full contacts sync was just started
	 */
	public static final int STATUS_SYNC_STARTED = 1;
	/**
	 * Signals that a full contacts sync was just completed
	 */
	public static final int STATUS_SYNC_NEW_CONTACT = 2;
	/**
	 * Signals that a full contacts sync was just completed
	 */
	public static final int STATUS_SYNC_COMPLETE = 3;
	
	private int nContactsToDownload;
	private int nContactsDownloaded = 0;
	
	private Context context;
	
	private Database database;
	
	private TelephonyManager telephonyManager;
	private String countryCode;
	
	private SharedPreferences preferences;
	
	final ArrayList<Contact> newContactsList = new ArrayList<Contact>();
	
	//This handler will listen for when each user's image has been downloaded
	final Handler contactAddedHandler = new Handler(){
		public void handleMessage(Message message){
			//Switch based on the code of the message
			switch(message.what){
			case DownloadFileTask.EVENT_DOWNLOAD_STARTED:			//Received when the download of the image is about to start
				
    			break;
			case DownloadFileTask.EVENT_DOWNLOAD_SUCCEEDED:			//Received when the download of the user image succeeded
				Log.d(TAG, "Image downloaded");
				int index = message.getData().getInt("uniqueId", -1);
				if(index!=-1){
					Contact contact = newContactsList.get(index);
					database.addContact(contact);
					sendSyncStatusBroadcast(STATUS_SYNC_NEW_CONTACT);
					nContactsDownloaded++;
					if(nContactsDownloaded>=nContactsToDownload){		//True if all contacts have been downloaded
						sendSyncStatusBroadcast(STATUS_SYNC_COMPLETE);	//Notify the system that the sync is complete
					}
				}
				break;
			case DownloadFileTask.EVENT_DOWNLOAD_FAILED:			//Received when the download of the user image failed
				//We should still increment the downloaded counter, or else the SYNC_COMPLETE broadcast will not be sent
				nContactsDownloaded++;
				if(nContactsDownloaded>=nContactsToDownload){		//True if all contacts have been downloaded
					sendSyncStatusBroadcast(STATUS_SYNC_COMPLETE);	//Notify the system that the sync is complete
				}
				break;
			default:
				break;
			}
		}
	};
	
	/**
	 * Constructor. Takes a Context from the calling activity or service as input, as this is needed for some function calls
	 * @param newContext	The Context of the calling activity or service
	 */
	public SyncContactsTask(Context newContext){
		context = newContext;
		preferences = PreferenceManager.getDefaultSharedPreferences(context);
		telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		countryCode = telephonyManager.getNetworkCountryIso();
	}
	
	/**
	 * Called on the main thread immediately before doInBackground()
	 */
	@Override
	protected void onPreExecute(){
		database = new Database(context);
		sendSyncStatusBroadcast(STATUS_SYNC_STARTED);
	}
	
	/**
	 * Called on the background thread to run the entire process, from scanning the contacts list to adding new Contact objects
	 */
	@Override
	protected Boolean doInBackground(Void... arg0) {
		//Looper.prepare();	//This allows us to create handlers on the background thread
		
		//The first thing to do is to get the list of Google email addresses from the device's contacts
		ArrayList<String> emailAddresses = getMissingContactEmailAddresses();
		
		if(emailAddresses.size()==0){		//True if no valid email addresses were found
			return false;
		}
		
        //The next step is to send these addresses to the server
		String response = sendEmailAddressesToServer(emailAddresses);
		
		if(response==null){					//True if there was a server error or no contacts were returned by the server. In either case, we need to exit
			return false;
		}
		
		//Finally, process the response from the server		
		if(processContactList(response)){
			return true;
		}
		else{
			return false;
		}
	}
	
	/**
	 * Called on the main thread immediately after doInBackground() has finished executing
	 */
	@Override
	protected void onPostExecute(Boolean success){
		if(!success){			//If there were no new contacts to fetch information for, send the sync complete event
			sendSyncStatusBroadcast(STATUS_SYNC_COMPLETE);
		}
	}
	

	private String convertPhoneNumberToInternationalFormat(String phoneNumber){
		return null;
	}
	
	/**
	 * Retrieves the set of Google email addresses in the device's contacts that do not have matching contacts in the database
	 * @return			An ArrayList containing the email addresses. Will be empty, not null, if there were no email addresses to return
	 */
	private ArrayList<String> getMissingContactEmailAddresses(){
		ArrayList<String> emailAddresses = new ArrayList<String>();
		long start = System.currentTimeMillis();
        String[] projection = new String[] {
            ContactsContract.CommonDataKinds.Email.DATA
        };

		TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		Log.d(TAG, "CODE "+manager.getNetworkCountryIso());
        //Query the system for a list of contacts, returning only their email address
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, projection, null, null, null);
        int n = 0;
        if (cursor != null) {
            try {
            	//Work out which column the email address lies in
                final int emailIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);
                //final int phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                Log.d(TAG, "INDEX: "+emailIndex);
                //Log.d(TAG, "INDEX: "+phoneIndex);
                String emailAddress, phoneNumber;
                while (cursor.moveToNext()) {
                	//Retrieve the email address
                	emailAddress = cursor.getString(emailIndex);
                	//phoneNumber = cursor.getString(phoneIndex);
                	//Log.d(TAG, "Phone: "+phoneNumber);
                	//boolean international = PhoneNumberUtils.isGlobalPhoneNumber(phoneNumber);
                	//if(international){
                	//	Log.d(TAG, "INTERNATIONAL");
                	//}
                	//Check if the email address is a Google one
                	if(emailAddress!=null && (emailAddress.endsWith("gmail.com") || emailAddress.endsWith("googlemail.com"))){				//Found a gmail or googlemail address
                		if(!contactExistsInDatabase(emailAddress)){
                			emailAddresses.add(emailAddress);
                		}
                	}
                	n++;
                }
            } finally {
                cursor.close();
            }
        }
        long end = System.currentTimeMillis();
        Log.d(TAG, n+" contacts scanned in "+(end - start)+"ms");
        return emailAddresses;
	}

	/**
	 * Checks whether the provided email address is already present in the contacts database
	 * @param emailAddress		The email address to check
	 * @return					Boolean indicating whether the email address was found in the database
	 */
	private boolean contactExistsInDatabase(String emailAddress) {
		Contact tempContact = database.getContact(emailAddress.toLowerCase());
		if(tempContact==null){			//True if the contact was not found in the database
			return false;
		}
		else{
			return true;
		}
	}
	
	/**
	 * Sends an HTTP request to the server that contains a list of email addresses. The server will respond with a JSON array containing the details of any matching contacts it found
	 * @param emailAddresses		An ArrayList contianing the email addresses to be sent
	 * @return						A JSON-encoded String containing the information of any users found, or null if none were found
	 */
	private String sendEmailAddressesToServer(ArrayList<String> emailAddresses) {
		String response = null;
		int nAddresses = emailAddresses.size();
		if(nAddresses>0){		//True if there is at least one new email address to send to the server
			//Build the comma separated list of email addresses
			StringBuilder builder = new StringBuilder();
			builder.append(emailAddresses.get(0));
			for(int i=1; i<nAddresses; i++){
				builder.append(","+emailAddresses.get(i));
			}
			String addressesString = builder.toString();
			//Send the data to the server using an HTTP POST request
			String serverAddress = preferences.getString("serverAddress", null);
			String request = serverAddress+"checkUserIds.php";
			response = HTTP.doHttpPost(request, "idList="+addressesString, 5);
		}
		return response;
	}

	/**
	 * Enters new contacts into the database
	 * @param serverResponse		A JSON-encoded String containing the information of the contacts to be added
	 * @return						Boolean, true if one or more contacts were added to the database, false otherwise
	 */
	private boolean processContactList(String serverResponse){
		int nContacts = 0;
		try{
			JSONArray contactsJSON = new JSONArray(serverResponse);
			nContacts = contactsJSON.length();
			nContactsToDownload = nContacts;
			
			JSONObject contactJSON;
			String contactId;
			String contactFirstName;
			String contactLastName;
			String contactPhoneNumber;
			
			Contact newContact;
			
			DownloadFileTask downloadContactImage;
			String contactImagePath;
			
			for(int i=0; i<nContacts; i++){
				//Get the contact's details from the response
				contactJSON = contactsJSON.getJSONObject(i);
				contactId = contactJSON.getString("id");
				contactFirstName = contactJSON.getString("firstName");
				contactLastName = contactJSON.getString("lastName");
				contactPhoneNumber = contactJSON.getString("devicePhoneNumber");
				
				//Download the image for the contact from the server
				contactImagePath = MainActivity.DIRECTORY_USER_IMAGES+contactId+".jpg";
				String serverAddress = preferences.getString("serverAddress", null);
				downloadContactImage = new DownloadFileTask(serverAddress+"getUserImage.php?userId="+contactId+"&size=500", contactImagePath);
				downloadContactImage.setHandler(contactAddedHandler);
				downloadContactImage.setUniqueId(i);		//Add the loop index to this task, this helps us keep track of which contact's image is being downloaded
				downloadContactImage.execute();				//Execute the task asynchronously. If there is a previous copy of this task running (i.e. from a previous iteration of this loop) then execution will wait for it to finish
				
				newContact = new Contact(contactId, contactFirstName, contactLastName, contactPhoneNumber, contactImagePath);
				newContactsList.add(newContact);		//Add this contact to a list, where it will be read from later, once the image has downloaded
			}
		}
		catch(JSONException e){
			Log.e(TAG, "Error processing server response: "+e.toString());
			return false;
		}
		if(nContacts>0){
			return true;
		}
		else{
			return false;
		}
	}
	
	/**
	 * Sends a broadcast intent with the attached status code
	 * @param statusSyncStarted		The status code associated with the broadcast. Must be one of the STATUS fields defined in this class
	 */
	private void sendSyncStatusBroadcast(int syncStatus) {
		//Send a broadcast to indicate a contact sync event has occured
		Intent newMessageIntent = new Intent(MainActivity.intentSignatureContactSyncComplete);
		newMessageIntent.putExtra("syncStatus", syncStatus);
		context.sendBroadcast(newMessageIntent, null);
	}
}