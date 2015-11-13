package com.tencent.tws.locationtrack;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.tencent.tws.locationtrack.database.SPUtils;
import com.tencent.tws.widget.BaseActivity;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";
    WakeLock mWakeLock;

    private Button locationButton;
    private Button historyButton;
    private TextView locationTV;
    private TextView historyTV;

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
    }


    class LocationClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Intent i = new Intent(MainActivity.this, LocationActivity.class);
            startActivity(i);
        }
    }

    class HistoryClick implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            Intent i = new Intent(MainActivity.this, HistoryActivity.class);
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
            Intent i = new Intent(MainActivity.this, LocationActivity.class);
            startActivity(i);
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
