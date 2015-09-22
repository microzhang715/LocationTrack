package com.tencent.tws.locationtrack.util;


public class DoublePoint {
	private double x;
	private double y;
	
	public double getX() { return x; }
	public double getY() { return y; }
	
	public DoublePoint(double ix, double iy) {
		x = ix;
		y = iy;
	}
	
	public void set(double newx, double newy) {
		x = newx;
		y = newy;
	}
	
	public void offset(double dx, double dy) {
		x += dx;
		y += dy;
	}
	
	public float distance() {
		return (float)Math.sqrt(Math.pow(x, 2) + Math.pow(y,2));
	}
	
	public float distanceFrom(DoublePoint other) {
		return (float)Math.sqrt(Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2));
	}
}