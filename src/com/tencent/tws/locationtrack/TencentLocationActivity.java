package com.tencent.tws.locationtrack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
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

public class TencentLocationActivity extends BaseActivity implements TencentLocationListener{

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
	    private HashMap<Long, TencentLocation> locationCache;
	    
	    private final static int ACCURACY = 3;
	    private final static int CACHE_SIZE = 5;
	    private BigDecimal lastLatitude;
	    private BigDecimal lastLongitude;
	    private long getLocationTime = (long)0;
	    @Override
	    protected void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.geolocation);
	        
	        context = this;
	        locationCache = new HashMap<Long, TencentLocation>();
	        this.nameHelper = new ArchiveNameHelper(context);
	        archivName = nameHelper.getNewName();
	        archiver = new Archiver(getApplicationContext());
	        archiver.open(archivName, Archiver.MODE_READ_WRITE);
	        
	        Intent intent = getIntent();  
	        intervalTime = intent.getLongExtra("intervalTime", 1000);
	        
	        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);  
	        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG); 
	        mWakeLock.acquire();
	        txtLat = (TextView) findViewById(R.id.tvLocation);
	        TextView tvGPSStatus = (TextView) findViewById(R.id.tvGPSStatus);
	        
			initMapView();
			Overlays = new ArrayList<Object>();
			
	        btnStartSports = (Button) findViewById(R.id.btnStartSports);
	        btnStartSports.setEnabled(false);
	        btnStartSports.setOnClickListener(new View.OnClickListener(){

				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					Overlays.add(drawPolyline());
				}	
	        });
	        Button btnViewRecords = (Button) findViewById(R.id.btnViewRecords);
		       // btnStartSports.setEnabled(false);
		        btnViewRecords.setOnClickListener(new View.OnClickListener(){

					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
						for(int i=0;i<Overlays.size();i++)
						{
							mMapView.removeOverlay(Overlays.remove(i));
						}
						Overlays.clear();
					}	
		        });
	 
	        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		      //判断是否已经打开GPS模块
		        if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) 
		        {
		        	//GPS模块打开，可以定位操作      
		        	tvGPSStatus.setText("GPS已打开");
		        }
		        else
		        {
		        	tvGPSStatus.setText("GPS已关闭");
		        	  //打开GPS 
		        }
	        
			mLocationManager = TencentLocationManager.getInstance(this);
	    	TencentLocationRequest request = TencentLocationRequest.create();
			request.setInterval(intervalTime);
			mLocationManager.requestLocationUpdates(request, this);		
			locationManager.addGpsStatusListener(statusListener);
	    }
	    
	    @Override
		protected void onResume() {
			super.onResume();
			if(mWakeLock!=null)
			{
				mWakeLock.acquire();
			}
		}

		@Override
		protected void onPause() {
			super.onPause();
			if(mWakeLock!=null)
			{
				mWakeLock.release();
			}
		}
	    
		@Override
		protected void onDestroy() {
			super.onDestroy();
			if(mWakeLock!=null)
			{
				mWakeLock.release();
			}
			mLocationManager.removeUpdates(this);
		}
		
		
		  /**
	     * flush cache
	     */
	    public void flushCache() {
	        Iterator<Long> iterator = locationCache.keySet().iterator();
	        while (iterator.hasNext()) {
	            Long timeMillis = iterator.next();
	            TencentLocation location = locationCache.get(timeMillis);
	            if (archiver.add(location, timeMillis)) {
	                Log.i(TAG,String.format(
	                    "Location(%f, %f) has been saved into database.", location.getLatitude(), location.getLongitude()));
	            }
	        }

	        locationCache.clear();
	    }
		
	    private boolean filter(TencentLocation location) {
	        BigDecimal longitude = (new BigDecimal(location.getLongitude()))
	            .setScale(ACCURACY, BigDecimal.ROUND_HALF_UP);

	        BigDecimal latitude = (new BigDecimal(location.getLatitude()))
	            .setScale(ACCURACY, BigDecimal.ROUND_HALF_UP);

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

			Bitmap bmpMarker = BitmapFactory.decodeResource(getResources(),
					R.drawable.mark_location);
			mLocationOverlay = new LocationOverlay(bmpMarker);
			mMapView.addOverlay(mLocationOverlay);
		}
		
		private Polyline drawPolyline() {
//			final LatLng latLng1 = new LatLng(22.540452, 113.935446);
//			final LatLng latLng2 = new LatLng(22.540549, 113.935044);
			// 如果要修改颜色，请直接使用4字节颜色或定义的变量
			PolylineOptions lineOpt = new PolylineOptions();
			lineOpt.color(0xAAFF0000);
//			lineOpt.add(latLng1);
//			lineOpt.add(latLng2);
			HashMap<Double,Double> mParamsLocation = LocationUtil.getLocationTrack();
			
			for(Object key :mParamsLocation.keySet() )
			{
				final LatLng latLng = new LatLng(mParamsLocation.get(key), (double) key);
				lineOpt.add(latLng);
				
				Log.d("guccigu","经度 = " + key + "，维度 = " +mParamsLocation.get(key));
			}
			
			Polyline line = mMapView.getMap().addPolyline(lineOpt);
			return line;
		}
		
		private static GeoPoint of(Location location) {
			GeoPoint ge = new GeoPoint((int) (location.getLatitude() * 1E6),
					(int) (location.getLongitude() * 1E6));
			return ge;
		}
		
		private static GeoPoint of(TencentLocation location) {
			GeoPoint ge = new GeoPoint((int) (location.getLatitude() * 1E6),
					(int) (location.getLongitude() * 1E6));
			return ge;
		}
	    
		@Override
		public void onLocationChanged(TencentLocation location, int arg1,
				String arg2) {
			// TODO Auto-generated method stub
			txtLat = (TextView) findViewById(R.id.tvLocation);
			
			TextView getIntervalTime = (TextView) findViewById(R.id.getIntervalTime);
			  txtLat.setText("维度:" + location.getLatitude() + ",经度:"
		                + location.getLongitude() + ",时间 :" +LocationUtil.convert(location.getTime()));
		        
		        if(getLocationTime != location.getTime())
		        {
		        	getIntervalTime.setText("获取间隔时间："+String.valueOf((location.getTime()-getLocationTime)/1000));
		        }
	        getLocationTime = location.getTime();
	        
//	        if (filter(location)) 
	        {
	            locationCache.put(System.currentTimeMillis(), location);
	            if (locationCache.size() > CACHE_SIZE) {
	                flushCache();
	            }
	            // 计算动态路径
	            this.meta = archiver.getMeta();
	            if (meta != null) {
	                meta.setRawDistance();
	            }
	            
	            LocationUtil.getLocationTrack().put(location.getLongitude(),location.getLatitude());
		        
		        btnStartSports.setEnabled(true);
		        
		        mMapView.getController().animateTo(of(location));
		        
		        mLocationOverlay.setAccuracy(location.getAccuracy());
		        mLocationOverlay.setGeoCoords(of(location));
		        mMapView.invalidate();
		        
		        if(location.getLatitude()>0&&location.getLongitude()>0)
		        {
			        LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
			        points.add(latLng);
		        }
		        
		        if (points.size() == 2) {
					// 这里绘制起点
		        	PolylineOptions lineOpt = new PolylineOptions();
					lineOpt.color(0xAAFF0000);
					for( LatLng point :points)
					{
						lineOpt.add(point);
					}
					mMapView.getMap().addPolyline(lineOpt);
					Overlays.add(lineOpt);
				}
				if (points.size() > 2 && points.size() <= 10) {
					PolylineOptions lineOpt = new PolylineOptions();
					lineOpt.color(0xAAFF0000);
					for( LatLng point :points)
					{
						lineOpt.add(point);
					}
					mMapView.getMap().addPolyline(lineOpt);
					Overlays.add(lineOpt);
				}
	//
//				if (points.size() > 10) {
//					// 每次绘制10个点，这样应该不会出现明显的折线吧
//					points_tem = points.subList(points.size() - 10, points.size());
//					// 绘图
//					
//					PolylineOptions lineOpt = new PolylineOptions();
//					lineOpt.color(0xAAFF0000);
//					for( LatLng point :points_tem)
//					{
//						lineOpt.add(point);
//					}
//					mMapView.getMap().addPolyline(lineOpt);
//					Overlays.add(lineOpt);
//				}
	        }
	        
	       
		}

		@Override
		public void onStatusUpdate(String arg0, int arg1, String arg2) {
			// TODO Auto-generated method stub
			
		}
	 
		
		 private final GpsStatus.Listener statusListener = new GpsStatus.Listener() 
		    {
				@Override
				public void onGpsStatusChanged(int event) {
					// TODO Auto-generated method stub
					//GPS状态变化时的回调，获取当前状态
					GpsStatus status = locationManager.getGpsStatus(null);
					
					//获取卫星相关数据
					GetGPSStatus(event,status);
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
				String strSatelliteNum = this.getString(R.string.satellite_num) +mSatelliteNum;
				TextView tv = (TextView) findViewById(R.id.tvSatelliteNum);
				tv.setText(strSatelliteNum);
				
			} else if (event == GpsStatus.GPS_EVENT_STARTED) {
				// 定位启动
			} else if (event == GpsStatus.GPS_EVENT_STOPPED) {
				// 定位结束
			}
		}
	
	}

