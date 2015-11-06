package com.tencent.tws.locationtrack;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.mapsdk.raster.model.GeoPoint;
import com.tencent.mapsdk.raster.model.LatLng;
import com.tencent.mapsdk.raster.model.Polyline;
import com.tencent.mapsdk.raster.model.PolylineOptions;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.MyContentProvider;
import com.tencent.tws.locationtrack.database.SPUtils;
import com.tencent.tws.locationtrack.util.Gps;
import com.tencent.tws.locationtrack.util.LocationUtil;
import com.tencent.tws.locationtrack.util.PositionUtil;
import com.tencent.tws.locationtrack.views.CustomShareBoard;
import com.tencent.tws.widget.BaseActivity;
import com.umeng.socialize.controller.UMServiceFactory;
import com.umeng.socialize.controller.UMSocialService;
import com.umeng.socialize.media.UMImage;
import com.umeng.socialize.sso.QZoneSsoHandler;
import com.umeng.socialize.sso.UMQQSsoHandler;
import com.umeng.socialize.weixin.controller.UMWXHandler;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LocationActivity extends BaseActivity {

	private static final String TAG = "LocationActivity";

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
	private long getLocationTime = (long) 0;

	private TextView tvAveSpeed;
	private TextView tvInsSpeed;
	private Button clearButton;
	private Button exitButton;
	private Button shareButton;

	private TextView tvKal;
	private TextView tvGPSStatus;
	private TextView tvLocation;

	private DBContentObserver mDBContentObserver;

	private Cursor cursor;
	double insSpeed = 0;
	double aveSpeed = 0;
	double kcal = 0;
	private boolean isFinishDBDraw = true;

	// 分享初始化控制器
	final UMSocialService mController = UMServiceFactory.getUMSocialService("com.umeng.share");

	// wx967daebe835fbeac是你在微信开发平台注册应用的AppID, 这里需要替换成你注册的AppID
	private static final String WEIXIN_APP_ID = "wx967daebe835fbeac";
	private static final String WEIXIN_APP_SECRET = "5fa9e68ca3970e87a1f83e563c8dcbce";


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.geolocation);

		//初始化分享平台内容
		initSharePlatform();

		//不灭屏
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
		mWakeLock.acquire();

		tvLocation = (TextView) findViewById(R.id.tvLocation);
		tvGPSStatus = (TextView) findViewById(R.id.tvGPSStatus);
		tvAveSpeed = (TextView) findViewById(R.id.ave_speed);
		tvInsSpeed = (TextView) findViewById(R.id.ins_speed);
		tvKal = (TextView) findViewById(R.id.kal);
		//地图
		initMapView();

		//启动后台服务获取地理信息
		Intent serviceIntent = new Intent(this, LocationService.class);
		serviceIntent.putExtra("intervalTime", getIntent().getLongExtra("intervalTime", 1000));
		startService(serviceIntent);
		Log.i("LocationService", "LocationService 启动");

		initContentObserver();

		//清屏按钮注册
		clearButton = (Button) findViewById(R.id.clearButton);
		clearButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mMapView.clearAllOverlays();
			}
		});

		exitButton = (Button) findViewById(R.id.exitButton);
		exitButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				SPUtils.clearSp(getApplicationContext());
			}
		});

		shareButton = (Button) findViewById(R.id.btnShare);
		shareButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				//mController.openShare(LocationActivity.this, false);
				postShare();
			}
		});


		//判断GPS是否打开
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			tvGPSStatus.setText("GPS已打开");
		} else {
			tvGPSStatus.setText("GPS已关闭");
		}
		//注册GPS监听回调
		locationManager.addGpsStatusListener(statusListener);
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
		// 设置分享内容
		mController.setShareContent("瞬时速度=" + insSpeed + " 平均速度=" + aveSpeed + " 能量=" + kcal);
		// 设置分享图片, 参数2为图片的url地址
		mController.setShareMedia(new UMImage(this, "http://i6.topit.me/6/5d/45/1131907198420455d6o.jpg"));


		CustomShareBoard shareBoard = new CustomShareBoard(this);
		shareBoard.showAtLocation(this.getWindow().getDecorView(), Gravity.BOTTOM, 0, 0);
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
		super.onResume();

		Log.i(TAG, "onResume");
		if (SPUtils.readSp(getApplicationContext()) != "") {//数据库是存在的
			if (mMapView != null) {
				Log.i(TAG, "clearAllOverlays");
				mMapView.clearAllOverlays();
			}
			dbDrawResume();
			isFinishDBDraw = true;
		}

		if (mWakeLock != null) {
			mWakeLock.acquire();
		}
	}

	//读取数据库，绘制数据库中所有数据
	private void dbDrawResume() {
		String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
		cursor = getApplicationContext().getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);

		points.clear();

		if (cursor.moveToFirst()) {
			while (cursor.moveToNext()) {
				double latitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
				double longitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
				long times = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
				double insSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.INS_SPEED));
				float aveSpeed = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.AVG_SPEED));
				float kcal = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.KCAL));
				float accuracy = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY));

				//updateTextViews(longitude, latitude, times, insSpeed, aveSpeed, kcal);
				Gps gps = PositionUtil.gps84_To_Gcj02(latitude, longitude);
				drawLines(gps.getWgLon(), gps.getWgLat(), accuracy, true);
			}
		}


	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mWakeLock != null) {
			mWakeLock.release();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (cursor != null) {
			cursor.close();
		}

		if (mWakeLock != null) {
			mWakeLock.release();
		}
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
		mMapView = (MapView) findViewById(R.id.mapviewOverlay);
		// mMapView.setBuiltInZoomControls(true);
		mMapView.getController().setZoom(50);

		Bitmap bmpMarker = BitmapFactory.decodeResource(getResources(), R.drawable.mark_location);
		mLocationOverlay = new LocationOverlay(bmpMarker);
		mMapView.addOverlay(mLocationOverlay);

		Overlays = new ArrayList<Object>();
	}

