package com.tencent.tws.locationtrack.douglas;

import com.tencent.tws.locationtrack.util.Gps;

/**
 * Created by microzhang on 2015/12/1 at 16:00.
 */

public class SpeedPoint {

    private Gps position;
    //id方便后期在数据库中拿到其他有用数据
    private int id;
    private double speed;

    public SpeedPoint(Gps position, int id, double speed) {
        this.position = position;
        this.id = id;
        this.speed = speed;
    }

    public Gps getPosition() {
        return position;
    }

    public void setPosition(Gps position) {
        this.position = position;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }
    //    private float accuracy;
//    private long time;
//    private double distance;
//    private double avg_speed;
//    private double kcal;


}
