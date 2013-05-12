package com.lbros.epicchat;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.Serializable;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class Game implements Serializable{
	private final String TAG = "Game";
	private static final long serialVersionUID = 1L;
	
	//Codes for game genres. Static so they may be used by any class
	public static Integer GENRE_FPS = 1;
	public static Integer GENRE_RTS = 2;
	public static Integer GENRE_RPG = 3;
	
	private final String defaultImagePath = MainActivity.DIRECTORY_USER_IMAGES+"default.png";
	
	private String id;
	private Integer genre;
	private String shortName;
	private String longName;
	private String description;
	private Integer rating;
	private Integer releaseDate;
	private String imagePath;
	
	public Game(String newId, Integer newGenre, String newShortName, String newLongName, String newDescription, Integer newRating, Integer newReleaseDate, String newImagePath){
		id = newId;
		genre = newGenre;
		shortName = newShortName;
		longName = newLongName;
		description = newDescription;
		rating = newRating;
		releaseDate = newReleaseDate;
		if(newImagePath==null){
			imagePath = defaultImagePath;
		}
		else{
			imagePath = newImagePath;
		}
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @return the genre
	 */
	public Integer getGenre() {
		return genre;
	}

	/**
	 * @return the shortName
	 */
	public String getShortName() {
		return shortName;
	}

	/**
	 * @return the longName
	 */
	public String getLongName() {
		return longName;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @return the rating
	 */
	public Integer getRating() {
		return rating;
	}

	/**
	 * @return the releaseDate
	 */
	public Integer getReleaseDate() {
		return releaseDate;
	}

	/**
	 * @return the imagePath
	 */
	public String getImagePath() {
		return imagePath;
	}
	
	/**
	 * Returns a Bitmap of the game's image ready for use with ImageView objects, or null if a bitmap could not be produced for some reason
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
			long startTime = System.currentTimeMillis();
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
			long endTime = System.currentTimeMillis();
			int kB = imageBitmap.getByteCount() / 1024;
			Log.d(TAG, "Image loaded from disk, time = "+(endTime - startTime)+"ms, size: "+kB);
			MainActivity.bitmapCache.put(imagePathFull, imageBitmap);
		}
		if(cornerRadius!=null){		//If the calling class specified a corner radius, round the bitmap
			return Utils.getBitmapWithRoundedCorners(imageBitmap, cornerRadius);
		}
		else return imageBitmap;	//Otherwise return the bitmap as is
	}
}
