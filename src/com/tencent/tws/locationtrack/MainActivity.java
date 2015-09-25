package com.tencent.tws.locationtrack;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

public class MainActivity extends Activity {
	
    private static final String MYMAP = "com.tencent.tws.locationtrack.MyMapActivity";
	private static final String TRACKMODE = "com.tencent.tws.locationtrack.TrackModeActivity";

	
	/** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
    	//calls super, sets GUI
        super.onCreate(savedInstanceState);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.mainmenu);
        
        //associates buttons with IDs
        
        ImageButton mapMenu = (ImageButton) findViewById(R.id.mapButton);
        ImageButton aboutUs = (ImageButton) findViewById(R.id.aboutButton);
                
        //associates listener for Map Selection button
        mapMenu.setOnClickListener(new View.OnClickListener(){

			public void onClick(View v) {
		      Intent i = new Intent(MYMAP);
              startActivity(i);
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
