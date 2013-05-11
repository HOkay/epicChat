package com.lbros.epicchat;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.android.gcm.GCMRegistrar;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewFlipper;

/**
 * Handles the setup of a user account. The process is as follows:
 * 1:	Check for any accounts on the device that are Gmail accounts
 * 2:	If there is only one Gmail account, use this as the account ID
 * 3:	If there is more than one, offer the user a choice
 * 4:	Check on the server if an account with this ID exists
 * 5:	If it does, download the account details (first & last name and profile image) and show them to the user
 * 6:	If not, provide an interface which the user can use to enter these details and choose a picture
 * @author Tom
 *
 */
public class EnterAccountDetailsActivity extends Activity {
	private final String TAG = "EnterAccountDetailsActivity";
	
	//Activity signatures
	private final int ACTION_SELECT_IMAGE_FROM_GALLERY = 1;

	private SharedPreferences preferences;
	private String serverAddress;
	
	private String userId;
	
	private boolean newAccount = false;
	
	//Stats to keep track of where we are in the process
	private final int STATE_INITIAL = -1;
	private final int STATE_START = 0;
	private final int STATE_CHOOSE_ACCOUNT = 1;
	private final int STATE_ENTER_DETAILS = 2;
	private final int STATE_CHOOSE_PROFILE_IMAGE = 3;
	private final int STATE_COMPLETE = 4;
	private int paneState = STATE_INITIAL;
	
	//UI stuff
	private TextView topText;
	private ViewFlipper viewFlipper;
	private Button buttonNext;
	
	//Account choice pane
	private int selectedAccountIndex = -1;
	private ListView accountsList;
	private ArrayList<Account> googleAccounts;
	private ArrayAdapter<String> accountsListAdapter;
	
	//Details pane
	private EditText editTextFirstName, editTextLastName;
	private String firstName = null;
	private String lastName = null;
	
	//Profile image pane
	private ImageView imageViewProfileImage;
	private Handler downloadImageHandler, uploadImageHandler;
	private String localImagePath = null;
	private String userImagePath = null;
	
	//Lower section
	private ProgressBar progressBarProgress;
	private TextView textViewProgressText;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setResult(Activity.RESULT_CANCELED);		//Set this in case the user backs out of this activity
		
		preferences = PreferenceManager.getDefaultSharedPreferences(this);	//Load the preferences

		serverAddress = preferences.getString("serverAddress", null);
		//Set up all the UI elements
		setContentView(R.layout.activity_enter_account_details);
		
		topText = (TextView) findViewById(R.id.activity_enter_account_details_title);
		
		viewFlipper = (ViewFlipper) findViewById(R.id.activity_enter_account_details_viewflipper);
		
