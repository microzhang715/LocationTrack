package com.tencent.tws.locationtrack;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.location.Location;
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
import com.tencent.mapsdk.raster.model.*;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tencentmap.mapsdk.map.Overlay;
import com.tencent.tencentmap.mapsdk.map.Projection;
import com.tencent.tencentmap.mapsdk.map.TencentMap;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.SPUtils;
import com.tencent.tws.locationtrack.douglas.Douglas;
import com.tencent.tws.locationtrack.douglas.DouglasPoint;
import com.tencent.tws.locationtrack.util.Gps;
import com.tencent.tws.locationtrack.util.PositionUtil;
import com.tencent.tws.locationtrack.views.ColorPathOverlay;
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

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HisLocationActivity extends Activity {

    private static final String TAG = "HisLocationActivity";

    protected Context context;
    private ExecutorService fixedThreadExecutor = Executors.newFixedThreadPool(2);

    private MapView mMapView;
    private TencentMap tencentMap;
    private List<Object> Overlays;

    WakeLock mWakeLock;

    private Button exitButton;
    private Button shareButton;

    private TextView hisAveSpeed;
    private TextView hisKal;
    private TextView hisDis;
    double hisSpeedValue = 0;
    double hisKcalValue = 0;
    double hisDisValue = 0;

    protected double topBoundary;
    protected double leftBoundary;
    protected double rightBoundary;
    protected double bottomBoundary;
    protected Location locationTopLeft;
    protected Location locationBottomRight;
    protected float maxDistance;
    protected Gps mapCenterPoint;

    // 分享相关
    final UMSocialService mController = UMServiceFactory.getUMSocialService("com.umeng.share");
    UMShakeService mShakeController = UMShakeServiceFactory.getShakeService("com.umeng.share");
    Bitmap shareBitmap = null;
    // wx967daebe835fbeac是你在微信开发平台注册应用的AppID, 这里需要替换成你注册的AppID
    private static final String WEIXIN_APP_ID = "wx967daebe835fbeac";
    private static final String WEIXIN_APP_SECRET = "5fa9e68ca3970e87a1f83e563c8dcbce";

    //数据库
    private static LocationDbHelper dbHelper;
    private SQLiteDatabase sqLiteDatabase;

    //所有原始数据
    private List<DouglasPoint> listPoints = new ArrayList<>();
    //压缩之后的绘制数据
    //private Queue<DouglasPoint> resumeLocationsQueue = new LinkedList<>();
    private List<DouglasPoint> resumeList = new ArrayList<>();

    //用于记录所有的点的速度的集合
//    private List<SpeedPoint> speedPointList = new ArrayList<>();
    private DouglasPoint maxSpeedPoint;
    private DouglasPoint minSpeedPoint;

    //MSG
    private static final int UPDATE_VIEWS = 1;
    private static final int DRAW_RESUME = 3;
    //每次绘制的点数
    private static final int RESUME_ONCE_DRAW_POINTS = 5;
    private static final int LINE_WIDTH = 15;

    private static HashMap<String, String> locationMaps;
    //颜色的数组

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
            }
        } else {
            Toast.makeText(getApplicationContext(), "数据库文件不存在", Toast.LENGTH_SHORT).show();
        }

        //退出按钮
        exitButton = (Button) findViewById(R.id.exitButton);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SPUtils.clearDBName(getApplicationContext());
                finish();
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
                    break;

                case DRAW_RESUME:
                    onResumeDrawImp();
                    break;
            }
        }
    };


    /**
     * onResume的时候真正的绘制流程
     * 每次绘制 RESUME_ONCE_DRAW_POINTS 个点 分批绘制
     */
    private void onResumeDrawImp() {
        Log.i(TAG, "onResumeDrawImp");
        getBoundary();

        //计算并添加最大最小值的marker
        if (listPoints != null && listPoints.size() > 0) {

            //绘制起点和终点的Marker
            mMapView.addOverlay(new PointMarkLayout(PositionUtil.gps84_To_Gcj02(listPoints.get(0)), R.drawable.point_start));
            mMapView.addOverlay(new PointMarkLayout(PositionUtil.gps84_To_Gcj02(listPoints.get(listPoints.size() - 1)), R.drawable.point_end));
            //移动到屏幕中心点
            final Gps centGps = PositionUtil.gps84_To_Gcj02(mapCenterPoint.getWgLat(), mapCenterPoint.getWgLon());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    tencentMap.animateTo(new LatLng(centGps.getWgLat(), centGps.getWgLon()));
                    tencentMap.setZoom(getFixedZoomLevel());
                }
            }, 1000);

            Gps gps = PositionUtil.gps84_To_Gcj02(maxSpeedPoint.getLatitude(), maxSpeedPoint.getLongitude());
            addMarker(new LatLng(gps.getWgLat(), gps.getWgLon()), "最大值");
            Gps gpsMin = PositionUtil.gps84_To_Gcj02(minSpeedPoint.getLatitude(), minSpeedPoint.getLongitude());
            addMarker(new LatLng(gpsMin.getWgLat(), gpsMin.getWgLon()), "最小值");

            Log.i(TAG, "getFixedZoomLevel=" + getFixedZoomLevel());
        }

