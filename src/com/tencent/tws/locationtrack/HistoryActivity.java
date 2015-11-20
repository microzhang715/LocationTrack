package com.tencent.tws.locationtrack;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.tencent.tws.locationtrack.database.DbNameUtils;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.util.LocationUtil;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private Button backupButton;
    private Button clearButton;
    private ExecutorService fixedThreadExecutor = Executors.newFixedThreadPool(2);

    static {
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

    private static final int COPY_SUCCESS = 1;
    private static final int COPY_FAILE = 2;
    private static final int CLEAN_SUCCESS = 3;
    private static final int CLEAN_FAILE = 4;

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

        backupButton = (Button) findViewById(R.id.backup_database);
        backupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fixedThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String srcDir = "/data/data/com.tencent.tws.locationtrack/databases";
                            String destDir;
                            boolean sdCardExist = Environment.getExternalStorageState()
                                    .equals(Environment.MEDIA_MOUNTED);   //判断sd卡是否存在
                            if (sdCardExist) {
                                File file = new File(srcDir);
                                if (file.exists() && file.isDirectory()) {

                                    destDir = Environment.getExternalStorageDirectory().toString();//获取跟目录

                                    Log.i(TAG, "destDir=" + destDir);
                                    FileUtils.copyDirectory(new File(srcDir), new File(destDir));

                                    handler.sendEmptyMessage(COPY_SUCCESS);
                                }
                            } else {
                                handler.sendEmptyMessage(COPY_FAILE);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        clearButton = (Button) findViewById(R.id.clean_database);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fixedThreadExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String srcDir = "/data/data/com.tencent.tws.locationtrack/databases";
                            File srcFile = new File(srcDir);
                            if (srcFile.exists() && srcFile.isDirectory()) {
                                String testDir = "/storage/emulated/legacy/test";
                                FileUtils.cleanDirectory(srcFile);

                                handler.sendEmptyMessage(CLEAN_SUCCESS);
                            } else {
                                handler.sendEmptyMessage(CLEAN_FAILE);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }
                });
            }
        });
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case COPY_SUCCESS:
                    Toast.makeText(getApplicationContext(), "备份成功", Toast.LENGTH_SHORT).show();
                    break;
                case COPY_FAILE:
                    Toast.makeText(getApplicationContext(), "SD卡不存在，备份失败", Toast.LENGTH_SHORT).show();
                    break;
                case CLEAN_SUCCESS:
                    Toast.makeText(getApplicationContext(), "清除数据库成功", Toast.LENGTH_SHORT).show();
                    //更新listview
                    dbaNamesList = dbNameUtils.getDbNames();
                    if (datebaseAdapter != null) {
                        datebaseAdapter.notifyDataSetChanged();
                    }
                    break;
                case CLEAN_FAILE:
                    Toast.makeText(getApplicationContext(), "清除数据库失败", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

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

            try {
                long time = Long.parseLong(dbNames.get(position));
                dbName.setText(LocationUtil.convert(time));
            } catch (Exception e) {
                e.printStackTrace();
            }

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
        double delt = 0;
        try {
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

            delt = (lastTime - startTime) / (1000 * 60f);
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();

        }

        return delt;
    }

    private double getAllDisInfo(String dbName) {
        String realName = dbName + "_location.db";
        dbHelper = new LocationDbHelper(getApplicationContext(), realName);
        sqLiteDatabase = dbHelper.getReadableDatabase();
        double allDis = 0;
        try {
            String[] PROJECTION = new String[]{LocationDbHelper.ID, LocationDbHelper.LATITUDE, LocationDbHelper.LONGITUDE, LocationDbHelper.INS_SPEED, LocationDbHelper.BEARING, LocationDbHelper.ALTITUDE, LocationDbHelper.ACCURACY, LocationDbHelper.TIME, LocationDbHelper.DISTANCE, LocationDbHelper.AVG_SPEED, LocationDbHelper.KCAL,};
            Cursor cursor = query(sqLiteDatabase, PROJECTION, null, null, null);
            if (cursor.getCount() > 0) {
                if (cursor.moveToFirst()) {
                    while (cursor.moveToNext()) {
                        float dis = cursor.getFloat(cursor.getColumnIndex(LocationDbHelper.DISTANCE));
                        allDis += dis;
                    }
                }
            }
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }


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
