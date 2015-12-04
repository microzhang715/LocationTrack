package com.tencent.tws.locationtrack.domain;

/**
 * 配速点的描述
 * Created by microzhang on 2015/12/3 at 17:27.
 */
public class KmPoint {
    //公里数
    private int disKm = 0;
    //总共时长,每公里耗时相加的时间长读
    private long allTime = 0;
    //每公里的平均速度
    private double avgSpeed = 0;
    //单公里耗时
    private long timePreKm = 0;
    //数据库中点的id,方便后期扩展使用
    private int id = 0;

    public KmPoint(int disKm, long allTime, double avgSpeed, long kmSpeedTime, int id) {
        this.disKm = disKm;
        this.allTime = allTime;
        this.avgSpeed = avgSpeed;
        this.timePreKm = kmSpeedTime;
        this.id = id;
    }

    public double getDisKm() {
        return disKm;
    }

    public void setDisKm(int disKm) {
        this.disKm = disKm;
    }

    public long getAllTime() {
        return allTime;
    }

    public void setAllTime(long allTime) {
        this.allTime = allTime;
    }

    public double getAvgSpeed() {
        return avgSpeed;
    }

    public void setAvgSpeed(double avgSpeed) {
        this.avgSpeed = avgSpeed;
    }

    public long getTimePreKm() {
        return timePreKm;
    }

    public void setTimePreKm(long timePreKm) {
        this.timePreKm = timePreKm;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "KmPoint{" +
                "disKm=" + disKm +
                ", allTime=" + allTime +
                ", avgSpeed=" + avgSpeed +
                ", timePreKm=" + timePreKm +
                ", id=" + id +
                '}';
    }
}
