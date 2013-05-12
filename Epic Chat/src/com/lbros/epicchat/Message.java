package com.lbros.epicchat;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import android.text.Html;
import android.text.Spanned;

/**
 * Class that is used to represent messages. Each message object contains a unique ID, the id of the conversation it belongs to, the user who sent it, the time it was sent, and its contents
 * This class is serialisable as this allows us to send Message objects between activities in Intents
 * @author Tom
 *
 */
public class Message implements Serializable {
	//private final String TAG = "Message";
	static final long serialVersionUID = 1L;	//Needed to guarantee serialisation works properly apparently. Probably just Java being pedantic

	//Various message types, accessible by all classes
	/**
	 * Code for an invalid message (one that has not had a type set)
	 */
	public static final int MESSAGE_TYPE_INVALID = -1;
	/**
	 * Code for a message ACK
	 */
	public static final int MESSAGE_TYPE_ACK = 1;
	/**
	 * Code for a standard message containing text
	 */
	public static final int MESSAGE_TYPE_TEXT = 2;
	/**
	 * Code for a message that contains a reference to an image
	 */
	public static final int MESSAGE_TYPE_IMAGE = 3;
	
	//Various message statuses, accessible by all classes
	/**
	 * Code for a message that does not have a status
	 */
	public static final int MESSAGE_STATUS_NOT_SET = -1;
	/**
	 * Code for a message that has not been sent yet
	 */
	public static final int MESSAGE_STATUS_PENDING = 1;
	/**
	 * Code for a message that has been delivered to the server
	 */
	public static final int MESSAGE_STATUS_ACK_SERVER = 2;
	/**
	 * Code for a message that has been delivered to the intended recipient
	 */
	public static final int MESSAGE_STATUS_ACK_RECIPIENT = 3;	
	/**
	 * Code for a message that failed to be delivered (either to the server or to GCM)
	 */
	public static final int MESSAGE_STATUS_FAILED = 4;	
	
	//Time format info
	private final String messageTimestampTimeFormatString = "HH:mm";
	private SimpleDateFormat messageTimestampTimeFormat;
	
	//Member fields
	private String id;
	private int timestamp;
	private int type = MESSAGE_TYPE_INVALID;
	private int status = MESSAGE_STATUS_PENDING;
	private String conversation;
	private String fromUser;
	private String contents;
	
	/**
	 * Constructor
	 * @param newId				The unique id of the message
	 * @param newTimestamp		The time at which the message was sent (recorded on the sender's device and transmitted with the message). Always in GMT, because we bloody invented time
	 * @param newType			The type of the message. Possible types are provided as static fields in this class
	 * @param newStatus			The status of the message. Possible statuses are provided as static fields in this class
     * @param newConversation	The ID of the conversation to  which the message belongs
     * @param newFromUser		The id of the user that sent the message
     * @param newContents		The actual text. No limit in length but GCM only supports messages up to 4K in length
	 */
	public Message(String newId, int newTimestamp, int newType, int newStatus, String newConversation, String newFromUser, String newContents){
		id = newId;
		timestamp = newTimestamp;
		type = newType;
		status = newStatus;
		conversation = newConversation;
		fromUser = newFromUser;
		contents = newContents;
	}
	
	/**
	 * Updates the contents of the message
	 * @param newContents		The new contents of the message
	 */
	public void updateContents(String newContents){
		contents = newContents;
	}

	/**
	 * Returns the id of the message
	 * @return		Message id
	 */
	public String getId(){
		return id;
	}
	
	/**
	 * Returns the timestamp of the message
	 * @return		Message timestamp, in seconds since Jan 1st 1970
	 */
	public int getTimestamp(){
		return timestamp;
	}
	
	/**
	 * Returns the type of the message
	 * @return		The message's type, which is one of the TYPE fields defined in this class
	 */
	public int getType(){
		return type;
	}
	
	/**
	 * Returns the type of the message
	 * @return		The message's status, which is one of the STATUS fields defined in this class
	 */
	public int getStatus(){
		return status;
	}
	
	/**
	 * Returns the message's timestamp formatted as a String, in the format: HH:mm
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
	 * Returns the set of users involved in the conversation, which is the conversation ID
	 * @return		Conversation ID
	 */
	public String getConversationId(){
		return conversation;
	}
	
	/**
	 * Returns the ID of the user who sent the message
	 * @return		User ID of the sender
	 */
	public String getSenderId(){
		return fromUser;
	}	
	
	/**
	 * Returns the contents of the message
	 * @param maxLength		The maximum number of characters to return, starting from the first character. If null, all characters are returned. If clipping does occur, three dots are appended to the end of the string
	 * @return				A string containing the message contents, optionally clipped to the length specified
	 */
	public CharSequence getContents(Integer maxLength){
		String contentsConcise;
		if(maxLength!=null && contents.length()>maxLength){		//True if a length was specified the contents need clipping to length
			contentsConcise = contents.substring(0,  maxLength)+"...";
		}
		else{									//No clipping required, so just return the full string
			contentsConcise = contents;
		}
		//The contents of the message could be a hyperlink, so style it accordingly if it is
		if(contentsConcise.startsWith("http://")){
			Spanned hyperlink = Html.fromHtml("<a href=\""+contentsConcise+"\">"+contentsConcise+"</a>");
			return hyperlink;
		}
		return contentsConcise;
	}
}