package com.lbros.epicchat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnShowListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class ViewConversationFragment extends Fragment{
	private final String TAG = "ViewConversationFragment";
	
	//Activity intent codes
	private final int ACTION_SELECT_IMAGE_FROM_GALLERY = 1;
	private final int ACTION_TAKE_PHOTO_WITH_CAMERA = 2;
	private final int ACTION_CHOOSE_CONTACT_FOR_FORWARDING_MESSAGE = 3;
	private final int ACTION_SHOW_CONVERSATION_IMAGE_GALLERY = 10;
	
	//These tags are used when placing values in the data Bundle which we return to the calling activity, and when retrieving these values
    private static final String EXTRA_CONVERSATION_ID = "conversationId";
    private static final String EXTRA_INTENT = "intent";
    
    //Flag that indicates if the activity is visible
  	private boolean isVisible = false;
  	private Boolean wasVisibleWhenPaused = null;
  	
  	private float pixelDensity;
	private int contactThumbnailSize;
    
  	private Message pendingImageMessage;
	private String capturePhotoImagePath = null;
  	
  	private IntentFilter messageReceivedFilter = new IntentFilter(MainActivity.intentSignatureNewMessageReceived);
  	private boolean messageReceiverRegistered = false;
	private Database database;
	private SharedPreferences preferences;
	private NotificationManager notificationManager;
	private ClipboardManager clipboardManager;
	
	private RelativeLayout fragmentLayout;
	private FragmentActivity fragmentActivity = null;
	
	private int nMessagesToShow = 50;		//Initially show 50 messages
    
    private Button buttonSend;
	private EditText textEntry;
	
	private MessageListAdapter chatListAdapter;
	private ListView chatList;
	
	private int colourFFFFFF, colourEEEEEE;
	private HashMap<String, ProgressBar> messageProgressBars;
    
    private Conversation conversation = null;
    private Intent intent = null;
    private Bundle intentData = null;
    private String intentAction = null;
    
    private boolean intentHandled = false;
	protected Message messageToBeForwarded = null;
    
    private int listViewScrollIndex = -1;
    private int listViewScrollOffset = 0;
    
    private String localUserId = null;
	
	private boolean showKeyboard = false;

	//Actions used in the message popup menu
	private final int POPUP_MENU_ACTION_COPY_TEXT = 1;
	private final int POPUP_MENU_ACTION_FORWARD = 2;
	private final int POPUP_MENU_ACTION_DELETE = 3;
	private final int POPUP_MENU_ACTION_RESEND = 4;
	private final String TAG_CLIPBOARD_MESSAGE_TEXT = "messageText";


    public static ViewConversationFragment newInstance(Conversation conversation, Intent intent) {
    	final ViewConversationFragment fragmentToReturn = new ViewConversationFragment();
        final Bundle data = new Bundle();
        data.putSerializable(EXTRA_CONVERSATION_ID, conversation);
        data.putParcelable(EXTRA_INTENT, intent);
        fragmentToReturn.setArguments(data);
        return fragmentToReturn;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Retrieve the data we need
        Bundle data = getArguments();
        conversation = (Conversation) data.getSerializable(EXTRA_CONVERSATION_ID);
        intent = data.getParcelable(EXTRA_INTENT);
        colourEEEEEE = Color.parseColor("#EEEEEE");
		colourFFFFFF = Color.parseColor("#FFFFFF");
		setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	
    	fragmentActivity = (FragmentActivity) super.getActivity();

    	//Connect to the SQLite database
		database = new Database(fragmentActivity);
		
		//Get the shared preferences
		preferences = PreferenceManager.getDefaultSharedPreferences(fragmentActivity);
		localUserId = preferences.getString("userId", null);

		//Get the pixel density of the screen. We need this as we have to set the height of some ImageView objects in pixels
		pixelDensity = fragmentActivity.getResources().getDisplayMetrics().density;
		contactThumbnailSize = (int) (60 * pixelDensity + 0.5f);

		//Get the notification manager
		notificationManager = (NotificationManager) fragmentActivity.getSystemService(Context.NOTIFICATION_SERVICE);
		//Get the clipboard manager. Used for copy and paste
		clipboardManager = (ClipboardManager) fragmentActivity.getSystemService(Context.CLIPBOARD_SERVICE);
		
		fragmentLayout = (RelativeLayout) inflater.inflate(R.layout.fragment_view_conversation, container, false);
        
        messageReceivedFilter = new IntentFilter(MainActivity.intentSignatureNewMessageReceived);
		messageReceivedFilter.setPriority(5);		//This receiver should have a higher priority than the notification receiver
		
		textEntry = (EditText) fragmentLayout.findViewById(R.id.fragment_view_conversation_text_entry);
		
		buttonSend = (Button) fragmentLayout.findViewById(R.id.fragment_view_conversation_send_message);
		
		//Set up the list and its custom adapter
		chatList = (ListView) fragmentLayout.findViewById(R.id.fragment_view_conversation_message_list);
		chatList.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
		
		chatList.setOnScrollListener(new OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {
				switch(scrollState){
				case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:			//List has finished scroll, re-enable transcript mode
					chatList.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
					break;
				case AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:	//List just about to scroll from a user touch, so don't do anything
					break;
				case AbsListView.OnScrollListener.SCROLL_STATE_FLING:			//List just about to scroll, we should enable smooth scrolling by disabling the transcript mode
					chatList.setTranscriptMode(ListView.TRANSCRIPT_MODE_DISABLED);
					break;
				default:
					break;
				}
			}
			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
			}
		});
		
		chatListAdapter = new MessageListAdapter(fragmentActivity);
		chatList.setAdapter(chatListAdapter);

		//Check if there is any previous state to restore
		if(savedInstanceState!=null){
			Parcelable previousState = savedInstanceState.getParcelable("listViewState");		//Attempt to restore the state of the list view. This ensures the scroll position is remembered
			if(previousState!=null){
				chatList.onRestoreInstanceState(previousState);
			}
		}
		//Initialise the HashMap, which will contain mappings of message IDs to Progress Bar objects
        messageProgressBars = new HashMap<String, ProgressBar>();

        //Listeners
		buttonSend.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				String textToSend = textEntry.getText().toString().trim();		//Get the text. trim() removes leading and trailing whitespace
				if(!TextUtils.isEmpty(textToSend)){		//True if there is something to send
					//This function will add the message to the chat list, store it in the database, and send it to the remote user(s)
					sendTextAsMessage(textToSend);
					//Clear the box
					textEntry.setText("");
				}
			}
		});
		
		if(!intentHandled){			//Check the intent if we have not already done so
			//Check if this activity was launched normally, or in response to the user sending an object to it (e.g. an image or other shareable item)
			if(intent!=null){
				intentData = intent.getExtras();
				intentAction = intent.getAction();
			}
			if(intentData!=null){
				showKeyboard = intentData.getBoolean("showKeyboard", false);
		    	if(showKeyboard){		//Show the keyboard
		    	}
			}
	        if(intentAction==null){
	        	//Do nothing if there is no action
	        }
	        else if(intentAction.equals(Intent.ACTION_SEND)){		//Something was sent to this activity, we should handle it
	        	String conversationId = intentData.getString("conversationId");
	        	if(conversationId!=null && conversationId.equals(conversation.getId())){
	        		//Check what kind of data we are being sent
		            String dataType = intent.getType();
		            if(dataType.startsWith("image/")){							//Data is a reference to an image. Use startsWith because matching "image/*" using string functions will fail for specific types (e.g. "image/jpg")
		        		Uri imageUri = intent.getData();
		        		if(imageUri!=null){
		            		String imagePath = getRealPathFromURI(imageUri);	//Convert the URI to a string and show the image preview dialog
		            		if(imagePath!=null){
		            			showImagePreviewDialog(imagePath);
		            		}
		            	}
		        	}
		            else if(dataType.startsWith("text/plain")){
		            	String text = intent.getStringExtra(Intent.EXTRA_TEXT);
		            	if(text!=null && text.trim().length()>0){
		            		sendTextAsMessage(text.trim());
		            	}
		            }
	        	}
	        	intentHandled = true;		//This prevents the Intent being checked again during the lifetime of this instance of the fragment. This is important as if the fragment is 
	        								//swiped away from, and swiped back to, we don't want to handle the Intent again
	        }
		}
        registerMessageReceiver();
        return fragmentLayout;
    }
    
    private void sendTextAsMessage(String text) {
    	//First, we need a timestamp for the message
		int messageTimestamp = createTimestampForMessage();
		//Also need a unique ID for the message
		String messageId = createMessageId(messageTimestamp);
		Message message = new Message(messageId, messageTimestamp, Message.MESSAGE_TYPE_TEXT, Message.MESSAGE_STATUS_PENDING, conversation.getId(), localUserId, text);
		storeMessage(message);
		chatListAdapter.refresh();
		chatList.setTranscriptMode(ListView.TRANSCRIPT_MODE_DISABLED);
		scrollListToBottom();
		sendMessageUsingGCM(message);
	}

    private int createTimestampForMessage() {
		return (int) (System.currentTimeMillis() / 1000);
	}

	private String createMessageId(int messageTimestamp) {
		return messageTimestamp+""+UUID.randomUUID();
	}
	
	private void registerMessageReceiver(){
    	fragmentActivity.registerReceiver(gcmMessageReceiver, messageReceivedFilter);
    	messageReceiverRegistered = true;
    }
    
    private void unregisterMessageReceiver(){
    	fragmentActivity.unregisterReceiver(gcmMessageReceiver);
    	messageReceiverRegistered = false;
    }
    
    /**
	 * Called by the OS when the activity is shown to the user
	 */
    @Override
    public void onResume(){
    	super.onResume();
		if(wasVisibleWhenPaused!=null){
			isVisible = wasVisibleWhenPaused;
		}
    	if(showKeyboard){		//Show the keyboard if it was requested in the intent
    	}
    	if(listViewScrollIndex!=-1){
    		chatList.setSelectionFromTop(listViewScrollIndex, listViewScrollOffset);
    	}
    	if(isVisible){
    		//registerMessageReceiver();
    		clearNotificationAndPendingMessages();
		}
    	//Check if there is text to load into the box
    	String previousText = preferences.getString("entryBoxText"+conversation.getId(), null);
    	if(previousText!=null){
    		textEntry.setText(previousText);
    		//textEntry.requestFocus();
    	}
    }
	
	/**
	 * Called by the OS when the activity is hidden from the user
	 */
	@Override
	public void onPause(){
		super.onPause();
		wasVisibleWhenPaused = isVisible;		//Need to remember this state so that we can restore it in onResume()
		isVisible = false;
		//The combination of the index of the first visible row, and the same row's offset from its parent will give us the exact scroll position. We can restore this in onResume() to preserve page scroll
		//We can't use the saved state of the list as we have to-add the adapter to the list every time the view is regenerated, which resets the list's state 
    	listViewScrollIndex = chatList.getFirstVisiblePosition();
    	listViewScrollOffset = chatList.getChildAt(0).getTop();
    	//Save the text currently in the text entry box so that it can be resumed
    	String entryBoxText = textEntry.getText().toString().trim();    	
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("entryBoxText"+conversation.getId(), entryBoxText);
		editor.commit();
	}
	
	/**
	 * Called when the activity is destroyed
	 */
	@Override
	public void onDestroy(){
		if(messageReceiverRegistered){
			unregisterMessageReceiver();
		}
		super.onDestroy();
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
	    inflater.inflate(R.menu.menu_view_conversation, menu);
	}
	
	/**
	 * Called when the options menu or action bar icon is touched 
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        case android.R.id.home:		//App icon in action bar clicked, so go to the main activity	            
	            fragmentActivity.finish();
	            return true;
	        case R.id.menu_view_conversation_item_gallery:		//User touched the gallery button
	        	selectImageFromGallery();
	        	return true;
	        case R.id.menu_view_conversation_item_camera:		//User touched the camera button
	        	takePhotoWithCamera();
	        	return true;
	        default:
	            return super.onOptionsItemSelected(item);
	    }
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent returnedIntent) {
		super.onActivityResult(requestCode, resultCode, returnedIntent);
	    switch(requestCode) {
	    case ACTION_SELECT_IMAGE_FROM_GALLERY:			//An image has been chosen from the gallery
	    	if(resultCode == Activity.RESULT_OK){
	        	String imagePath = getRealPathFromURI(returnedIntent.getData());
	        	if(imagePath!=null){
	        		showImagePreviewDialog(imagePath);
	        	}
	        }
	    	break;
	    case ACTION_TAKE_PHOTO_WITH_CAMERA:				//A photo has been taken with the camera
	    	if(resultCode == Activity.RESULT_OK){
	        	if(capturePhotoImagePath!=null){
	        		showImagePreviewDialog(capturePhotoImagePath);
	        	}
	        }
	    	break;
	    case ACTION_CHOOSE_CONTACT_FOR_FORWARDING_MESSAGE:				//A photo has been taken with the camera
	    	if(resultCode == Activity.RESULT_OK){
	        	Contact chosenContact= (Contact) returnedIntent.getSerializableExtra(ChooseContactActivity.EXTRA_CONTACT);
	        	if(chosenContact!=null && messageToBeForwarded!=null){
	        		//First, we need a timestamp for the message
	        		int messageTimestamp = createTimestampForMessage();
	        		//Also need a unique ID for the message
	        		String messageId = createMessageId(messageTimestamp);
	        		String newConversationId = chosenContact.getId()+','+localUserId;
	        		Message newMessage = new Message(messageId, messageTimestamp, messageToBeForwarded.getType(), Message.MESSAGE_STATUS_PENDING, newConversationId, localUserId, messageToBeForwarded.getContents(null).toString());
	        		sendMessageUsingGCM(newMessage);			//Send the message
	        		storeMessage(newMessage);					//And store it in the database
	        		messageToBeForwarded = null;
	        		//Last thing to do is to switch page so the destination conversation is displayed
	        		Intent switchConversationIntent = new Intent(fragmentActivity, ViewConversationsActivity.class);
	        		switchConversationIntent.putExtra(ViewConversationsActivity.EXTRA_CONVERSATION_ID, newConversationId);
	        		switchConversationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
	        		startActivity(switchConversationIntent);
	        	}
	        }
	    	break;
	    default:
	    	break;
	    }
	}
	
	public void setVisibility(boolean visible){
		isVisible = visible;
		if(isVisible){
			clearNotificationAndPendingMessages();
		}
	}
	
	/**
	 * Responsible for storing a Message in the database and refreshing the chat list
	 * @param message		The Message object to store
	 */
	private void storeMessage(Message message) {
		//Add the message to the database
		database.addMessage(message);
		nMessagesToShow++;
	}
	
	/**
	 * Sends a message using GCM. This is achieved by sending the message data to the server, which then sends it on to GCM
	 * @param message		The message to be sent. All the information we need is contained in this object
	 */
	private void sendMessageUsingGCM(final Message message){
		//The message is sent using a new thread to avoid blocking on the UI thread
		Thread sendMessageThread = new Thread(new Runnable(){
			public void run(){
				//Extract the data from the message
				final String messageId = message.getId();
				int timestamp = message.getTimestamp();
				int messageType = message.getType();
				String fromUser = message.getSenderId();
				String toUsers = message.getConversationId();
				String messageContents = message.getContents(null).toString();
				String messageTextEncoded;
				try {
					messageTextEncoded = URLEncoder.encode(messageContents, "utf-8");
				}
				catch (UnsupportedEncodingException e) {
					messageTextEncoded = "URLEncoder failed: "+e.toString();
				}
				String serverAddress = preferences.getString("serverAddress", null);
				String request = serverAddress+"sendMessage.php";
				String gcmId = preferences.getString("gcmId", null);
				String parameters = "messageId="+messageId+"&sentTimestamp="+timestamp+"&messageType="+messageType+"&fromUser="+fromUser+"&toUsers="+toUsers+"&messageText="+messageTextEncoded+"&senderDeviceId="+gcmId;
				String response = HTTP.doHttpPost(request, parameters, 5);
				if(response!=null){
					try {
						JSONObject responseJSON = new JSONObject(response);
						if(responseJSON.has("ack")){			//If we got an ACK back then the message was sent
							//The last thing to do is to update the list view
							fragmentActivity.runOnUiThread(new Runnable(){
								@Override
								public void run(){
									messageSentAck(messageId);
								}
							});
						}
					}
					catch (JSONException e) {
						Log.e(TAG, "Error reading response: "+e.toString());
					}
				}
			}
		});
		sendMessageThread.start();
	}
    
    /**
	 * Called when a the epicChat server has sent the message using GCM. This does not mean the GCM message has been received 
	 * @param message		A Message object containing the ACK
	 */
	protected void messageSentAck(String messageId) {
		//Update the status of the message in the database 
		database.updateMessageStatus(messageId, Message.MESSAGE_STATUS_ACK_SERVER);
		//Refresh the list view
		chatListAdapter.refresh();
	}

	/**
	 * Called when a message ACK is received
	 * @param message		A Message object containing the ACK
	 */
	protected void messageReceivedAck(Message message) {
		//Update the status of the message in the database 
		//database.updateMessageStatus(message.getContents(null).toString(), Message.MESSAGE_STATUS_ACK_RECIPIENT);
		//Refresh the list view
		//Log.d(TAG, "GOT ACK");
		chatListAdapter.refresh();
	}
	
	//Displays a dialog box that shows the selected image to the user, along with a text box for adding a caption to the message 
	private void showImagePreviewDialog(final String imagePath) {
		//Create the dialog
		final Dialog imagePreviewDialog = new Dialog(fragmentActivity);
		imagePreviewDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		imagePreviewDialog.setContentView(R.layout.dialog_preview_image);
		imagePreviewDialog.setOnShowListener(new OnShowListener() {
			@Override
			public void onShow(final DialogInterface dialog) {
				ImageView image = (ImageView) imagePreviewDialog.findViewById(R.id.dialog_preview_image_image);
				new Utils.LoadBitmapAsync(imagePath, image, 600, null, true, MainActivity.bitmapCache).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
				final EditText caption = (EditText) imagePreviewDialog.findViewById(R.id.dialog_preview_image_caption);
				Button buttonCancel = (Button) imagePreviewDialog.findViewById(R.id.dialog_preview_image_button_cancel);
				buttonCancel.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						dialog.cancel();
					}
				});
				Button buttonSend = (Button) imagePreviewDialog.findViewById(R.id.dialog_preview_image_button_send);
				buttonSend.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						String imageCaptionFinal = null;
						String imageCaption = caption.getText().toString().trim();
						if(imageCaption.length()>0){
							imageCaptionFinal = imageCaption;
						}
						sendImageAsMessage(imagePath, imageCaptionFinal);
						storeMessage(pendingImageMessage);
					    chatListAdapter.refresh();
					    scrollListToBottom();
					    dialog.dismiss();
					}
				});
			}
		});
		imagePreviewDialog.show();
	}

	/**
	 * Returns the full, real path for the object with the provided URI
	 * @param contentUri			The URI of the object
	 * @return						A String containing the full path of the object, relative to the system root
	 */
	public String getRealPathFromURI(Uri contentUri) {
		Log.d(TAG, "URI: "+contentUri.toString());
		String path = null;
		String uriScheme = contentUri.getScheme();
		if(uriScheme.equals("file")){
			path = contentUri.toString().substring(7);		//In this case the path starts after file://, so the 7th address is the start of the path
		}
		else if(uriScheme.equals("content")){
	        String [] projection = {MediaStore.Images.Media.DATA};
	        Cursor cursor = fragmentActivity.getContentResolver().query(contentUri,
	        				projection, 		// Which columns to return
	                        null,       		// WHERE clause; which rows to return (all rows)
	                        null,      			// WHERE clause selection arguments (none)
	                        null); 				// Order-by clause (ascending by name)
	        if(cursor!=null){
		        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
		        cursor.moveToFirst();
		        path = cursor.getString(columnIndex);
		        cursor.close();
        }
		}
        return path;
	}

	/**
	 * Sends the provided Bitmap as an image to the other members of this conversation
	 * @param imagePath		The path of the image to be sent
	 * @param imageCaption  A caption for the image. This will displayed with the image
	 */
	private void sendImageAsMessage(String imagePath, String imageCaption) {
		//First, copy the image to the resources directory, so it may be loaded by other activities. We will also resample the image at this stage
		File file = new File(imagePath);
		String fileName = file.getName();		//Get the name from the file
		
		String newFilePath = MainActivity.DIRECTORY_RESOURCES+fileName;
		try {
			//The image we load will be downsampled when loading to reduce the memory footprint. To do this, we need to know the factor by which to downsample
			BitmapFactory.Options options = Utils.getImageInformation(imagePath);
			float originalWidth = options.outWidth;				//Original dimensions of the image
			float originalHeight = options.outHeight;
			
			//Get the width and height of the image. This information will be used to scale the new image
			int newWidth;
			int newHeight;
			int maxDimen = 2560;
			
			if(originalWidth<=maxDimen && originalHeight<=maxDimen){ 	//Both dimensions are within the bounds, so no scaling is required
				newWidth = (int) originalWidth;
				newHeight = (int) originalHeight;
			}
			else{
				if(originalWidth>originalHeight){		//The image has a landscape aspect ratio
					float aspectRatio = (float) originalWidth / (float) originalHeight;
					newWidth = maxDimen;
					newHeight = (int) (newWidth / aspectRatio);		//New height is the width divided by the aspect ratio
				}
				else if(originalWidth<originalHeight){		//The image has a portrait aspect ratio
					float aspectRatio = (float) originalWidth / (float) originalHeight;
					newHeight = maxDimen;
					newWidth = (int) (newHeight * aspectRatio);		//New height is the width divided by the aspect ratio
				}
				else{						//Image is a square
					newWidth = maxDimen;
					newHeight = maxDimen;
				}
			}
			int scalingFactor = Utils.calculateSubSamplingFactor(options, newWidth, newHeight, true);
			options.inSampleSize = scalingFactor;
			options.inJustDecodeBounds = false;		//We want the Bitmap's pixels this time
			Bitmap outputImage = BitmapFactory.decodeFile(imagePath, options);				//This will load the bitmap and subsample with the value we provided, to minimise memory usage
			
			//Write the new Bitmap to the new location
			FileOutputStream outputStream = new FileOutputStream(newFilePath);
			outputImage.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

		    //We need a timestamp for the message
			int messageTimestamp = (int) (System.currentTimeMillis() / 1000);
			//Also need a unique ID for the message
			String messageId = messageTimestamp+""+UUID.randomUUID();
			
			//The message contents will be a JSON Object, containing not only the image's thumbnail, but its resource ID (from the server)
		    JSONObject messageContents = new JSONObject();
		    try {
				messageContents.put("fileName", fileName);
				if(imageCaption!=null && imageCaption.length()>0){
					messageContents.put("caption", imageCaption);
				}
				messageContents.put("width", outputImage.getWidth());
			    messageContents.put("height", outputImage.getHeight());
			}
		    catch(JSONException e){
		    	Log.e(TAG, "Error building JSON Object: "+e.toString());
		    }
		    
			//The message will be stored in this variable and sent once the image has been uploaded. The final parameter will be overwritten once we have created a thumbnail, it's just there so we have a reference to the source image
		    pendingImageMessage = new Message(messageId, messageTimestamp, Message.MESSAGE_TYPE_IMAGE, Message.MESSAGE_STATUS_PENDING, conversation.getId(), localUserId, messageContents.toString());
		    
		    //Send this file to the server
		    String serverAddress = preferences.getString("serverAddress", null);
			String remoteAddress = serverAddress+"uploadResource.php?userId="+localUserId+"&fileName="+fileName;
			UploadFileTask uploadImage = new UploadFileTask(remoteAddress, newFilePath);
			uploadImage.setTag(messageId);				//Tag the task with the message ID, so we will know which image upload we any events received come from
			uploadImage.setHandler(messageImageHandler);
			//Executing this task on the thread pool executor allows it to run in parallel with any other tasks, which means we can run LoadBitmapAsync immediately rather than having to wait for this upload to complete. This means we can display the image as soon as it is ready
			uploadImage.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}
		catch (Exception e) {
			Log.e(TAG, "Error copying file images folder: "+e.toString());
		}
	}

	private void selectImageFromGallery() {
		Intent imagePickerIntent = new Intent(Intent.ACTION_GET_CONTENT);
		imagePickerIntent.setType("image/*");
		imagePickerIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
		startActivityForResult(imagePickerIntent, ACTION_SELECT_IMAGE_FROM_GALLERY);
	}
	
	private void takePhotoWithCamera() {
		//We will provide the camera app with the path to which we want the file saved, so let's generate this now
		String photoTimestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date());
		String photoPath = MainActivity.DIRECTORY_RESOURCES+photoTimestamp+".jpg";		//Save the path to the global variable, as we will need it when the camera app returns
		
		File capturePhotoImageFile = new File(photoPath);
		capturePhotoImagePath = capturePhotoImageFile.getAbsolutePath();
		Uri capturePhotoImageUri = Uri.fromFile(capturePhotoImageFile);
		//Create the Intent and add the Uri to it
		Intent imagePickerIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		imagePickerIntent.putExtra(MediaStore.EXTRA_OUTPUT, capturePhotoImageUri);
		startActivityForResult(imagePickerIntent, ACTION_TAKE_PHOTO_WITH_CAMERA);
	}
	
	private void scrollListToBottom(){
		chatList.smoothScrollToPosition(chatListAdapter.getCount() - 1);
		//chatList.setSelection(chatListAdapter.getCount() - 1);
	}
    
    /**
	 * An inner class that represents a custom list adapter that is used to show the list of messages in the conversation, each image containing serveral items, such as a user image and content text
	 * @author Tom
	 *
	 */
	public class MessageListAdapter extends BaseAdapter {
		private ArrayList<Message> messageList;
		LayoutInflater layoutInflater;
		Context context;
		
		private int chatListFontSize;

		/**
		 * Constructor
		 * @param newConversationsList	An ArrayList of Conversation objects that this adapter will use
		 * @param newContext			The context of the activity that instantiated this adapter
		 */
		MessageListAdapter(Context newContext){
			context = newContext;
			layoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			refresh();
		}
		
		public int getCount() {
			return messageList.size();
		}

		public Object getItem(int position) {
			return messageList.get(position);
		}

		public long getItemId(int position) {
			return position;
		}
		
		public void refresh(){
			messageList = database.getMessagesByConversation(conversation.getId(), 0, nMessagesToShow, true);
			String fontSizeString = preferences.getString("chat_font_size", "16");
			try{
				chatListFontSize = Integer.parseInt(fontSizeString);
			}
			catch(NumberFormatException e){
				chatListFontSize = 16;
			}
			notifyDataSetChanged();
		}
		
		@Override
		public int getItemViewType(int position) {
			int messageType = messageList.get(position).getType();
			int viewType;
			switch(messageType){
			case Message.MESSAGE_TYPE_TEXT:
				viewType = 0;
				break;
			case Message.MESSAGE_TYPE_IMAGE:
				viewType = 1;
				break;
			default:
				viewType = Adapter.IGNORE_ITEM_VIEW_TYPE;
				break;
			}
			return viewType;
		}
		
		@Override
		public int getViewTypeCount() {
		    return 2;		//There are three different layouts that may be used for rows
		}
		
		private String getFormattedDate(Calendar time){
			String formattedDate = "";
			DecimalFormat formatter = new DecimalFormat("00");
			int year = time.get(Calendar.YEAR);		//Year doesn't need formatting, it is always 4 digits (ever since the year 1000 AD)
			String month = formatter.format(time.get(Calendar.MONTH));
			String day = formatter.format(time.get(Calendar.DAY_OF_MONTH));
			formattedDate = day+"/"+month+"/"+year;
			return formattedDate;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			//long startTime = System.currentTimeMillis();
			View view = null;
			boolean convertViewValid = false;

			if(convertView!=null){
				convertViewValid = true;
			}

			//Get the Message object from the list
			Message message = messageList.get(position);

			//Get the data from the message
			String senderId = message.getSenderId();
			int messageType = message.getType();
			int messageStatus = message.getStatus();
			String messageID = message.getId();

			final Contact contact = database.getContact(senderId);
			String senderFirstName;
			
			ImageView userImage, messageImage;
			TextView messageHeading, messageBody;
			
			//Some elements are message-type dependent, so use a switch to run specific code
			switch(messageType){
			case Message.MESSAGE_TYPE_TEXT:				//Standard text message
				if(convertViewValid){
					view = convertView;
				}
				else{
					view = layoutInflater.inflate(R.layout.activity_view_conversation_message_list_item, null);					//Inflate the standard version of the layout
				}
				messageBody = (TextView) view.findViewById(R.id.activity_view_conversation_message_list_item_contents);
				messageBody.setText(message.getContents(null));
				messageBody.setTextSize(chatListFontSize);
				break;				
			case Message.MESSAGE_TYPE_IMAGE:			//Image message
				if(convertViewValid){
					view = convertView;
				}
				else{
					view = layoutInflater.inflate(R.layout.activity_view_conversation_message_list_item_image, null);		//Inflate a list item template for displaying an image
				}
				
				messageImage = (ImageView) view.findViewById(R.id.activity_view_conversation_message_list_item_image);
				String imageResourceId = null;
				//The message is a JSON object containing several fields, one of which is the file name which we will use to get the image
				try {
					JSONObject messageJSON = new JSONObject(message.getContents(null).toString());
					String imagePath = MainActivity.DIRECTORY_RESOURCES+messageJSON.getString("fileName");
					int imageWidth = messageJSON.getInt("width");		//We want the dimensions in order to calculate the aspect ratio of the image
					int imageHeight = messageJSON.getInt("height");
					if(messageJSON.has("resourceId")){
						imageResourceId = messageJSON.getString("resourceId");	//This is used when opening the image gallery
					}
					//The width of the image in DP is already defined in the XML
					int imageWidthDp = 150;
					float imageAspectRatio = (float) imageHeight / (float) imageWidth;		//The aspect ratio of the image
					int imageHeightDp = (int) (imageAspectRatio * imageWidthDp);
					//Now convert the dimensions in DP into dimensions in pixels
					int imageWidthPixels = (int) (imageWidthDp * pixelDensity + 0.5f);		//Image width in pixels
					int imageHeightPixels = (int) (imageHeightDp * pixelDensity + 0.5f);	//Image width in pixels
					String imagePathFull = imagePath+imageWidthPixels+imageHeightPixels;	//For the caching
					Bitmap originalImage = null;
					//Check the bitmap cache exists. If not, reinstantiate it
					if(MainActivity.bitmapCache==null){					//Cache is null
						MainActivity.loadBitmapCache();
					}
					else{												//Cache is not null, so check it to see if this image is in it
						originalImage = MainActivity.bitmapCache.get(imagePathFull);
					}
					messageImage.getLayoutParams().height = imageHeightPixels;
					if(originalImage==null){		//True if the bitmap was not in the cache. So we must load from disk instead
						//Run the AsyncTask on the THREAD_POOL_EXECUTOR, this allows multiple concurrent instances to run in parallel, which is good on multi-core devices
						new Utils.LoadBitmapAsync(imagePath, messageImage, imageWidthPixels, imageHeightPixels, true, MainActivity.bitmapCache).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
					}
					else{
						messageImage.setImageBitmap(originalImage);
					}
				}
				catch (JSONException e) {
					Log.e(TAG, "Error reading image JSON: "+e.toString());
				}
				if(imageResourceId!=null){		//Only attach the listener if we got a valid resource ID
					final String resourceIdFinal = imageResourceId;
					final String conversationIdFinal =  message.getConversationId();
				    messageImage.setOnClickListener(new OnClickListener() {
						@Override
						public void onClick(View v) {
							Intent showConversationImageGalleryIntent = new Intent(context, ViewConversationImageGalleryActivity.class);
							showConversationImageGalleryIntent.putExtra("conversationId", conversationIdFinal);
							showConversationImageGalleryIntent.putExtra("resourceId", resourceIdFinal);
							startActivityForResult(showConversationImageGalleryIntent, ACTION_SHOW_CONVERSATION_IMAGE_GALLERY);
						}
					});
				}
				//If the message is uploading, get a reference to the progress bar in the layout and put it in the HashMap
				if(messageStatus==Message.MESSAGE_STATUS_PENDING){
					//Get the progress bar
					ProgressBar messageProgressBar = (ProgressBar) view.findViewById(R.id.activity_view_conversation_message_list_item_progress_indicator);
					messageProgressBar.setMax(100);			//100% is the max
					messageProgressBar.setProgress(0);		//0% Initially
					messageProgressBar.setVisibility(View.VISIBLE);
					//Store a reference to it in the HashMap, where it can be looked up using the message's ID
					messageProgressBars.put(messageID, messageProgressBar);
				}
				break;
			case Message.MESSAGE_TYPE_INVALID:
			default:
				break;
			}
			
			//Some layout items are present in all layouts. Typically these are the user's image, the message status indicator and the message time
			Calendar messageTime = Calendar.getInstance(Locale.getDefault());
			messageTime.setTimeInMillis((long) message.getTimestamp() * 1000);

			//If this message is from a different day as the previous one, or there is no previous message, then we should show a thin line with the date
			TextView dateStrip = (TextView) view.findViewById(R.id.activity_view_conversation_message_list_item_date_strip);
			
			if(position==0){		//First message, so show the date strip
				dateStrip.setVisibility(View.VISIBLE);
				dateStrip.setText(getFormattedDate(messageTime));
			}
			else{					//Otherwise check this message to the previous
				Message previousMessage = (Message) getItem(position - 1);		//Get the previous message
				Calendar previousMessageTime = Calendar.getInstance(Locale.getDefault());
				previousMessageTime.setTimeInMillis((long) previousMessage.getTimestamp() * 1000);
				if(messageTime.get(Calendar.DAY_OF_YEAR)!=previousMessageTime.get(Calendar.DAY_OF_YEAR)){		//True if the previous message is from a different day of the year (0-365)
					dateStrip.setVisibility(View.VISIBLE);
					dateStrip.setText(getFormattedDate(messageTime));
				}
				else{
					dateStrip.setVisibility(View.GONE);
				}
			}
			
			//Get a reference to the item's layout
			RelativeLayout itemLayout = (RelativeLayout) view.findViewById(R.id.activity_view_conversation_message_list_item_wrapper);
			//If the message is from the local user, give it a subtle grey background
			if(localUserId.equals(message.getSenderId())){
				itemLayout.setBackgroundColor(colourEEEEEE);
			}
			else{
				itemLayout.setBackgroundColor(colourFFFFFF);
			}

			userImage = (ImageView) view.findViewById(R.id.activity_view_conversation_message_list_item_user_image);
			userImage.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					showContactDetails(contact);
					
				}
			});

			messageHeading = (TextView) view.findViewById(R.id.activity_view_conversation_message_list_item_heading);

			if(position>0 && senderId.equals(messageList.get(position-1).getSenderId())){		//True if this is not the first message AND the sender id matches that of the previous message
				userImage.setVisibility(View.GONE);				//Hide both the sender's name and their image
				messageHeading.setVisibility(View.GONE);
			}
			else{
				userImage.setVisibility(View.VISIBLE);
				contact.loadImage(userImage, contactThumbnailSize, contactThumbnailSize);
				messageHeading.setVisibility(View.VISIBLE);
				if(senderId.equals(localUserId)){		//True if the local user sent this message
					senderFirstName = "Me";
				}
				else{
					senderFirstName = contact.getFirstName();
				}
				messageHeading.setText(senderFirstName);
			}
			
			TextView messageTimeText = (TextView) view.findViewById(R.id.activity_view_conversation_message_list_item_time);
			messageTimeText.setText(message.getFormattedTime());
			
			ImageView messageStatusImage = (ImageView) view.findViewById(R.id.activity_view_conversation_message_list_item_ack);
			//Set the status image according to the status of the message. This only applies to messages sent by the local user
			if(message.getSenderId().equals(localUserId)){			//True if the local user sent this message
				switch(messageStatus){
				case Message.MESSAGE_STATUS_PENDING:		//Pending messages should have a red dot
					messageStatusImage.setImageResource(R.drawable.red_dot_8dp);
					messageStatusImage.setVisibility(View.VISIBLE);
					break;
				case Message.MESSAGE_STATUS_ACK_SERVER:		//Messages that reached the server should have an orange dot
					messageStatusImage.setImageResource(R.drawable.orange_dot_8dp);
					messageStatusImage.setVisibility(View.VISIBLE);
					break;
				case Message.MESSAGE_STATUS_ACK_RECIPIENT:	//Messages that reached the recipient should have an green dot
					messageStatusImage.setImageResource(R.drawable.green_dot_8dp);
					messageStatusImage.setVisibility(View.VISIBLE);
					break;
				case Message.MESSAGE_STATUS_NOT_SET:		//Not set typically means the message came from another user, in which case the status image should be hidden
				default:									//Also default here
					messageStatusImage.setVisibility(View.INVISIBLE);
					break;
				}
			}
			else{
				messageStatusImage.setVisibility(View.INVISIBLE);
			}
			//long endTime = System.currentTimeMillis();
			//Log.d(TAG, "GETVIEW TIME: "+(endTime - startTime)+", TYPE: "+viewType);
			
			//This final thing to do is to attach a popup menu to this row. This will be shown when a row is long-pressed, and will display some common options
			createPopupMenu(itemLayout, message);
			return view;
		}
    }
	
	protected void showContactDetails(Contact contact) {
		Intent showContactDetailsIntent = new Intent(fragmentActivity, ViewContactProfileActivity.class);
		showContactDetailsIntent.putExtra("contact", contact);
		startActivity(showContactDetailsIntent);
	}

	private void createPopupMenu(RelativeLayout itemLayout, final Message message) {
		final PopupMenu popupMenu = new PopupMenu(fragmentActivity, itemLayout);
		Menu menu = popupMenu.getMenu();
		//Add various items to the menu. Some items are not always shown 
		if(message.getType()==Message.MESSAGE_TYPE_TEXT){				//If the message contains text, then it can be copied to the clipboard
			menu.add(Menu.NONE, POPUP_MENU_ACTION_COPY_TEXT, Menu.NONE, "Copy text");
		}
		if(message.getStatus()>=Message.MESSAGE_STATUS_ACK_SERVER){		//If the message status is at least ACK_SERVER, that means the server has sent it to a recipient.
																		//This means  it can be forwarded to any other recipient. Messages from other users automatically have the status ACK_RECIPIENT
			menu.add(Menu.NONE, POPUP_MENU_ACTION_FORWARD, Menu.NONE, "Forward");
		}
		else{															//Message hasn't been acked from the server
			menu.add(Menu.NONE, POPUP_MENU_ACTION_RESEND, Menu.NONE, "Resend");
		}

		menu.add(Menu.NONE, POPUP_MENU_ACTION_DELETE, Menu.NONE, "Delete");
		popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			//Called when a popup menu item is touched
			@Override
			public boolean onMenuItemClick(MenuItem menuItem) {
				String toastText = null;
				switch(menuItem.getItemId()){
				case POPUP_MENU_ACTION_COPY_TEXT:
					ClipData messageTextClip = ClipData.newPlainText(TAG_CLIPBOARD_MESSAGE_TEXT, message.getContents(null));
					clipboardManager.setPrimaryClip(messageTextClip);
					toastText = "Text copied";
					break;
				case POPUP_MENU_ACTION_FORWARD:			//Start the message forwarding process. The first step is to choose a recipient
					messageToBeForwarded = message;
					Intent forwardMessageIntent = new Intent(fragmentActivity, ChooseContactActivity.class);
					forwardMessageIntent.putExtra(ChooseContactActivity.EXTRA_TITLE, "Choose contact");
					forwardMessageIntent.putExtra(ChooseContactActivity.EXTRA_SUBTITLE, "Choose a contact to forward this message to");
					startActivityForResult(forwardMessageIntent, ACTION_CHOOSE_CONTACT_FOR_FORWARDING_MESSAGE);
					break;
				case POPUP_MENU_ACTION_RESEND:			//Resend the message
					switch(message.getType()){									//What we do depends on the message type
					case Message.MESSAGE_TYPE_TEXT:		//Text messages are ready to be resent immediately
						sendMessageUsingGCM(message);
						break;
					case Message.MESSAGE_TYPE_IMAGE:	//Image messages need a bit more work
						String imagePath = null, imageCaption = null;
						try {
							JSONObject imageMessageContents = new JSONObject(message.getContents(null).toString());
							if(imageMessageContents.has("fileName")){
								imagePath = MainActivity.DIRECTORY_RESOURCES+imageMessageContents.getString("fileName");
							}
							if(imageMessageContents.has("caption")){
								imageCaption = imageMessageContents.getString("caption");
							}
							if(imagePath!=null && imageCaption!=null){
								sendImageAsMessage(imagePath, imageCaption);
							}
						}
						catch (JSONException e) {
							Log.e(TAG, "Error reading image message contents: "+e.toString());
						}
						break;
					default:
						break;
					}
					break;
				case POPUP_MENU_ACTION_DELETE:
					database.deleteMessage(message, false);
					chatListAdapter.refresh();
					toastText = "Message deleted";
					break;
				default:
					break;
				}
				if(toastText!=null){
					Toast.makeText(fragmentActivity, toastText, Toast.LENGTH_SHORT).show();
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
	/**
	 * Listens for new GCM messages
	 */
	BroadcastReceiver gcmMessageReceiver = new BroadcastReceiver(){
	    @Override
	    public void onReceive(Context context, Intent intent){
    		Message message = (Message) intent.getSerializableExtra("message");
	    	if(message!=null){									//True if a message object was sent with this Intent
	    		int messageType = message.getType();
	    		switch(messageType){							//Do different things depending on the type of the message
	    		case Message.MESSAGE_TYPE_ACK:					//ACK from another device
	    			messageReceivedAck(message);
	    			abortBroadcast();							//We don't want to do anything else with this ACK
	    			break;
	    		case Message.MESSAGE_TYPE_TEXT:					//Standard text message from another user
	    		case Message.MESSAGE_TYPE_IMAGE:				//Image message from another user
	    			//Check if the message is intended for this particular conversation
			    	String newConversationId = message.getConversationId();
			    	if(newConversationId.equals(conversation.getId())){			//True if the message belongs to this conversation
			    		//Refresh the list view
			    		nMessagesToShow++;
			    		chatListAdapter.refresh();
			    		scrollListToBottom();
			    		if(isVisible){								//If the activity is running and visible to the user, do not allow the broadcast to continue (i.e. the Notification Receiver will not receive it)
				    		abortBroadcast();
				    	}
				    	//Get the local user's ID from the preferences. We can't use the static USER_ID field in EpicChatActivity.java as that class may not exist when this activity is invoked from a notification
						preferences = PreferenceManager.getDefaultSharedPreferences(context);
						String localUserId = preferences.getString("userId", null);
						
						//Check if the message was sent from another device by the same user as is logged in on this device. If this is the case, we don't want a notification either
				    	if(message.getSenderId().equals(localUserId)){	//True if the user ids match
				    		abortBroadcast();
				    	}
			    	}
			    	break;
	    		}		    	
	    	}
	    }
	};
	
	final Handler messageImageHandler = new Handler(){
		public void handleMessage(android.os.Message message){
			//Get the message's bundled data
			Bundle messageData = message.getData();
			String messageId;
			ProgressBar messageProgressBar;
			//Switch based on the code of the message
			switch(message.what){
			case UploadFileTask.EVENT_UPLOAD_STARTED:		//The upload just started
				messageId = messageData.getString(UploadFileTask.EXTRA_TAG);		//The tag of the message is the message ID
				messageProgressBar = messageProgressBars.get(messageId);
				if(messageProgressBar!=null){		//This progress bar is available
				}
				break;
			case UploadFileTask.EVENT_UPLOAD_PROGRESS_UPDATED:
				messageId = messageData.getString(UploadFileTask.EXTRA_TAG);		//The tag of the message is the message ID
				//Attempt to get the progress bar for this image upload
				messageProgressBar = messageProgressBars.get(messageId);
				if(messageProgressBar!=null){		//This progress bar is available
					//Retrieve the bytes uploaded and the total bytes from the message
					int bytesUploaded = messageData.getInt(UploadFileTask.EXTRA_BYTES_UPLOADED, -1);
					int bytesTotal = messageData.getInt(UploadFileTask.EXTRA_BYTES_TOTAL, -1);
					int percentComplete = (int) (((float) bytesUploaded / (float) bytesTotal) * 100);
					messageProgressBar.setProgress(percentComplete);
				}
				break;
			case UploadFileTask.EVENT_UPLOAD_FAILED:		//Received when the image failed to be uploaded for some reason
				messageId = messageData.getString(UploadFileTask.EXTRA_TAG);		//The tag of the message is the message ID
				//Attempt to get the progress bar for this image upload
				messageProgressBar = messageProgressBars.get(messageId);
				if(messageProgressBar!=null){		//This progress bar is available
					messageProgressBar.setVisibility(View.INVISIBLE);
					messageProgressBars.remove(messageId);
				}
				database.updateMessageStatus(messageId, Message.MESSAGE_STATUS_FAILED);		//Update the status of this message to "failed"
				break;
			case UploadFileTask.EVENT_UPLOAD_SUCCEEDED:		//Received when the image has been sent to the server
				messageId = messageData.getString(UploadFileTask.EXTRA_TAG);		//The tag of the message is the message ID
				//Attempt to get the progress bar for this image upload
				messageProgressBar = messageProgressBars.get(messageId);
				if(messageProgressBar!=null){		//This progress bar is available
					messageProgressBar.setVisibility(View.INVISIBLE);
					messageProgressBars.remove(messageId);
				}
				String resourceId = null;
				//Get the resource ID 
				String responseString = messageData.getString(UploadFileTask.EXTRA_RESPONSE_STRING, null);
				if(responseString!=null){					//Only continue if there is a valid response
					try {
						JSONObject responseJSON = new JSONObject(responseString);
						if(responseJSON.has("ack")){			//If we got an ACK back then the message was sent
							resourceId = responseJSON.getString("ack");
						}
					}
					catch (JSONException e) {
						Log.e(TAG, "Error reading response: "+e.toString());
					}
				}
				if(resourceId!=null){					//Only continue if there is a valid response
					//At this point, the contents of the message contains a JSON object with the filename, width, and height. We will now add the resource ID and send the message
					try {
				    	JSONObject messageJSON = new JSONObject(pendingImageMessage.getContents(null).toString());
				    	messageJSON.put("resourceId", resourceId);
					    //TODO Smarter update, maybe update using a Message object?
					    pendingImageMessage.updateContents(messageJSON.toString());		//Update the contents of the message and send it
					    database.updateMessageContents(pendingImageMessage.getId(), messageJSON.toString());
						sendMessageUsingGCM(pendingImageMessage);
						//Create an entry in the database for this resource
						int timestamp = (int) (System.currentTimeMillis() / 1000);
						String filePath = MainActivity.DIRECTORY_RESOURCES+messageJSON.getString("fileName");
						String imageCaption = null;
						if(messageJSON.has("caption")){
							imageCaption = messageJSON.getString("caption");
						}
						Resource resource = new Resource(resourceId, Resource.TYPE_IMAGE, timestamp, conversation.getId(), pendingImageMessage.getSenderId(), filePath, imageCaption);
						database.addResource(resource);
					}
				    catch (JSONException e) {
						Log.e(TAG, "Error creating image message: "+e.toString());
					}
				}
				break;
			default:
				break;
			}
		}
	};

	public void clearNotificationAndPendingMessages() {
		if(notificationManager!=null){
			notificationManager.cancel("com.lbros.newMessage."+conversation.getId(), 0x01);
		}
		if(database!=null){
			database.deletePendingMessagesFromConversation(conversation);				//Also remove any pending messages sent from this conversation from the database
		}
	}
}