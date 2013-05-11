package com.lbros.epicchat;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import android.util.Log;


public class HTTP {
	private final static String TAG = "HTTP";
	
	public static String doHttpGet(String address, int maximumRetries) {
		Log.d(TAG, "REQUEST: "+address);
		String responseString = null;

		URL url = null;
		HttpURLConnection urlConnection = null;

		try {
			url = new URL(address);
		}
		catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setDoOutput(true);
			urlConnection.setRequestMethod("GET");

			int responseCode = urlConnection.getResponseCode();
			if(responseCode!=HttpURLConnection.HTTP_OK){		//Response code was not ok, output a log message
				Log.e(TAG, "Error executing GET request, response code was: "+responseCode);
			}
			
			InputStream in = new BufferedInputStream(urlConnection.getInputStream());
			responseString = readStream(in);
		}
		catch (SocketTimeoutException e) {		//Reach here if the server didn't give us a socket. This seems to be a persistent issue on my Synology NAS
			//The policy in this case is to retry the connection, provided we have not already reached the maximum number of retries
			if(maximumRetries>0){			//True if there is still at least one retry left
				Log.d(TAG, "Socket timeout, retrying connection, retries remaining: "+maximumRetries);
				responseString = doHttpGet(address, maximumRetries - 1);
			}
		}
		catch (IOException e) {
			Log.e(TAG, "Error executing GET request: "+e.toString());
		}
		finally {
			urlConnection.disconnect();
		}
		Log.d(TAG, "RESPONSE: "+responseString);
		return responseString;
	}
	
	/**
	 * Executes an HTTP POST request to the specified address, attaching the provided parameters as POST variables
	 * @param address			The address to connect to
	 * @param parameters		A String containing the variables to be sent, in the form: "var1=value1,var2=value2"
	 * @param maximumRetrys		How many times the connecion to be retried before giving up
	 * @return					A string containing the server's response, or null if there was no response
	 */
	public static String doHttpPost(String address, String parameters, int maximumRetries) {
		Log.d(TAG, "REQUEST: "+address);
		String responseString = null;

		URL url = null;
		HttpURLConnection urlConnection = null;

		try {
			url = new URL(address);
		}
		catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setConnectTimeout(2000);
			urlConnection.setReadTimeout(2500);
			
			urlConnection.setDoOutput(true);
			urlConnection.setRequestMethod("POST");
			
			urlConnection.setFixedLengthStreamingMode(parameters.getBytes().length);

			PrintWriter out = new PrintWriter(urlConnection.getOutputStream());
			out.print(parameters);
			out.close();
			
			int responseCode = urlConnection.getResponseCode();
			if(responseCode!=HttpURLConnection.HTTP_OK){		//Response code was not ok, output a log message
				Log.e(TAG, "Error executing POST request, response code was: "+responseCode);
			}
			InputStream in = new BufferedInputStream(urlConnection.getInputStream());
			responseString = readStream(in);
		}
		catch (Exception e) {		//Reach here if the server didn't give us a socket. This seems to be a persistent issue on my Synology NAS
			//The policy in this case is to retry the connection, provided we have not already reached the maximum number of retries
			if(maximumRetries>0){			//True if there is still at least one retry left
				Log.d(TAG, "Exception while executing request, retrying, retries remaining: "+maximumRetries);
				responseString = doHttpPost(address, parameters, maximumRetries - 1);
			}
		}
		finally {
			urlConnection.disconnect();
		}
		Log.d(TAG, "RESPONSE: "+responseString);
		return responseString;
	}

	public static String readStream(InputStream in) throws IOException {
		StringBuilder sb = new StringBuilder();
		BufferedReader r = new BufferedReader(new InputStreamReader(in), 1000);

		for (String line = r.readLine(); line != null; line = r.readLine()) {
			sb.append(line).append("\n");
		}

		in.close();

		return sb.toString();
	}
}