package com.tencent.tws.locationtrack;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.tencent.tws.locationtrack.database.DbNameUtils;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.util.LocationUtil;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

public class HistoryActivity extends Activity implements AdapterView.OnItemClickListener {
    private static final String TAG = "HistoryActivity";

    private Context context;

    private ListView listView;
    private DatebaseAdapter datebaseAdapter;
    private DbNameUtils dbNameUtils;

    private ArrayList<String> dbaNamesList;

    private static LocationDbHelper dbHelper;


    private static HashMap<String, String> locationMaps;
    SQLiteDatabase sqLiteDatabase;

    static {
        //�������
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


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.records);

        this.context = getApplicationContext();
        dbNameUtils = new DbNameUtils(context);

        this.listView = (ListView) findViewById(R.id.records_list);
        if (dbNameUtils.getDbNames().size() > 0) {
            dbaNamesList = dbNameUtils.getDbNames();
            this.datebaseAdapter = new DatebaseAdapter(dbaNamesList);
            this.listView.setAdapter(datebaseAdapter);
            this.listView.setOnItemClickListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (datebaseAdapter != null) {
            datebaseAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String tmpdbName = dbaNamesList.get(position);
        String fulldbName = tmpdbName + "_location.db";

        Intent intent = new Intent(this, HisLocationActivity.class);
        intent.putExtra("fulldbName", fulldbName);
        startActivity(intent);

        Log.i(TAG, "fulldbName=" + fulldbName);
    }


    public class DatebaseAdapter extends ArrayAdapter<String> {
        private ArrayList<String> dbNames;

        public DatebaseAdapter(ArrayList<String> dbNames) {
            super(context, R.layout.record_row, dbNames);
            this.dbNames = dbNames;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.db_names, parent, false);

            TextView dbName = (TextView) rowView.findViewById(R.id.db_name);
            TextView infoTime = (TextView) rowView.findViewById(R.id.info_time);
            TextView infoDis = (TextView) rowView.findViewById(R.id.info_dis);

            //��������
            try {
                long time = Long.parseLong(dbNames.get(position));
                dbName.setText(LocationUtil.convert(time));
            } catch (Exception e) {
                e.printStackTrace();
            }

            //����ʱ��;���
            DecimalFormat myformat = new DecimalFormat("#0.00");
            infoDis.setText(myformat.format(getAllDisInfo(dbNames.get(position))));
            infoTime.setText(myformat.format(getDeltTime(dbNames.get(position))));

            return rowView;
        }
    }

    private double getDeltTime(String dbName) {
        String realName = dbName + "_location.db";

        Log.i(TAG, "realName = " + realName);
        dbHelper = new LocationDbHelper(getApplicationContext(), realName);
        sqLiteDatabase = dbHelper.getReadableDatabase();
        String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
        Cursor cursor = query(sqLiteDatabase, PROJECTION, null, null, null);

        long startTime = 0;
        long lastTime = 0;

        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            startTime = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
            cursor.moveToLast();
            lastTime = cursor.getLong(cursor.getColumnIndex(LocationDbHelper.TIME));
        }

        double delt = (lastTime - startTime) / (1000 * 60f);
        cursor.close();
        return delt;
    }

    private double getAllDisInfo(String dbName) {
        String realName = dbName + "_location.db";
        dbHelper = new LocationDbHelper(getApplicationContext(), realName);
        sqLiteDatabase = dbHelper.getReadableDatabase();
        String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
        Cursor cursor = query(sqLiteDatabase, PROJECTION, null, null, null);

        double allDis = 0;

        if (cursor.getCount() > 0) {
            if (cursor.moveToFirst()) {
                while (cursor.moveToNext()) {
                    float dis = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
                    allDis += dis;
                }
            }
        }
        cursor.close();
        return allDis / 1000;
    }

    public Cursor query(SQLiteDatabase sqLiteDatabase, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        //SQLiteDatabase sqLiteDatabase = dbHelper.getWritableDatabase();
        SQLiteQueryBuilder sqLiteQueryBuilder = new SQLiteQueryBuilder();
        sqLiteQueryBuilder.setTables(LocationDbHelper.TABLE_NAME);
        sqLiteQueryBuilder.setProjectionMap(locationMaps);

        String orderBy = LocationDbHelper.DEFAULT_ORDERBY;

        Cursor cursor = sqLiteQueryBuilder.query(sqLiteDatabase, projection, selection, selectionArgs, null, null, orderBy);
        return cursor;
    }

}
