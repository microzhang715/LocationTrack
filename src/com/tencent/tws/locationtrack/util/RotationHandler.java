package com.tencent.tws.locationtrack.util;

import android.hardware.SensorEvent;

public class RotationHandler implements EventHandler {
	public void service(SensorEvent e) {
		//if(!LocationUtil.getEndTrack())
		{
			LocationUtil.rotationUpdate(e);
		}
	}
}
