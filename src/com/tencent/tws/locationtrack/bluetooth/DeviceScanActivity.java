/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tws.locationtrack.bluetooth;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.tencent.tws.locationtrack.R;
import com.tencent.tws.locationtrack.util.LocationUtil;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends Activity implements AdapterView.OnItemClickListener {

    private final static String TAG = "DeviceScanActivity";
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private ListView listView;
    private static TextView name;
    private static TextView address;
    private static TextView state;
    private static TextView rssiTexeView;
    private Button scan;
    private static final int REQUEST_ENABLE_BT = 1;
    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;
    private static SharedPreferences sharedPreferences;

    private static final String TRACKMODE = "com.tencent.tws.locationtrack.TrackModeActivity";
    private static final String DeviceScan = "com.tencent.tws.locationtrack.bluetooth.DeviceScanActivity";

    private static MediaPlayer mediaPlayer;
    protected static final float BEEP_VOLUME = 1.00f;

    WakeLock mWakeLock;
    static Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_list);
        mContext = this;
        mHandler = new Handler();


        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        mWakeLock.acquire();

        listView = (ListView) findViewById(R.id.my_list);
        name = (TextView) findViewById(R.id.name);
        address = (TextView) findViewById(R.id.address);
        state = (TextView) findViewById(R.id.state);
        rssiTexeView = (TextView) findViewById(R.id.rssiValue);

        scan = (Button) findViewById(R.id.scan);
        scan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLeDeviceListAdapter.clear();
                mLeDeviceListAdapter.notifyDataSetChanged();

                //scanLeDevice(false);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        scanLeDevice(true);
                    }
                }, 300);

            }
        });


        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mWakeLock != null) {
            mWakeLock.acquire();
        }
        sharedPreferences = getSharedPreferences("test", Context.MODE_PRIVATE);
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        listView.setAdapter(mLeDeviceListAdapter);
        listView.setOnItemClickListener(this);

        initAlarmSound();
        scanLeDevice(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //scanLeDevice(false);
        if (mWakeLock != null) {
            mWakeLock.release();
        }
        mLeDeviceListAdapter.clear();
    }

    private void initAlarmSound() {
        if (mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(R.raw.alarm_bird);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            } finally {
                if (file != null) {
                    try {
                        file.close();
                    } catch (IOException e2) {
                        e2.printStackTrace();
                    }
                }
            }
        }
    }


    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final OnCompletionListener beepListener = new OnCompletionListener() {
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }
    };

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    //Log.d(TAG, "stopLeScan()=====scanLeDevice true");
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            Log.d(TAG, "stopLeScan()=====scanLeDevice false");
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;

        //点击后保存数据
        if (sharedPreferences == null || !DeviceControlActivity.EXTRAS_DEVICE_ADDRESS.equals(device.getAddress())) {
            SharedPreferences.Editor editor = sharedPreferences.edit();//获取编辑器
            editor.putString(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
            editor.putString(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
            editor.commit();
        }

        if (mScanning) {
            Log.d(TAG, "stopLeScan()=====onItemClick");
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mScanning = false;
        }

        name.setText(sharedPreferences.getString(DeviceControlActivity.EXTRAS_DEVICE_NAME, ""));
        address.setText(sharedPreferences.getString(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, ""));

        //启动service去连接设备
        final Intent intent = new Intent(DeviceScanActivity.this, BluetoothLeService.class);
        startService(intent);

    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);

                //找到存在过的地址了，就自动请求连接
                if (device.getAddress().equals(sharedPreferences.getString(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, ""))) {
                    Toast.makeText(getApplicationContext(), "进入自动连接", Toast.LENGTH_SHORT).show();

                    if (mScanning) {
                        Log.d(TAG, "stopLeScan()=====addDevice");
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        mScanning = false;
                    }

                    name.setText(sharedPreferences.getString(DeviceControlActivity.EXTRAS_DEVICE_NAME, ""));
                    address.setText(sharedPreferences.getString(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, ""));

                    final Intent intent = new Intent(DeviceScanActivity.this, BluetoothLeService.class);
                    startService(intent);
                }
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0) viewHolder.deviceName.setText(deviceName);
            else viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());
            return view;
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {

            Log.d(TAG, "LeScanCallback======onLeScan");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(device);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    public static class ConnRec extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothLeService.ACTION_GATT_CONNECTED)) {
                if (state != null) {
                    state.setText("连接成功");
                    // 刷新界面
                    if (name.equals("未知名称")) {
                        name.setText(sharedPreferences.getString(
                                DeviceControlActivity.EXTRAS_DEVICE_NAME, ""));
                        address.setText(sharedPreferences
                                .getString(
                                        DeviceControlActivity.EXTRAS_DEVICE_ADDRESS,
                                        ""));
                    }
                    Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

                    vibrator.vibrate(500);
                    // LocationUtil.setEndTrack(true);
                    // SensorUtil sU = LocationUtil.getSensorUtil();
                    // if(sU!=null)
                    // {
                    // sU.unregisterListeners();
                    // }
                    Toast.makeText(mContext, R.string.endSearch,
                            Toast.LENGTH_SHORT).show();
                    Intent i = new Intent(DeviceScan);
                    mContext.startActivity(i);
                }
            } else if (action
                    .equals(BluetoothLeService.ACTION_GATT_DISCONNECTED)) {
                if (state != null) {
                    state.setText("连接断开");
                    // 刷新界面
                    if (name.equals("未知名称")) {
                        name.setText(sharedPreferences.getString(
                                DeviceControlActivity.EXTRAS_DEVICE_NAME, ""));
                        address.setText(sharedPreferences
                                .getString(
                                        DeviceControlActivity.EXTRAS_DEVICE_ADDRESS,
                                        ""));
                    }
                    if (mediaPlayer != null) {
                        mediaPlayer.start();
                    }
                    Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

                    vibrator.vibrate(500);
                    LocationUtil.setEndTrack(false);
                    LocationUtil.init(false);
                    Intent i = new Intent(TRACKMODE);
                    mContext.startActivity(i);
                }
            } else if (action.equals(BluetoothLeService.ACTION_GATT_RSSI)) {
                int rssiValue = intent.getIntExtra("rssi", 0);
                int statusValue = intent.getIntExtra("status", 0);

                if (rssiTexeView != null)
                    rssiTexeView.setText(rssiValue + "");
                Log.i("kermit", "rssiValue = " + rssiValue + " | status="
                        + statusValue);
            }
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}