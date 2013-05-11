package com.lbros.epicchat;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

public class GamesListFragment extends Fragment {
	//private final String TAG = "GamesListFragment";
	
	Database database;
	
	private RelativeLayout fragmentLayout;
	private FragmentActivity fragmentActivity;

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		fragmentActivity = (FragmentActivity) super.getActivity();
		fragmentLayout = (RelativeLayout) inflater.inflate(R.layout.fragment_games_list, container, false);

        database = new Database(fragmentActivity);		//Connect to the SQLite database

		return fragmentLayout;
	}
}