		buttonNext = (Button) findViewById(R.id.activity_enter_account_details_button_next);
		buttonNext.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				//The behaviour of this button varies depending on the state we are currently in
				switch(paneState){
				case STATE_INITIAL:
					goToState(STATE_START);
					break;
				case STATE_START:
					requestUserId();
					break;
				case STATE_CHOOSE_ACCOUNT:
					if(selectedAccountIndex!=-1){
						userId = accountsListAdapter.getItem(selectedAccountIndex);
						checkForUserDetailsOnServer();
					}
					break;
				case STATE_ENTER_DETAILS:
					createUserAccountOnServer();
					break;
				case STATE_CHOOSE_PROFILE_IMAGE:
					if(localImagePath!=null){				//True if the user selected an image from the device's gallery to upload to the server
				    	uploadProfileImage();
				    }
					else{
						goToState(STATE_COMPLETE);
					}
					break;
				case STATE_COMPLETE:
					setResult(Activity.RESULT_OK);
					finish();
					break;
				default:
					break;
				}
			}
		});

		//Choose account pane
		accountsList = (ListView) findViewById(R.id.activity_enter_account_details_viewflipper_choose_account_list);
		accountsList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int index, long arg3) {
				selectedAccountIndex = index;
			}
		});
		
		//Details pane
		editTextFirstName = (EditText) findViewById(R.id.activity_enter_account_details_viewflipper_name_details_first_name);
		editTextLastName = (EditText) findViewById(R.id.activity_enter_account_details_viewflipper_name_details_last_name);
		
		//Profile image pane
		imageViewProfileImage = (ImageView) findViewById(R.id.activity_enter_account_details_viewflipper_profile_image_image);
		imageViewProfileImage.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {		//Clicking on the photo launches the gallery to pick an image
				Intent imagePickerIntent = new Intent(Intent.ACTION_PICK);
				imagePickerIntent.setType("image/*");
				imagePickerIntent.putExtra("crop", "true");
				imagePickerIntent.putExtra("scale", true);
				imagePickerIntent.putExtra("outputX", 300);
				imagePickerIntent.putExtra("outputY", 300);
				imagePickerIntent.putExtra("aspectX", 1);
				imagePickerIntent.putExtra("aspectY", 1);
				imagePickerIntent.putExtra("return-data", true);
				//imagePickerIntent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.toString());
				startActivityForResult(imagePickerIntent, ACTION_SELECT_IMAGE_FROM_GALLERY); 
			}
		});

		//Lower section
		progressBarProgress = (ProgressBar) findViewById(R.id.activity_enter_account_details_progress_indicator);
		textViewProgressText = (TextView) findViewById(R.id.activity_enter_account_details_progress_text);

		//Start the process
		goToState(STATE_INITIAL);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
		super.onActivityResult(requestCode, resultCode, imageReturnedIntent); 

	    switch(requestCode) { 
	    case ACTION_SELECT_IMAGE_FROM_GALLERY:
	        if(resultCode == RESULT_OK){
	            //Uri selectedImage = imageReturnedIntent.getData();
				try {
					Bundle extras = imageReturnedIntent.getExtras();  
					if(extras != null ) {  
					    Bitmap croppedImage = extras.getParcelable("data");  
					    ByteArrayOutputStream stream = new ByteArrayOutputStream();  
					    croppedImage.compress(Bitmap.CompressFormat.JPEG, 80, stream);
					    imageViewProfileImage.setImageBitmap(croppedImage);
					    userImagePath = MainActivity.DIRECTORY_USER_IMAGES+userId+".jpg";
					    localImagePath = userImagePath;
					    FileOutputStream fo = new FileOutputStream(userImagePath);
					    fo.write(stream.toByteArray());
					    fo.close();
					}
				}
				catch (FileNotFoundException e) {
					Log.e(TAG, "Error opening file: "+e.toString());
				}
				catch (IllegalArgumentException e) {
					Log.e(TAG, "Error opening file: "+e.toString());
				}
				catch (IOException e) {
					Log.e(TAG, "Error opening file: "+e.toString());
				}
	        }
	    }
	}
	
	private void goToState(int stateToAdvanceTo){
		paneState = stateToAdvanceTo;		//Advance to the chosen state
		switch(paneState){
		case STATE_INITIAL:
			showInitialPane();
			break;
		case STATE_START:
			requestUserId();
			break;
		case STATE_CHOOSE_ACCOUNT:
			showAccountChoicePane();
			break;
		case STATE_ENTER_DETAILS:
			showDetailsPane();
			break;
		case STATE_CHOOSE_PROFILE_IMAGE:
			showProfileImagePane();
			break;
		case STATE_COMPLETE:
			showProcessCompletePane();
			break;
		default:
			break;
		}
	}
	
	//Requests the device owner's ID from Google
    private void requestUserId(){
    	String accountName = null;
    	Pattern emailPattern = Patterns.EMAIL_ADDRESS; // API level 8+
    	Account[] accounts = AccountManager.get(this).getAccountsByType("com.google");
    	googleAccounts = new ArrayList<Account>();
    	for (Account account : accounts) {
    		accountName = account.name;
    	    if(emailPattern.matcher(accountName).matches()) {
		    	googleAccounts.add(account);
		    	Log.d(TAG, "Found google account: "+account);
    	    }
    	}
    	int nGoogleAccounts = googleAccounts.size();
    	if(nGoogleAccounts==0){
    		//TODO This should show an error message
    	}
    	else if(nGoogleAccounts==1){
    		userId = googleAccounts.get(0).name;
    		checkForUserDetailsOnServer();
    	}
    	else{
    		goToState(STATE_CHOOSE_ACCOUNT);
    	}
    }

    //Checks if the user is already registered on the server
    private void checkForUserDetailsOnServer(){
    	//An AsyncTask that will check if the user is registered
    	class CheckForDetails extends AsyncTask<String, Void, String>{
    		protected void onPreExecute(){
    			showProgressIndicator("Checking server");
    		}
    		/**
    		 * Do the HTTP request here on the background thread
    		 * @param userId		The user ID to check
    		 */
			protected String doInBackground(String... userId) {
				String request = serverAddress+"getUserDetails.php?userId="+userId[0];
				String response = HTTP.doHttpGet(request, 5);
				if(response!=null){
					return response;
				}
				else{
					return null;
				}
			}			
			/**
			 * Called after the request has completed
			 * @param response		The raw response text from the server
			 */
			protected void onPostExecute(String response){
				hideProgressIndicator();
    			if(response!=null){
					try {
						JSONObject responseJSON = new JSONObject(response);
						if(responseJSON.has("warning")){		//True if the user id was not found on the server
							goToState(STATE_ENTER_DETAILS);
							newAccount = true;
						}
						else if(responseJSON.has("firstName")){		//True if the user id was found on the server
							firstName = responseJSON.getString("firstName");
							lastName = responseJSON.getString("lastName");
							newAccount = false;
							goToState(STATE_ENTER_DETAILS);
						}
					}
					catch (JSONException e) {
						Log.e(TAG, "Error parsing JSON response: "+e.toString());
					}
				}
			}
    	}
    	//Run the task
    	new CheckForDetails().execute(userId);
    }
   
    
    //Requests that an account be created on the server, or an existing one be updated ith new details
    private void createUserAccountOnServer() {
    	//Get the details from the boxes
    	final String tempFirstName = editTextFirstName.getText().toString();
    	final String tempLastName = editTextLastName.getText().toString();
    	
    	final String devicePhoneNumber = getDevicePhoneNumber();
    	//AsyncTask that will create an account
    	class CreateAccountOnServer extends AsyncTask<Void, Void, String>{
    		protected void onPreExecute(){
    			if(newAccount){
    				showProgressIndicator("Creating account");
    			}
    			else{
    				showProgressIndicator("Updating account");
    			}
    		}
    		protected String doInBackground(Void... params) {
    			String request;
    			//Encode the names to make them URL safe
    	    	try {
    				String newTempFirstName = URLEncoder.encode(tempFirstName, "utf-8");
    				String newTempLastName = URLEncoder.encode(tempLastName, "utf-8");
    				request = serverAddress+"userRegister.php?userId="+userId+"&firstName="+newTempFirstName+"&lastName="+newTempLastName;
    			} catch (UnsupportedEncodingException e) {
    				Log.e(TAG, "Error encoding names: "+e.toString());
    				return null;		//We cannot continue
    			}
    			
				//Add the phone number if available
				if(devicePhoneNumber!=null){
					request+= "&devicePhoneNumber="+devicePhoneNumber;
				}				
				String response = HTTP.doHttpGet(request, 5);
				return response;
			}
			protected void onPostExecute(String response){
				hideProgressIndicator();
    			if(response!=null){
					try {
						JSONObject responseJSON = new JSONObject(response);
						if(responseJSON.has("error")){		//True if the user account was not created for some reason
							Log.e(TAG, "User account not created: "+responseJSON.getDouble("error"));
						}
						else if(responseJSON.has("ack")){		//True if the user account was created successfully
							SharedPreferences.Editor editor = preferences.edit();
							editor.putString("userId", userId);
							editor.commit();
							checkGCMStatus();					//Register this device with GCM
							goToState(STATE_CHOOSE_PROFILE_IMAGE);
						}
					}
					catch (JSONException e) {
						Log.e(TAG, "Error parsing JSON response: "+e.toString());
					}
				}
    			firstName = tempFirstName;
    			lastName = tempLastName;
			}
		}
    	//Check that data was entered
    	if(tempFirstName!=null && tempLastName!=null){
        	new CreateAccountOnServer().execute();
    	}
	}
    
    protected void uploadProfileImage() {
		final UploadFileTask uploadUserImage = new UploadFileTask("http://tomhomewood.dyndns.org/epicChat/setUserImage.php?userId="+userId, localImagePath);			
    	uploadImageHandler = new Handler(){
			public void handleMessage(Message message){
				//Switch based on the code of the message
				switch(message.what){
				case UploadFileTask.EVENT_UPLOAD_STARTED:			//Received when the upload of the image is about to start
					Log.d(TAG, "Started: "+message.getData().getInt("fileLength"));
					showProgressIndicator("Uploading profile picture");
	    			break;
				case UploadFileTask.EVENT_UPLOAD_PROGRESS_UPDATED:
					Log.d(TAG, "Uploaded: "+message.getData().getInt("bytesUploaded"));
					break;
				case UploadFileTask.EVENT_UPLOAD_SUCCEEDED:			//If the upload succeeded, hide the indicator
					hideProgressIndicator();
					goToState(STATE_COMPLETE);
					break;
				case UploadFileTask.EVENT_UPLOAD_FAILED:			//If the upload failed, hide the indicator and stay in this state
					hideProgressIndicator();
					break;
				default:
					break;
				}
			}
		};
		uploadUserImage.setHandler(uploadImageHandler);
		uploadUserImage.execute();
	}
	
	/**
	 * Connects to the SQLite database and adds a Contact containing the user's details to it, before setting the userId in the shared preferences and finishing the activity
	 */
	private void saveUserDetails(){
		//Create a Contact object for the user
		Contact userContact = new Contact(userId, firstName, lastName, getDevicePhoneNumber(), userImagePath);
		Database database = new Database(this);
		database.addContact(userContact);		//Save the user in the database
		database.close();
		//Store the userId in the preferences
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("userId", userId);
		editor.commit();
	}

	
    
    
    //Shows the initial pane, just a welcome screen
    private void showInitialPane(){
    	hideProgressIndicator();
    	setTopText("Welcome to Epic Chat! Let's spend a minute setting up your account");
    	viewFlipper.setDisplayedChild(0);		//Welcome screen is the first child
    	setButtonText("Start");
    }
    
    //Shows the account selection pane
    private void showAccountChoicePane(){
    	setTopText("There is more than one Google account on this device. Choose the one you want to use with Epic Chat");
    	setButtonText("Next");
		viewFlipper.setDisplayedChild(1);		//Show the second child
	  	int nAccounts = googleAccounts.size();
	  	String[] accountNames = new String[nAccounts];
	  	for(int i=0; i<nAccounts; i++) {		//Add each account name to the list
	  		accountNames[i] = googleAccounts.get(i).name;
	  	}
	  	accountsListAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_single_choice, android.R.id.text1, accountNames);
	  	accountsList.setAdapter(accountsListAdapter);
		accountsList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
	  }
	  
	//Shows the user details pane
    private void showDetailsPane(){
    	setButtonText("Next");
		viewFlipper.setDisplayedChild(2);		//Show the third child
		//Set the details if they were provided
	  	if(firstName!=null && lastName!=null){
	  		setTopText("You already have an Epic Chat account. If you want to edit your details you can do it now");
			editTextFirstName.setText(firstName);
	  		editTextLastName.setText(lastName);
	  	}
	  	else{
	  		setTopText("Enter your details");
	  	}
	}
    
    //Shows the penultimate pane in the viewflipper, which is where the user can select a profile image to use for their account
    public void showProfileImagePane() {
    	setTopText("Choose a profile picture. Touch the picture to choose another one.");
    	viewFlipper.setDisplayedChild(3);		//Show the fourth child
		setButtonText("Next");
		if(!newAccount){				//If the user account already exists on the server, download the user's profile image from the server
			userImagePath = MainActivity.DIRECTORY_USER_IMAGES+userId+".jpg";
	    	final DownloadFileTask downloadUserImage = new DownloadFileTask("http://tomhomewood.dyndns.org/epicChat/getUserImage.php?userId="+userId+"&size=500", userImagePath);			
	    	downloadImageHandler = new Handler(){
				public void handleMessage(Message message){
					//Switch based on the code of the message
					switch(message.what){
					case DownloadFileTask.EVENT_DOWNLOAD_STARTED:			//Received when the download of the image is about to start
						showProgressIndicator("Downloading profile picture");
		    			break;
					case DownloadFileTask.EVENT_DOWNLOAD_SUCCEEDED:			//If the download succeeded, get the bitmap of the downloaded image and display it in the image view 
						hideProgressIndicator();
						imageViewProfileImage.setImageBitmap(downloadUserImage.getImageBitmap(null, null));
						break;
					default:
						break;
					}
				}
			};
			downloadUserImage.setHandler(downloadImageHandler);
			downloadUserImage.execute();
    	}
	}
    
    //Shows the final pane in the viewflipper, which just says complete and stores the user id the preferences
    public void showProcessCompletePane() {
    	setTopText("All done! Touch done to start using Epic Chat");
    	viewFlipper.setDisplayedChild(4);		//Show the last child
		setButtonText("Done");
		saveUserDetails();						//Create a contact for the user
		SyncContactsTask syncContactsTask = new SyncContactsTask(this);		//Start the contact sync process
    	syncContactsTask.execute();    	
	}
    
    //Updates the text in the next button
    private void setButtonText(String text){
    	buttonNext.setText(text);
    }
    
    //Updates the text at the top of the screen
    private void setTopText(String text){
    	topText.setText(text);
    }
    
    private void showProgressIndicator(String text){
    	textViewProgressText.setText(text);
		textViewProgressText.setVisibility(View.VISIBLE);
		progressBarProgress.setVisibility(View.VISIBLE);
    }
    
    private void hideProgressIndicator(){
    	textViewProgressText.setVisibility(View.INVISIBLE);
		progressBarProgress.setVisibility(View.INVISIBLE);
    }

	//Checks if this device has a GCM ID
    private void checkGCMStatus(){
    	GCMRegistrar.checkDevice(this);
        GCMRegistrar.checkManifest(this);
        //unregisterWithGCM();
        registerWithGCM();
        /*
        final String regId = GCMRegistrar.getRegistrationId(this);
        if (regId.equals("")) {
        	registerWithGCM();
        	//Log.d(TAG, "Registered with GCM, id: "+regId);
        }
        else {
        	Log.d(TAG, "Already registered with GCM, id: "+regId);
        	SharedPreferences.Editor editor = preferences.edit();
			editor.putString("gcmId", regId);
			editor.commit();
        }
        */
    }
    
    //Retrieves the device's phone number, or null if there is no number available. Used when creating an account on the server
    private String getDevicePhoneNumber(){
        TelephonyManager telephonyMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE); 
        return telephonyMgr.getLine1Number();
    }    
    
    //Attempts to register this device with GCM
    private void registerWithGCM(){
    	GCMRegistrar.register(this, MainActivity.GCM_SENDER_ID);
    }
/*    
    //Attempts to unregister this device with GCM
    private void unregisterWithGCM(){
    	GCMRegistrar.unregister(this);
    }
*/
}
