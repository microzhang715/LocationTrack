package com.tencent.tws.locationtrack.util;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.util.Log;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.douglas.Douglas;
import com.tencent.tws.locationtrack.douglas.DouglasPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 分析函数会比较耗时，建议放在子线程中去做处理
 * Created by microzhang on 2015/12/2 at 17:40.
 */
public class PointsAnalysis {
    private static final String TAG = "PointsAnalysis";
    private LocationDbHelper dbHelper;
    //所有点的集合,数据拿过来分析后返回
    private List<DouglasPoint> listPoints = new ArrayList<>();

    //记录信息保存
    private DouglasPoint maxPoint;
    private DouglasPoint minPoint;

    public PointsAnalysis(LocationDbHelper dbHelper) {
        this.dbHelper = dbHelper;
        listPoints = getAllPoints2List();
    }

    private static HashMap<String, String> locationMaps;

    static {
        //定义别名
        locationMaps = new HashMap<String, String>();
        locationMaps.put(LocationDbHelper.ID, LocationDbHelper.ID);
        locationMaps.put(LocationDbHelper.LATITUDE, LocationDbHelper.LATITUDE);
        locationMaps.put(LocationDbHelper.LONGITUDE, LocationDbHelper.LONGITUDE);
        locationMaps.put(LocationDbHelper.INS_SPEED, LocationDbHelper.INS_SPEED);
        locationMaps.put(LocationDbHelper.BEARING, LocationDbHelper.BEARING);
        locationMaps.put(LocationDbHelper.ALTITUDE, LocationDbHelper.ALTITUDE);
        locationMaps.put(LocationDbHelper.ACCURACY, LocationDbHelper.ACCURACY);
        locationMaps.put(LocationDbHelper.TIME, LocationDbHelper.TIME);
        locationMaps.put(LocationDbHelper.DISTANCE, LocationDbHelper.DISTANCE);
        locationMaps.put(LocationDbHelper.AVG_SPEED, LocationDbHelper.AVG_SPEED);
        locationMaps.put(LocationDbHelper.KCAL, LocationDbHelper.KCAL);
    }


    public DouglasPoint getMaxInsSpeedPoint() {
        maxPoint = listPoints.get(0);
        for (int i = 0; i < listPoints.size(); i++) {
            if (maxPoint.getInsSpeed() < listPoints.get(i).getInsSpeed()) {
                maxPoint = listPoints.get(i);
            }
        }
        return maxPoint;
    }


    public DouglasPoint getMinInsSpeedPoint() {
        minPoint = listPoints.get(0);
        for (int i = 0; i < listPoints.size(); i++) {
            if (minPoint.getInsSpeed() > listPoints.get(i).getInsSpeed()) {
                minPoint = listPoints.get(i);
            }
        }
        return minPoint;
    }

    //返回单位为KM
    public double getAllDis() {
        double allDis = 0;
        for (int i = 0; i < listPoints.size(); i++) {
            allDis += listPoints.get(i).getDis();
        }
        return allDis / 1000;
    }

    public double getKcal() {
        return 60 * getAllDis() * 1.036 / 1000;
    }

    public double getAvgSpeed() {
        long startTime = listPoints.get(0).getTime();
        long endTime = listPoints.get(listPoints.size() - 1).getTime();
        long deltTime = (endTime - startTime) / 1000;
        double aveSpeed = (getAllDis() * 10) / (deltTime * 36f);
        return aveSpeed;
    }

    //返回压缩后的数据
    public List<DouglasPoint> getResumeList() {
        Log.i(TAG, "listPoints.size()=" + listPoints.size());
        List<DouglasPoint> resumeList = new ArrayList<DouglasPoint>();
        //对数据进行压缩处理
        Douglas douglas = new Douglas(listPoints);
        douglas.compress(listPoints.get(0), listPoints.get(listPoints.size() - 1));
        for (int i = 0; i < douglas.douglasPoints.size(); i++) {
            DouglasPoint douglasPoint = douglas.douglasPoints.get(i);
            if (douglasPoint.getIndex() > -1) {
                //所有数据进入队列
                resumeList.add(douglasPoint);
            }
        }
        Log.i(TAG, "resumeList.size()=" + resumeList.size());
        return resumeList;
    }


    public List<DouglasPoint> getAllPoints2List() {
        int index = 0;
        List<DouglasPoint> allPointList = new ArrayList<DouglasPoint>();
        try {
            String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            SQLiteQueryBuilder sqLiteQueryBuilder = new SQLiteQueryBuilder();
            sqLiteQueryBuilder.setTables(LocationDbHelper.TABLE_NAME);
            sqLiteQueryBuilder.setProjectionMap(locationMaps);
            String orderBy = LocationDbHelper.DEFAULT_ORDERBY;
            Cursor cursor = sqLiteQueryBuilder.query(db, PROJECTION, null, null, null, null, orderBy);
            Log.i(TAG, "cursor.getCount() = " + cursor.getCount());

            if (cursor.getCount() > 0 && cursor.moveToFirst()) {
                //把所有点记录在集合里面
                do {
                    double latitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LATITUDE));
                    double longitude = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.LONGITUDE));
                    double insSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.INS_SPEED));
                    int id = cursor.getInt(cursor.getColumnIndex(LocationDbHelper.ID));
                    double dis = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
                    long time = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
                    float bearing = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.BEARING));
                    float accuracy = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.ACCURACY));
                    double avgSpeed = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.AVG_SPEED));
                    double kcal = cursor.getDouble(cursor.getColumnIndex(LocationDbHelper.KCAL));

                    DouglasPoint tmpPoint = new DouglasPoint(latitude, longitude, insSpeed, id, dis, time, bearing, accuracy, avgSpeed, kcal, index++);
                    allPointList.add(tmpPoint);
                } while (cursor.moveToNext());
            }

            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.i(TAG, "allPointList.size()=" + allPointList.size());
        return allPointList;
    }
}
