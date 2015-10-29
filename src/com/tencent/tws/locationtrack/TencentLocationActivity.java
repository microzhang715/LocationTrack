package com.tencent.tws.locationtrack;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;
import com.tencent.mapsdk.raster.model.GeoPoint;
import com.tencent.mapsdk.raster.model.LatLng;
import com.tencent.mapsdk.raster.model.Polyline;
import com.tencent.mapsdk.raster.model.PolylineOptions;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tws.locationtrack.record.ArchiveMeta;
import com.tencent.tws.locationtrack.record.ArchiveNameHelper;
import com.tencent.tws.locationtrack.record.Archiver;
import com.tencent.tws.locationtrack.util.LocationUtil;
import com.tencent.tws.widget.BaseActivity;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;

public class TencentLocationActivity extends BaseActivity implements TencentLocationListener {

	private static final String TAG = "TencentLocationActivity";

	protected LocationManager locationManager;
	protected LocationListener locationListener;
	protected Context context;
	TextView txtLat;
	String lat;
	String provider;
	protected String latitude, longitude;
	protected boolean gps_enabled, network_enabled;

	private TencentLocationManager mLocationManager;

	private MapView mMapView;
	private LocationOverlay mLocationOverlay;
	private List<Object> Overlays;

	Button btnStartSports;

	WakeLock mWakeLock;

	List<LatLng> points = new ArrayList<LatLng>();
	List<LatLng> points_tem = new ArrayList<LatLng>();

	int mSatelliteNum;
	private ArrayList<GpsSatellite> numSatelliteList = new ArrayList<>();

	private Long intervalTime = (long) 1000;

	private ArchiveNameHelper nameHelper;
	private String archivName;
	private Archiver archiver;
	private ArchiveMeta meta = null;
	private HashMap<Long, TencentLocation> tencentLocationCache;

	private final static int ACCURACY = 3;
	private final static int CACHE_SIZE = 2;
	private BigDecimal lastLatitude;
	private BigDecimal lastLongitude;
	private long getLocationTime = (long) 0;

	private HashMap<Long, Location> locationCache;
	private ArrayList<Location> hisLocations;

	private TextView aveSpeed;
	private TextView insSpeed;
	private long startTime = 0;
	private Long currentTime;
	private Button clearButton;