//	private Polyline drawPolyline() {
//		// final LatLng latLng1 = new LatLng(22.540452, 113.935446);
//		// final LatLng latLng2 = new LatLng(22.540549, 113.935044);
//		// 如果要修改颜色，请直接使用4字节颜色或定义的变量
//		PolylineOptions lineOpt = new PolylineOptions();
//		lineOpt.color(0xAAFF0000);
//		// lineOpt.add(latLng1);
//		// lineOpt.add(latLng2);
//		HashMap<Double, Double> mParamsLocation = LocationUtil.getLocationTrack();
//
//		for (Object key : mParamsLocation.keySet()) {
//			final LatLng latLng = new LatLng(mParamsLocation.get(key), (double) key);
//			lineOpt.add(latLng);
//
//			Log.d("guccigu", "经度 = " + key + "，维度 = " + mParamsLocation.get(key));
//		}
//
//		Polyline line = mMapView.getMap().addPolyline(lineOpt);
//		return line;
//	}


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

	private void updateTextViews(double longitude, double latitude, long time, double insSpeed, double aveSpeed, double kal) {
		TextView getIntervalTime = (TextView) findViewById(R.id.getIntervalTime);
		tvLocation.setText("维度:" + latitude + ",经度:" + longitude + ",时间 :" + LocationUtil.convert(time));

		if (getLocationTime != time) {
			getIntervalTime.setText("获取间隔时间：" + String.valueOf((time - getLocationTime) / 1000));
		}
		getLocationTime = time;

		DecimalFormat myformat = new DecimalFormat("#0.00");
		tvInsSpeed.setText(myformat.format(insSpeed) + " km/h");
		tvAveSpeed.setText(myformat.format(aveSpeed) + " km/h");
		tvKal.setText(myformat.format(kal));
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


	private class DBContentObserver extends ContentObserver {

		public DBContentObserver(Handler handler) {
			super(handler);
		}

		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};

			cursor = getApplicationContext().getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);
			if (cursor.moveToLast()) {
				double latitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
				double longitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
				long times = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
				insSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.INS_SPEED));
				aveSpeed = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.AVG_SPEED));
				kcal = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.KCAL));
				float accuracy = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY));

				updateTextViews(longitude, latitude, times, insSpeed, aveSpeed, kcal);
				if (isFinishDBDraw == false) {
					Gps gps = PositionUtil.gps84_To_Gcj02(latitude, longitude);
					LatLng latLng = new LatLng(gps.getWgLon(), gps.getWgLat());
					points.add(latLng);
				} else {
					Gps gps = PositionUtil.gps84_To_Gcj02(latitude, longitude);
					drawLines(gps.getWgLon(), gps.getWgLat(), accuracy, true);
				}
			}
		}

		@Override
		public boolean deliverSelfNotifications() {
			return true;
		}
	}
}
