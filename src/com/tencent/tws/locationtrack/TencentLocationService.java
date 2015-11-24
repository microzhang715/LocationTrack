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
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by microzhang on 2015/10/30 at 17:08.
 */
public class TencentLocationService extends Service implements TencentLocationListener {
    private static final String TAG = "TencentLocationService";
    private TencentLocationManager mTencentLocationManager;

    private static final int INTERVAL_TIME = 1000;
    private static final int INTERVAL_DISTANCE = 10;

    //用于记录所有点信息
    private Queue<TencentLocation> locationQueue = new LinkedList<TencentLocation>();
    private Queue<TencentLocation> templocationQueue = new LinkedList<TencentLocation>();
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
    private TencentLocation insertSingalPoint;

    //用于过滤数据时候使用
    private BigDecimal lastBigLatitude;
    private BigDecimal lastBigLongitude;
    private static final int SCALE = 4;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "TencentLocationService onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        mTencentLocationManager = TencentLocationManager.getInstance(this);
        TencentLocationRequest request = TencentLocationRequest.create();
        request.setInterval(INTERVAL_TIME);
        request.setAllowCache(true);
        int error = mTencentLocationManager.requestLocationUpdates(request, this);

        if (error == 0) {
            Log.i(TAG, "Tencent地图注册成功");
        } else {
            Log.i(TAG, "Tencent地图注册失败 errorNo=" + error);
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        mTencentLocationManager.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(TencentLocation tencentLocation, int error, String reason) {
        if (tencentLocation != null && TencentLocation.ERROR_OK == error) {

            //if (filter(tencentLocation.getLongitude(), tencentLocation.getLatitude())) {
            //插入数据
            locationQueue.offer(tencentLocation);

            int ccount = locationQueue.size();
            //如果数据达到了就插入数据库
            if (ccount >= LOCATION_QUEUE_SIZE) {

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
                                TencentLocation tempLocation = templocationQueue.poll();
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


//                                    Log.i(TAG, "lastLocationStartTime=" + lastLocationStartTime + "  firstLocationStartTime=" + firstLocationStartTime);
                                    if (lastLocationStartTime != firstLocationStartTime) {

                                        avgSpeed = (allDistance * 3600) / (lastLocationStartTime - firstLocationStartTime);
                                        Log.i(TAG, "avgSpeed 1=" + avgSpeed);
                                    } else {
                                        avgSpeed = 0;
                                        Log.i(TAG, "avgSpeed 2");
                                    }

                                    double kcal = 60 * allDistance * 1.036 / 1000;
                                    Log.i(TAG, "avgSpeed=" + avgSpeed + "  kcal=" + kcal);

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
        //}
    }


    @Override
    public void onStatusUpdate(String name, int status, String desc) {

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

    private void insertNotif(TencentLocation location) {
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
}
