package com.tencent.tws.locationtrack;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import com.tencent.map.geolocation.TencentLocationUtils;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.MyContentProvider;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by microzhang on 2015/10/30 at 17:08.
 */
public class LocationService extends Service implements LocationListener {
    private static final String TAG = "LocationService";
    private LocationManager mLocationManager;

    private static final int INTERVAL_TIME = 1000;
    private static final int INTERVAL_DISTANCE = 10;

    //用于记录所有点信息
    private List<Location> listLocations = new ArrayList<>();
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
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");

        //去读取最后一次点的时间
        long lastTime = getLastLocationTime();
        if (lastTime != 0) {
            long currentTime = System.currentTimeMillis();
            long deltTime = currentTime - lastTime;

            long deltMins = deltTime / (1000 * 60);
        }

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        //为获取地理位置信息时设置查询条件
        String bestProvider = mLocationManager.getBestProvider(getCriteria(), true);
        //获取位置信息
        //如果不设置查询要求，getLastKnownLocation方法传人的参数为LocationManager.GPS_PROVIDER
        Location location = mLocationManager.getLastKnownLocation(bestProvider);
        if (location != null) {
            insertAndNotif(location);
        }

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_TIME, INTERVAL_DISTANCE, this);

        //mLocationManager.addGpsStatusListener(statusListener);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        mLocationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {
            insertAndNotif(location);
        }
    }

    private void insertAndNotif(Location location) {
        ContentValues values = new ContentValues();
        values.put(LocationDbHelper.LATITUDE, location.getLatitude());
        values.put(LocationDbHelper.LONGITUDE, location.getLongitude());
        values.put(LocationDbHelper.INS_SPEED, location.getSpeed());
        values.put(LocationDbHelper.BEARING, location.getBearing());
        values.put(LocationDbHelper.ALTITUDE, location.getAltitude());
        values.put(LocationDbHelper.ACCURACY, location.getAccuracy());
        values.put(LocationDbHelper.TIME, System.currentTimeMillis());

        //保存所有历史点记录
        listLocations.add(location);

        if (lastLongitude != 0 && lastLatitude != 0) {
            values.put(LocationDbHelper.DISTANCE, getDistanceBetween2Point(lastLatitude, lastLongitude, location.getLatitude(), location.getLongitude()));
        } else {
            values.put(LocationDbHelper.DISTANCE, 0.0);
        }

        values.put(LocationDbHelper.AVG_SPEED, getAvgSpeed());
        values.put(LocationDbHelper.KCAL, getkcal());

        lastLatitude = location.getLatitude();
        lastLongitude = location.getLongitude();


        Log.i(TAG, "latitude=" + location.getLatitude() + " | " +
                "longitude=" + location.getLongitude() + " | " +
                "ins_speed=" + location.getSpeed() + " | " +
                "bearing=" + location.getBearing() + " | " +
                "altitude=" + location.getAltitude() + " | " +
                "accuracy=" + location.getAccuracy() + " | " +
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


    private long getFirstLocationTime() {
        String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
        long time = 0;
        Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            time = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
        }
        cursor.close();
        Log.i(TAG, "firstTime=" + time);
        return time;
    }

    private long getLastLocationTime() {
        long time = 0;
        String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
        Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);
        if (cursor.moveToLast()) {
            time = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
        }
        cursor.close();
        Log.i(TAG, "lastTime=" + time);
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
        return 60 * getAllDistance() * 1.036 / 1000;
    }

    private double getAvgSpeed() {
        long startTime = getFirstLocationTime();
        long currentTime = System.currentTimeMillis();
        long deltTime = (currentTime - startTime) / 1000;
        Log.i(TAG, "deltTime=" + deltTime + " avg_speed=" + getAllDistance() / deltTime * 3600 / 1000);

        return (getAllDistance() / deltTime) * (3600 / 1000);
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


    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {
        Location location = mLocationManager.getLastKnownLocation(provider);
        if (location != null) {
            insertAndNotif(location);
        }

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    /**
     * 返回查询条件
     *
     * @return
     */
    private Criteria getCriteria() {
        Criteria criteria = new Criteria();
        //设置定位精确度 Criteria.ACCURACY_COARSE比较粗略，Criteria.ACCURACY_FINE则比较精细
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        //设置是否要求速度
        criteria.setSpeedRequired(true);
        // 设置是否允许运营商收费
        criteria.setCostAllowed(false);
        //设置是否需要方位信息
        criteria.setBearingRequired(true);
        //设置是否需要海拔信息
        criteria.setAltitudeRequired(false);
        // 设置对电源的需求
        criteria.setPowerRequirement(Criteria.POWER_LOW);
        return criteria;
    }
}
