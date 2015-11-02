package com.tencent.tws.locationtrack;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.util.Log;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.MyContentProvider;

/**
 * Created by microzhang on 2015/10/30 at 17:08.
 */
public class LocationService extends Service implements TencentLocationListener {
	private static final String TAG = "LocationService";
	private TencentLocationManager mLocationManager;
//	Cursor cursor = null;

	private double tmpLatitude = 0;
	private double tmpLongitude = 0;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "onCreate");
		//locationManager.addGpsStatusListener(statusListener); GPS 状态相关的
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "onStartCommand");
		mLocationManager = TencentLocationManager.getInstance(this);
		TencentLocationRequest request = TencentLocationRequest.create();
		request.setInterval(intent.getLongExtra("intervalTime", 1000));
		mLocationManager.requestLocationUpdates(request, this);
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mLocationManager.removeUpdates(this);
	}

	@Override
	public void onLocationChanged(TencentLocation tencentLocation, int i, String s) {
		ContentValues values = new ContentValues();
		values.put(LocationDbHelper.LATITUDE, tencentLocation.getLatitude());
		values.put(LocationDbHelper.LONGITUDE, tencentLocation.getLongitude());
		values.put(LocationDbHelper.INS_SPEED, tencentLocation.getSpeed());
		values.put(LocationDbHelper.BEARING, tencentLocation.getBearing());
		values.put(LocationDbHelper.ALTITUDE, tencentLocation.getAltitude());
		values.put(LocationDbHelper.ACCURACY, tencentLocation.getAccuracy());
		values.put(LocationDbHelper.TIME, System.currentTimeMillis());

		//计算出来的数值
		values.put(LocationDbHelper.DISTANCE, getDistance());
		values.put(LocationDbHelper.AVG_SPEED, getAvgSpeed());
		values.put(LocationDbHelper.KCAL, getkcal());

		Log.i(TAG, "latitude=" + tencentLocation.getLatitude() + " | " +
				"longitude=" + tencentLocation.getLongitude() + " | " +
				"ins_speed=" + tencentLocation.getSpeed() + " | " +
				"bearing=" + tencentLocation.getBearing() + " | " +
				"altitude=" + tencentLocation.getAltitude() + " | " +
				"accuracy=" + tencentLocation.getAccuracy() + " | " +
				"times=" + System.currentTimeMillis() + " | " +
				"distance=" + getDistance() + " | " +
				"avg_speed=" + getAvgSpeed() + " | " +
				"kcal=" + getkcal());


		Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, null, null, null, null);
		if (cursor.moveToLast()) {
			tmpLatitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
			tmpLongitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
		}

		if (tmpLatitude == tencentLocation.getLatitude() && tmpLongitude == tencentLocation.getLongitude()) {
			Log.i(TAG, "update db");
			getContentResolver().update(MyContentProvider.CONTENT_URI, values, null, null);
		} else {
			Log.i(TAG, "insert db");
			getContentResolver().insert(MyContentProvider.CONTENT_URI, values);
		}

		this.getContentResolver().notifyChange(MyContentProvider.CONTENT_URI, null);
		cursor.close();
	}

	@Override
	public void onStatusUpdate(String s, int i, String s1) {

	}

	private long getFirstLocationTime() {
		String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
		long time = 0;
		Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);
		if (cursor != null && cursor.moveToFirst()) {
			time = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
		}
		return time;
	}

	private long getLastTime() {
		long time = 0;
		Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, null, null, null, null);
		if (cursor.moveToLast()) {
			time = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
		}
		return time;
	}

	private float getDistance() {
		float allDistance = 0;
		Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, null, null, null, null);
		if (cursor != null && cursor.moveToFirst()) {
			for (int i = 0; i < cursor.getCount(); i++) {
				float tmpDistance = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
				allDistance += tmpDistance;
			}
		}
		cursor.close();
		return allDistance;
	}

	private float getkcal() {
		return 60 * getDistance() * 1.036f;
	}

	private float getAvgSpeed() {
		long startTime = getFirstLocationTime();
		long currentTime = System.currentTimeMillis();
		float avgSpeed = getDistance() / (currentTime - startTime) * 3600 / 1000;
		return avgSpeed;
	}

	private int getLastId() {
		int id = 0;
		Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, null, null, null, null);
		if (cursor.moveToLast()) {
			id = cursor.getInt(cursor.getColumnIndex(LocationDbHelper.ID));
		}
		return id;
	}
}
