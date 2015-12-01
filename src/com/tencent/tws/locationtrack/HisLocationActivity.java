package com.tencent.tws.locationtrack;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.*;
import android.location.GpsSatellite;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.tencent.mapsdk.raster.model.GeoPoint;
import com.tencent.mapsdk.raster.model.LatLng;
import com.tencent.mapsdk.raster.model.Polyline;
import com.tencent.mapsdk.raster.model.PolylineOptions;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tencentmap.mapsdk.map.Overlay;
import com.tencent.tencentmap.mapsdk.map.Projection;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.SPUtils;
import com.tencent.tws.locationtrack.util.Gps;
import com.tencent.tws.locationtrack.util.PositionUtil;
import com.tencent.tws.locationtrack.views.CustomShareBoard;
import com.umeng.scrshot.UMScrShotController;
import com.umeng.scrshot.adapter.UMAppAdapter;
import com.umeng.socialize.controller.UMServiceFactory;
import com.umeng.socialize.controller.UMSocialService;
import com.umeng.socialize.media.UMImage;
import com.umeng.socialize.sensor.controller.UMShakeService;
import com.umeng.socialize.sensor.controller.impl.UMShakeServiceFactory;
import com.umeng.socialize.sso.SinaSsoHandler;
import com.umeng.socialize.sso.UMQQSsoHandler;
import com.umeng.socialize.weixin.controller.UMWXHandler;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HisLocationActivity extends Activity {

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

    protected ArrayList<Gps> locations = new ArrayList<Gps>();
    protected ArrayList<Gps> resultlocations = new ArrayList<Gps>();
    protected double topBoundary;
    protected double leftBoundary;
    protected double rightBoundary;
    protected double bottomBoundary;

    protected Location locationTopLeft;
    protected Location locationBottomRight;
    protected float maxDistance;
    protected GeoPoint mapCenterPoint;
    private PathOverlay pathOverlay;
    private long TIME_TO_WAIT_IN_MS = 3000;//时间设置过短 setZoom不生效，原因待查！

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

    private ExecutorService fixedThreadExecutor = Executors.newFixedThreadPool(2);
    private static final int UPDATE_VIEWS = 1;

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

        pathOverlay = new PathOverlay();

        //退出按钮
        exitButton = (Button) findViewById(R.id.exitButton);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SPUtils.clearDBName(getApplicationContext());

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

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case UPDATE_VIEWS:
                    Bundle bundle = (Bundle) msg.obj;
                    double hisDisValue = bundle.getDouble("hisDisValue");
                    double hisSpeedValue = bundle.getDouble("hisSpeedValue");
                    double hisKcalValue = bundle.getDouble("hisKcalValue");
                    Log.i(TAG, "UPDATE_VIEWS:" + " hisSpeedValue=" + hisSpeedValue + " hisKcalValue=" + hisKcalValue + " hisDisValue=" + hisDisValue);
                    updateTextViews(hisSpeedValue, hisKcalValue, hisDisValue);

                    if (locations.size() > 0) {
                        getBoundary();
                        mMapView.addOverlay(pathOverlay);

                        mMapView.addOverlay(new PointMarkLayout(locations.get(0), R.drawable.point_start));
                        mMapView.addOverlay(new PointMarkLayout(locations.get(locations.size() - 1), R.drawable.point_end));
                        mMapView.getController().setCenter(mapCenterPoint);
                        mMapView.postDelayed(waitForMapTimeTask, TIME_TO_WAIT_IN_MS);
                    }
                    break;
            }
        }
    };

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
//        QZoneSsoHandler qZoneSsoHandler = new QZoneSsoHandler(this, "100424468", "c7394704798a158208a74ab60104f0ba");
//        qZoneSsoHandler.addToSocialSDK();
        //参数1为当前Activity， 参数2为开发者在QQ互联申请的APP ID，参数3为开发者在QQ互联申请的APP kEY.
        UMQQSsoHandler qqSsoHandler = new UMQQSsoHandler(this, "100424468", "c7394704798a158208a74ab60104f0ba");
        qqSsoHandler.addToSocialSDK();

        mController.getConfig().setSsoHandler(new SinaSsoHandler());

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

        //修改绘制逻辑，使用Overlay添加，减少绘制次数 at 20151116 by guccigu
        dbDrawResume();


        setLocationInfo();

        Log.i(TAG, "onResume");


        if (mWakeLock != null) {
            mWakeLock.acquire();
        }
    }

    private void setLocationInfo() {

    }

