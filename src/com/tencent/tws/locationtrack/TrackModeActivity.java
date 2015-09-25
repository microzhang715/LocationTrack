package com.tencent.tws.locationtrack;

import com.tencent.tws.locationtrack.util.DoublePoint;
import com.tencent.tws.locationtrack.util.LocationUtil;
import com.tencent.tws.locationtrack.util.MySurfaceRenderer;
import com.tencent.tws.locationtrack.util.SensorUtil;
import com.tencent.tws.widget.BaseActivity;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;


public class TrackModeActivity extends BaseActivity implements SensorEventListener{
	private MySurfaceView GLView;
	private boolean sysOk;
	private SensorUtil SU;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        SU = new SensorUtil(this);
        LocationUtil.init(false);
        if (SU.systemMeetsRequirements()) {
        	
        	requestWindowFeature(Window.FEATURE_NO_TITLE);
        	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        	
        	 setContentView(R.layout.trackmode);
        	 GLView = (MySurfaceView) findViewById(R.id.glSurfaceViewID);
//        	GLView = new MySurfaceView(this);
//        	setContentView(GLView);
        	sysOk = true;
        	
        	Button btnEndTrack  = (Button)this.findViewById(R.id.btnEndTrack);
        	Button btnStartNavigate  = (Button)this.findViewById(R.id.btnStartNavigate);
        	
        	btnEndTrack.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					SU.unregisterListeners();
					LocationUtil.setEndTrack(true);
				}
			});
        	
        	btnStartNavigate.setOnClickListener(new View.OnClickListener() {
				
				@Override
				public void onClick(View v) {
					// TODO Auto-generated method stub
					LocationUtil.cloneTrail();
					DoublePoint dPoint =  LocationUtil.getCurrentLocation();
					
					LocationUtil.reset(true);
					LocationUtil.setStartLocation(dPoint);
					LocationUtil.getBreadCrumbs().add(new DoublePoint(dPoint.getX(),dPoint.getY()));
					SU.registerListeners();
					LocationUtil.setEndTrack(false);
				}
			});
        
        } else {
        	
        	//setContentView(R.layout.main);
        	sysOk = false;
        }
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	if (sysOk) {
    		SU.registerListeners();
    		GLView.onResume();
    	}
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	if (sysOk) {
    		SU.unregisterListeners();
    		GLView.onPause();
    	}
    }
    
    @Override 
    public boolean onTouchEvent(MotionEvent event) {
    	//LocationUtil.reset();
        return true; 
    } 

    public void onSensorChanged(SensorEvent event) {
    	SU.routeEvent(event);
    }
    
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    	
    }
}

class MySurfaceView extends GLSurfaceView {
	public MySurfaceView(Context context, AttributeSet attrs) {
		super(context,attrs);
		
		setRenderer(new MySurfaceRenderer());
	}
}
