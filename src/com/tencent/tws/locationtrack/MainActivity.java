package com.tencent.tws.locationtrack;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.tencent.tws.locationtrack.database.DbNameUtils;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.database.SPUtils;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    WakeLock mWakeLock;

    private Button locationButton;
    private Button historyButton;
    private Button tencentLocationButton;
    private TextView locationTV;
    private TextView historyTV;
    private TextView tencentTV;

    private ExecutorService fixedThreadExecutor = Executors.newFixedThreadPool(2);
    private DbNameUtils dbNameUtils;
    private static LocationDbHelper dbHelper;
    private static final int POINTS_TO_DELETE = 2;

    private LocationManager mLocationManager;

    public void onCreate(Bundle savedInstanceState) {
        // calls super, sets GUI
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.mainmenu);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        mWakeLock.acquire();

        locationButton = (Button) findViewById(R.id.btn_location);
        locationTV = (TextView) findViewById(R.id.tv_location);
        locationButton.setOnClickListener(new LocationClick());
        locationTV.setOnClickListener(new LocationClick());

        historyButton = (Button) findViewById(R.id.btn_history);
        historyTV = (TextView) findViewById(R.id.tv_history);
        historyButton.setOnClickListener(new HistoryClick());
        historyTV.setOnClickListener(new HistoryClick());

        tencentLocationButton = (Button) findViewById(R.id.btn_tencent_location);
        tencentTV = (TextView) findViewById(R.id.tv_tencent_location);
        tencentLocationButton.setOnClickListener(new TencentLocationClick());
        tencentTV.setOnClickListener(new TencentLocationClick());

        //此处用于判断GPS状态
        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {//GPS打开状态
            Toast.makeText(getApplicationContext(), "GPS未开启，请打开GPS", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendGpsBroadcast(String action) {
        Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private Runnable deletNoUseDb = new Runnable() {
        @Override
        public void run() {
            try {
                //1、扫描数据库目录并提前名称
                SQLiteDatabase db;
                Cursor cursor = null;
                dbNameUtils = new DbNameUtils(getApplicationContext());
                ArrayList<String> namesLists = dbNameUtils.getDbNames();

                //2、打开数据库并删除文件
                for (int i = 0; i < namesLists.size(); i++) {
                    String tmpdbName = namesLists.get(i);
                    if (tmpdbName != null && !tmpdbName.equals("")) {
                        String fulldbName = tmpdbName + "_location.db";
                        //打开数据库
                        dbHelper = new LocationDbHelper(getApplicationContext(), fulldbName);
                        db = dbHelper.getReadableDatabase();
                        //查询数据库
                        String SQLString = String.format("select * from %s ORDER BY id ASC;", LocationDbHelper.TABLE_NAME);
                        cursor = db.rawQuery(SQLString, null);
                        //删除空数据库
                        if (cursor != null && cursor.getCount() <= POINTS_TO_DELETE) {
                            Log.i(TAG, "删除数据库文件名称---->" + fulldbName);
                            String fName = "/data/data/com.tencent.tws.locationtrack/databases/" + fulldbName;
                            dbNameUtils.deleteFile(fName);
                        }
                    }
                }

                if (cursor != null) {
                    cursor.close();
                }

                //启动历史记录的Activity
                Intent i = new Intent(MainActivity.this, HistoryActivity.class);
                startActivity(i);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    class LocationClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Intent i = new Intent(MainActivity.this, LocationActivity.class);
            SPUtils.writeStartActivity(getApplicationContext(), "LocationActivity");
            startActivity(i);

        }
    }

    class HistoryClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            //启动的时候检查数据库文件，如果是空数据库直接删除掉
            fixedThreadExecutor.execute(deletNoUseDb);
        }
    }

    class TencentLocationClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Intent i = new Intent(MainActivity.this, TencentLocationActivity.class);
            SPUtils.writeStartActivity(getApplicationContext(), "TencentLocationActivity");
            startActivity(i);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();


        Log.i(TAG, "onResume");
        Log.i(TAG, "exitFlag=" + SPUtils.readExitFlag(getApplicationContext()));
        if (!SPUtils.readExitFlag(getApplicationContext())) {
            Log.i(TAG, "onResume  start activity");
            if (SPUtils.readStartActivity(getApplicationContext()).equals("LocationActivity")) {
                Intent i = new Intent(MainActivity.this, LocationActivity.class);
                startActivity(i);
            } else if (SPUtils.readStartActivity(getApplicationContext()).equals("TencentLocationActivity")) {
                Intent i = new Intent(MainActivity.this, TencentLocationActivity.class);
                startActivity(i);
            }
        }

        if (mWakeLock != null) {
            mWakeLock.acquire();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mWakeLock != null) {
            mWakeLock.release();
        }
    }
}
