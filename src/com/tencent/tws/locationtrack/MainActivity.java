package com.tencent.tws.locationtrack;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import com.tencent.tws.locationtrack.database.SPUtils;
import com.tencent.tws.widget.BaseActivity;

public class MainActivity extends BaseActivity {

	private static final String TAG = "MainActivity";
	private static final String MYMAP = "com.tencent.tws.locationtrack.MyMapActivity";
	private static final String TRACKMODE = "com.tencent.tws.locationtrack.TrackModeActivity";

	private static final String GEOLOCATION = "com.tencent.tws.locationtrack.GeoLocationActivity";
	private static final String TENCENTLOCATION = "com.tencent.tws.locationtrack.TencentLocationActivity";

	private static final String TAG_PKG = "com.tencent.tws.locationtrack.MainActivity";
	WakeLock mWakeLock;

	private static final int INTERNAL_TIME = 1000;
	private static final int INTERNAL_DISTANCE = 10;

//	private ContentObserverSubClass mContentObserverSubClass;

	/**
	 * Called when the activity is first created.
	 */
	public void onCreate(Bundle savedInstanceState) {
		// calls super, sets GUI
		super.onCreate(savedInstanceState);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.mainmenu);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
		mWakeLock.acquire();
		// associates buttons with IDs

		ImageButton mapGeo = (ImageButton) findViewById(R.id.mapButtonGeo);
//		ImageButton mapTencent = (ImageButton) findViewById(R.id.mapButtonTencent);
//		ImageButton aboutUs = (ImageButton) findViewById(R.id.aboutButton);
//		final EditText intervalTime = (EditText) findViewById(R.id.edit_intervalTime);
//		final EditText intervalDistance = (EditText) findViewById(R.id.edit_intervalDistance);

		// associates listener for Map Selection button
		mapGeo.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
//				if (TextUtils.isEmpty(intervalTime.getText().toString()) || TextUtils.isEmpty(intervalDistance.getText().toString())) {
//					Toast.makeText(MainActivity.this, "请输入定位时间和距离间隔", Toast.LENGTH_SHORT).show();
//				} else {
				//Intent i = new Intent(GEOLOCATION);
				Intent i = new Intent(MainActivity.this, LocationActivity.class);
				i.putExtra("intervalTime", INTERNAL_TIME);
				i.putExtra("intervalDistance", INTERNAL_DISTANCE);
				startActivity(i);
//				}
			}
		});

//		Intent serviceIntent = new Intent(this, TencentLocationService.class);
//		startService(serviceIntent);
//		Log.i("TencentLocationService", "TencentLocationService 启动");

//		initContentObserver();

		// associates listener for Map Selection button
//		mapTencent.setOnClickListener(new View.OnClickListener() {
//
//			public void onClick(View v) {
//				if (TextUtils.isEmpty(intervalTime.getText().toString()) || TextUtils.isEmpty(intervalDistance.getText().toString())) {
//					Toast.makeText(MainActivity.this, "请输入定位时间和距离间隔", Toast.LENGTH_SHORT).show();
//				} else {
//					Intent i = new Intent(MainActivity.this, TencentLocationActivity.class);
//					i.putExtra("intervalTime", Long.parseLong(intervalTime.getText().toString()) * 1000);
//					startActivity(i);
//				}
//			}
//		});

		// associates listener for About Us Button
//		aboutUs.setOnClickListener(new View.OnClickListener() {
//
//			public void onClick(View v) {
////				Intent i = new Intent(MYMAP);
////				startActivity(i);

//			}
//		});

	}

//	private void initContentObserver() {
//		mContentObserverSubClass = new ContentObserverSubClass(new Handler());
//		this.getContentResolver().registerContentObserver(MyContentProvider.CONTENT_URI, true, mContentObserverSubClass);
//	}

	@Override
	protected void onResume() {
		super.onResume();

		if (SPUtils.readSp(getApplicationContext()) != "") {
			Log.i(TAG, "read_sp=" + SPUtils.readSp(getApplicationContext()));

			Intent i = new Intent(MainActivity.this, LocationActivity.class);
			i.putExtra("intervalTime", 1000);
			i.putExtra("intervalDistance", 10);
			startActivity(i);
		}

		if (mWakeLock != null) {
			mWakeLock.acquire();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mWakeLock != null) {
			mWakeLock.release();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mWakeLock != null) {
			mWakeLock.release();
		}
	}

//	private class ContentObserverSubClass extends ContentObserver {
//
//		public ContentObserverSubClass(Handler handler) {
//			super(handler);
//		}
//
//		//采用时间戳避免多次调用onChange( )
//		@Override
//		public void onChange(boolean selfChange) {
//			super.onChange(selfChange);
//			//有数据更新的时候就自行对数据库进行操作 这个时候就是对数据库进行操作了
//			Log.i("kermit","onChange");
//		}
//
//		@Override
//		public boolean deliverSelfNotifications() {
//			return true;
//		}
//	}

}
