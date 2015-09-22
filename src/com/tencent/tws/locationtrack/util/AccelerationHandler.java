package com.tencent.tws.locationtrack.util;

import android.hardware.SensorEvent;

public class AccelerationHandler implements EventHandler {
	public void service(SensorEvent e) {
		LocationUtil.accelerationUpdate(e);
	}
}