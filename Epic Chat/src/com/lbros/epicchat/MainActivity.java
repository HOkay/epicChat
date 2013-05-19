package com.lbros.epicchat;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Vector;

import android.app.ActionBar;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.util.LruCache;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class MainActivity extends FragmentActivity {
	private final String TAG = "MainActivity";

	private SharedPreferences preferences;
	private AlarmManager alarmManager;

	public static final String DIRECTORY_BASE = Environment.getExternalStorageDirectory()+"/epicChat/";
	
	public static final String DIRECTORY_RESOURCES = DIRECTORY_BASE+"Epic Chat Images/";
	public static final String DIRECTORY_USER_IMAGES = DIRECTORY_BASE+"userImages/";
	public static final String DIRECTORY_GAME_IMAGES = DIRECTORY_BASE+"gameImages/";

	//private final long INTERVAL_ONE_MINUTE = 60 * 1000;	//One minute in milliseconds
	
	private Intent sendFailedMessagesIntent;
	private PendingIntent sendFailedMessagesIntentPending;
	
	//Activity intent signatures
	private final int ACTION_ENTER_ACCOUNT_DETAILS = 1;
	private final int ACTION_CHOOSE_CONTACT = 2;
	
	private Intent receivedDataIntent;
	
	//Intent signatures used for events
	public static final String intentSignatureNewMessageReceived = "com.lbros.epicChat.newMessageReceived";
	public static final String intentSignatureMessageAckReceived = "com.lbros.epicChat.newAckReceived";
	public static final String intentSignatureRemovePendingMessages = "com.lbros.epicChat.removePendingMessages";
	public static final String intentSignatureContactSyncComplete = "com.lbros.epicChat.contactSyncCompleted";
	public static final String intentSignatureConversationsModified = "com.lbros.epicChat.conversationsModified";

	//This is the Google Cloud Messaging (GCM) API ID for this project
	public static final String GCM_SENDER_ID = "702059871658";

	//Set of codes for use with various notifications
	public static final int NOTIFICATION_NEW_MESSAGE = 1;
	public static final int NOTIFICATION_NEW_CONTACT = 2;

	private List<Fragment> fragments;
	private ViewPager viewPager;
	private PagerTabStrip fragmentsPagerTabStrip;
	private SectionsPagerAdapter sectionsPagerAdapter;
	
	//Fragment codes used in conjunction with the fragment list
	private final int TAB_CONVERSATIONS = 0;
	private final int TAB_CONTACTS = 1;
	private final int TAB_GAMES = 2;

	//This is a global cache for the bitmaps we will load. All activities may share this cache to increase the overal cache hit rate
	public static LruCache<String, Bitmap> bitmapCache;
	public static final int cacheSize = 4096;		//Size of the cache in KB

	private ActionBar actionBar;
	private boolean userIdSet;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		//Initialise the cache that we will use for Bitmap images
		loadBitmapCache();

		preferences = PreferenceManager.getDefaultSharedPreferences(this);	//Load the preferences

		//This is for getting the WiFi MAC from the device, which we use as a UUID for the device
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        String UUID = wifiManager.getConnectionInfo().getMacAddress();
        //Save the UUID
        SharedPreferences.Editor editor = preferences.edit();

        editor.putString("serverAddress", "http://epicchat.cufflinksdesign.co.uk/");
        editor.putString("UUID", UUID);
        editor.commit();

		setContentView(R.layout.activity_main);

		actionBar = getActionBar();
		// Create the adapter that will return a fragment for each of the three primary sections of the app's main view.

		fragments = new Vector<Fragment>();
		addFragmentAsTab(TAB_CONVERSATIONS);
		addFragmentAsTab(TAB_CONTACTS);
		//addFragmentAsTab(TAB_GAMES);
		
		sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager(), fragments);

		// Set up the ViewPager with the sections adapter.
		viewPager = (ViewPager) findViewById(R.id.activity_main_viewpager);
		viewPager.setAdapter(sectionsPagerAdapter);
		fragmentsPagerTabStrip = (PagerTabStrip) findViewById(R.id.activity_main_viewpager_tab_strip);
		fragmentsPagerTabStrip.setDrawFullUnderline(false);					//Makes the tab indicator fill the whole width
		fragmentsPagerTabStrip.setTabIndicatorColor(0xFFFF8800);
		
		viewPager.setOnPageChangeListener(tabChangedListener);			//This will update the Action Bar when the tab is changed
		actionBar.setTitle(sectionsPagerAdapter.getPageTitle(viewPager.getCurrentItem()));		//Set the title of the action bar
		//viewPager.setPageTransformer(false, new ZoomOutPageTransformer(0.9f, 0.9f));
		
		//Initialise the alarm manager, which will be used to run periodic background tasks
		alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
		//This Intent will trigger a check for messages that have not been sent yet
		sendFailedMessagesIntent = new Intent(this, CheckForUnsentMessages.class);
		sendFailedMessagesIntentPending = PendingIntent.getBroadcast(this, 0, sendFailedMessagesIntent, 0);
		//First, cancel any previous alarm with this PendingIntent
		alarmManager.cancel(sendFailedMessagesIntentPending);
		//Calculate the start time
		//long sendFailedMessagesInterval = INTERVAL_ONE_MINUTE;		//One minute
		//long firstBroadcastTime = System.currentTimeMillis() + sendFailedMessagesInterval;		//This task will run after the interval has elapsed
		//alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, firstBroadcastTime, sendFailedMessagesInterval, sendFailedMessagesIntentPending);
		
		//Check the device's filesystem
		checkFilesystem();
		
		//Check if a user ID is set
		userIdSet = userIdSet();
		//userIdSet = false;
        if(!userIdSet){		//True if the user ID has not been set, so launch an activity to handle account creation / synchronisation
        	showEnterAccountDetailsActivity();
        }
        
        //Check if this activity was launched normally, or in response to the user sending an object to it (e.g. an image or other shareable item)
        Intent intent = getIntent();
        String action = intent.getAction();
        if(action==null){
        	//No action, do nothing here
        }
        else if(action.equals(Intent.ACTION_SEND)){		//Something was sent to this activity, we should handle it
        	receivedDataIntent = intent;			//Just store the intent for now, we need to choose a user first before doing any processing on it
        	Intent chooseContact = new Intent(this, ChooseContactActivity.class);
        	startActivityForResult(chooseContact, ACTION_CHOOSE_CONTACT);		//We will do the rest of the processing when this activity returns to us with a contact
        }
	}
	
	private void showEnterAccountDetailsActivity() {
    	Intent enterAccountDetailsIntent = new Intent(this, EnterAccountDetailsActivity.class);
    	startActivityForResult(enterAccountDetailsIntent, ACTION_ENTER_ACCOUNT_DETAILS);
	}

	/**
	 * Checks to see if the necessary folders exist in the device's filesystem. They are created if they do not exist
	 */
	private void checkFilesystem() {
		checkDirectoryExists(MainActivity.DIRECTORY_BASE, true);
		checkDirectoryExists(MainActivity.DIRECTORY_USER_IMAGES, true);
		checkDirectoryExists(MainActivity.DIRECTORY_RESOURCES, true);
		checkDirectoryExists(MainActivity.DIRECTORY_GAME_IMAGES, true);
		checkFileExists(DIRECTORY_USER_IMAGES+".nomedia", true);
		checkFileExists(DIRECTORY_GAME_IMAGES+".nomedia", true);
	}
	
	/**
	 * Checks if the directory specified by the provided path exists. If not, and the parameter createIfNotPresent is true, it is created
	 * @param path					The path to check
	 * @param createIfNotPresent	Whether or not the directory should be created if it does not exist
	 * @return						Whether or not this directory exists. If a directory was successfully created, this will be true to reflect this
	 */
	private boolean checkDirectoryExists(String path, boolean createIfNotPresent){
		boolean directoryExists = true;
		File directory = new File(path);
		if(!directory.exists()){	//True if the directory does not exist
			directoryExists = false;
			if(createIfNotPresent){
				directory.mkdir();
				directoryExists = true;
			}
		}
		return directoryExists;
	}
	
	/**
	 * Checks if the file specified by the provided path exists. If not, and the parameter createIfNotPresent is true, an empty file is created
	 * @param path					The path to check
	 * @param createIfNotPresent	Whether or not the file should be created if it does not exist
	 * @return						Whether or not this file exists. If an empty file was successfully created, this will be true to reflect this
	 */
	private boolean checkFileExists(String path, boolean createIfNotPresent){
		boolean fileExists = true;
		File file = new File(path);
		if(!file.exists()){	//True if the directory does not exist
			fileExists = false;
			if(createIfNotPresent){
				try {
					file.createNewFile();
					fileExists = true;
				}
				catch (IOException e) {
					Log.e(TAG, "Error creating file: "+e.toString());
				}
			}
		}
		return fileExists;
	}

	/**
	 * Adds the Fragment specified by the provided code to the list of fragments
	 * @param fragmentTabCode		The code associated with the Fragment. Must be one of the TAB fields defined in this class
	 */
	private void addFragmentAsTab(int fragmentTabCode) {
		switch(fragmentTabCode){
		case TAB_CONVERSATIONS:
			fragments.add(Fragment.instantiate(this, ConversationsListFragment.class.getName()));
			break;
		case TAB_CONTACTS:
			fragments.add(Fragment.instantiate(this, ContactsListFragment.class.getName()));
			break;
		case TAB_GAMES:
			fragments.add(Fragment.instantiate(this, GamesListFragment.class.getName()));
			break;
		default:
			break;
		}
	}

	@Override
	protected void onResume(){
		super.onResume();
		userIdSet();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.menu_main_activity, menu);
	    //If the user is signed in to Epic Chat, remove the sign in option from the menu
  		if(userIdSet){
  			menu.removeItem(R.id.menu_main_activity_setup_account);
  		}
  		return true;
	}
	
	/**
	 * Called when the options menu or action bar icon is touched 
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case R.id.menu_main_activity_setup_account:
	    	showEnterAccountDetailsActivity();
	    	return true;
	    default:
	    	return super.onOptionsItemSelected(item);
	    }
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent returnedIntent) {
		super.onActivityResult(requestCode, resultCode, returnedIntent);
	    switch(requestCode) {
	    case ACTION_ENTER_ACCOUNT_DETAILS:		//After getting the user's account details, trigger a refresh of the user's contacts
	        if(resultCode==RESULT_OK){
	        	viewPager.setCurrentItem(1, true);		//Jump to the contacts list tab
	        }
	        userIdSet = userIdSet();
	        break;
	    case ACTION_CHOOSE_CONTACT:				//A contact was successfully chosen by the user
	    	if(resultCode==RESULT_OK){
		    	Contact chosenContact = (Contact) returnedIntent.getSerializableExtra(ChooseContactActivity.EXTRA_CONTACT);
		    	String localUserId = preferences.getString("userId", null);
		    	if(localUserId!=null && chosenContact!=null){
		    		String contactId = chosenContact.getId();
		    		String conversationId = Conversation.sortConversationId(contactId+','+localUserId);		//Create the conversation ID we need using the local user's ID and the ID of the selected contact. Sort to avoid ID problems
			    	String dataType = receivedDataIntent.getType();		//The data we want was stored earlier in this variable
			    	Uri dataPath = null;
		        	
			    	//Open the required conversation using an Intent
		        	Intent openChatIntent = new Intent(this, ViewConversationsActivity.class);
		        	openChatIntent.putExtra("conversationId", conversationId);
		        	openChatIntent.setAction(Intent.ACTION_SEND);	//Set the action so the activity knows what is happening
			    	
			    	//Check what kind of data we are being sent
		        	if(dataType.startsWith("image/")){							//Data is a reference to an image. Use startsWith because matching "image/*" using string functions will fail for specific types (e.g. "image/jpg")
		        		dataPath = (Uri) receivedDataIntent.getParcelableExtra(Intent.EXTRA_STREAM);
		        		if(dataPath==null){		//Most of the time the Uri will come from the parcelable extra, as above, but sometimes it will come as the intent data itself, so check this if the former fails
		        			dataPath = receivedDataIntent.getData();
		        		}
		        		openChatIntent.setDataAndType(dataPath, dataType);				//Add the path of the resource (e.g. image) as the Intent's data. Copy the type from the intent we received
			        }
		        	else if(dataType.startsWith("text/plain")){
		        		openChatIntent.putExtra(Intent.EXTRA_TEXT, receivedDataIntent.getStringExtra(Intent.EXTRA_TEXT));
		        		openChatIntent.setType(dataType);
		        	}
		        	startActivity(openChatIntent);
		    	}
	    	}
	    	else{		//User backed out, so exit the app
	    		finish();
	    	}
	    	break;
	    default:
	        	break;
	    }
	}

    /**
     * Initialises the bitmap cache
     */
	public static void loadBitmapCache() {		
		bitmapCache = new LruCache<String, Bitmap>(cacheSize){
	        @Override
	        protected int sizeOf(String key, Bitmap bitmap) {
	            return bitmap.getByteCount() / 1024;// The cache size will be measured in kilobytes rather than number of items
	        }
	    };
	}
	
	public class SectionsPagerAdapter extends FragmentPagerAdapter {

		private final List<Fragment> fragments;

		/**
		* @param fm
		* @param fragments
		*/
		public SectionsPagerAdapter(FragmentManager fragmentManager, List<Fragment> newFragments) {
			super(fragmentManager);
			fragments = newFragments;
		}		
		/**
		 *  getItem is called to instantiate the fragment for the specified page
		 *  @param position		The position of the fragment in the sliding menu
		 */

		@Override
		public Fragment getItem(int position) {
			return fragments.get(position);
		}

		@Override
		public int getCount() {
			return fragments.size();
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
			case TAB_CONVERSATIONS:
				return "Chats";
			case TAB_CONTACTS:
				return "Contacts";
			case TAB_GAMES:
				return "Games";
			}
			return null;
		}
	}
	
	private ViewPager.OnPageChangeListener tabChangedListener = new OnPageChangeListener() {
		@Override
		public void onPageSelected(int position) {
			actionBar.setTitle(sectionsPagerAdapter.getPageTitle(position));		//Update the Action Bar's title
		}

		@Override
		public void onPageScrollStateChanged(int arg0) {}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2) {}
	};
	
	//Returns a boolean indicating if the user ID has been set
    private boolean userIdSet(){
    	String userId = preferences.getString("userId", null);
    	if(userId!=null){
    		return true;
    	}
    	else{
    		return false;
    	}
    }
}