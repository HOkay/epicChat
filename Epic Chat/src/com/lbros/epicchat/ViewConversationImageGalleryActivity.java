package com.lbros.epicchat;

import java.util.ArrayList;

import android.app.ActionBar;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.AnimationUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ShareActionProvider;
import android.widget.TextView;

public class ViewConversationImageGalleryActivity extends FragmentActivity {
	//private final String TAG = "ViewConversationImageGalleryActivity";
	
	private Database database;
	
	private String resourceId;
	private String conversationId;

	private NotificationManager notificationManager;

	private ActionBar actionBar;
	private ShareActionProvider actionBarShareWidget;

	private Handler interfaceHandler;
	
	private ImagePagerAdapter mainImageAdapter;
	private ViewPager mainImage;
	private RelativeLayout bottomPanel;
	private TextView mainImageCaption;
	
	private boolean uiVisible = true;
	
	private ArrayList<Resource> imageList;
	private ArrayList<String> imageResourceIds;
	
	private int mainImageWidth;
    private int mainImageHeight;
    
    private int resourceIndex;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		interfaceHandler = new Handler();
		
		database = new Database(this);

		//Get the conversation ID and the resource ID of the image to show first form the Intent extras
		Bundle extras = getIntent().getExtras();
		resourceId = extras.getString("resourceId", null);
		conversationId = extras.getString("conversationId", null);
		
		imageList = database.getResourcesByConversation(conversationId, null, null);
		//Build a list of resource IDs, this will be used for searching later on
		imageResourceIds = new ArrayList<String>();
		int nImages = imageList.size();
		for(int i=0; i<nImages; i++){
			imageResourceIds.add(imageList.get(i).getId());
		}

		//Setup the UI
		setContentView(R.layout.activity_conversation_image_gallery);		
		actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		
		mainImage = (ViewPager) findViewById(R.id.activity_conversation_image_gallery_main_image_pager);
		mainImageAdapter = new ImagePagerAdapter(getSupportFragmentManager());
		mainImage.setAdapter(mainImageAdapter);
		mainImage.setPageMargin(20);
		mainImage.setOnPageChangeListener(imageChangedListener);		//This listener listens for when the contents of this pager change
		mainImage.setPageTransformer(false, new ZoomOutPageTransformer(0.9f, 0.6f));
		
		bottomPanel = (RelativeLayout) findViewById(R.id.activity_conversation_image_gallery_bottom_panel);
		mainImageCaption = (TextView) findViewById(R.id.activity_conversation_image_gallery_main_image_caption);
		
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		mainImageWidth = metrics.widthPixels;
        mainImageHeight = metrics.heightPixels;
		
		//Get the index of the resource that was touched to launch this activity
		resourceIndex = imageResourceIds.indexOf(resourceId);
		mainImage.setCurrentItem(resourceIndex, true);
		
		//interfaceHandler.postDelayed(hideUIAfterDelay, 2000);	//Post a new task that will hide the UI after this time

		//Setting these flags here allows us to have a full screen window (with lights-out navigation) but still allows the use of the Action Bar
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
		
