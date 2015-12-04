package com.tencent.tws.locationtrack.util;

import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import com.tencent.mapsdk.raster.model.GeoPoint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.*;


public class LocationUtil {

    //variables and constants
    //*************************************************************************
    //*************************************************************************

    private static String TAG = "LocationUtil";

    //step parameters
    //*************************************************************************
    private static final float STEP_INCREMENT = 0.03f;
    private static final float STEP_ACCEL_THRESHOLD = 1.2f;
    private static final int NUM_ACCEL_SAMPLES = 8;
    private static final float SCALE_TO_FEET = 7.0f;

    //visual trail parameters
    //*************************************************************************
    private static final float CRUMB_RADIUS = 0.25f;
    private static final int MAX_POINTS = 250;

    //location and orientation
    //*************************************************************************
    private static DoublePoint mStartLocation = new DoublePoint(0, 0);
    private static DoublePoint mLocation = new DoublePoint(0, 0);
    private static float[] mRotationVector = new float[4];
    private static float[] mRotationMatrix = new float[16];
    private static float mAccelerationAvg = 0;
    private static float mAzimuth;
    private static float mSpeed;
    private static float mTotalDistance = 0;

    private static List<String> Listlocation = new ArrayList<String>();

    private static boolean mEndTrack;

    private static SensorUtil mSU;

    private static HashMap<Double, Double> mParamsLocation = new HashMap<Double, Double>();

    //crumb trail
    //*************************************************************************
    private static Deque<DoublePoint> mBreadCrumbs;
    private static FloatBuffer crumbBuffer;
    private static float crumbCoords[] = new float[MAX_POINTS * 3];

    //crumb other trail
    //*************************************************************************
    private static Deque<DoublePoint> mOldBreadCrumbs;
    private static FloatBuffer oldCrumbBuffer;
    private static float otherCrumbCoords[] = new float[MAX_POINTS * 3];

    //averaging
    //*************************************************************************
    private static int sampleIndex = 0;
    private static boolean averageReady = false;
    private static float samples[] = new float[NUM_ACCEL_SAMPLES];

    private static double m_a = 6378245.0, m_f = 1.0 / 298.3; //54年北京坐标系参数
    // private static double m_a=6378140.0, m_f=1/298.257; //80年西安坐标系参数

    //member functions
    //*************************************************************************
    //*************************************************************************

    //reset and init
    //*************************************************************************
    public static void reset(boolean hasPosition) {
        mBreadCrumbs.clear();
        if (!hasPosition) {
            mBreadCrumbs.add(new DoublePoint(0, 0));
            mLocation.set(0, 0);
        }
        mTotalDistance = 0;
    }

    public static void init(boolean hasPosition) {
        mBreadCrumbs = new LinkedList<DoublePoint>();
        mOldBreadCrumbs = new LinkedList<DoublePoint>();
        if (!hasPosition) {
            mBreadCrumbs.add(new DoublePoint(0, 0));
            mLocation.set(0, 0);
        }
        mTotalDistance = 0;

        ByteBuffer cbb = ByteBuffer.allocateDirect(crumbCoords.length * 4);
        cbb.order(ByteOrder.nativeOrder());
        crumbBuffer = cbb.asFloatBuffer();
        crumbBuffer.put(crumbCoords);
        crumbBuffer.position(0);
    }

    public static void cloneTrail() {
        ByteBuffer cbb = ByteBuffer.allocateDirect(otherCrumbCoords.length * 4);
        cbb.order(ByteOrder.nativeOrder());
        oldCrumbBuffer = cbb.asFloatBuffer();
        oldCrumbBuffer.put(otherCrumbCoords);
        oldCrumbBuffer.position(0);

        int i = 0;
        for (DoublePoint p : mBreadCrumbs) {
            otherCrumbCoords[i++] = (float) p.getX();
            otherCrumbCoords[i++] = (float) p.getY();
            otherCrumbCoords[i++] = 0;
            mOldBreadCrumbs.add(p);
        }

        oldCrumbBuffer.put(otherCrumbCoords);
        oldCrumbBuffer.position(0);
    }

