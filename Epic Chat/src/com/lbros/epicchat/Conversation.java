package com.lbros.epicchat;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

/**
 * A class that represents a conversation within the application
 * @author Tom
 *
 */
public class Conversation implements Serializable{
	private final String TAG = "Conversation";
	
	private static final long serialVersionUID = 1L;	//Needed to guarantee serialisation works properly apparently. Probably just Java being pedantic
	private final String defaultImagePath = MainActivity.DIRECTORY_USER_IMAGES+"default.png";

	private String conversationId;
	private String imagePath = null;

	public Conversation(String newConversationId, String newImagePath){
		//Sort the conversation ID
		conversationId = sortConversationId(newConversationId);
		if(newImagePath==null){
			imagePath = defaultImagePath;
		}
		else{
			imagePath = newImagePath;
		}
	}
	
	/**
	 * Returns the conversation's ID
	 * @return		The unique ID of the conversation
	 */
	public String getId(){
		return conversationId;
	}
	
	/**
	 * Returns 		Returns the list of user IDs involved in this conversation
	 * @return		An ArrayList of Strings, where each entry is a user ID of a user involved in this conversation
	 */
	public ArrayList<String> getUserList(){
		ArrayList<String> userList = new ArrayList<String>();
		String[] userArray = getId().split(",");
		int arrayLength = userArray.length;
		for(int i=0; i<arrayLength; i++){
			userList.add(userArray[i]);
		}
		return userList;
	}
	
	/**
	 * Returns the path of the image used for this conversation
	 * @return
	 */
	public String getImagePath(){
		return imagePath;
	}
	
	/**
	 * Returns a Bitmap of the contact's image ready for use with ImageView objects, or null if a bitmap could not be produced for some reason
	 * This method uses bitmap caching to improve performance. The cache is a static global LruCache located in the MainActivity, so that all classes may share it
	 * @param width			The width of the image
	 * @param height		The height of the image
	 * @param cornerRadius	The desired radius of the bitmap's corners. Set to null for no rounding
	 * @return				A Bitmap image, with dimensions as specified, or original size if not
	 */
	public Bitmap getImageBitmap(Integer width, Integer height, Integer cornerRadius){
		Bitmap imageBitmap = null;
		String imagePathFull = imagePath+width+height;		//The path to reference the bitmap to in the cache contains the dimensions, so that multiple copies of the same image (but at different resolutions) may be cached simultaneously
		//First, attempt to retreive the bitmap from the cache
		//Check the cache exists. If not, reinstantiate it
		if(MainActivity.bitmapCache==null){					//Cache is null
			MainActivity.loadBitmapCache();
		}
		else{												//Cache is not null, so check it to see if this image is in it
			imageBitmap = MainActivity.bitmapCache.get(imagePathFull);
		}
		if(imageBitmap==null){		//True if the bitmap was not in the cache. So we must load from disk instead
			//Open the file
			FileInputStream imageInputStream;
			try {
				imageInputStream = new FileInputStream(imagePath);
				imageBitmap = BitmapFactory.decodeStream(imageInputStream);
				if(imageBitmap!=null && width!=null && height!=null){
					imageBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
				}
			}
			catch (FileNotFoundException e) {
				Log.e(TAG, "Error opening image input stream: "+e.toString());
			}
			//int kB = 0;
			if(imageBitmap!=null){
				//kB = imageBitmap.getByteCount() / 1024;
				MainActivity.bitmapCache.put(imagePathFull, imageBitmap);
			}
			//Log.d(TAG, "Image loaded from disk, time = "+(endTime - startTime)+"ms, size: "+kB);
		}
		
		if(cornerRadius!=null && imageBitmap!=null){		//If the calling class specified a corner radius, round the bitmap
			return Utils.getBitmapWithRoundedCorners(imageBitmap, cornerRadius);
		}
		else return imageBitmap;	//Otherwise return the bitmap as is
	}
	
	/**
	 * Sorts a conversation ID (a comma-separated string of user ids) alphabetically. Static so that other classes may use it
	 * @param newConversationId		The unsorted conversation ID
	 * @return						The sorted conversation ID
	 */
	public static String sortConversationId(String newConversationId) {
		String[] users =  newConversationId.split(",");
		Arrays.sort(users);		
		String sortedConversationId = TextUtils.join(",", users);
		return sortedConversationId;
	}
	
	/**
	 * Loads the conversation's image into the provided ImageView. If the image is not found in the cache, it is loaded in the background to keep the UI snappy
	 * @param imageView			The ImageView into which the image should be loaded
	 * @param width				The width of the image
	 * @param height			The height of the image
	 */
	public void loadImage(ImageView imageView, Integer width, Integer height){
		Bitmap imageBitmap = null;
		String imagePathFull = imagePath+width+height;		//The path to reference the bitmap to in the cache contains the dimensions, so that multiple copies of the same image (but at different resolutions) may be cached simultaneously
		//First, attempt to retreive the bitmap from the cache
		//Check the cache exists. If not, reinstantiate it
		if(MainActivity.bitmapCache==null){					//Cache is null
			MainActivity.loadBitmapCache();
		}
		else{												//Cache is not null, so check it to see if this image is in it
			imageBitmap = MainActivity.bitmapCache.get(imagePathFull);
		}
		if(imageBitmap==null){		//True if the bitmap was not in the cache. So we must load it in the background
			new Utils.LoadBitmapAsync(imagePath, imageView, width, height, true, MainActivity.bitmapCache).execute();
		}
		else{
			imageView.setImageBitmap(imageBitmap);
		}
	}
}