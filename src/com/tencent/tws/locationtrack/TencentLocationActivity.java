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
import com.tencent.tws.locationtrack.util.LocationUtil;
import com.tencent.tws.widget.BaseActivity;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TencentLocationActivity extends BaseActivity {

	private static final String TAG = "TencentLocationActivity";

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

	private TextView tvKal;
	private TextView tvGPSStatus;
	private TextView tvLocation;

	private DBContentObserver mDBContentObserver;

	private Cursor cursor;

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
		//地图
		initMapView();

		//启动后台服务获取地理信息
		Intent serviceIntent = new Intent(this, TencentLocationService.class);
		serviceIntent.putExtra("intervalTime", getIntent().getLongExtra("intervalTime", 1000));
		startService(serviceIntent);
		Log.i("TencentLocationService", "TencentLocationService 启动");

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

		//判断GPS是否打开
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
			tvGPSStatus.setText("GPS已打开");
		} else {
			tvGPSStatus.setText("GPS已关闭");
		}
		//注册GPS监听回调
		locationManager.addGpsStatusListener(statusListener);
	}

	private void initContentObserver() {
		mDBContentObserver = new DBContentObserver(new Handler());
		getContentResolver().registerContentObserver(MyContentProvider.CONTENT_URI, true, mDBContentObserver);
	}

	@Override
	protected void onResume() {
		mMapView.onResume();
		super.onResume();

		if (SPUtils.readSp(getApplicationContext()) != "") {//数据库是存在的
			dbDrawResume();
		}

		if (mWakeLock != null) {
			mWakeLock.acquire();
		}
	}

	//读取数据库，绘制数据库中所有数据
	private void dbDrawResume() {
		String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
		cursor = getApplicationContext().getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);
		if (cursor.moveToFirst()) {
			for (int i = 0; i < cursor.getCount(); i++) {
				cursor.moveToPosition(i);
				double latitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
				double longitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
				long times = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
				double insSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.INS_SPEED));
				float aveSpeed = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.AVG_SPEED));
				float kcal = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.KCAL));
				float accuracy = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY));

				updateTextViews(longitude, latitude, times, insSpeed, aveSpeed, kcal);
				drawLines(longitude, latitude, accuracy, true);
			}
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

	private void updateTextViews(double longitude, double latitude, long time, double insSpeed, float aveSpeed, float kal) {
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
				double insSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.INS_SPEED));
				float aveSpeed = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.AVG_SPEED));
				float kcal = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.KCAL));
				float accuracy = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY));

//				Log.i("kermit", "latitude = " + latitude + " | " + "longitude = " + longitude);
				updateTextViews(longitude, latitude, times, insSpeed, aveSpeed, kcal);
				drawLines(longitude, latitude, accuracy, true);
			}
		}

		@Override
		public boolean deliverSelfNotifications() {
			return true;
		}
	}
}
