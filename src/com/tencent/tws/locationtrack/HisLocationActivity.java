package com.tencent.tws.locationtrack;

import android.app.Activity;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
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
import com.tencent.tws.locationtrack.douglas.DouglasPoint;
import com.tencent.tws.locationtrack.util.Gps;
import com.tencent.tws.locationtrack.util.PointsAnalysis;
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
    private List<DouglasPoint> listPoints = new ArrayList<DouglasPoint>();
    //压缩之后的绘制数据
    private List<DouglasPoint> resumeList = new ArrayList<DouglasPoint>();
    private PointsAnalysis pointsAnalysis;

    DouglasPoint maxSpeedPoint;
    DouglasPoint minSpeedPoint;
    //MSG
    private static final int UPDATE_VIEWS = 1;
    private static final int DRAW_RESUME = 3;

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
            mMapView.addOverlay(new PointMarkLayout(PositionUtil.gps84ToGcj02(listPoints.get(0).getLatitude(), listPoints.get(0).getLongitude()), R.drawable.point_start));
            mMapView.addOverlay(new PointMarkLayout(PositionUtil.gps84ToGcj02(listPoints.get(listPoints.size() - 1).getLatitude(), listPoints.get(listPoints.size() - 1).getLongitude()), R.drawable.point_end));
            //移动到屏幕中心
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

        //overlay绘制流程
        ColorPathOverlay overlay = new ColorPathOverlay(mMapView, resumeList);
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
                try {
                    listPoints.clear();
                    resumeList.clear();

                    //获取所有点
                    pointsAnalysis = new PointsAnalysis(getApplicationContext());
                    listPoints = pointsAnalysis.getAllPointsFromHelper(dbHelper);

                    //获取显示的值
                    hisDisValue = pointsAnalysis.getAllDis(listPoints);
                    hisSpeedValue = pointsAnalysis.getAvgSpeed(listPoints);
                    hisKcalValue = pointsAnalysis.getKcal(listPoints);
                    //更新UI
                    Message msg = Message.obtain();
                    msg.what = UPDATE_VIEWS;
                    Bundle bundle = new Bundle();
                    bundle.putDouble("hisDisValue", hisDisValue);
                    bundle.putDouble("hisSpeedValue", hisSpeedValue);
                    bundle.putDouble("hisKcalValue", hisKcalValue);
                    msg.obj = bundle;
                    handler.sendMessage(msg);

                    //获取压缩后的数据值
                    resumeList = pointsAnalysis.getResumeList(listPoints);

                    //获取最大最小速度点
                    maxSpeedPoint = pointsAnalysis.getMaxInsSpeedPoint(listPoints);
                    minSpeedPoint = pointsAnalysis.getMinInsSpeedPoint(listPoints);
                    Log.i(TAG, "maxSpeedPoint = " + maxSpeedPoint.getId() + "--->" + maxSpeedPoint.getInsSpeed());
                    Log.i(TAG, "minSpeedPoint = " + minSpeedPoint.getId() + "--->" + minSpeedPoint.getInsSpeed());
                    handler.sendEmptyMessage(DRAW_RESUME);
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
        private LatLng location;
        private int drawable;
        private Projection projection;

        PointMarkLayout(LatLng location, int drawable) {
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
