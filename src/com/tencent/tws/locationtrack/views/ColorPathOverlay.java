package com.tencent.tws.locationtrack.views;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.util.Log;
import com.tencent.mapsdk.raster.model.LatLng;
import com.tencent.tencentmap.mapsdk.map.MapView;
import com.tencent.tencentmap.mapsdk.map.Overlay;
import com.tencent.tencentmap.mapsdk.map.Projection;
import com.tencent.tws.locationtrack.douglas.DouglasPoint;
import com.tencent.tws.locationtrack.util.Gps;
import com.tencent.tws.locationtrack.util.PositionUtil;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by microzhang on 2015/12/2 at 17:21.
 */
public class ColorPathOverlay extends Overlay {
    private static final String TAG = "ColorPathOverlay";

    private Projection projection;
    private MapView mapView;
    private Paint paint;
    private List<DouglasPoint> resumeList;

    private ExecutorService fixedThreadExecutor = Executors.newFixedThreadPool(2);

    double maxSpeed = 0, minSpeed = 0;
    private static final int ONCE_DRAW_POINT_COUNT = 10;
    private int color[] = new int[]{0xFF06C840, 0xFF2CD036, 0XFF75DF24, 0XFF99E71B, 0XFF8BED14, 0XFFEEF906,
            0XFFFEE508, 0XFFFEBB14, 0XFFFFA819, 0XFFFF8423, 0XFFFF6D29};

    public ColorPathOverlay(MapView mapView, List<DouglasPoint> resumeList) {
        this.mapView = mapView;
        this.resumeList = resumeList;
        this.projection = mapView.getProjection();

        setPaint();
    }

    private void setPaint() {
        paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);

//        paint.setColor(0XFFFF6D29);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(15);
        paint.setAlpha(188);

        fixedThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                maxSpeed = resumeList.get(0).getInsSpeed();
                minSpeed = resumeList.get(0).getInsSpeed();
                for (int i = 0; i < resumeList.size(); i++) {
                    if (maxSpeed < resumeList.get(i).getInsSpeed()) {
                        maxSpeed = resumeList.get(i).getInsSpeed();
                    }

                    if (minSpeed > resumeList.get(i).getInsSpeed()) {
                        minSpeed = resumeList.get(i).getInsSpeed();
                    }
                }
            }
        });
    }


    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);

        synchronized (canvas) {

            final int maxWidth = mapView.getWidth();
            final int maxHeight = mapView.getHeight();

            Point lastPoint = null;
            double allSpeed = 0;
            int size = resumeList.size();
            int rest = size % ONCE_DRAW_POINT_COUNT;
            int count = size / ONCE_DRAW_POINT_COUNT;

            //绘制点数
            for (int i = 0; i < count; i++) {
                Path path = new Path();

                //移动到下条线段的绘制点
                DouglasPoint tpoint;
                if (i > 0) {
                    tpoint = resumeList.get(i * ONCE_DRAW_POINT_COUNT - 1);
                } else {
                    tpoint = resumeList.get(0);
                }
                Point tCurrentPoint = projection.toScreenLocation(douglasPoint2LatLng(tpoint));
                path.moveTo(tCurrentPoint.x, tCurrentPoint.y);

                //绘制每条线段的逻辑
                for (int j = 0; j < ONCE_DRAW_POINT_COUNT; j++) {
                    DouglasPoint currentDouglasPoint = resumeList.get(i * ONCE_DRAW_POINT_COUNT + j);
                    allSpeed += currentDouglasPoint.getInsSpeed();

                    Point currentPoint = projection.toScreenLocation(douglasPoint2LatLng(currentDouglasPoint));
                    if (lastPoint != null && lastPoint.x < maxWidth && lastPoint.y < maxHeight) {
                        path.lineTo(currentPoint.x, currentPoint.y);
                    } else {
                        path.moveTo(currentPoint.x, currentPoint.y);
                    }

                    lastPoint = currentPoint;
                }

                paint.setColor(getColor(allSpeed / ONCE_DRAW_POINT_COUNT));
                canvas.drawPath(path, paint);
                allSpeed = 0;
            }

            Log.i(TAG, "draw Count=" + ONCE_DRAW_POINT_COUNT * count);
            Log.i(TAG, "resumeList.size()=" + resumeList.size());
            Log.i(TAG, "rest=" + rest);

            //有余数的点
            if (rest != 0 && count > 0) {
                Path path = new Path();
                allSpeed = 0;
                //移动到上一个点的最后一个点
                DouglasPoint tpoint = resumeList.get(count * ONCE_DRAW_POINT_COUNT - 1);
                allSpeed += tpoint.getInsSpeed();

                Point tCurrentPoint = projection.toScreenLocation(douglasPoint2LatLng(tpoint));
                path.moveTo(tCurrentPoint.x, tCurrentPoint.y);

                for (int i = 0; i < rest; i++) {
                    DouglasPoint currentDouglasPoint = resumeList.get(count * ONCE_DRAW_POINT_COUNT + i);
                    Point currentPoint = projection.toScreenLocation(douglasPoint2LatLng(currentDouglasPoint));
                    if (lastPoint != null && (lastPoint.y < maxHeight && lastPoint.x < maxWidth)) {
                        path.lineTo(currentPoint.x, currentPoint.y);
                    } else {
                        path.moveTo(currentPoint.x, currentPoint.y);
                    }
                    lastPoint = currentPoint;
                }

                paint.setColor(getColor(allSpeed / rest));
                canvas.drawPath(path, paint);

            } else if (count == 0 && rest != 0) {//数目少于ONCE_DRAW_POINT_COUNT个的特殊情况处理
                allSpeed = 0;
                Path path = new Path();

                DouglasPoint tpoint = resumeList.get(0);
                allSpeed += tpoint.getInsSpeed();
                Point tCurrentPoint = projection.toScreenLocation(douglasPoint2LatLng(tpoint));
                path.moveTo(tCurrentPoint.x, tCurrentPoint.y);

                for (int i = 0; i < rest; i++) {
                    DouglasPoint currentDouglasPoint = resumeList.get(i);
                    Point currentPoint = projection.toScreenLocation(douglasPoint2LatLng(currentDouglasPoint));
                    if (lastPoint != null && (lastPoint.y < maxHeight && lastPoint.x < maxWidth)) {
                        path.lineTo(currentPoint.x, currentPoint.y);
                    } else {
                        path.moveTo(currentPoint.x, currentPoint.y);
                    }
                    lastPoint = currentPoint;
                }

                paint.setColor(getColor(allSpeed / rest));
                canvas.drawPath(path, paint);
            }
        }
    }

    private int getColor(double speed) {
        if (minSpeed == 0 && maxSpeed == 0) {
            return color[0];
        }

        double delt = (maxSpeed - minSpeed) / color.length;

        if (speed <= delt) {
            return color[0];
        } else if (speed > delt && speed <= 2 * delt) {
            return color[1];
        } else if (speed > 2 * delt && speed <= 3 * delt) {
            return color[2];
        } else if (speed > 3 * delt && speed <= 4 * delt) {
            return color[3];
        } else if (speed > 4 * delt && speed <= 5 * delt) {
            return color[4];
        } else if (speed > 5 * delt && speed <= 6 * delt) {
            return color[5];
        } else if (speed > 6 * delt && speed <= 7 * delt) {
            return color[6];
        } else if (speed > 7 * delt && speed <= 8 * delt) {
            return color[7];
        } else if (speed > 8 * delt && speed <= 9 * delt) {
            return color[8];
        } else if (speed > 9 * delt && speed <= 10 * delt) {
            return color[9];
        } else {
            return color[10];
        }
    }

    private LatLng douglasPoint2LatLng(DouglasPoint douglasPoint) {
        if (douglasPoint != null) {
            Gps gps = PositionUtil.gps84_To_Gcj02(douglasPoint.getLatitude(), douglasPoint.getLongitude());
            return new LatLng(gps.getWgLat(), gps.getWgLon());
        } else {
            return null;
        }
    }
}
