package com.tencent.tws.locationtrack.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.MyContentProvider;
import com.tencent.tws.locationtrack.domain.KmPoint;
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
    private Context contex;


    //所有点的集合,数据拿过来分析后返回
//    private List<DouglasPoint> listPoints = new ArrayList<>();

    //记录信息保存
    private DouglasPoint maxPoint;
    private DouglasPoint minPoint;

    public PointsAnalysis(Context contex) {
        this.contex = contex;
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

    public DouglasPoint getMaxInsSpeedPoint(List<DouglasPoint> listPoints) {
        maxPoint = listPoints.get(0);
        for (int i = 0; i < listPoints.size(); i++) {
            if (maxPoint.getInsSpeed() < listPoints.get(i).getInsSpeed()) {
                maxPoint = listPoints.get(i);
            }
        }
        return maxPoint;
    }


    public DouglasPoint getMinInsSpeedPoint(List<DouglasPoint> listPoints) {
        minPoint = listPoints.get(0);
        for (int i = 0; i < listPoints.size(); i++) {
            if (minPoint.getInsSpeed() > listPoints.get(i).getInsSpeed()) {
                minPoint = listPoints.get(i);
            }
        }
        return minPoint;
    }

    public DouglasPoint getLastPoint(List<DouglasPoint> listPoints) {
        return listPoints.get(listPoints.size() - 1);
    }

    public DouglasPoint getFirstPoint(List<DouglasPoint> listPoints) {
        return listPoints.get(0);
    }

    //返回单位为KM
    public double getAllDis(List<DouglasPoint> listPoints) {
        double allDis = 0;
        for (int i = 0; i < listPoints.size(); i++) {
            allDis += listPoints.get(i).getDis();
        }
        return allDis / 1000;
    }

    public double getKcal(List<DouglasPoint> listPoints) {
        return 60 * getAllDis(listPoints) * 1.036 / 1000;
    }

    public double getAvgSpeed(List<DouglasPoint> listPoints) {
        long startTime = listPoints.get(0).getTime();
        long endTime = listPoints.get(listPoints.size() - 1).getTime();
        long deltTime = (endTime - startTime) / 1000;
        double aveSpeed = (getAllDis(listPoints) * 10) / (deltTime * 36f);
        return aveSpeed;
    }

    //返回压缩后的数据
    public List<DouglasPoint> getResumeList(List<DouglasPoint> listPoints) {
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

        //最后一个点不能被过滤掉
        if (!resumeList.contains(listPoints.get(listPoints.size() - 1))) {
            resumeList.add(listPoints.get(listPoints.size() - 1));
        }

        Log.i(TAG, "resumeList.size()=" + resumeList.size());
        return resumeList;
    }

    public List<DouglasPoint> getAllPointsFromDP() {
        List<DouglasPoint> allPointList = new ArrayList<DouglasPoint>();

        try {
            String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
            Cursor cursor = contex.getContentResolver().query(MyContentProvider.CONTENT_URI, PROJECTION, null, null, null);
            Log.i(TAG, "getAllPointsFromDP cursor.getCount() = " + cursor.getCount());

            allPointList = getPoints(cursor);

            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.i(TAG, "allPointList.size()=" + allPointList.size());
        return allPointList;
    }


    private static final int KM = 1000;

    public List<KmPoint> getKmSpeed(List<DouglasPoint> listPoints) {
        List<KmPoint> kmPointList = new ArrayList<KmPoint>();
        int index = 1;
        float allDis = 0;
        long lastTime = 0, currentTime = 0;

        for (int i = 0; i < listPoints.size(); i++) {
            allDis += listPoints.get(i).getDis();

            if (allDis > KM) {
                currentTime = listPoints.get(i).getTime();
                long deltTime = currentTime - lastTime;
                kmPointList.add(new KmPoint(
                        index++,
                        listPoints.get(i).getTime(),
                        listPoints.get(i).getAvgSpeed(),
                        deltTime,
                        listPoints.get(i).getId()));

                float delt = allDis - KM;
                allDis = delt;
                lastTime = currentTime;
            }
        }

        //添加最后一些点的数据的处理
        if (allDis != 0) {
            kmPointList.add(new KmPoint(
                    index++,
                    listPoints.get(listPoints.size() - 1).getTime(),
                    listPoints.get(listPoints.size() - 1).getAvgSpeed(),
                    listPoints.get(listPoints.size() - 1).getTime() - lastTime,
                    listPoints.get(listPoints.size() - 1).getId()
            ));
        }

        //kmSpeedList中存放的是所有点的集合
        return kmPointList;
    }


    //下面函数还可以进一步优化的空间
    public List<DouglasPoint> getAllPointsFromHelper(LocationDbHelper dbHelper) {
        List<DouglasPoint> allPointList = new ArrayList<DouglasPoint>();

        try {
            String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.query(LocationDbHelper.TABLE_NAME, null, null, null, null, null, LocationDbHelper.DEFAULT_ORDERBY);
//            SQLiteQueryBuilder sqLiteQueryBuilder = new SQLiteQueryBuilder();
//            sqLiteQueryBuilder.setTables(LocationDbHelper.TABLE_NAME);
//            sqLiteQueryBuilder.setProjectionMap(locationMaps);
//            String orderBy = LocationDbHelper.DEFAULT_ORDERBY;
//            Cursor cursor = sqLiteQueryBuilder.query(db, PROJECTION, null, null, null, null, orderBy);
//            Log.i(TAG, "getAllPointsFromHelper cursor.getCount() = " + cursor.getCount());

            allPointList = getPoints(cursor);

            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.i(TAG, "allPointList.size()=" + allPointList.size());
        return allPointList;
    }

    private List<DouglasPoint> getPoints(Cursor cursor) {
        List<DouglasPoint> allPointList = new ArrayList<DouglasPoint>();
        int index = 0;
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
        return allPointList;
    }
}