	private TextView kalTextView;
	private Handler handler;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.geolocation);

		Log.d("kermit", "onCreate");
		context = this;
		tencentLocationCache = new HashMap<Long, TencentLocation>();
		locationCache = new HashMap<Long, Location>();

		//数据库
		this.nameHelper = new ArchiveNameHelper(context);
		// archivName = nameHelper.getMyDBName();
		archivName = nameHelper.getNewName();
		archiver = new Archiver(getApplicationContext());
		archiver.open(archivName, Archiver.MODE_READ_WRITE);

		//获取参数
		Intent intent = getIntent();
		intervalTime = intent.getLongExtra("intervalTime", 1000);

		//启动定时器
		timer.schedule(task, 0, 1000); // 1s后执行task,经过1s再次执行

		//gps状态
		txtLat = (TextView) findViewById(R.id.tvLocation);
		TextView tvGPSStatus = (TextView) findViewById(R.id.tvGPSStatus);
		// 平均\瞬时速度
		aveSpeed = (TextView) findViewById(R.id.ave_speed);
		insSpeed = (TextView) findViewById(R.id.ins_speed);

		kalTextView = (TextView) findViewById(R.id.kal);

		//地图
		initMapView();
		Overlays = new ArrayList<Object>();

		//不灭屏
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
		mWakeLock.acquire();


		clearButton = (Button) findViewById(R.id.clearButton);
		clearButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mMapView.clearAllOverlays();
			}
		});

		//按钮监听
		btnStartSports = (Button) findViewById(R.id.btnStartSports);
		btnStartSports.setEnabled(false);
		btnStartSports.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				Overlays.add(drawPolyline());
			}
		});


		Button btnViewRecords = (Button) findViewById(R.id.btnViewRecords);
		// btnStartSports.setEnabled(false);
		btnViewRecords.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// TODO Auto-generated method stub
				mMapView.clearAllOverlays();
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
		mLocationManager = TencentLocationManager.getInstance(this);
		TencentLocationRequest request = TencentLocationRequest.create();
		request.setInterval(intervalTime);
		mLocationManager.requestLocationUpdates(request, this);
		locationManager.addGpsStatusListener(statusListener);
	}

	@Override
	protected void onResume() {
		super.onResume();

		handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				// TODO Auto-generated method stub
				switch (msg.what) {
					case 1:
						Bundle bundle = (Bundle) msg.obj;
						float speed = bundle.getFloat("speed");
						double kcal = bundle.getDouble("kcak");

						DecimalFormat myformat = new DecimalFormat("#0.00");
						aveSpeed.setText(myformat.format(speed * 3600 / 1000)  + " km/h");
						kalTextView.setText(myformat.format(kcal) + " kcal");
						break;

					default:
						break;
				}
			}
		};

		// 数据库不为空,则进行绘制
		if (archiver.fetchAll().size() != 0) {
			//清除屏幕上所有标注
			mMapView.clearAllOverlays();
			//从数据库读取数据进行绘制
			drawLines(null, true);
		}

		if (mWakeLock != null) {
			mWakeLock.acquire();
		}
	}

	Timer timer = new Timer();
	TimerTask task = new TimerTask() {
		@Override
		public void run() {
			if (archiver.getFirstRecord() != null) {
				//封装平均速度
				if (startTime == 0) {
					startTime = archiver.getFirstRecord().getTime();
					Log.d("kermit", "startTime = " + startTime);
				}
				currentTime = System.currentTimeMillis();
				long detTime = currentTime - startTime;
				float distance = meta.getRawDistance();
				float speed = distance / detTime;

				//封装卡路里 跑步热量（kcal）＝体重（kg）×距离（公里）×1.036 默认体重60KG
				double kcak = distance / 1000 * 60 * 1.036;

				Message msg = Message.obtain();
				msg.what = 1;
				Bundle bundle = new Bundle();
				bundle.putFloat("speed", speed);
				bundle.putDouble("kcak", kcak);

				msg.obj = bundle;
				handler.sendMessage(msg);


			}
		}
	};

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
		if (mWakeLock != null) {
			mWakeLock.release();
		}
		mLocationManager.removeUpdates(this);
	}

	/**
	 * flush cache
	 */
	public void flushCache() {
		Iterator<Long> iterator = tencentLocationCache.keySet().iterator();
		while (iterator.hasNext()) {
			Long timeMillis = iterator.next();
			TencentLocation location = tencentLocationCache.get(timeMillis);
			if (archiver.add(location, timeMillis)) {
				Log.i(TAG, String.format("Location(%f, %f) has been saved into database.", location.getLatitude(), location.getLongitude()));
			}
		}

		tencentLocationCache.clear();
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
	}

	private Polyline drawPolyline() {
		// final LatLng latLng1 = new LatLng(22.540452, 113.935446);
		// final LatLng latLng2 = new LatLng(22.540549, 113.935044);
		// 如果要修改颜色，请直接使用4字节颜色或定义的变量
		PolylineOptions lineOpt = new PolylineOptions();
		lineOpt.color(0xAAFF0000);
		// lineOpt.add(latLng1);
		// lineOpt.add(latLng2);
		HashMap<Double, Double> mParamsLocation = LocationUtil.getLocationTrack();

		for (Object key : mParamsLocation.keySet()) {
			final LatLng latLng = new LatLng(mParamsLocation.get(key), (double) key);
			lineOpt.add(latLng);

			Log.d("guccigu", "经度 = " + key + "，维度 = " + mParamsLocation.get(key));
		}

		Polyline line = mMapView.getMap().addPolyline(lineOpt);
		return line;
	}

	private static GeoPoint of(Location location) {
		GeoPoint ge = new GeoPoint((int) (location.getLatitude() * 1E6), (int) (location.getLongitude() * 1E6));
		return ge;
	}

	private static GeoPoint of(TencentLocation location) {
		GeoPoint ge = new GeoPoint((int) (location.getLatitude() * 1E6), (int) (location.getLongitude() * 1E6));
		return ge;
	}

	@Override
	public void onLocationChanged(TencentLocation location, int arg1, String arg2) {
		updateTextViews(location);
		// 绘制流程
		drawLines(location, false);
	}

	private void drawLines(TencentLocation location, boolean isFromDB) {
		if (isFromDB) {
			if (archiver != null) {
				// 获取所有历史数据
				hisLocations = archiver.fetchAll();
				Log.d("kermit", "hisLocations size = " + hisLocations.size());
				points.clear();

				for (int i = 0; i < hisLocations.size(); i++) {
					Location tmpLocation = hisLocations.get(i);
					updateLocation(tmpLocation);
				}
			}
		} else {
			updateTencentLocation(location);
		}
	}

	private void updateLocation(Location location) {

		if (/*filter(location)*/ true) {
			locationCache.put(System.currentTimeMillis(), location);
			if (tencentLocationCache.size() > CACHE_SIZE) {
				flushCache();
			}
			// 计算动态路径
			this.meta = archiver.getMeta();
			if (meta != null) {
				meta.setRawDistance();
			}

			LocationUtil.getLocationTrack().put(location.getLongitude(), location.getLatitude());

			mMapView.getController().animateTo(of(location));

			mLocationOverlay.setAccuracy(location.getAccuracy());
			mLocationOverlay.setGeoCoords(of(location));
			mMapView.invalidate();

			if (location.getLatitude() > 0 && location.getLongitude() > 0) {
				LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
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
		}
	}

	private void updateTencentLocation(TencentLocation location) {
		if (/*filter(location)*/ true) {
			tencentLocationCache.put(System.currentTimeMillis(), location);
			if (tencentLocationCache.size() > CACHE_SIZE) {
				flushCache();
			}
			// 计算动态路径
			this.meta = archiver.getMeta();
			if (meta != null) {
				meta.setRawDistance();
			}

			LocationUtil.getLocationTrack().put(location.getLongitude(), location.getLatitude());

			mMapView.getController().animateTo(of(location));

			mLocationOverlay.setAccuracy(location.getAccuracy());
			mLocationOverlay.setGeoCoords(of(location));
			mMapView.invalidate();

			if (location.getLatitude() > 0 && location.getLongitude() > 0) {
				LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
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
		}
	}

	private void updateTextViews(TencentLocation location) {
		txtLat = (TextView) findViewById(R.id.tvLocation);

		TextView getIntervalTime = (TextView) findViewById(R.id.getIntervalTime);
		txtLat.setText("维度:" + location.getLatitude() + ",经度:" + location.getLongitude() + ",时间 :" + LocationUtil.convert(location.getTime()));

		if (getLocationTime != location.getTime()) {
			getIntervalTime.setText("获取间隔时间：" + String.valueOf((location.getTime() - getLocationTime) / 1000));
		}
		getLocationTime = location.getTime();

		btnStartSports.setEnabled(true);
		DecimalFormat myformat = new DecimalFormat("#0.00");
		// 获取瞬时速度
		if (insSpeed != null) {
			insSpeed.setText(myformat.format(location.getSpeed() * 3600 / 1000) + " km/h");
		}
	}

	@Override
	public void onStatusUpdate(String arg0, int arg1, String arg2) {
		// TODO Auto-generated method stub

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
		Log.d(TAG, "enter the updateGpsStatus()");
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

				Log.d(TAG, "updateGpsStatus----count=" + count);
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

}
