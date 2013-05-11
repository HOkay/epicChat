package com.lbros.epicchat;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Class used to represent resources in the system. A resource is a file sent from one user to another, and may be an image, video or any permitted file type
 * This class is serialisable as this allows us to send Contact objects between activities in Intents
 * @author Tom
 *
 */
public class Resource implements Serializable{
	//private final String TAG = "Resource";
	private static final long serialVersionUID = 1L;	//Needed to guarantee serialisation works properly apparently. Probably just Java being pedantic
	
	/**
	 * Type used when a Resource does not have a type explicitly set
	 */
	public static final int TYPE_NOT_SET = -1;
	/**
	 * Type representing an image resource
	 */
	public static final int TYPE_IMAGE = 1;
	/**
	 * Type representing a video resource
	 */
	public static final int TYPE_VIDEO = 2;
	/**
	 * Type representing a file resource
	 */
	public static final int TYPE_FILE = 3;
	
	//Time format info
	private final String messageTimestampTimeFormatString = "HH:mm";
	private SimpleDateFormat messageTimestampTimeFormat;
	
	private String id = null;
	private int type = TYPE_NOT_SET;
	private int timestamp = 0;
	private String conversationId = null;
	private String fromUser = null;
	private String path = null;
	private String text = null;
	
	/**
	 * Constructor
	 * @param newId					The ID of the the resource, which is typically provided by the server
	 * @param newType				The type of the resource
	 * @param newTimestamp			The timestamp at which this resources was created
	 * @param newConversationId		The conversation to which the resource belongs
	 * @param newFromUser			The user ID of the sender
	 * @param newPath				The path to the resource, relative to the system root
	 * @param newText				The text associated with this resource (e.g. a caption for an image resource)
	 **/
	public Resource(String newId, int newType, int newTimestamp, String newConversationId, String newFromUser, String newPath, String newText){
		id = newId.toLowerCase();
		type = newType;
		timestamp = newTimestamp;
		conversationId = newConversationId;
		fromUser = newFromUser;
		path = newPath;
		text = newText;
	}
	
	/**
	 * Returns the resource's ID
	 * @return		The unique ID of the resource
	 */
	public String getId(){
		return id;
	}
	
	/**
	 * Returns the type of the resource
	 * @return		The resource's type, or null if a type was not set
	 */
	public int getType(){
		return type;
	}
	
	/**
	 * Returns the resource's timestamp
	 * @return		The resource's timestamp
	 */
	public int getTimestamp(){
		return timestamp;
	}
	
	/**
	 * Returns the resources's timestamp formatted as a String, in the format: HH:mm
	 * @return		The formatted string
	 */
	public String getFormattedTime(){
		long timestampInMs = ((long) timestamp * 1000);
		messageTimestampTimeFormat = new SimpleDateFormat(messageTimestampTimeFormatString, Locale.getDefault()); 	//Initialise the time object we will use
		messageTimestampTimeFormat.setTimeZone(TimeZone.getDefault());
		String messageSentString = messageTimestampTimeFormat.format(new Date(timestampInMs));
		return messageSentString;
	}
	
	/**
	 * Returns the ID of the conversation to which the resource belongs
	 * @return		The conversation ID associated with the contact
	 */
	public String getConversationId(){
		return conversationId;
	}
	
	/**
	 * Returns the ID of the user who sent this resource
	 * @return		The user ID associated with the contact who sent the resource
	 */
	public String getFromUser(){
		return fromUser;
	}
	
	/**
	 * Returns the path of the resource, relative to the system root
	 * @return		The resource's path, or null if not set
	 */
	public String getPath(){
		return path;
	}
	
	/**
	 * Returns the text belonging to the resource
	 * @return		The resource's path, or null if no text is present
	 */
	public String getText(){
		return text;
	}
}