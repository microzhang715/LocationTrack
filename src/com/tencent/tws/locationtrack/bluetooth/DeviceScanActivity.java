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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.tencent.tws.framework.global.GlobalObj;
import com.tencent.tws.locationtrack.R;
import com.tencent.tws.widget.BaseActivity;

import java.util.ArrayList;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends BaseActivity implements AdapterView.OnItemClickListener {
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
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_list);
		GlobalObj.g_appContext = this;
		mHandler = new Handler();

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

				scanLeDevice(false);
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

		scanLeDevice(true);
	}

	@Override
	protected void onPause() {
		super.onPause();
		scanLeDevice(false);
		mLeDeviceListAdapter.clear();
	}
	

	private void scanLeDevice(final boolean enable) {
		if (enable) {
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					mScanning = false;
					mBluetoothAdapter.stopLeScan(mLeScanCallback);
					invalidateOptionsMenu();
				}
			}, SCAN_PERIOD);

			mScanning = true;
			mBluetoothAdapter.startLeScan(mLeScanCallback);
		} else {
			mScanning = false;
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
					//刷新界面
					if (name.equals("未知名称")) {
						name.setText(sharedPreferences.getString(DeviceControlActivity.EXTRAS_DEVICE_NAME, ""));
						address.setText(sharedPreferences.getString(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, ""));
					}
				}
			} else if (action.equals(BluetoothLeService.ACTION_GATT_DISCONNECTED)) {
				if (state != null) {
						state.setText("连接断开");
						//刷新界面
					if (name.equals("未知名称")) {
						name.setText(sharedPreferences.getString(DeviceControlActivity.EXTRAS_DEVICE_NAME, ""));
						address.setText(sharedPreferences.getString(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, ""));
					}
						Intent i = new Intent(TRACKMODE);
						GlobalObj.g_appContext.startActivity(i);
					}
			} else if (action.equals(BluetoothLeService.ACTION_GATT_RSSI)) {
				int rssiValue = intent.getIntExtra("rssi", 0);
				int statusValue = intent.getIntExtra("status", 0);

				if (rssiTexeView != null) rssiTexeView.setText(rssiValue + "");
				Log.i("kermit", "rssiValue = " + rssiValue + " | status=" + statusValue);
			}
		}
	}

	static class ViewHolder {
		TextView deviceName;
		TextView deviceAddress;
	}
}