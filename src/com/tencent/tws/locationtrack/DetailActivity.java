package com.tencent.tws.locationtrack;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import com.tencent.mapsdk.raster.model.GeoPoint;
import com.tencent.mapsdk.raster.model.LatLng;
import com.tencent.mapsdk.raster.model.Polyline;
import com.tencent.mapsdk.raster.model.PolylineOptions;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tws.locationtrack.record.Archiver;
import com.tencent.tws.locationtrack.util.Gps;
import com.tencent.tws.locationtrack.util.LocationUtil;
import com.tencent.tws.locationtrack.util.PositionUtil;
import com.tencent.tws.widget.BaseActivity;

import java.util.ArrayList;

public class DetailActivity extends BaseActivity {

    private Archiver archiver;

    private String archiveFileName;
    protected ArrayList<Location> locations;


    private MapView mMapView;
    private LocationOverlay mLocationOverlay;

    private Bitmap bmpPointStart;
    private Bitmap bmpPointEnd;

    protected double topBoundary;
    protected double leftBoundary;
    protected double rightBoundary;
    protected double bottomBoundary;

    protected Location locationTopLeft;
    protected Location locationBottomRight;
    protected float maxDistance;
    protected GeoPoint mapCenterPoint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detail);

        initMapView();
        archiveFileName = getIntent().getStringExtra(RecordsActivity.INTENT_ARCHIVE_FILE_NAME);

        archiver = new Archiver(getApplicationContext(), archiveFileName);
        locations = archiver.fetchAll();

    }


    @Override
    public void onStart() {
        super.onStart();
        locations = optimizePoints(locations);
        Gps gcj02GpsPointStart = PositionUtil.gps84_To_Gcj02(locations.get(0).getLatitude(), locations.get(0).getLongitude());

        getBoundary();
        mMapView.getController().setCenter(mapCenterPoint);
        mMapView.getController().setZoom(getFixedZoomLevel());
//	        setCenterPoint(gcj02GpsPointStart);


        drawPolyline();

        bmpPointStart = BitmapFactory.decodeResource(getResources(),
                R.drawable.point_start);
        LocationOverlay mLocationOverlayStart = new LocationOverlay(bmpPointStart);
        mLocationOverlayStart.setGeoCoords(LocationUtil.of(gcj02GpsPointStart));
        mMapView.addOverlay(mLocationOverlayStart);


        Gps gcj02GpsPointEnd = PositionUtil.gps84_To_Gcj02(locations.get(locations.size() - 1).getLatitude(), locations.get(locations.size() - 1).getLongitude());
        bmpPointEnd = BitmapFactory.decodeResource(getResources(),
                R.drawable.point_end);
        LocationOverlay mLocationOverlayEnd = new LocationOverlay(bmpPointEnd);
        mLocationOverlayEnd.setGeoCoords(LocationUtil.of(gcj02GpsPointEnd));
        mMapView.addOverlay(mLocationOverlayEnd);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        mMapView.getOverlay().clear();
        if (bmpPointStart != null && !bmpPointStart.isRecycled()) {
            bmpPointStart.recycle();
            bmpPointStart = null;
        }
        if (bmpPointEnd != null && !bmpPointEnd.isRecycled()) {
            bmpPointEnd.recycle();
            bmpPointEnd = null;
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        archiver.close();
        super.onDestroy();
    }

    private void initMapView() {
        mMapView = (MapView) findViewById(R.id.mapview);
        // mMapView.setBuiltInZoomControls(true);
        //mMapView.getController().setZoom(50);

//			Bitmap bmpMarker = BitmapFactory.decodeResource(getResources(),
//					R.drawable.mark_location);
//			mLocationOverlay = new LocationOverlay(bmpMarker);
//			mMapView.addOverlay(mLocationOverlay);
    }


    //绘制轨迹
    private Polyline drawPolyline() {
        // 如果要修改颜色，请直接使用4字节颜色或定义的变量
        PolylineOptions lineOpt = new PolylineOptions();
        lineOpt.color(0xAAFF0000);

        for (Location key : locations) {
            Gps gcj02Gps = PositionUtil.gps84_To_Gcj02(key.getLatitude(), key.getLongitude());
            if (gcj02Gps != null) {
                final LatLng latLng = new LatLng(gcj02Gps.getWgLat(), gcj02Gps.getWgLon());
                lineOpt.add(latLng);
            }

            Log.d("guccigu", "经度 = " + key.getLongitude() + "，维度 = " + key.getLatitude());
        }

        Polyline line = mMapView.getMap().addPolyline(lineOpt);
        return line;
    }


    public ArrayList<Location> optimizePoints(ArrayList<Location> inPoint) {
        int size = inPoint.size();
        ArrayList<Location> outPoint;

        int i;
        if (size < 5) {
            return inPoint;
        } else {
            // Latitude
            inPoint.get(0)
                    .setLatitude((3.0 * inPoint.get(0).getLatitude() + 2.0
                            * inPoint.get(1).getLatitude() + inPoint.get(2).getLatitude() - inPoint
                            .get(4).getLatitude()) / 5.0);
            inPoint.get(1)
                    .setLatitude((4.0 * inPoint.get(0).getLatitude() + 3.0
                            * inPoint.get(1).getLatitude() + 2
                            * inPoint.get(2).getLatitude() + inPoint.get(3).getLatitude()) / 10.0);

            inPoint.get(size - 2).setLatitude(
                    (4.0 * inPoint.get(size - 1).getLatitude() + 3.0
                            * inPoint.get(size - 2).getLatitude() + 2
                            * inPoint.get(size - 3).getLatitude() + inPoint.get(
                            size - 4).getLatitude()) / 10.0);
            inPoint.get(size - 1).setLatitude(
                    (3.0 * inPoint.get(size - 1).getLatitude() + 2.0
                            * inPoint.get(size - 2).getLatitude()
                            + inPoint.get(size - 3).getLatitude() - inPoint.get(
                            size - 5).getLatitude()) / 5.0);

            // Longitude
            inPoint.get(0)
                    .setLongitude((3.0 * inPoint.get(0).getLongitude() + 2.0
                            * inPoint.get(1).getLongitude() + inPoint.get(2).getLongitude() - inPoint
                            .get(4).getLongitude()) / 5.0);
            inPoint.get(1)
                    .setLongitude((4.0 * inPoint.get(0).getLongitude() + 3.0
                            * inPoint.get(1).getLongitude() + 2
                            * inPoint.get(2).getLongitude() + inPoint.get(3).getLongitude()) / 10.0);

            inPoint.get(size - 2).setLongitude(
                    (4.0 * inPoint.get(size - 1).getLongitude() + 3.0
                            * inPoint.get(size - 2).getLongitude() + 2
                            * inPoint.get(size - 3).getLongitude() + inPoint.get(
                            size - 4).getLongitude()) / 10.0);
            inPoint.get(size - 1).setLongitude(
                    (3.0 * inPoint.get(size - 1).getLongitude() + 2.0
                            * inPoint.get(size - 2).getLongitude()
                            + inPoint.get(size - 3).getLongitude() - inPoint.get(
                            size - 5).getLongitude()) / 5.0);
            for (i = 2; i < size - 2; i++) {
                // Latitude
                inPoint.get(i)
                        .setLatitude((4.0 * inPoint.get(i - 1).getLatitude() + 3.0
                                * inPoint.get(i).getLatitude() + 2
                                * inPoint.get(i + 1).getLatitude() + inPoint.get(i + 2).getLatitude()) / 10.0);
                // Longitude
                inPoint.get(i)
                        .setLongitude((4.0 * inPoint.get(i - 1).getLongitude() + 3.0
                                * inPoint.get(i).getLongitude() + 2
                                * inPoint.get(i + 1).getLongitude() + inPoint.get(i + 2).getLongitude()) / 10.0);
            }
        }
        return inPoint;
    }




    private void setCenterPoint(Gps point) {
        mMapView.getController().animateTo(LocationUtil.of(point));
    }

    private void setCenterPoint(GeoPoint point) {
        mMapView.getController().animateTo(point);
    }

    protected void getBoundary() {
        leftBoundary = locations.get(0).getLatitude();
        bottomBoundary = locations.get(0).getLongitude();

        rightBoundary = locations.get(0).getLatitude();
        topBoundary = locations.get(0).getLongitude();

        for (Location location : locations) {
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

        for (int i = mMapView.getMaxZoomLevel(); i > 0; i--) {
            mMapView.getController().setZoom(i);
            int latSpan = mMapView.getLatitudeSpan();
            int longSpan = mMapView.getLongitudeSpan();

            if (latSpan > fixedLatitudeSpan && longSpan > fixedLongitudeSpan) {
                return i;
            }
        }

        return mMapView.getMaxZoomLevel();
    }
}
