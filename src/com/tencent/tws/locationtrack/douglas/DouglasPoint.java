package com.tencent.tws.locationtrack.douglas;

/**
 * 采样点数据类
 */
public class DouglasPoint {

    private int index = 0;

    private double latitude = 0;
    private double longitude = 0;
    private double insSpeed = 0;
    private int id = 0;
    private double dis = 0;
    private long time = 0;
    private float bearing = 0;
    private float accuracy = 0;
    private double avgSpeed = 0;
    private double kcal = 0;

    //数据库中一个点的描述
    public DouglasPoint(double latitude, double longitude, double insSpeed, int id, double dis, long time, float bearing, float accuracy, double avgSpeed, double kcal, int index) {
        this.index = index;
        this.latitude = latitude;
        this.longitude = longitude;
        this.insSpeed = insSpeed;
        this.id = id;
        this.dis = dis;
        this.time = time;
        this.bearing = bearing;
        this.accuracy = accuracy;
        this.avgSpeed = avgSpeed;
        this.kcal = kcal;
    }

    public float getBearing() {
        return bearing;
    }

    public void setBearing(float bearing) {
        this.bearing = bearing;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public double getAvgSpeed() {
        return avgSpeed;
    }

    public void setAvgSpeed(double avgSpeed) {
        this.avgSpeed = avgSpeed;
    }

    public double getKcal() {
        return kcal;
    }

    public void setKcal(double kcal) {
        this.kcal = kcal;
    }

    public double getDis() {
        return dis;
    }

    public void setDis(double dis) {
        this.dis = dis;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public double getInsSpeed() {
        return insSpeed;
    }

    public void setInsSpeed(double insSpeed) {
        this.insSpeed = insSpeed;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}
