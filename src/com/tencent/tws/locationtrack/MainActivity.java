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
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

public class MainActivity extends BaseActivity {

	private static final String MYMAP = "com.tencent.tws.locationtrack.MyMapActivity";
	private static final String TRACKMODE = "com.tencent.tws.locationtrack.TrackModeActivity";

	private static final String GEOLOCATION = "com.tencent.tws.locationtrack.GeoLocationActivity";
	private static final String TENCENTLOCATION = "com.tencent.tws.locationtrack.TencentLocationActivity";

	private static final String TAG_PKG = "com.tencent.tws.locationtrack.MainActivity";

	/** Called when the activity is first created. */
	public void onCreate(Bundle savedInstanceState) {
		// calls super, sets GUI
		super.onCreate(savedInstanceState);
		GlobalObj.g_appContext = this;
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.mainmenu);

		QDozeManager.getInstance(this).ensureScreenOn(TAG_PKG, true);
		// associates buttons with IDs

		ImageButton mapGeo = (ImageButton) findViewById(R.id.mapButtonGeo);
		ImageButton mapTencent = (ImageButton) findViewById(R.id.mapButtonTencent);
		ImageButton aboutUs = (ImageButton) findViewById(R.id.aboutButton);
		final EditText intervalTime = (EditText) findViewById(R.id.edit_intervalTime);
		final EditText intervalDistance = (EditText) findViewById(R.id.edit_intervalDistance);
		
		// associates listener for Map Selection button
		mapGeo.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				if (TextUtils.isEmpty(intervalTime.getText().toString()) || TextUtils.isEmpty(intervalDistance.getText().toString())) {
					 Toast.makeText(MainActivity.this, "请输入定位时间和距离间隔", Toast.LENGTH_SHORT).show();
				} else {
					//Intent i = new Intent(GEOLOCATION);
					Intent i = new Intent(MainActivity.this,GeoLocationActivity.class);
					i.putExtra("intervalTime",
							Long.parseLong(intervalTime.getText().toString()) * 1000);
					i.putExtra("intervalDistance", Integer.parseInt(intervalDistance.getText().toString()));
					startActivity(i);
				}

			}
		});

		// associates listener for Map Selection button
		mapTencent.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				if (TextUtils.isEmpty(intervalTime.getText().toString()) || TextUtils.isEmpty(intervalDistance.getText().toString())) {
					 Toast.makeText(MainActivity.this, "请输入定位时间和距离间隔", Toast.LENGTH_SHORT).show();
				} else {
					Intent i = new Intent(MainActivity.this,TencentLocationActivity.class);
					i.putExtra("intervalTime",
							Long.parseLong(intervalTime.getText().toString()) * 1000);
					startActivity(i);
				}
			}
		});

		// associates listener for About Us Button
		aboutUs.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
//				Intent i = new Intent(MYMAP);
//				startActivity(i);

			}
		});

	}

}