//        //2个点的方式加入，然后绘制
//        int count = resumeList.size();
//        Log.i(TAG, "count=" + count);
//        PolylineOptions linOptions = new PolylineOptions();
//        linOptions.width(LINE_WIDTH);
//        linOptions.color(0xAAFF0000);
//        for (int i = 0; i < count; i++) {
//            DouglasPoint douglasPoint = resumeList.get(i);
//            Gps gps = PositionUtil.gps84_To_Gcj02(douglasPoint.getLatitude(), douglasPoint.getLongitude());
//            if (gps != null) {
//                linOptions.add(new LatLng(gps.getWgLat(), gps.getWgLon()));
//            }
//        }
//        Polyline line = tencentMap.addPolyline(linOptions);
//        Overlays.add(line);

        //overlay绘制流程
        ColorPathOverlay overlay = new ColorPathOverlay(mMapView,resumeList);
        mMapView.addOverlay(overlay);
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

        //子线程读取数据到集合中
        readDbforDraw();

        Log.i(TAG, "onResume");

        if (mWakeLock != null) {
            mWakeLock.acquire();
        }
    }


    private void readDbforDraw() {
        Log.i(TAG, "readDbforDraw");

        fixedThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {

                Cursor cursor = null;
                try {
                    listPoints.clear();
                    resumeList.clear();

                    int index = 0;

                    long startTime = 0;
                    long lastTime = 0;
                    double allDis = 0;

                    String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
                    //获取数据的cursor
                    cursor = query(PROJECTION, null, null, null);

                    Log.i(TAG, "cursor.getCount() = " + cursor.getCount());
                    if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                        //获取起始时间点
                        startTime = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
                        Log.i(TAG, "startTime=" + startTime);

                        //把所有点记录在集合里面
                        do {
                            int id = cursor.getInt(cursor.getColumnIndex(LocationDbHelper.ID));
                            double latitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                            double longitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                            double insSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.INS_SPEED));
                            float dis = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
                            long time = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));

                            DouglasPoint tmpPoint = new DouglasPoint(latitude, longitude, insSpeed, dis, time, id, index++);
                            listPoints.add(tmpPoint);

                            allDis += dis;
                        } while (cursor.moveToNext());

                        //计算 平均速度 卡路里 距离 发到主线程中
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

                        Log.i(TAG, "压缩前 : listPoints.size()=" + listPoints.size());
                        //对数据进行压缩处理
                        Douglas douglas = new Douglas(listPoints);
                        douglas.compress(listPoints.get(0), listPoints.get(listPoints.size() - 1));
                        for (int i = 0; i < douglas.douglasPoints.size(); i++) {
                            DouglasPoint p = douglas.douglasPoints.get(i);
                            if (p.getIndex() > -1) {
                                //Gps gps = PositionUtil.gps84_To_Gcj02(p.getLatitude(), p.getLongitude());
                                if (p != null) {
                                    //所有数据进入队列
                                    resumeList.add(p);
                                }
                            }
                        }
                        Log.i(TAG, "压缩后 : resumeLocationsQueue.size()=" + resumeList.size());
                    }

                    //speedPointList中取出最大值
                    maxSpeedPoint = resumeList.get(0);
                    minSpeedPoint = resumeList.get(0);

                    for (int i = 0; i < resumeList.size(); i++) {
                        if (maxSpeedPoint.getSpeed() < resumeList.get(i).getSpeed()) {
                            maxSpeedPoint = resumeList.get(i);
                        }

                        if (minSpeedPoint.getSpeed() > resumeList.get(i).getSpeed()) {
                            minSpeedPoint = resumeList.get(i);
                        }
                    }


                    Log.i(TAG, "maxSpeedPoint = " + maxSpeedPoint.getId() + "--->" + maxSpeedPoint.getSpeed());
                    Log.i(TAG, "minSpeedPoint = " + minSpeedPoint.getId() + "--->" + minSpeedPoint.getSpeed());

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
        tencentMap = mMapView.getMap();
        Log.i(TAG, "getMaxZoomLevel=" + tencentMap.getMaxZoomLevel());
        tencentMap.setZoom(tencentMap.getMaxZoomLevel());

        Overlays = new ArrayList<Object>();
    }

    private void updateTextViews(double aveSpeed, double kal, double allDis) {
        DecimalFormat myformat = new DecimalFormat("#0.00");
        hisAveSpeed.setText(myformat.format(aveSpeed) + " km/h");
        hisKal.setText(myformat.format(kal) + " kcal");
        hisDis.setText(myformat.format(allDis) + "km");
    }

    private void addMarker(LatLng pos, String title) {
        Marker markerFix = tencentMap.addMarker(new MarkerOptions()
                .position(pos)
                .title(title)
                .anchor(0.5f, 0.5f)
                .icon(BitmapDescriptorFactory
                        .defaultMarker())
                .draggable(false));
        markerFix.showInfoWindow();// 设置默认显示一个infowinfow
    }

    public Cursor query(String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        sqLiteDatabase = dbHelper.getWritableDatabase();
        SQLiteQueryBuilder sqLiteQueryBuilder = new SQLiteQueryBuilder();
        sqLiteQueryBuilder.setTables(LocationDbHelper.TABLE_NAME);
        sqLiteQueryBuilder.setProjectionMap(locationMaps);

        String orderBy = LocationDbHelper.DEFAULT_ORDERBY;

        Cursor cursor = sqLiteQueryBuilder.query(sqLiteDatabase, projection, selection, selectionArgs, null, null, orderBy);
        return cursor;
    }

    protected void getBoundary() {
        leftBoundary = listPoints.get(0).getLatitude();
        bottomBoundary = listPoints.get(0).getLongitude();

        rightBoundary = listPoints.get(0).getLatitude();
        topBoundary = listPoints.get(0).getLongitude();

        //找到边界点
        for (DouglasPoint location : listPoints) {
            if (leftBoundary > location.getLatitude()) {
                leftBoundary = location.getLatitude();
            }

            if (rightBoundary < location.getLatitude()) {
                rightBoundary = location.getLatitude();
            }

            if (topBoundary < location.getLongitude()) {
                topBoundary = location.getLongitude();
            }

            if (bottomBoundary > location.getLongitude()) {
                bottomBoundary = location.getLongitude();
            }
        }

        //设置边界点
        locationTopLeft = new Location("");
        locationTopLeft.setLongitude(topBoundary);
        locationTopLeft.setLatitude(leftBoundary);

        locationBottomRight = new Location("");
        locationBottomRight.setLongitude(bottomBoundary);
        locationBottomRight.setLatitude(rightBoundary);

        maxDistance = locationTopLeft.distanceTo(locationBottomRight);

        mapCenterPoint = new Gps(
                leftBoundary + (rightBoundary - leftBoundary) / 2,
                bottomBoundary + (topBoundary - bottomBoundary) / 2);
    }

    protected int getFixedZoomLevel() {
        int fixedLatitudeSpan = (int) ((rightBoundary - leftBoundary) * 1e6);
        int fixedLongitudeSpan = (int) ((topBoundary - bottomBoundary) * 1e6);
//            int j =tencentMap.getZoomLevel();
        for (int i = tencentMap.getMaxZoomLevel(); i > 0; i--) {
            tencentMap.setZoom(i);
//	        	j =mMapView.getMap().getZoomLevel();
            int latSpan = mMapView.getProjection().getLatitudeSpan();
            int longSpan = mMapView.getProjection().getLongitudeSpan();

            if (latSpan > fixedLatitudeSpan && longSpan > fixedLongitudeSpan) {
                return i;
            }
        }

        return tencentMap.getMaxZoomLevel();
    }


    private class PointMarkLayout extends Overlay {
        private DouglasPoint location;
        private int drawable;
        private Projection projection;

        PointMarkLayout(DouglasPoint location, int drawable) {
            this.location = location;
            this.drawable = drawable;
        }

        @Override
        public void draw(final Canvas canvas, final MapView mapView) {
            super.draw(canvas, mapView);

            this.projection = mapView.getProjection();
            GeoPoint piont = new GeoPoint((int) (location.getLatitude() * 1e6), (int) (location.getLongitude() * 1e6));
            Point current = projection.toPixels(piont, null);

            Bitmap markerImage = BitmapFactory.decodeResource(getResources(), drawable);

            // 根据实际的条目而定偏移位置
            canvas.drawBitmap(markerImage,
                    current.x - Math.round(markerImage.getWidth() * 0.4),
                    current.y - Math.round(markerImage.getHeight() * 0.9), null);

            return;
        }
    }
}
