package com.tencent.tws.locationtrack;

import android.app.Service;
import android.content.*;
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
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by microzhang on 2015/10/30 at 17:08.
 */
public class LocationService extends Service implements LocationListener {
    private static final String TAG = "LocationService";
    private LocationManager mLocationManager;

    private static final int INTERVAL_TIME_SCREEN_ON = 1000;
    private static final int INTERVAL_DISTANCE_SCREEN_ON = 1;
    private static final int INTERVAL_TIME_SCREEN_OFF = 5000;
    private static final int INTERVAL_DISTANCE_SCREEN_OFF = 10;

    //用于记录所有点信息
    private Queue<Location> locationQueue = new LinkedList<Location>();
    private Queue<Location> templocationQueue = new LinkedList<Location>();
    public static final int LOCATION_QUEUE_SIZE = 3;

    private ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService fixedThreadExecutor = Executors.newFixedThreadPool(2);

    private double lastLatitude;
    private double lastLongitude;
    //第一个点的起始时间
    private long firstLocationStartTime = 0;
    //上报前最后一个点的结束时间
    private long lastLocationStartTime = 0;
    //总共的距离
    private long allDistance = 0;

    //总共的距离
    private Location insertSingalPoint;

    //用于过滤数据时候使用
    private BigDecimal lastBigLatitude;
    private BigDecimal lastBigLongitude;
    private static final int SCALE = 5;

    public static Location extLocation = null;

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

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        //为获取地理位置信息时设置查询条件
        String bestProvider = mLocationManager.getBestProvider(getCriteria(), true);
        //如果不设置查询要求，getLastKnownLocation方法传人的参数为LocationManager.GPS_PROVIDER
        Location location = mLocationManager.getLastKnownLocation(bestProvider);
        if (location != null) {
            insertNotif(location);
        }

