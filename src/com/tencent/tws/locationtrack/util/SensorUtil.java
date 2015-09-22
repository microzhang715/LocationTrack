package com.tencent.tws.locationtrack.util;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


public class SensorUtil {
	
	private SensorManager mSensorManager;
	private SensorEventListener mListener;
	private Sensor mAccelerometer;
	private Sensor mVector;
	private Map<Integer, EventHandler> mHandlers;
	
	public SensorUtil(Activity a){
		mSensorManager = (SensorManager) a.getSystemService(Context.SENSOR_SERVICE);
		mListener = (SensorEventListener)a;
		mHandlers = new HashMap<Integer, EventHandler>();
	}
	
	public boolean systemMeetsRequirements() {
		if (mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
			if (mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null)
				return true;
		
		return false;
	}
	
	public void registerListeners() {
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    	mSensorManager.registerListener(mListener, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    	
    	mVector = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    	mSensorManager.registerListener(mListener, mVector, SensorManager.SENSOR_DELAY_FASTEST);
    	registerHandlers();
	}
	
	public void unregisterListeners() {
		mSensorManager.unregisterListener(mListener);
		unregisterHandlers();
	}
	
	public void registerHandlers() {
		mHandlers.put(Integer.valueOf(Sensor.TYPE_ROTATION_VECTOR), new RotationHandler());
		mHandlers.put(Integer.valueOf(Sensor.TYPE_ACCELEROMETER), new AccelerationHandler());
	}
	
	public void unregisterHandlers() {
		mHandlers.clear();
	}
	
	public void routeEvent(SensorEvent e) {
		EventHandler h = mHandlers.get(Integer.valueOf(e.sensor.getType()));
		if (h != null)
			h.service(e);
	}
}
