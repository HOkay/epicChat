package com.lbros.epicchat;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Serializable;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/**
 * Class used to represent contacts in the system. Each Contact has a user ID, a first name, a second name and an optional phone number.
 * This class is serialisable as this allows us to send Contact objects between activities in Intents
 * @author Tom
 *
 */
public class Contact implements Serializable{
	private final String TAG = "Contact";
	
	private static final long serialVersionUID = 1L;	//Needed to guarantee serialisation works properly apparently. Probably just Java being pedantic
	private final String defaultImagePath = MainActivity.DIRECTORY_USER_IMAGES+"default.png";
	
	private String id;
	private String firstName;
	private String lastName;
	private String phoneNumber = null;
	private String imagePath = null;
	
	/**
	 * Constructor
	 * @param newId				The ID of the the contact
	 * @param newFirstName		The contact's first name
	 * @param newLastName		The contact's last name
	 * @param newPhoneNumber	The phone number for the contact. May be null
	 * @param newImagePath		The location of the image to user for this user, relative to the system root. May be null, in which case a default image is used
	 */
	public Contact(String newId, String newFirstName, String newLastName, String newPhoneNumber, String newImagePath){
		id = newId.toLowerCase();
		firstName = newFirstName;
		lastName = newLastName;
		phoneNumber = newPhoneNumber;
		if(newImagePath==null){
			imagePath = defaultImagePath;
		}
		else{
			imagePath = newImagePath;
		}
	}
	
	/**
	 * Returns the contact's ID
	 * @return		The unique ID of the contact
	 */
	public String getId(){
		return id;
	}
	
	/**
	 * Returns the contact's first name
	 * @return		The first name of the contact
	 */
	public String getFirstName(){
		return firstName;
	}
	
	/**
	 * Returns the contact's last name
	 * @return		The last name of the contact
	 */
	public String getLastName(){
		return lastName;
	}
	
	/**
	 * Returns the contact's full name
	 * @return		The combination of the contact's first and last name
	 */
	public String getFullName(){
		return firstName+" "+lastName;
	}
	
	/**
	 * Returns the contact's phone number if set, or null if not
	 * @return		The contact's phone number, or null
	 */
	public String getPhoneNumber(){
		return phoneNumber;
	}
	
	/**
	 * Returns the path of the image used for this contact, relative to the system root
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
				if(imageBitmap!=null && width!=null && height!=null){			//If the height or width properties were set, use them to scale the image
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
}