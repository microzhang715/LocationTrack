package com.tencent.tws.locationtrack;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import com.tencent.tws.framework.global.GlobalObj;
import com.tencent.tws.qdozemanager.QDozeManager;
import com.tencent.tws.widget.BaseActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

public class MainActivity extends BaseActivity {
	
    private static final String MYMAP = "com.tencent.tws.locationtrack.MyMapActivity";
	private static final String TRACKMODE = "com.tencent.tws.locationtrack.TrackModeActivity";

	private static final String TAG_PKG = "com.tencent.tws.locationtrack.MainActivity";
	
	/** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
    	//calls super, sets GUI
        super.onCreate(savedInstanceState);
		GlobalObj.g_appContext = this;
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.mainmenu);
        
        
    	QDozeManager.getInstance(this).ensureScreenOn(TAG_PKG, true);
        //associates buttons with IDs
        
        ImageButton mapMenu = (ImageButton) findViewById(R.id.mapButton);
        ImageButton aboutUs = (ImageButton) findViewById(R.id.aboutButton);
                
        //associates listener for Map Selection button
        mapMenu.setOnClickListener(new View.OnClickListener(){

			public void onClick(View v) {
//		      Intent i = new Intent(MYMAP);
//              startActivity(i);
			}
        });
        
        //associates listener for About Us Button
        aboutUs.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				Intent i = new Intent(TRACKMODE);
				startActivity(i);
				
			}
		});




    }



}
