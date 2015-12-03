package com.tencent.tws.locationtrack.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.tencent.mapsdk.raster.model.*;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tencentmap.mapsdk.map.TencentMap;
import com.tencent.tws.locationtrack.R;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.MyContentProvider;
import com.tencent.tws.locationtrack.database.SPUtils;
import com.tencent.tws.locationtrack.douglas.Douglas;
import com.tencent.tws.locationtrack.douglas.DouglasPoint;
import com.tencent.tws.locationtrack.util.Gps;
import com.tencent.tws.locationtrack.util.LocationUtil;
import com.tencent.tws.locationtrack.util.PositionUtil;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationActivity extends Activity {

    private static final String TAG = "LocationActivity";
    WakeLock mWakeLock;

    private MapView mMapView;
    private TencentMap tencentMap;
    private List<Object> Overlays;
    private Marker marker;

    List<LatLng> points = new ArrayList<LatLng>();
    List<LatLng> points_tem = new ArrayList<LatLng>();

    private long lastLocationTime = (long) 0;

    //按钮
    private Button startButton;
    private Button exitButton;
    //ui控件更新
    private TextView tvAveSpeed;
    private TextView tvInsSpeed;
    private TextView tvKal;
    private TextView tvGPSStatus;
    private TextView tvLocation;
    private TextView getIntervalTime;
    private TextView allDis;
    private TextView satelliteNum;
    private DBContentObserver mDBContentObserver;

    //    int mSatelliteNum;
    protected Queue<Gps> resumeLocations = new LinkedList<Gps>();
    //    private ArrayList<GpsSatellite> numSatelliteList = new ArrayList<>();
    private ExecutorService fixedThreadExecutor = Executors.newFixedThreadPool(2);
    Intent locationServiceIntent;
    private boolean isFinishDBDraw = true;

    //每次绘制的点数
    private static final int RESUME_ONCE_DRAW_POINTS = 500;
    //初始化缩放级别
    private static final int ZOOM_LEVER = 18;

    //handle的msg
    private static final int UPDATE_TEXT_VIEWS = 1;
    private static final int UPDATE_DRAW_LINES = 2;
    private static final int DRAW_RESUME = 3;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.geolocation);

        //不灭屏
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        mWakeLock.acquire();

        tvLocation = (TextView) findViewById(R.id.tvLocation);
        tvGPSStatus = (TextView) findViewById(R.id.tvGPSStatus);
        tvAveSpeed = (TextView) findViewById(R.id.ave_speed);
        tvInsSpeed = (TextView) findViewById(R.id.ins_speed);
        tvKal = (TextView) findViewById(R.id.kal);
        allDis = (TextView) findViewById(R.id.all_dis);
        getIntervalTime = (TextView) findViewById(R.id.getIntervalTime);
        satelliteNum = (TextView) findViewById(R.id.tvSatelliteNum);

        //地图
        initMapView();
        //初始化观察者
        initContentObserver();

        //开始按钮
        startButton = (Button) findViewById(R.id.startButton);
        exitButton = (Button) findViewById(R.id.exitButton);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //清空退出标志
                SPUtils.writeExitFlag(getApplicationContext(), false);
                //按钮状态改变
                startButton.setEnabled(false);
                exitButton.setEnabled(true);

                long currentTime = System.currentTimeMillis();
                Log.i(TAG, "currentTime=" + currentTime + "  convert=" + LocationUtil.convert(currentTime));
                MyContentProvider.createNewDB(getApplicationContext(), currentTime);
                //启动后台服务获取地理信息
                locationServiceIntent = new Intent(getApplicationContext(), LocationService.class);
                startService(locationServiceIntent);
                Log.i("LocationService", "LocationService 启动");
            }
        });

        //activity被杀掉重新进来的时候不需要再次点击开始按钮,自动重新启动服务
        if (SPUtils.readExitFlag(getApplicationContext()) == false) {
            locationServiceIntent = new Intent(getApplicationContext(), LocationService.class);
            startService(locationServiceIntent);

            //改变按钮状态
            startButton.setEnabled(false);
            exitButton.setEnabled(true);
        }

        //退出按钮
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //清除数据库
                SPUtils.clearDBName(getApplicationContext());

                if (locationServiceIntent != null) {
                    stopService(locationServiceIntent);
                }

                //设置退出标志位
                SPUtils.writeExitFlag(getApplicationContext(), true);
                //退出当前Activity
                finish();
            }
        });

        //注册GPS状态监听广播
        registerGpsStatusReceiver();

    }

    private void registerGpsStatusReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationService.UPDATE_SATELLITE_NUM);
        registerReceiver(myGpsStateReceiver, filter);
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case UPDATE_TEXT_VIEWS:
                    Bundle bundle = (Bundle) msg.obj;
                    if (bundle != null) {
                        updateTextViews(bundle.getDouble("longitude"), bundle.getDouble("latitude"), bundle.getLong("times"), bundle.getDouble("insSpeed"), bundle.getDouble("aveSpeed"), bundle.getDouble("kcal"), bundle.getDouble("allDistance"));
                    }
                    break;

                case UPDATE_DRAW_LINES:
                    Bundle bundle1 = (Bundle) msg.obj;
                    //Log.i(TAG, "UPDATE_DRAW_LINES -----> longitude=" + bundle1.getDouble("longitude") + " latitude=" + bundle1.getDouble("latitude") + " accuracy=" + bundle1.getFloat("accuracy"));
                    drawLines(bundle1.getDouble("longitude"), bundle1.getDouble("latitude"), bundle1.getFloat("accuracy"));
                    break;

                case DRAW_RESUME:
                    //locations 中包含了所有需要绘制的点信息
                    onResumeDrawImp();
                    break;
                default:
            }
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
                if (!SPUtils.readExitFlag(getApplicationContext())) { //已经开始轨迹定位且没有退出，只能通过点击按钮退出
                    Toast.makeText(getApplicationContext(), "正在记录轨迹，点击退出按钮结束轨迹记录", Toast.LENGTH_SHORT).show();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
        isFinishDBDraw = false;
    }

    private void initContentObserver() {
        mDBContentObserver = new DBContentObserver(new Handler());
        getContentResolver().registerContentObserver(MyContentProvider.CONTENT_URI, true, mDBContentObserver);
    }

    @Override
    protected void onResume() {
        mMapView.onResume();
        super.onResume();

        Log.i(TAG, "onResume");
        if (SPUtils.readDBName(getApplicationContext()) != "") {//数据库是存在的
            if (mMapView != null) {
                //先清除所有的点
                mMapView.clearAllOverlays();
                //重新添加marker
                marker = tencentMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromResource(R.drawable.red_location)));
            }

            //将数据库文件读取到locations队列中，为真正的绘制流程做准备
            Log.i(TAG, "readDbforDraw");
            readDbforDraw();
        }

        if (mWakeLock != null) {
            mWakeLock.acquire();
        }

        sendStatusBroadcast("onResume");
    }

    private List<DouglasPoint> listDouglasPoints = new ArrayList<DouglasPoint>();

    //读取数据库，绘制数据库中所有数据
    private void readDbforDraw() {
        fixedThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Cursor cursor = null;
                try {
                    resumeLocations.clear();
                    listDouglasPoints.clear();
                    int index = 0;

                    String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
                    cursor = getApplicationContext().getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);
                    if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {

                        do {
                            double latitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                            double longitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                            double insSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.INS_SPEED));
                            int id = cursor.getInt(cursor.getColumnIndex(LocationDbHelper.ID));
                            double dis = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
                            long time = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
                            float bearing = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.BEARING));
                            float accuracy = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY));
                            double avgSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.AVG_SPEED));
                            double kcal = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.KCAL));

                            DouglasPoint tmpPoint = new DouglasPoint(latitude, longitude, insSpeed, id, dis, time, bearing, accuracy, avgSpeed, kcal, index++);
                            listDouglasPoints.add(tmpPoint);

                        } while (cursor.moveToNext());

                        Log.i(TAG, "listDouglasPoints.size()=" + listDouglasPoints.size());
                        //对数据进行压缩处理
                        Douglas douglas = new Douglas(listDouglasPoints);
                        douglas.compress(listDouglasPoints.get(0), listDouglasPoints.get(listDouglasPoints.size() - 1));
                        for (int i = 0; i < douglas.douglasPoints.size(); i++) {
                            DouglasPoint p = douglas.douglasPoints.get(i);
                            if (p.getIndex() > -1) {
                                Gps gps = PositionUtil.gps84_To_Gcj02(p.getLatitude(), p.getLongitude());
                                if (gps != null) {
                                    //所有数据进入队列
                                    resumeLocations.offer(gps);
                                }
                            }
                        }
                        Log.i(TAG, "resumeLocations.size()=" + resumeLocations.size());
                    }
                    handler.sendEmptyMessage(DRAW_RESUME);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (cursor != null) {
                    cursor.close();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "onPause");
        mMapView.onPause();
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(TAG, "onDestroy");
        mMapView.onDestroy();
        super.onDestroy();

        if (myGpsStateReceiver != null) {
            unregisterReceiver(myGpsStateReceiver);
        }

        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop");
        mMapView.onStop();
        super.onStop();

        sendStatusBroadcast("onStop");
    }

    private void initMapView() {
        Log.i(TAG, "initMapView----------");
        mMapView = (MapView) findViewById(R.id.mapviewOverlay);
        mMapView.getController().setZoom(ZOOM_LEVER);
        mMapView.setDrawingCacheEnabled(true);
        tencentMap = mMapView.getMap();

        if (LocationService.extLocation != null) {//定位到最新位置
            tencentMap.setCenter(new LatLng(LocationService.extLocation.getLatitude(), LocationService.extLocation.getLongitude()));
            tencentMap.setZoom(ZOOM_LEVER);
        }


        if (marker == null) {
            marker = tencentMap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromResource(R.drawable.red_location)).position(new LatLng(39.890000, 116.350777)));
        }

        Overlays = new ArrayList<Object>();
    }

    /**
     * onResume的时候真正的绘制流程
     * 每次绘制 RESUME_ONCE_DRAW_POINTS 个点 分批绘制
     */
    private void onResumeDrawImp() {
        Log.i(TAG, "onResumeDrawImp");
        int count = resumeLocations.size();
            PolylineOptions lineOpt2 = new PolylineOptions();
            lineOpt2.color(0xAAFF0000);
        for (int i = 0; i < count; i++) {
            if (resumeLocations.peek() != null) {
                Gps gps = resumeLocations.poll();
                lineOpt2.add(new LatLng(gps.getWgLat(), gps.getWgLon()));
            }
        }
        Polyline line = tencentMap.addPolyline(lineOpt2);
        Overlays.add(line);

        isFinishDBDraw = true;
    }

    private void drawLines(double longitude, double latitude, float accuracy) {

        //Log.i(TAG, "douglasPoints size = " + douglasPoints.size());
        tencentMap.animateTo(new LatLng(latitude, longitude));
        marker.setPosition(new LatLng(latitude, longitude));

        if (longitude > 0 && latitude > 0) {
            LatLng latLng = new LatLng(latitude, longitude);
            points.add(latLng);
        }

        if (points.size() > 0 && points.size() <= 10) {
            drawLinePoints();
        }

        if (points.size() > 10) {
            // 每次绘制10个点，这样应该不会出现明显的折线吧
            points_tem = points.subList(points.size() - 10, points.size());
            // 绘图
            PolylineOptions lineOpt = new PolylineOptions();
            lineOpt.color(0xAAFF0000);
            for (LatLng point : points_tem) {
                lineOpt.add(point);
            }
            Polyline line = tencentMap.addPolyline(lineOpt);
            Overlays.add(line);
        }

        //Log.i(TAG, "douglasPoints.size() = " + douglasPoints.size());
    }

    private void drawLinePoints() {
        PolylineOptions lineOpt = new PolylineOptions();
        lineOpt.color(0xAAFF0000);
        for (LatLng point : points) {
            lineOpt.add(point);
        }
        tencentMap.addPolyline(lineOpt);
        Overlays.add(lineOpt);
    }

    private void updateTextViews(double longitude, double latitude, long times, double insSpeed, double aveSpeed, double kal, double allDistance) {
        tvLocation.setText("维度:" + latitude + ",经度:" + longitude + ",时间 :" + LocationUtil.convert(times));

        if (lastLocationTime != times && lastLocationTime != 0) {
            Log.i("kermit", "lastLocationTime=" + lastLocationTime);
            Log.i("kermit", "times=" + times);
            Log.i("kermit", "deltTimes=" + (times - lastLocationTime));
            long deltTIme = (times - lastLocationTime) / 1000;
            getIntervalTime.setText("   获取间隔时间：" + deltTIme);
            //Log.i(TAG, "获取间隔时间：" + deltTIme);
        }

        lastLocationTime = times;

        DecimalFormat myformat = new DecimalFormat("#0.00");
        tvInsSpeed.setText(myformat.format(insSpeed) + " km/h");
        tvAveSpeed.setText(myformat.format(aveSpeed) + " km/h");
        tvKal.setText(myformat.format(kal) + " kal");
        allDis.setText(allDistance + " m");
    }

    public static final String ACTIVITY_STATUS_CHANGE = "com.tencent.tws.locationtrack.activity_status_change";
    public static final String STATUS = "status";

    private void sendStatusBroadcast(String status) {
        Intent intent = new Intent(ACTIVITY_STATUS_CHANGE);
        intent.putExtra(STATUS, status);
        sendBroadcast(intent);
    }

    BroadcastReceiver myGpsStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(LocationService.UPDATE_SATELLITE_NUM)) {
                int num = intent.getIntExtra(LocationService.STAELLITE_EXTR, 0);
                if (satelliteNum != null) {
                    satelliteNum.setText(num + "");
                }
            }
        }
    };

    private class DBContentObserver extends ContentObserver {

        public DBContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            //Log.i(TAG, "onChange~~~~");
            //子线程中去做数据库操作，更新UI
            fixedThreadExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
                        long allDistance = 0;

                        Cursor cursor = getApplicationContext().getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);
                        if (cursor != null) {
                            int cursorCount = cursor.getCount();
                            //Log.i(TAG, "cursorCount = " + cursorCount);

                            //获取总距离
                            if (cursorCount > 0 && cursor.moveToFirst()) {
                                for (int i = 0; i < cursor.getCount(); i++) {
                                    cursor.moveToPosition(i);
                                    allDistance += cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
                                }
                            }

                            //更新UI TextView，每次采集  LocationService.LOCATION_QUEUE_SIZE  个点更新一次UI
                            if (cursor.moveToLast()) {
                                double latitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                                double longitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                                long times = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
                                double insSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.INS_SPEED));
                                double aveSpeed = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.AVG_SPEED));
                                double kcal = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.KCAL));
                                float accuracy = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY));

                                //更新显示UI
                                Message msg = Message.obtain();
                                Bundle bundle = new Bundle();
                                bundle.putDouble("longitude", longitude);
                                bundle.putDouble("latitude", latitude);
                                bundle.putLong("times", times);
                                bundle.putDouble("insSpeed", insSpeed);
                                bundle.putDouble("aveSpeed", aveSpeed);
                                bundle.putDouble("kcal", kcal);
                                bundle.putDouble("allDistance", allDistance);
                                msg.obj = bundle;
                                msg.what = UPDATE_TEXT_VIEWS;
                                handler.sendMessage(msg);


                                if (isFinishDBDraw == false) {
                                    Gps gps = PositionUtil.gps84_To_Gcj02(latitude, longitude);
                                    if (gps != null) {
                                        LatLng latLng = new LatLng(gps.getWgLat(), gps.getWgLon());
                                        points.add(latLng);
                                    }
                                } else {
                                    if (cursorCount > LocationService.LOCATION_QUEUE_SIZE) {
                                        for (int i = cursorCount - LocationService.LOCATION_QUEUE_SIZE; i < cursorCount; i++) {
//                                            Log.i(TAG, "cursorCount - LocationService.LOCATION_QUEUE_SIZE = " + String.valueOf(cursorCount - LocationService.LOCATION_QUEUE_SIZE));
                                            cursor.moveToPosition(i);
                                            Bundle drawLinesBundle = new Bundle();
                                            double tLatitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                                            double tLongtiude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                                            //Log.i(TAG, "tLatitude=" + tLatitude + " tLongtiude=" + tLongtiude);
                                            Gps gps = PositionUtil.gps84_To_Gcj02(tLatitude, tLongtiude);
                                            if (gps != null) {
                                                drawLinesBundle.putDouble("longitude", gps.getWgLon());
                                                drawLinesBundle.putDouble("latitude", gps.getWgLat());
                                                drawLinesBundle.putFloat("accuracy", cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY)));

                                                Message msg1 = Message.obtain();
                                                msg1.what = UPDATE_DRAW_LINES;
                                                msg1.obj = drawLinesBundle;
                                                handler.sendMessage(msg1);
                                            }
                                        }
                                    } else {
                                        cursor.moveToLast();

                                        double tLatitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                                        double tLongtiude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                                        Gps gps = PositionUtil.gps84_To_Gcj02(tLatitude, tLongtiude);
                                        if (gps != null) {
                                            Bundle drawLinesBundle = new Bundle();
                                            drawLinesBundle.putDouble("longitude", gps.getWgLon());
                                            drawLinesBundle.putDouble("latitude", gps.getWgLat());
                                            drawLinesBundle.putFloat("accuracy", cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY)));

                                            Message msg1 = Message.obtain();
                                            msg1.what = UPDATE_DRAW_LINES;
                                            msg1.obj = drawLinesBundle;
                                            handler.sendMessage(msg1);
                                        }

                                    }
                                }
                            }
                        }

                        if (cursor != null) {
                            cursor.close();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }
    }
}
