package com.tencent.tws.locationtrack;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.mapsdk.raster.model.GeoPoint;
import com.tencent.mapsdk.raster.model.LatLng;
import com.tencent.mapsdk.raster.model.Polyline;
import com.tencent.mapsdk.raster.model.PolylineOptions;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.SPUtils;
import com.tencent.tws.locationtrack.util.Gps;
import com.tencent.tws.locationtrack.util.PositionUtil;
import com.tencent.tws.locationtrack.views.CustomShareBoard;
import com.tencent.tws.widget.BaseActivity;
import com.umeng.scrshot.UMScrShotController;
import com.umeng.scrshot.adapter.UMAppAdapter;
import com.umeng.socialize.controller.UMServiceFactory;
import com.umeng.socialize.controller.UMSocialService;
import com.umeng.socialize.media.UMImage;
import com.umeng.socialize.sensor.controller.UMShakeService;
import com.umeng.socialize.sensor.controller.impl.UMShakeServiceFactory;
import com.umeng.socialize.sso.QZoneSsoHandler;
import com.umeng.socialize.sso.UMQQSsoHandler;
import com.umeng.socialize.weixin.controller.UMWXHandler;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class HisLocationActivity extends BaseActivity {

    private static final String TAG = "HisLocationActivity";

    protected LocationManager locationManager;
    protected Context context;

    private MapView mMapView;
    private LocationOverlay mLocationOverlay;
    private List<Object> Overlays;

    WakeLock mWakeLock;

    List<LatLng> points = new ArrayList<LatLng>();
    List<LatLng> points_tem = new ArrayList<LatLng>();

    int mSatelliteNum;
    private ArrayList<GpsSatellite> numSatelliteList = new ArrayList<>();

    private final static int ACCURACY = 3;
    private BigDecimal lastLatitude;
    private BigDecimal lastLongitude;
    private long lastLocationTime = (long) 0;

    private Button exitButton;
    private Button shareButton;

    private TextView hisAveSpeed;
    private TextView hisKal;
    private TextView hisDis;

    double hisSpeedValue = 0;
    double hisKcalValue = 0;
    double hisDisValue = 0;

    private Cursor cursor;
    double insSpeed = 0;
    double aveSpeed = 0;
    double kcal = 0;

    // 分享初始化控制器
    final UMSocialService mController = UMServiceFactory.getUMSocialService("com.umeng.share");
    UMShakeService mShakeController = UMShakeServiceFactory.getShakeService("com.umeng.share");
    Bitmap shareBitmap = null;


    // wx967daebe835fbeac是你在微信开发平台注册应用的AppID, 这里需要替换成你注册的AppID
    private static final String WEIXIN_APP_ID = "wx967daebe835fbeac";
    private static final String WEIXIN_APP_SECRET = "5fa9e68ca3970e87a1f83e563c8dcbce";

    Intent serviceIntent;

    public static final String LAST_DATABASE_NAME = "_location.db";
    private static LocationDbHelper dbHelper;

    private static HashMap<String, String> locationMaps;
    SQLiteDatabase sqLiteDatabase;


    static {
        //定义别名
        locationMaps = new HashMap<String, String>();
        locationMaps.put(LocationDbHelper.ID, LocationDbHelper.ID);
        locationMaps.put(LocationDbHelper.LATITUDE, LocationDbHelper.LATITUDE);
        locationMaps.put(LocationDbHelper.LONGITUDE, LocationDbHelper.LONGITUDE);
        locationMaps.put(LocationDbHelper.INS_SPEED, LocationDbHelper.INS_SPEED);
        locationMaps.put(LocationDbHelper.BEARING, LocationDbHelper.BEARING);
        locationMaps.put(LocationDbHelper.ALTITUDE, LocationDbHelper.ALTITUDE);
        locationMaps.put(LocationDbHelper.ACCURACY, LocationDbHelper.ACCURACY);
        locationMaps.put(LocationDbHelper.TIME, LocationDbHelper.TIME);
        locationMaps.put(LocationDbHelper.DISTANCE, LocationDbHelper.DISTANCE);
        locationMaps.put(LocationDbHelper.AVG_SPEED, LocationDbHelper.AVG_SPEED);
        locationMaps.put(LocationDbHelper.KCAL, LocationDbHelper.KCAL);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.his_geolocation);

        //初始化分享平台内容
        initSharePlatform();

        //不灭屏
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        mWakeLock.acquire();

        hisAveSpeed = (TextView) findViewById(R.id.his_ave_speed);
        hisKal = (TextView) findViewById(R.id.his_kal);
        hisDis = (TextView) findViewById(R.id.his_all_dis);

        //地图
        initMapView();
        String intentDbName = getIntent().getStringExtra("fulldbName");
        if (intentDbName != null && !intentDbName.equals("")) {
            String dbName = getIntent().getStringExtra("fulldbName");
            Log.i(TAG, "get intentDbName=" + intentDbName);
            if (dbName != null && !dbName.equals("") && !dbName.equals("0")) {
                dbHelper = new LocationDbHelper(getApplicationContext(), dbName);
                sqLiteDatabase = dbHelper.getWritableDatabase();
            }
        } else {
            Toast.makeText(getApplicationContext(), "数据库文件不存在", Toast.LENGTH_SHORT).show();
        }

        //退出按钮
        exitButton = (Button) findViewById(R.id.exitButton);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SPUtils.clearSp(getApplicationContext());

                if (serviceIntent != null) {
                    stopService(serviceIntent);
                }

                android.os.Process.killProcess(android.os.Process.myPid());    //获取PID
                System.exit(0);   //常规java、c#的标准退出法，返回值为0代表正常退出
            }
        });

        //数据分享按钮
        shareButton = (Button) findViewById(R.id.his_share);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //mController.openShare(LocationActivity.this, false);
                postShare();
            }
        });
    }

    private void initSharePlatform() {
        //在相应的地方要注册Handler才可以相应事件
        // 添加微信平台
        UMWXHandler wxHandler = new UMWXHandler(this, WEIXIN_APP_ID, WEIXIN_APP_SECRET);
        wxHandler.addToSocialSDK();
        // 支持微信朋友圈
        UMWXHandler wxCircleHandler = new UMWXHandler(this, WEIXIN_APP_ID, WEIXIN_APP_SECRET);
        wxCircleHandler.setToCircle(true);
        wxCircleHandler.addToSocialSDK();
        //参数1为当前Activity， 参数2为开发者在QQ互联申请的APP ID，参数3为开发者在QQ互联申请的APP kEY.
        QZoneSsoHandler qZoneSsoHandler = new QZoneSsoHandler(this, "100424468", "c7394704798a158208a74ab60104f0ba");
        qZoneSsoHandler.addToSocialSDK();
        //参数1为当前Activity， 参数2为开发者在QQ互联申请的APP ID，参数3为开发者在QQ互联申请的APP kEY.
        UMQQSsoHandler qqSsoHandler = new UMQQSsoHandler(this, "100424468", "c7394704798a158208a74ab60104f0ba");
        qqSsoHandler.addToSocialSDK();
    }

    private void postShare() {

        //截屏方法
        mShakeController.takeScrShot(HisLocationActivity.this, new UMAppAdapter(HisLocationActivity.this), new UMScrShotController.OnScreenshotListener() {
            @Override
            public void onComplete(Bitmap bmp) {
                if (bmp != null) {
                    shareBitmap = bmp;
                }
            }
        });

        if (shareBitmap != null) {
            mController.setShareMedia(new UMImage(getApplicationContext(), shareBitmap));
        }

        CustomShareBoard shareBoard = new CustomShareBoard(this);
        shareBoard.showAtLocation(this.getWindow().getDecorView(), Gravity.BOTTOM, 0, 0);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i(TAG, "onRestart");
    }


    @Override
    protected void onResume() {
        mMapView.onResume();
        super.onResume();
        if (mMapView != null) {
            mMapView.clearAllOverlays();
        }

        dbDrawResume();

        setLocationInfo();

        Log.i(TAG, "onResume");


        if (mWakeLock != null) {
            mWakeLock.acquire();
        }
    }

    private void setLocationInfo() {

    }

    //读取数据库，绘制数据库中所有数据
    private void dbDrawResume() {
        String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
        cursor = query(PROJECTION, null, null, null);

        long startTime = 0;
        long lastTime = 0;

        double allDis = 0;
        points.clear();

        Log.i(TAG, "dbDrawResume");
        if (cursor.moveToFirst()) {
            startTime = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));

            while (cursor.moveToNext()) {
                double latitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                double longitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                long times = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
                double insSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.INS_SPEED));
                float aveSpeed = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.AVG_SPEED));
                float kcal = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.KCAL));
                float accuracy = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY));
                float dis = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.DISTANCE));

                allDis += dis;

                Log.i(TAG, "latitude=" + latitude);
                Gps gps = PositionUtil.gps84_To_Gcj02(latitude, longitude);
                if (gps != null) {
                    drawLines(gps.getWgLon(), gps.getWgLat(), accuracy, true);
                }
            }
        }

        if (cursor.getCount() != 0) {
            cursor.moveToLast();
            lastTime = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));

            long deltTime = (lastTime - startTime) / 1000;
            double aveSpeed = (allDis * 10) / (deltTime * 36f);
            double allKcal = 60 * allDis * 1.036 / 1000;

            Log.i(TAG, "allDis=" + allDis + " | allKcal=" + allKcal + " | aveSpeed=" + aveSpeed);

            hisDisValue = allDis / 1000;
            hisSpeedValue = aveSpeed;
            hisKcalValue = allKcal;

            updateTextViews(hisSpeedValue, hisKcalValue, hisDisValue);
        }
    }

    @Override
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();
        if (cursor != null) {
            cursor.close();
        }

        if (locationManager != null) {
            locationManager.removeGpsStatusListener(statusListener);
        }

        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onStop() {
        mMapView.onStop();
        super.onStop();
    }


    private boolean filter(TencentLocation location) {
        BigDecimal longitude = (new BigDecimal(location.getLongitude())).setScale(ACCURACY, BigDecimal.ROUND_HALF_UP);

        BigDecimal latitude = (new BigDecimal(location.getLatitude())).setScale(ACCURACY, BigDecimal.ROUND_HALF_UP);

        if (latitude.equals(lastLatitude) && longitude.equals(lastLongitude)) {
            return false;
        }

        lastLatitude = latitude;
        lastLongitude = longitude;
        return true;
    }

    private boolean filter(Location location) {
        BigDecimal longitude = (new BigDecimal(location.getLongitude())).setScale(ACCURACY, BigDecimal.ROUND_HALF_UP);

        BigDecimal latitude = (new BigDecimal(location.getLatitude())).setScale(ACCURACY, BigDecimal.ROUND_HALF_UP);

        if (latitude.equals(lastLatitude) && longitude.equals(lastLongitude)) {
            return false;
        }

        lastLatitude = latitude;
        lastLongitude = longitude;
        return true;
    }

    private void initMapView() {
        mMapView = (MapView) findViewById(R.id.his_mapviewOverlay);
        // mMapView.setBuiltInZoomControls(true);
        mMapView.getController().setZoom(50);

        Bitmap bmpMarker = BitmapFactory.decodeResource(getResources(), R.drawable.mark_location);
        mLocationOverlay = new LocationOverlay(bmpMarker);
        mMapView.addOverlay(mLocationOverlay);

        Overlays = new ArrayList<Object>();
    }


    private static GeoPoint of(double latitude, double longitude) {
        GeoPoint ge = new GeoPoint((int) (latitude * 1E6), (int) (longitude * 1E6));
        return ge;
    }


    private void drawLines(double longitude, double latitude, float accuracy, boolean isFromDB) {
        mMapView.getController().animateTo(of(latitude, longitude));

        mLocationOverlay.setAccuracy(accuracy);
        mLocationOverlay.setGeoCoords(of(latitude, longitude));
        mMapView.invalidate();

        if (longitude > 0 && latitude > 0) {
            LatLng latLng = new LatLng(latitude, longitude);
            points.add(latLng);
        }

        if (points.size() == 2) {
            // 这里绘制起点
            PolylineOptions lineOpt = new PolylineOptions();
            lineOpt.color(0xAAFF0000);
            for (LatLng point : points) {
                lineOpt.add(point);
            }
            mMapView.getMap().addPolyline(lineOpt);
            Overlays.add(lineOpt);
        }

        if (points.size() > 2 && points.size() <= 10) {
            PolylineOptions lineOpt = new PolylineOptions();
            lineOpt.color(0xAAFF0000);
            for (LatLng point : points) {
                lineOpt.add(point);
            }
            mMapView.getMap().addPolyline(lineOpt);
            Overlays.add(lineOpt);
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
            Polyline line = mMapView.getMap().addPolyline(lineOpt);
            Overlays.add(line);
        }

        Log.i(TAG, "points.size() = " + points.size());

    }

    private void updateTextViews(double aveSpeed, double kal, double allDis) {

        DecimalFormat myformat = new DecimalFormat("#0.00");
        hisAveSpeed.setText(myformat.format(aveSpeed) + " km/h");
        hisKal.setText(myformat.format(kal) + " kcal");
        hisDis.setText(myformat.format(allDis) + "km");
    }


    private final GpsStatus.Listener statusListener = new GpsStatus.Listener() {
        @Override
        public void onGpsStatusChanged(int event) {
            // TODO Auto-generated method stub
            // GPS状态变化时的回调，获取当前状态
            GpsStatus status = locationManager.getGpsStatus(null);
            // 获取卫星相关数据
            GetGPSStatus(event, status);
        }

    };

    private void GetGPSStatus(int event, GpsStatus status) {
        if (status == null) {
        } else if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
            // 获取最大的卫星数（这个只是一个预设值）
            int maxSatellites = status.getMaxSatellites();
            Iterator<GpsSatellite> it = status.getSatellites().iterator();
            numSatelliteList.clear();
            // 记录实际的卫星数目
            int count = 0;
            while (it.hasNext() && count <= maxSatellites) {
                // 保存卫星的数据到一个队列，用于刷新界面
                GpsSatellite s = it.next();
                numSatelliteList.add(s);
                count++;
            }
            mSatelliteNum = numSatelliteList.size();
            String strSatelliteNum = this.getString(R.string.satellite_num) + mSatelliteNum;
            TextView tv = (TextView) findViewById(R.id.tvSatelliteNum);
            tv.setText(strSatelliteNum);

        } else if (event == GpsStatus.GPS_EVENT_STARTED) {
            // 定位启动
        } else if (event == GpsStatus.GPS_EVENT_STOPPED) {
            // 定位结束
        }
    }

    public Cursor query(String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteDatabase sqLiteDatabase = dbHelper.getWritableDatabase();
        SQLiteQueryBuilder sqLiteQueryBuilder = new SQLiteQueryBuilder();
        sqLiteQueryBuilder.setTables(LocationDbHelper.TABLE_NAME);
        sqLiteQueryBuilder.setProjectionMap(locationMaps);

        String orderBy = LocationDbHelper.DEFAULT_ORDERBY;

        Cursor cursor = sqLiteQueryBuilder.query(sqLiteDatabase, projection, selection, selectionArgs, null, null, orderBy);
        return cursor;
    }
}
