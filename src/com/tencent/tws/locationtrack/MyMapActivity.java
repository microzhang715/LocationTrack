package com.tencent.tws.locationtrack;

import java.util.ArrayList;
import java.util.List;

import com.tencent.map.geolocation.TencentLocation;
import com.tencent.map.geolocation.TencentLocationListener;
import com.tencent.map.geolocation.TencentLocationManager;
import com.tencent.map.geolocation.TencentLocationRequest;
import com.tencent.mapsdk.raster.model.GeoPoint;
import com.tencent.mapsdk.raster.model.LatLng;
import com.tencent.mapsdk.raster.model.Polyline;
import com.tencent.mapsdk.raster.model.PolylineOptions;
import com.tencent.tencentmap.mapsdk.map.MapActivity;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tencentmap.mapsdk.map.Overlay;
import com.tencent.tencentmap.mapsdk.map.Projection;
import com.tencent.tws.locationtrack.util.DoublePoint;
import com.tencent.tws.locationtrack.util.LocationUtil;
import com.tencent.tws.locationtrack.util.SensorUtil;

import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Paint.Style;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.os.Build;

public class MyMapActivity extends MapActivity implements
		TencentLocationListener,SensorEventListener{

	private TextView mStatus;
	private MapView mMapView;
	private LocationOverlay mLocationOverlay;

	private TencentLocation mLocation;
	private TencentLocationManager mLocationManager;

	// 用于记录定位参数, 以显示到 UI
	private String mRequestParams;

	private List<Object> Overlays;
	
	//private List<String>  Listlocation = new ArrayList<String>();
	
	private SensorUtil SU;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		 
		SU = new SensorUtil(this);
		LocationUtil.init(true);
		SU.registerListeners();

		mStatus = (TextView) findViewById(R.id.status);
		mStatus.setTextColor(Color.RED);
		initMapView();

		mLocationManager = TencentLocationManager.getInstance(this);
		// 设置坐标系为 gcj-02, 缺省坐标为 gcj-02, 所以通常不必进行如下调用
		mLocationManager
				.setCoordinateType(TencentLocationManager.COORDINATE_TYPE_GCJ02);
		
		Overlays = new ArrayList<Object>();

	}

	private Polyline drawPolyline() {
//		final LatLng latLng1 = new LatLng(22.540552, 113.935446);
//		final LatLng latLng2 = new LatLng(22.540549, 113.935044);

		// 如果要修改颜色，请直接使用4字节颜色或定义的变量
		PolylineOptions lineOpt = new PolylineOptions();
		
		for(String strLocation: LocationUtil.getListLocation())
		{
			int index = strLocation.indexOf(",");
			if(index !=-1)
			{
				String strX = strLocation.substring(0, index);
				String strY = strLocation.substring(index+1, strLocation.length());
				double long_la[] = LocationUtil.GaussProjInvCal(Double.parseDouble(strY), Double.parseDouble(strX));
				final LatLng latLng = new LatLng(long_la[1], long_la[0]);
				lineOpt.add(latLng);
				
				Log.d("guccigu","经度 = " + long_la[0] + "，维度 = " +long_la[1]);
			}

		}
//  	lineOpt.add(latLng1);
//		lineOpt.add(latLng2);

		
		Polyline line = mMapView.getMap().addPolyline(lineOpt);
		return line;
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

	@Override
	protected void onResume() {
		super.onResume();
		startLocation();
	}

	@Override
	protected void onPause() {
		super.onPause();
		stopLocation();
	}

	// ===== view listeners
	public void myLocation(View view) {
		if (mLocation != null) {
			mMapView.getController().animateTo(of(mLocation));
		}
	}
	
	public void locationTrack(View view) {
		stopLocation();
		Overlays.add(drawPolyline());
	}
	
	public void deleteLocationTrack(View view) {
		if(Overlays.size()!=0)
		{
			mMapView.removeOverlay(Overlays.remove(0));
			Overlays.clear();
		}
	}

	// ===== view listeners

	// ====== location callback

	@Override
	public void onLocationChanged(TencentLocation location, int error,
			String reason) {
		if (error == TencentLocation.ERROR_OK) {
			mLocation = location;
			if (mLocation != null) {
				mMapView.getController().animateTo(of(mLocation));
				
			}
			// 定位成功
			StringBuilder sb = new StringBuilder();
			sb.append("定位参数=").append(mRequestParams).append("\n");
			sb.append("(纬度=").append(location.getLatitude()).append(",经度=")
					.append(location.getLongitude()).append(",精度=")
					.append(location.getAccuracy()).append("), 来源=")
					.append(location.getProvider()).append(", 地址=")
					.append(location.getAddress());
			
			double long_laNum[] = LocationUtil.GaussProjCal(location.getLongitude(), location.getLatitude());
			
			double locationY = Double.parseDouble((int)long_laNum[2] + ""+ long_laNum[0]);
			double locationX = long_laNum[1];
			
			LocationUtil.setStartLocation(new DoublePoint(locationX,locationY));
			LocationUtil.getBreadCrumbs().add(new DoublePoint(locationX,locationY));
			LocationUtil.getListLocation().add(locationX + "," +locationY);
			
			// 更新 status
			mStatus.setText(sb.toString());

			// 更新 location 图层
			mLocationOverlay.setAccuracy(mLocation.getAccuracy());
			mLocationOverlay.setGeoCoords(of(mLocation));
			mMapView.invalidate();
			mLocationManager.removeUpdates(this);
		}
	}

	@Override
	public void onStatusUpdate(String name, int status, String desc) {
		// ignore
	}

	// ====== location callback

	private void startLocation() {
		TencentLocationRequest request = TencentLocationRequest.create();
		request.setInterval(30000);
		mLocationManager.requestLocationUpdates(request, this);

		mRequestParams = request.toString() + ", 坐标系="
				+ mLocationManager.getCoordinateType();
	}

	private void stopLocation() {
		mLocationManager.removeUpdates(this);
	}

	// ====== util methods

	private static GeoPoint of(TencentLocation location) {
		GeoPoint ge = new GeoPoint((int) (location.getLatitude() * 1E6),
				(int) (location.getLongitude() * 1E6));
		return ge;
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)  {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		if(LocationUtil.getStartLocation().getX()!=0||LocationUtil.getStartLocation().getY()!=0)
		{
			SU.routeEvent(event);
		}
	}
}