		//Get the notification manager
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_view_conversation_image_gallery, menu);
		// Locate MenuItem with ShareActionProvider
	    MenuItem shareWidget = menu.findItem(R.id.menu_item_share);
	    
	    // Fetch and store ShareActionProvider
	    actionBarShareWidget = (ShareActionProvider) shareWidget.getActionProvider();
	    //For some reason this method is not called until after onCreate, onStart or onResume, so we have to update the action bar here, once we know that the share widget is not null
	    if(resourceIndex!=-1){
	    	updateActionBar(imageList.get(resourceIndex));
	    }
		return true;
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		//The user may have just turned on the screen and shown this chat, and a notification for this chat may exist in the notification bar. In this case, we should remove the notification
		notificationManager.cancel("com.lbros.newMessage."+conversationId, 0x01);
		Conversation conversation = database.getConversation(conversationId);
		if(conversation!=null){
			database.deletePendingMessagesFromConversation(conversation);				//Also remove any pending messages sent from this conversation from the database
		}
	}
	
	public class ImagePagerAdapter extends FragmentStatePagerAdapter {
		Resource imageBeingShown;

        public ImagePagerAdapter(FragmentManager fragmentManager) {
            super(fragmentManager);
        }

        @Override
        public int getCount() {
            return imageList.size();
        }

        @Override
        public Fragment getItem(int position) {
        	//Get a reference to the image currently being shown
        	imageBeingShown = imageList.get(position);
        	return ImageDetailFragment.newInstance(imageBeingShown, uiTouchedListener, mainImageWidth, mainImageHeight);
        }
    }
	
	private void updateActionBar(Resource imageResource){
		//Update the intent for the action bar share widget
		if (actionBarShareWidget!=null) {
			Intent shareIntent = new Intent(Intent.ACTION_SEND);
			Uri imagePath = Uri.parse("file://"+imageResource.getPath());
			shareIntent.putExtra(Intent.EXTRA_STREAM, imagePath);
			shareIntent.setType("image/jpg");
			//shareIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
			//shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); 
			actionBarShareWidget.setShareIntent(shareIntent);
	    }
		//We want to show who sent the message and at what time
		String senderId = imageResource.getFromUser();
		if(senderId!=null){				//True if we got the sender ID
	    	Contact sender = database.getContact(senderId);
	    	actionBar.setTitle(sender.getFirstName());
	    	String subTitle = "Sent at "+imageResource.getFormattedTime();
	    	actionBar.setSubtitle(subTitle);
	    	//Also update the image
			Bitmap conversationImage = sender.getImageBitmap(100, 100, null);
	    	Drawable imageDrawable = new BitmapDrawable(getResources(), conversationImage);
	    	actionBar.setIcon(imageDrawable);
		}
		//Also get the image's caption, if one is available
		String messageText = imageResource.getText();
		if(messageText==null){
			messageText = "";
		}
		mainImageCaption.setText(messageText);
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
	
	public static class ImageDetailFragment extends Fragment {
		private final String TAG = "ImageDetailFragment";
		//These tags are used when placing values in the data Bundle which we return to the calling activity, and when retrieving these values
	    private static final String EXTRA_IMAGE_PATH = "imagePath";
	    
	    private String imagePath;
	    private ImageView imageView;
	    private static OnClickListener uiTouchedListener;
	    private static int imageWidth;
	    private static int imageHeight;

	    static ImageDetailFragment newInstance(Resource imageResource, OnClickListener newUiTouchedListener, int newImageWidth, int newImageHeight) {
	        final ImageDetailFragment fragmentToReturn = new ImageDetailFragment();
	        uiTouchedListener = newUiTouchedListener;
	        imageWidth = newImageWidth;
	        imageHeight = newImageHeight;
	        final Bundle data = new Bundle();
	        data.putString(EXTRA_IMAGE_PATH, imageResource.getPath());
	        Log.d("STUFF", "IMAGE PATH: "+imageResource.getPath());
	        //data.putString(EXTRA_IMAGE_SENDER, imageResource.getFromUser());
	        fragmentToReturn.setArguments(data);
	        return fragmentToReturn;
	    }

	    @Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        //Retrieve the data we need
	        Bundle data = getArguments();
	        imagePath = data.getString(EXTRA_IMAGE_PATH, null);
	        //imageSender = data.getString(EXTRA_IMAGE_SENDER, null);
	    }

	    @Override
	    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	        //image_detail_fragment.xml contains just an ImageView into which we will load the image
	        final View v = inflater.inflate(R.layout.activity_conversation_image_gallery_fragment, container, false);
	        imageView = (ImageView) v.findViewById(R.id.activity_conversation_image_gallery_fragment_image);
	        imageView.setOnClickListener(uiTouchedListener);
	        Log.d(TAG, "Created");
	        return v;
	    }

	    @Override
	    public void onActivityCreated(Bundle savedInstanceState) {
	        super.onActivityCreated(savedInstanceState);
	        //Load the bitmap on the background thread
	        new Utils.LoadBitmapAsync(imagePath, imageView, imageWidth, imageHeight, true, null).execute();
	    }
	    
	    @Override
	    public void onDestroy(){
	    	super.onDestroy();
	    	Log.d(TAG, "Destroyed");
	    }
	}

	Runnable hideUIAfterDelay = new Runnable() {		
		@Override
		public void run() {
			setUIVisibility(false);
		}
	};
	
	protected void setUIVisibility(boolean visible) {
		if(visible){
			actionBar.show();
			bottomPanel.setAnimation(AnimationUtils.loadAnimation(this, R.anim.playback_controls_slide_up));
			bottomPanel.setVisibility(View.VISIBLE);
		}
		else{
			actionBar.hide();
			bottomPanel.setAnimation(AnimationUtils.loadAnimation(this, R.anim.playback_controls_slide_down));
			bottomPanel.setVisibility(View.GONE);
			
		}
		uiVisible = visible;
	}
	
	private ViewPager.OnPageChangeListener imageChangedListener = new OnPageChangeListener() {
		@Override
		public void onPageSelected(int position) {
			updateActionBar(imageList.get(position));
		}

		@Override
		public void onPageScrollStateChanged(int arg0) {}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2) {}
	};
	
	private View.OnClickListener uiTouchedListener = new View.OnClickListener() {		
		@Override
		public void onClick(View v) {			
			if(uiVisible){
				setUIVisibility(false);
			}
			else{
				setUIVisibility(true);
			}
			interfaceHandler.removeCallbacks(hideUIAfterDelay);		//Cancel any previous pending callbacks
		}
	};
}