    //rotationUpdate
    //
    //Takes a 3d rotation vector and isolates the rotation about the z-axis
    //*************************************************************************
    public static void rotationUpdate(SensorEvent e) {
        float projected[] = new float[3];

        mRotationVector = Arrays.copyOf(e.values, e.values.length);
        SensorManager.getRotationMatrixFromVector(mRotationMatrix, e.values);
        SensorManager.getOrientation(mRotationMatrix, projected);

        mAzimuth = projected[0];
        updateLocation();
    }

    //accelerationUpdate
    //
    //Keeps a running average of the magnitude of the phone's acceleration.
    //When the instantaneous acceleration exceeds the average acceleration by
    //a threshold value, a step is recorded
    //*************************************************************************
    public static void accelerationUpdate(SensorEvent e) {
        float magnitude = (float) Math.sqrt(
                Math.pow(e.values[0], 2) +
                        Math.pow(e.values[1], 2) +
                        Math.pow(e.values[2], 2)
        );

        samples[(sampleIndex++) % NUM_ACCEL_SAMPLES] = magnitude;

        if (sampleIndex > NUM_ACCEL_SAMPLES || averageReady) {
            averageReady = true;
            mAccelerationAvg = samples[0];
            for (int i = 1; i < NUM_ACCEL_SAMPLES; ++i)
                mAccelerationAvg += samples[i];

            mAccelerationAvg /= (float) NUM_ACCEL_SAMPLES;

            if (Math.abs(magnitude - mAccelerationAvg) > STEP_ACCEL_THRESHOLD) {
                takeStep();
                updateLocation();
            }
        }
    }

    //takeStep / speedDecay
    //Control the velocity associated with taking a step
    //*************************************************************************
    public static void takeStep() {
        mSpeed = STEP_INCREMENT;
    }

    public static void speedDecay() {
        mSpeed = 0;
    }

    //updateLocation
    //*************************************************************************
    public static void updateLocation() {
        float dx = (float) Math.cos(getCurrentAzimuth()) * getCurrentSpeed();
        float dy = (float) Math.sin(getCurrentAzimuth()) * getCurrentSpeed();

        mLocation.offset(dx, dy);
        //Log.d(TAG, "dx = " +dx +",dy = " +dy);
        //Log.d(TAG, "X = " +mLocation.getX() +",Y = " +mLocation.getY());
        speedDecay();

        DoublePoint last = mBreadCrumbs.getLast();
        float dist = mLocation.distanceFrom(last);
        float dd = (float) Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2));
        //Log.d(TAG, "last point x = " +last.getX() +",y = " +last.getY());

        mTotalDistance += dd;
        //Log.d(TAG, "mTotalDistance = " + dd);

        if (dist >= CRUMB_RADIUS) {

            if (mBreadCrumbs.size() >= MAX_POINTS)
                mBreadCrumbs.removeFirst();

            mBreadCrumbs.addLast(new DoublePoint(
                    mLocation.getX(),
                    mLocation.getY())
            );

            Listlocation.add(mLocation.getX() + "," + mLocation.getY());

            int i = 0;
            for (DoublePoint p : mBreadCrumbs) {
                crumbCoords[i++] = (float) p.getX();
                crumbCoords[i++] = (float) p.getY();
                crumbCoords[i++] = 0;
            }

            crumbBuffer.put(crumbCoords);
            crumbBuffer.position(0);
        }