//    private long lastTime = 0;

    //读取数据库，绘制数据库中所有数据
    private void dbDrawResume() {

        fixedThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Cursor cursor = null;
                    String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
                    cursor = query(PROJECTION, null, null, null);

                    long startTime = 0;
                    long lastTime = 0;

                    double allDis = 0;
                    points.clear();

                    Log.i(TAG, "dbDrawResume");
                    if (cursor != null && cursor.moveToFirst()) {
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
                            locations.add(gps);
                        }
                    }

                    if (cursor != null && cursor.getCount() != 0) {
                        cursor.moveToLast();
                        lastTime = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));

                        long deltTime = (lastTime - startTime) / 1000;
                        double aveSpeed = (allDis * 10) / (deltTime * 36f);
                        double allKcal = 60 * allDis * 1.036 / 1000;

                        Log.i(TAG, "allDis=" + allDis + " | allKcal=" + allKcal + " | aveSpeed=" + aveSpeed);

                        hisDisValue = allDis / 1000;
                        hisSpeedValue = aveSpeed;
                        hisKcalValue = allKcal;

                        Message msg = Message.obtain();
                        msg.what = UPDATE_VIEWS;
                        Bundle bundle = new Bundle();
                        bundle.putDouble("hisDisValue", hisDisValue);
                        bundle.putDouble("hisSpeedValue", hisSpeedValue);
                        bundle.putDouble("hisKcalValue", hisKcalValue);
                        msg.obj = bundle;
                        handler.sendMessage(msg);
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
    protected void onPause() {
        mMapView.onPause();
        super.onPause();
        locations.clear();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onDestroy() {
        mMapView.onDestroy();
        super.onDestroy();

        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onStop() {
        mMapView.onStop();
        super.onStop();
    }

    private void initMapView() {
        mMapView = (MapView) findViewById(R.id.his_mapviewOverlay);
        // mMapView.setBuiltInZoomControls(true);
//        mMapView.getController().setZoom(50);
        mMapView.getMap().setZoom(mMapView.getMap().getMaxZoomLevel());
        ;
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

        Log.i(TAG, "douglasPoints.size() = " + points.size());

    }

    private void updateTextViews(double aveSpeed, double kal, double allDis) {

        DecimalFormat myformat = new DecimalFormat("#0.00");
        hisAveSpeed.setText(myformat.format(aveSpeed) + " km/h");
        hisKal.setText(myformat.format(kal) + " kcal");
        hisDis.setText(myformat.format(allDis) + "km");
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

    protected void getBoundary() {
        leftBoundary = locations.get(0).getWgLat();
        bottomBoundary = locations.get(0).getWgLon();

        rightBoundary = locations.get(0).getWgLat();
        topBoundary = locations.get(0).getWgLon();

        for (Gps location : locations) {
            if (leftBoundary > location.getWgLat()) {
                leftBoundary = location.getWgLat();
            }

            if (rightBoundary < location.getWgLat()) {
                rightBoundary = location.getWgLat();
            }

            if (topBoundary < location.getWgLon()) {
                topBoundary = location.getWgLon();
            }

            if (bottomBoundary > location.getWgLon()) {
                bottomBoundary = location.getWgLon();
            }
        }

        locationTopLeft = new Location("");
        locationTopLeft.setLongitude(topBoundary);
        locationTopLeft.setLatitude(leftBoundary);

        locationBottomRight = new Location("");
        locationBottomRight.setLongitude(bottomBoundary);
        locationBottomRight.setLatitude(rightBoundary);

        maxDistance = locationTopLeft.distanceTo(locationBottomRight);
        mapCenterPoint = new GeoPoint(
                (int) ((leftBoundary + (rightBoundary - leftBoundary) / 2) * 1e6),
                (int) ((bottomBoundary + (topBoundary - bottomBoundary) / 2) * 1e6)
        );
    }

    protected int getFixedZoomLevel() {
        int fixedLatitudeSpan = (int) ((rightBoundary - leftBoundary) * 1e6);
        int fixedLongitudeSpan = (int) ((topBoundary - bottomBoundary) * 1e6);
//            int j =mMapView.getMap().getZoomLevel();
        for (int i = mMapView.getMap().getMaxZoomLevel(); i > 0; i--) {
            mMapView.getMap().setZoom(i);
//	        	j =mMapView.getMap().getZoomLevel();
            int latSpan = mMapView.getProjection().getLatitudeSpan();
            int longSpan = mMapView.getProjection().getLongitudeSpan();

            if (latSpan > fixedLatitudeSpan && longSpan > fixedLongitudeSpan) {
                return i;
            }
        }

        return mMapView.getMap().getMaxZoomLevel();
    }

    /**
     * Wait for mapview to become ready.
     */
    private Runnable waitForMapTimeTask = new Runnable() {
        public void run() {
            // If either is true we must wait.
            if (mMapView.getProjection().getLatitudeSpan() == 0 || mMapView.getProjection().getLongitudeSpan() == 360000000)
                mMapView.postDelayed(this, TIME_TO_WAIT_IN_MS);
            else {
                mMapView.getMap().setZoom(getFixedZoomLevel());
            }
        }
    };

    private class PathOverlay extends Overlay {
        private Paint paint;
        private Projection projection;
        private static final int MIN_POINT_SPAN = 5;

        public PathOverlay() {
            setPaint();
        }

        private void setPaint() {
            paint = new Paint();
            paint.setAntiAlias(true);
            paint.setDither(true);

            paint.setColor(getResources().getColor(R.color.highlight));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(10);
            paint.setAlpha(188);
        }


        @Override
        public void draw(final Canvas canvas, final MapView mapView) {
            this.projection = mapView.getProjection();

            synchronized (canvas) {
                final Path path = new Path();
                final int maxWidth = mapView.getWidth();
                final int maxHeight = mapView.getHeight();

                Point lastGeoPoint = null;
                for (Gps location : locations) {
                    GeoPoint piont = new GeoPoint((int) (location.getWgLat() * 1e6), (int) (location.getWgLon() * 1e6));
                    Point current = projection.toPixels(piont, null);

                    if (lastGeoPoint != null && (lastGeoPoint.y < maxHeight && lastGeoPoint.x < maxWidth)) {
    /*                            if (Math.abs(current.x - lastGeoPoint.x) < MIN_POINT_SPAN
                                    || Math.abs(current.y - lastGeoPoint.y) < MIN_POINT_SPAN) {
	                                continue;
	                            } else {*/
                        path.lineTo(current.x, current.y);
                                /*                   }*/
                    } else {
                        path.moveTo(current.x, current.y);
                    }
                    lastGeoPoint = current;
                }

                canvas.drawPath(path, paint);
            }
        }
    }

    private class PointMarkLayout extends Overlay {
        private Gps location;
        private int drawable;
        private Projection projection;

        PointMarkLayout(Gps location, int drawable) {
            this.location = location;
            this.drawable = drawable;
        }

        @Override
        public void draw(final Canvas canvas, final MapView mapView) {
            super.draw(canvas, mapView);

            this.projection = mapView.getProjection();
            GeoPoint piont = new GeoPoint((int) (location.getWgLat() * 1e6), (int) (location.getWgLon() * 1e6));
            Point current = projection.toPixels(piont, null);

            Bitmap markerImage = BitmapFactory.decodeResource(getResources(), drawable);

            // 根据实际的条目而定偏移位置
            canvas.drawBitmap(markerImage,
                    current.x - Math.round(markerImage.getWidth() * 0.4),
                    current.y - Math.round(markerImage.getHeight() * 0.9), null);

            return;
        }
    }

    public ArrayList<Gps> optimizeGpsPoints(ArrayList<Gps> inPoint) {
        int size = inPoint.size();
        ArrayList<Location> outPoint;

        int i;
        if (size < 5) {
            return inPoint;
        } else {
            // Latitude
            inPoint.get(0)
                    .setWgLat((3.0 * inPoint.get(0).getWgLat() + 2.0
                            * inPoint.get(1).getWgLat() + inPoint.get(2).getWgLat() - inPoint
                            .get(4).getWgLat()) / 5.0);
            inPoint.get(1)
                    .setWgLat((4.0 * inPoint.get(0).getWgLat() + 3.0
                            * inPoint.get(1).getWgLat() + 2
                            * inPoint.get(2).getWgLat() + inPoint.get(3).getWgLat()) / 10.0);

            inPoint.get(size - 2).setWgLat(
                    (4.0 * inPoint.get(size - 1).getWgLat() + 3.0
                            * inPoint.get(size - 2).getWgLat() + 2
                            * inPoint.get(size - 3).getWgLat() + inPoint.get(
                            size - 4).getWgLat()) / 10.0);
            inPoint.get(size - 1).setWgLat(
                    (3.0 * inPoint.get(size - 1).getWgLat() + 2.0
                            * inPoint.get(size - 2).getWgLat()
                            + inPoint.get(size - 3).getWgLat() - inPoint.get(
                            size - 5).getWgLat()) / 5.0);

            // Longitude
            inPoint.get(0)
                    .setWgLon((3.0 * inPoint.get(0).getWgLon() + 2.0
                            * inPoint.get(1).getWgLon() + inPoint.get(2).getWgLon() - inPoint
                            .get(4).getWgLon()) / 5.0);
            inPoint.get(1)
                    .setWgLon((4.0 * inPoint.get(0).getWgLon() + 3.0
                            * inPoint.get(1).getWgLon() + 2
                            * inPoint.get(2).getWgLon() + inPoint.get(3).getWgLon()) / 10.0);

            inPoint.get(size - 2).setWgLon(
                    (4.0 * inPoint.get(size - 1).getWgLon() + 3.0
                            * inPoint.get(size - 2).getWgLon() + 2
                            * inPoint.get(size - 3).getWgLon() + inPoint.get(
                            size - 4).getWgLon()) / 10.0);
            inPoint.get(size - 1).setWgLon(
                    (3.0 * inPoint.get(size - 1).getWgLon() + 2.0
                            * inPoint.get(size - 2).getWgLon()
                            + inPoint.get(size - 3).getWgLon() - inPoint.get(
                            size - 5).getWgLon()) / 5.0);
            for (i = 2; i < size - 2; i++) {
                // Latitude
                inPoint.get(i)
                        .setWgLat((4.0 * inPoint.get(i - 1).getWgLat() + 3.0
                                * inPoint.get(i).getWgLat() + 2
                                * inPoint.get(i + 1).getWgLat() + inPoint.get(i + 2).getWgLat()) / 10.0);
                // Longitude
                inPoint.get(i)
                        .setWgLon((4.0 * inPoint.get(i - 1).getWgLon() + 3.0
                                * inPoint.get(i).getWgLon() + 2
                                * inPoint.get(i + 1).getWgLon() + inPoint.get(i + 2).getWgLon()) / 10.0);
            }
        }
        return inPoint;
    }
}
