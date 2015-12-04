package com.tencent.tws.locationtrack.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import com.tencent.mapsdk.raster.model.GeoPoint;
import com.tencent.mapsdk.raster.model.LatLng;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tencentmap.mapsdk.map.Overlay;
import com.tencent.tencentmap.mapsdk.map.Projection;

/**
 * Created by microzhang on 2015/12/4 at 14:07.
 */
public class MarkerOverlay extends Overlay {
    private LatLng location;
    private int drawable;
    private Projection projection;
    private Context context;

    public MarkerOverlay(Context context, LatLng location, int drawable) {
        this.location = location;
        this.drawable = drawable;
        this.context = context;
    }

    @Override
    public void draw(final Canvas canvas, final MapView mapView) {
        super.draw(canvas, mapView);

        this.projection = mapView.getProjection();
        GeoPoint piont = new GeoPoint((int) (location.getLatitude() * 1e6), (int) (location.getLongitude() * 1e6));
        Point current = projection.toPixels(piont, null);

        Bitmap markerImage = BitmapFactory.decodeResource(context.getResources(), drawable);

        // 根据实际的条目而定偏移位置
        canvas.drawBitmap(markerImage,
                current.x - Math.round(markerImage.getWidth() * 0.4),
                current.y - Math.round(markerImage.getHeight() * 0.9), null);

        return;
    }
}
