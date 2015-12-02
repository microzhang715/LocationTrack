package com.tencent.tws.locationtrack.douglas;

/**
 * 采样点数据类
 */
public class DouglasPoint {

    /**
     * 点所属的曲线的索引
     */
    private int index = 0;
    /**
     * 点的X坐标
     */
    private double latitude = 0;

    /**
     * 点的Y坐标
     */
    private double longitude = 0;

    private double speed = 0;

    private int id = 0;

    private double dis = 0;
    private long time;

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

    public DouglasPoint(double latitude, double longitude, double speed, double dis, long time, int id, int index) {
        this.index = index;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.id = id;
        this.dis = dis;
        this.time = time;

    }

    public double getSpeed() {

        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
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

//    /**
//     * 点数据的构造方法
//     *
//     * @param latitude  点的X坐标
//     * @param longitude 点的Y坐标
//     * @param index     点所属的曲线的索引
//     */
//    public DouglasPoint(double latitude, double longitude, int index) {
//        this.latitude = latitude;
//        this.longitude = longitude;
//        this.index = index;
//    }

}