class LocationOverlay extends Overlay {

	GeoPoint geoPoint;
	Bitmap bmpMarker;
	float fAccuracy = 0f;

	public LocationOverlay(Bitmap mMarker) {
		bmpMarker = mMarker;
	}

	public void setGeoCoords(GeoPoint point) {
		if (geoPoint == null) {
			geoPoint = new GeoPoint(point.getLatitudeE6(),
					point.getLongitudeE6());
		} else {
			geoPoint.setLatitudeE6(point.getLatitudeE6());
			geoPoint.setLongitudeE6(point.getLongitudeE6());
		}
	}

	public void setAccuracy(float fAccur) {
		fAccuracy = fAccur;
	}

	@Override
	public void draw(Canvas canvas, MapView mapView) {
		if (geoPoint == null) {
			return;
		}
		Projection mapProjection = mapView.getProjection();
		Paint paint = new Paint();
		Point ptMap = mapProjection.toPixels(geoPoint, null);
		paint.setColor(Color.BLUE);
		paint.setAlpha(8);
		paint.setAntiAlias(true);

		float fRadius = mapProjection.metersToEquatorPixels(fAccuracy);
		canvas.drawCircle(ptMap.x, ptMap.y, fRadius, paint);
		paint.setStyle(Style.STROKE);
		paint.setAlpha(200);
		canvas.drawCircle(ptMap.x, ptMap.y, fRadius, paint);

		if (bmpMarker != null) {
			paint.setAlpha(255);
			canvas.drawBitmap(bmpMarker, ptMap.x - bmpMarker.getWidth() / 2,
					ptMap.y - bmpMarker.getHeight() / 2, paint);
		}

		super.draw(canvas, mapView);
	}

}
