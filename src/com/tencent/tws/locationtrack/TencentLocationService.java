package com.tencent.tws.locationtrack;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.util.Log;
import com.tencent.map.geolocation.*;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.MyContentProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by microzhang on 2015/10/30 at 17:08.
 */
public class TencentLocationService extends Service implements TencentLocationListener {
	private static final String TAG = "TencentLocationService";
	private TencentLocationManager mTencentLocationManager;

	private static final int INTERVAL_TIME = 3000;
	//用于记录所有点信息
	private List<TencentLocation> listLocations = new ArrayList<>();
	private double lastLatitude;
	private double lastLongitude;


	//用于过滤数据时候使用
	private BigDecimal lastBigLatitude;
	private BigDecimal lastBigLongitude;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "onCreate");
//		locationManager.addGpsStatusListener(statusListener); GPS 状态相关的
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "onStartCommand");
		mTencentLocationManager = TencentLocationManager.getInstance(this);
		TencentLocationRequest request = TencentLocationRequest.create();
		if (intent != null) {
			request.setInterval(intent.getLongExtra("intervalTime", INTERVAL_TIME));
		} else {
			request.setInterval(INTERVAL_TIME);
		}

		mTencentLocationManager.requestLocationUpdates(request, this);
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mTencentLocationManager.removeUpdates(this);
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

		//保存所有历史点记录
		listLocations.add(tencentLocation);

		if (lastLongitude != 0 && lastLongitude != 0) {
			values.put(LocationDbHelper.DISTANCE, getDistanceBetween2Point(lastLatitude, lastLongitude, tencentLocation.getLatitude(), tencentLocation.getLongitude()));
		} else {
			values.put(LocationDbHelper.DISTANCE, 0.0);
		}

		values.put(LocationDbHelper.AVG_SPEED, getAvgSpeed());
		values.put(LocationDbHelper.KCAL, getkcal());

		lastLatitude = tencentLocation.getLatitude();
		lastLongitude = tencentLocation.getLongitude();


		Log.i(TAG, "latitude=" + tencentLocation.getLatitude() + " | " +
				"longitude=" + tencentLocation.getLongitude() + " | " +
				"ins_speed=" + tencentLocation.getSpeed() + " | " +
				"bearing=" + tencentLocation.getBearing() + " | " +
				"altitude=" + tencentLocation.getAltitude() + " | " +
				"accuracy=" + tencentLocation.getAccuracy() + " | " +
				"times=" + System.currentTimeMillis() + " | " +
				"allDistance=" + getAllDistance() + " | " +
				"avg_speed=" + getAvgSpeed() + " | " +
				"kcal=" + getkcal());

		getContentResolver().insert(MyContentProvider.CONTENT_URI, values);

		//过滤数据并进行插入操作
//		if (filter(tencentLocation.getLongitude(), tencentLocation.getLatitude())) {//插入数据
//			Log.i(TAG, "insert data");
//			getContentResolver().insert(MyContentProvider.CONTENT_URI, values);
//		} else {//更新数据
//			Log.i(TAG, "update data");
//			getContentResolver().update(MyContentProvider.CONTENT_URI, values, null, null);
//		}

		//通知观察者数据更新
		this.getContentResolver().notifyChange(MyContentProvider.CONTENT_URI, null);
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
		cursor.close();
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

	private double getDistanceBetween2Point(double lastLatitude, double lastLongitude, double newLatitude, double newLongitude) {
		return TencentLocationUtils.distanceBetween(lastLatitude, lastLongitude, newLatitude, newLongitude);
	}

	private double getAllDistance() {
		double allDistance = 0;
		String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
		Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);
		if (cursor.moveToFirst()) {
			for (int i = 0; i < cursor.getCount(); i++) {
				cursor.moveToPosition(i);
				allDistance += cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
			}
		}
		cursor.close();
		return allDistance;
	}

	private double getkcal() {
		return 60 * getAllDistance() * 1.036;
	}

	private double getAvgSpeed() {
		long startTime = getFirstLocationTime();
		long currentTime = System.currentTimeMillis();
		long deltTime = (currentTime - startTime) / 1000;
		Log.i(TAG, "deltTime=" + deltTime + " avg_speed=" + getAllDistance() / deltTime * 3600 / 1000);

		return getAllDistance() / deltTime * 3600 / 1000;
	}

	private int getLastId() {
		int id = 0;
		Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, null, null, null, null);
		if (cursor.moveToLast()) {
			id = cursor.getInt(cursor.getColumnIndex(LocationDbHelper.ID));
		}
		return id;
	}

	private boolean filter(double longitude, double latitude) {
		BigDecimal mylongitude = (new BigDecimal(longitude)).setScale(5, BigDecimal.ROUND_HALF_UP);
		BigDecimal mylatitude = (new BigDecimal(latitude)).setScale(5, BigDecimal.ROUND_HALF_UP);

		if (lastBigLatitude != null && lastBigLongitude != null) {
			if (mylatitude.equals(lastBigLatitude) && mylongitude.equals(lastBigLongitude)) {
				return false;
			}
		}
		lastBigLatitude = mylatitude;
		lastBigLongitude = mylongitude;
		return true;
	}
}
