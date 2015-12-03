package com.tencent.tws.locationtrack.domain;

/**
 * 配速点的描述
 * Created by microzhang on 2015/12/3 at 17:27.
 */
public class KmPoint {
    //
    private int disKm = 0;
    private double allTime = 0;
    private double avgSpeed = 0;
    private float kmSpeedTime = 0;
    private int id = 0;

    public KmPoint(int disKm, double allTime, double avgSpeed, float kmSpeedTime, int id) {
        this.disKm = disKm;
        this.allTime = allTime;
        this.avgSpeed = avgSpeed;
        this.kmSpeedTime = kmSpeedTime;
        this.id = id;
    }

    public double getDisKm() {
        return disKm;
    }

    public void setDisKm(int disKm) {
        this.disKm = disKm;
    }

    public double getAllTime() {
        return allTime;
    }

    public void setAllTime(double allTime) {
        this.allTime = allTime;
    }

    public double getAvgSpeed() {
        return avgSpeed;
    }

    public void setAvgSpeed(double avgSpeed) {
        this.avgSpeed = avgSpeed;
    }

    public double getKmSpeedTime() {
        return kmSpeedTime;
    }

    public void setKmSpeedTime(float kmSpeedTime) {
        this.kmSpeedTime = kmSpeedTime;
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
                ", kmSpeedTime=" + kmSpeedTime +
                ", id=" + id +
                '}';
    }
}
