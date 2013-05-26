package com.lbros.epicchat;


import java.util.ArrayList;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnShowListener;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class GamesListFragment extends Fragment {
	//private final String TAG = "GamesListFragment";
	
	private Database database;
	
	private RelativeLayout fragmentLayout;
	private FragmentActivity parentActivity;
	
	private ListView gamesList;
	
	private GamesListAdapter gamesListAdapter;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		parentActivity = (FragmentActivity) super.getActivity();
		fragmentLayout = (RelativeLayout) inflater.inflate(R.layout.fragment_games_list, container, false);
		
		database = new Database(parentActivity);		//Connect to the SQLite database

		gamesList = (ListView) fragmentLayout.findViewById(R.id.fragment_games_listview);
		
		gamesListAdapter = new GamesListAdapter(parentActivity);
		
		gamesList.setAdapter(gamesListAdapter);
		
		gamesList.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				Game gameClicked = gamesListAdapter.getItem(position);
				showGameMenuDialog(gameClicked);
			}
		});
        
		return fragmentLayout;
	}
	
	//Displays a dialog box that shows the selected image to the user, along with a text box for adding a caption to the message 
	private void showGameMenuDialog(final Game game) {
		//Create the dialog
		final Dialog imagePreviewDialog = new Dialog(parentActivity);
		imagePreviewDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		imagePreviewDialog.setContentView(R.layout.dialog_game_menu);
		imagePreviewDialog.setOnShowListener(new OnShowListener() {
			@Override
			public void onShow(final DialogInterface dialog) {
				//Increase the amount of background dimming
				WindowManager.LayoutParams params = imagePreviewDialog.getWindow().getAttributes();
				params.dimAmount = 0.8f;
				imagePreviewDialog.getWindow().setAttributes(params);
				ImageView userImage = (ImageView) imagePreviewDialog.findViewById(R.id.dialog_game_menu_image);
				userImage.setImageBitmap(game.getImageBitmap(600, 600, null));
				TextView userName = (TextView) imagePreviewDialog.findViewById(R.id.dialog_game_menu_name);
				userName.setText(game.getLongName());
				Button buttonChat = (Button) imagePreviewDialog.findViewById(R.id.dialog_game_menu_button_invite);
				buttonChat.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						Intent inviteDetailsIntent = new Intent(parentActivity, CreateGameInviteActivity.class);
						startActivity(inviteDetailsIntent);
						dialog.dismiss();
					}
				});
				Button buttonViewProfile = (Button) imagePreviewDialog.findViewById(R.id.dialog_game_menu_button_info);
				buttonViewProfile.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						dialog.dismiss();
					}
				});
				Button buttonRemove = (Button) imagePreviewDialog.findViewById(R.id.dialog_game_menu_button_remove);
				buttonRemove.setOnClickListener(new View.OnClickListener() {
					public void onClick(View v) {
						dialog.dismiss();						
					}
				});
			}
		});
		imagePreviewDialog.show();
	}
	
	/**
	 * An inner class that represents a custom list adapter that is used to show a list of ongoing conversations, each with an image and a name
	 * @author Tom
	 *
	 */
	public class GamesListAdapter extends BaseAdapter {
		private ArrayList<Game> gamesList;
		Context context;
		
		/**
		 * Constructor
		 * @param newConversationsList	An ArrayList of Conversation objects that this adapter will use
		 * @param newContext			The context of the activity that instantiated this adapter
		 */
		GamesListAdapter (Context newContext){
			context = newContext;
			refresh();
		}
		
		private void refresh() {
			//Get the list of contacts from the database
			gamesList = database.getAllGames(null, null);
			notifyDataSetChanged();
		}

		public int getCount() {
			return gamesList.size();
		}

		public Game getItem(int position) {
			return gamesList.get(position);
		}

		public long getItemId(int position) {
			return position;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			Game newGame = gamesList.get(position);
			
			if (view==null){
				LayoutInflater vi = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				view = vi.inflate(R.layout.fragment_games_list_list_item, null);
			}
			
			TextView gameTitleText = (TextView) view.findViewById(R.id.fragment_games_list_list_item_text);
			ImageView gameImageThumbnail = (ImageView) view.findViewById(R.id.fragment_games_list_list_item_image);
			
			gameImageThumbnail.setImageBitmap(newGame.getImageBitmap(null, null, null));
			
			gameTitleText.setText(newGame.getLongName());
			return view;
		}
	}
}