        //注册亮屏灭屏广播处理
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mScreenBroadcastReceiver, filter);

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_TIME_SCREEN_ON, INTERVAL_DISTANCE_SCREEN_ON, this);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        if (mLocationManager != null) {
            Log.i(TAG, "mLocationManager.removeUpdates(this)");
            mLocationManager.removeUpdates(this);
        }
        if (mScreenBroadcastReceiver != null) {
            Log.i(TAG, "unregisterReceiver(mScreenBroadcastReceiver)");
            unregisterReceiver(mScreenBroadcastReceiver);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location != null) {

            if (extLocation == null) {
                //为了保证快速更新第一个点，不做过滤处理,以后不会走到这个函数里面来
                insertNotif(location);
            }

            //if (extLocation != null && isBetterLocation(extLocation, location)) {
            operatePoint(location);
            //}

            extLocation = location;
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private void operatePoint(Location location) {
        if (filter(location.getLongitude(), location.getLatitude())) {
            //插入数据
            locationQueue.offer(location);

            int ccount = locationQueue.size();
            //如果数据达到了就插入数据库
            if (ccount >= LOCATION_QUEUE_SIZE) {

                //先把数据放到内存缓存中
//                while (locationQueue.peek() != null) {
//                    templocationQueue.offer(locationQueue.poll());
//                }

                for (int k = 0; k < LOCATION_QUEUE_SIZE; k++) {
                    if (locationQueue.peek() != null) {
                        templocationQueue.offer(locationQueue.poll());
                    }
                }

                singleThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        ContentValues[] values = new ContentValues[LOCATION_QUEUE_SIZE];
                        int count = templocationQueue.size();
                        for (int i = 0; i < count; i++) {
                            if (templocationQueue.peek() != null) {
                                Location tempLocation = templocationQueue.poll();
                                values[i] = new ContentValues();
                                values[i].put(LocationDbHelper.LATITUDE, tempLocation.getLatitude());
                                values[i].put(LocationDbHelper.LONGITUDE, tempLocation.getLongitude());
                                values[i].put(LocationDbHelper.INS_SPEED, tempLocation.getSpeed());
                                values[i].put(LocationDbHelper.BEARING, tempLocation.getBearing());
                                values[i].put(LocationDbHelper.ALTITUDE, tempLocation.getAltitude());
                                values[i].put(LocationDbHelper.ACCURACY, tempLocation.getAccuracy());
                                long currentTime = System.currentTimeMillis();
                                values[i].put(LocationDbHelper.TIME, currentTime);

                                //距离写入数据库
                                if (lastLongitude != 0 && lastLatitude != 0) {
                                    values[i].put(LocationDbHelper.DISTANCE, getDistanceBetween2Point(lastLatitude, lastLongitude, tempLocation.getLatitude(), tempLocation.getLongitude()));
                                } else {
                                    values[i].put(LocationDbHelper.DISTANCE, 0.0);
                                }

                                //子线程内部处理平均速度和卡路里的获取
                                try {
                                    String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
                                    Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);

                                    if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                                        //获取起始时间
                                        if (firstLocationStartTime == 0) {
                                            firstLocationStartTime = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
                                        }

                                        //获取总距离
                                        if (allDistance == 0) {
                                            for (int j = 0; j < cursor.getCount(); j++) {
                                                cursor.moveToPosition(j);
                                                allDistance += cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
                                                Log.i(TAG, "allDistance1 = " + allDistance);
                                            }
                                        } else {
                                            if (lastLongitude != 0 && lastLatitude != 0) {
                                                allDistance += getDistanceBetween2Point(lastLatitude, lastLongitude, tempLocation.getLatitude(), tempLocation.getLongitude());
                                                Log.i(TAG, "allDistance2 = " + allDistance);
                                            }
                                        }

                                        //最后一个点的时间
                                        lastLocationStartTime = currentTime;
                                    }

                                    double avgSpeed = 0;

                                    if (lastLocationStartTime != firstLocationStartTime) {
                                        avgSpeed = (allDistance * 3600) / (lastLocationStartTime - firstLocationStartTime);
                                    } else {
                                        avgSpeed = 0;
                                    }

                                    double kcal = 60 * allDistance * 1.036 / 1000;

                                    //平均速度和卡路里写入数据库
                                    values[i].put(LocationDbHelper.AVG_SPEED, avgSpeed);
                                    values[i].put(LocationDbHelper.KCAL, kcal);

                                    if (cursor != null) {
                                        cursor.close();
                                    }

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                lastLatitude = tempLocation.getLatitude();
                                lastLongitude = tempLocation.getLongitude();
                            }
                        }

                        //批量插入数据库
                        long rowIds = getContentResolver().bulkInsert(MyContentProvider.CONTENT_URI, values);
                        Log.i(TAG, "insert database numbers rowIds = " + rowIds);
                        if (rowIds == LOCATION_QUEUE_SIZE) {
                            //通知观察者
                            getContentResolver().notifyChange(MyContentProvider.CONTENT_URI, null);
                        }
                    }
                });
            }
        }
    }

    private double getDistanceBetween2Point(double lastLatitude, double lastLongitude, double newLatitude, double newLongitude) {
        return TencentLocationUtils.distanceBetween(lastLatitude, lastLongitude, newLatitude, newLongitude);
    }

    private boolean filter(double longitude, double latitude) {
        BigDecimal mylongitude = (new BigDecimal(longitude)).setScale(SCALE, BigDecimal.ROUND_HALF_UP);
        BigDecimal mylatitude = (new BigDecimal(latitude)).setScale(SCALE, BigDecimal.ROUND_HALF_UP);

        if (lastBigLatitude != null && lastBigLongitude != null) {
            if (mylatitude.equals(lastBigLatitude) && mylongitude.equals(lastBigLongitude)) {
                return false;
            }
        }
        lastBigLatitude = mylatitude;
        lastBigLongitude = mylongitude;
        return true;
    }


    private void insertNotif(Location location) {
        if (location != null) {
            insertSingalPoint = location;
        }

        fixedThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                ContentValues values = new ContentValues();
                values.put(LocationDbHelper.LATITUDE, insertSingalPoint.getLatitude());
                values.put(LocationDbHelper.LONGITUDE, insertSingalPoint.getLongitude());
                values.put(LocationDbHelper.INS_SPEED, insertSingalPoint.getSpeed());
                values.put(LocationDbHelper.BEARING, insertSingalPoint.getBearing());
                values.put(LocationDbHelper.ALTITUDE, insertSingalPoint.getAltitude());
                values.put(LocationDbHelper.ACCURACY, insertSingalPoint.getAccuracy());
                long currentTime = System.currentTimeMillis();
                values.put(LocationDbHelper.TIME, currentTime);

                if (lastLongitude != 0 && lastLatitude != 0) {
                    values.put(LocationDbHelper.DISTANCE, getDistanceBetween2Point(lastLatitude, lastLongitude, insertSingalPoint.getLatitude(), insertSingalPoint.getLongitude()));
                } else {
                    values.put(LocationDbHelper.DISTANCE, 0.0);
                }

                //子线程内部处理平均速度和卡路里的获取
                try {
                    String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
                    Cursor cursor = getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);

                    if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                        //获取起始时间
                        if (firstLocationStartTime == 0) {
                            firstLocationStartTime = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
                        }

                        //获取总距离
                        if (allDistance == 0) {
                            for (int j = 0; j < cursor.getCount(); j++) {
                                cursor.moveToPosition(j);
                                allDistance += cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
                                Log.i(TAG, "allDistance1 = " + allDistance);
                            }
                        } else {
                            if (lastLongitude != 0 && lastLatitude != 0) {
                                allDistance += getDistanceBetween2Point(lastLatitude, lastLongitude, insertSingalPoint.getLatitude(), insertSingalPoint.getLongitude());
                                Log.i(TAG, "allDistance2 = " + allDistance);
                            }
                        }

                        //最后一个点的时间
                        lastLocationStartTime = currentTime;
                    }

                    //设置平均速度和卡路里
                    double avgSpeed = 0;
                    double kcal = 0;
                    if (allDistance == 0 || lastLocationStartTime == 0 || firstLocationStartTime == 0) {
                        avgSpeed = 0;
                        kcal = 0;
                    } else {
                        avgSpeed = (allDistance * 3600) / (lastLocationStartTime - firstLocationStartTime);
                        kcal = 60 * allDistance * 1.036 / 1000;
                        Log.i(TAG, "avgSpeed=" + avgSpeed + " kcal=" + kcal);
                    }

                    //平均速度和卡路里写入数据库
                    values.put(LocationDbHelper.AVG_SPEED, avgSpeed);
                    values.put(LocationDbHelper.KCAL, kcal);

                    if (cursor != null) {
                        cursor.close();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

                lastLatitude = insertSingalPoint.getLatitude();
                lastLongitude = insertSingalPoint.getLongitude();

                getContentResolver().insert(MyContentProvider.CONTENT_URI, values);

                //通知观察者数据更新
                getContentResolver().notifyChange(MyContentProvider.CONTENT_URI, null);
            }
        });
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

    BroadcastReceiver mScreenBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            Log.d(TAG, "onReceive");
            String action = intent.getAction();

            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mLocationManager != null) {
                    Log.i(TAG, "screen on and register");
                    mLocationManager.removeUpdates(LocationService.this);
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_TIME_SCREEN_ON, INTERVAL_DISTANCE_SCREEN_ON, LocationService.this);
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (mLocationManager != null) {
                    Log.i(TAG, "screen off and register");
                    mLocationManager.removeUpdates(LocationService.this);
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_TIME_SCREEN_OFF, INTERVAL_DISTANCE_SCREEN_OFF, LocationService.this);
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                Log.d(TAG, "screen unlock");
            } else if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                Log.i(TAG, " receive Intent.ACTION_CLOSE_SYSTEM_DIALOGS");
            }
        }
    };


    private static final int TWO_MINUTES = 1000 * 60 * 2;

    /**
     * Determines whether one Location reading is better than the current Location fix
     *
     * @param location            The new Location that you want to evaluate
     * @param currentBestLocation The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same
     */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }
}