//		if(mTotalDistance>=100)
//		{
//			if(mSU!=null)
//			{
//				mSU.unregisterListeners();
//				mEndTrack = true;
//				NotifyUtil.vibrate(GlobalObj.g_appContext, 50);
//			}
//		}
    }

    //经纬度转换平面坐标
    public static double[] GaussProjCal(double longitude, double latitude) {
        int ProjNo = 0;
        int ZoneWide; ////带宽
        double longitude1, latitude1, longitude0, latitude0, X0, Y0, xval, yval;
        double a, f, e2, ee, NN, T, C, A, M, iPI;
        iPI = 0.0174532925199433; ////3.1415926535898/180.0;
        ZoneWide = 6; ////6度带宽
//        a=6378245.0; f=1.0/298.3; //54年北京坐标系参数
//        a=6378140.0; f=1/298.257; //80年西安坐标系参数
        a = m_a;
        f = m_f;
        if (longitude % ZoneWide == 0)
            ProjNo = (int) (longitude / ZoneWide) - 1;
        else
            ProjNo = (int) (longitude / ZoneWide);
        longitude0 = ProjNo * ZoneWide + ZoneWide / 2;
        longitude0 = longitude0 * iPI;
        latitude0 = 0;
        longitude1 = longitude * iPI; //经度转换为弧度
        latitude1 = latitude * iPI; //纬度转换为弧度
        e2 = 2 * f - f * f;
        ee = e2 * (1.0 - e2);
        NN = a / Math.sqrt(1.0 - e2 * Math.sin(latitude1) * Math.sin(latitude1));
        T = Math.tan(latitude1) * Math.tan(latitude1);
        C = ee * Math.cos(latitude1) * Math.cos(latitude1);
        A = (longitude1 - longitude0) * Math.cos(latitude1);
        M = a * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * latitude1 - (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2
                * e2 / 1024) * Math.sin(2 * latitude1)
                + (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * Math.sin(4 * latitude1) - (35 * e2 * e2 * e2 / 3072) * Math.sin(6 * latitude1));
        xval = NN * (A + (1 - T + C) * A * A * A / 6 + (5 - 18 * T + T * T + 72 * C - 58 * ee) * A * A * A * A * A / 120);
        yval = M + NN * Math.tan(latitude1) * (A * A / 2 + (5 - T + 9 * C + 4 * C * C) * A * A * A * A / 24
                + (61 - 58 * T + T * T + 600 * C - 330 * ee) * A * A * A * A * A * A / 720);
        X0 = 500000L;
        Y0 = 0;
        xval = xval + X0;
        yval = yval + Y0;
        double X = xval;
        double Y = yval;
        double long_laNum[] = new double[3];
        long_laNum[0] = (X);//经度
        long_laNum[1] = (Y);//纬度
        long_laNum[2] = ProjNo + 1;//经度
        return long_laNum;
    }


    public static double[] GaussProjInvCal(double X, double Y) {
        int ProjNo;
        int ZoneWide; ////带宽
        double longitude1, latitude1, longitude0, latitude0, X0, Y0, xval, yval;
        double e1, e2, f, a, ee, NN, T, C, M, D, R, u, fai, iPI;
        iPI = 0.0174532925199433; ////3.1415926535898/180.0;
//      a = 6378245.0; 
//      f = 1.0/298.3; //54年北京坐标系参数
        a = m_a;
        f = m_f;
        ////a=6378140.0; f=1/298.257; //80年西安坐标系参数
        ZoneWide = 6; ////6度带宽
        ProjNo = (int) (X / 1000000L); //查找带号
        longitude0 = (ProjNo - 1) * ZoneWide + ZoneWide / 2;
        longitude0 = longitude0 * iPI; //中央经线
        X0 = ProjNo * 1000000L + 500000L;
        Y0 = 0;
        xval = X - X0;
        yval = Y - Y0; //带内大地坐标
        e2 = 2 * f - f * f;
        e1 = (1.0 - Math.sqrt(1 - e2)) / (1.0 + Math.sqrt(1 - e2));
        ee = e2 / (1 - e2);
        M = yval;
        u = M / (a * (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256));
        fai = u + (3 * e1 / 2 - 27 * e1 * e1 * e1 / 32) * Math.sin(2 * u) + (21 * e1 * e1 / 16 - 55 * e1 * e1 * e1 * e1 / 32) * Math.sin(
                4 * u)
                + (151 * e1 * e1 * e1 / 96) * Math.sin(6 * u) + (1097 * e1 * e1 * e1 * e1 / 512) * Math.sin(8 * u);
        C = ee * Math.cos(fai) * Math.cos(fai);
        T = Math.tan(fai) * Math.tan(fai);
        NN = a / Math.sqrt(1.0 - e2 * Math.sin(fai) * Math.sin(fai));
        R = a * (1 - e2) / Math.sqrt((1 - e2 * Math.sin(fai) * Math.sin(fai)) * (1 - e2 * Math.sin(fai) * Math.sin(fai)) * (1 - e2 * Math.sin
                (fai) * Math.sin(fai)));
        D = xval / NN;
        //计算经度(Longitude) 纬度(Latitude)
        longitude1 = longitude0 + (D - (1 + 2 * T + C) * D * D * D / 6 + (5 - 2 * C + 28 * T - 3 * C * C + 8 * ee + 24 * T * T) * D
                * D * D * D * D / 120) / Math.cos(fai);
        latitude1 = fai - (NN * Math.tan(fai) / R) * (D * D / 2 - (5 + 3 * T + 10 * C - 4 * C * C - 9 * ee) * D * D * D * D / 24
                + (61 + 90 * T + 298 * C + 45 * T * T - 256 * ee - 3 * C * C) * D * D * D * D * D * D / 720);
        //转换为度 DD
        double longitude = longitude1 / iPI;
        double latitude = latitude1 / iPI;
        double long_la[] = new double[2];
        long_la[0] = longitude;
        long_la[1] = latitude;
        return long_la;
    }

    //Generic accessor functions
    //*************************************************************************
    public static float[] getCurrentRotationMatrix() {
        return mRotationMatrix;
    }

    public static float[] getCurrentRotationVector() {
        return mRotationVector;
    }

    public static float getCurrentAzimuth() {
        return mAzimuth;
    }

    public static float getCurrentAzimuthDegrees() {
        return mAzimuth * (float) (180 / Math.PI);
    }

    public static float getCurrentSpeed() {
        return mSpeed;
    }

    public static DoublePoint getCurrentLocation() {
        return mLocation;
    }

    public static void setCurrentLocation(DoublePoint location) {
        mLocation = location;
    }

    public static DoublePoint getStartLocation() {
        return mStartLocation;
    }

    public static void setStartLocation(DoublePoint location) {
        mLocation = mStartLocation = location;
    }

    public static float getAverageAcceleration() {
        return mAccelerationAvg;
    }


    public static FloatBuffer getOldCrumbBuffer() {
        return oldCrumbBuffer;
    }

    public static int getOldCrumbBufferSize() {
        return mOldBreadCrumbs.size();
    }

    public static FloatBuffer getCrumbBuffer() {
        return crumbBuffer;
    }

    public static int getCrumbBufferSize() {
        return mBreadCrumbs.size();
    }

    public static Deque<DoublePoint> getBreadCrumbs() {
        return mBreadCrumbs;
    }

    public static float getTotalDistance() {
        return mTotalDistance;
    }

    public static float getTotalDistanceFeet() {
        return mTotalDistance * SCALE_TO_FEET;
    }

    public static List<String> getListLocation() {
        return Listlocation;
    }

    public static boolean getEndTrack() {
        return mEndTrack;
    }

    public static void setEndTrack(boolean endTrack) {
        mEndTrack = endTrack;
    }

    public static void setSensorUtil(SensorUtil SU) {
        mSU = SU;
    }

    public static SensorUtil getSensorUtil() {
        return mSU;
    }

    public static void setLocationTrack(HashMap<Double, Double> paramsLocation) {
        mParamsLocation = paramsLocation;
    }

    public static HashMap<Double, Double> getLocationTrack() {
        return mParamsLocation;
    }


    public static GeoPoint of(Gps location) {
        GeoPoint ge = new GeoPoint((int) (location.getWgLat() * 1E6),
                (int) (location.getWgLon() * 1E6));
        return ge;
    }

    public static String convert(long mill) {
        Date date = new Date(mill);
        String strs = "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            strs = sdf.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return strs;
    }

    //这个是时长
    public static String convertHM(long mill) {
        Date date = new Date(mill);
        String strs = "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
            strs = sdf.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return strs;
    }


}
