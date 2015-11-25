package com.tencent.tws.locationtrack.douglas;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by microzhang on 2015/11/25 at 10:21.
 * 道格拉斯 数据抽稀 算法
 */
public class Douglas {
    /**
     * 存储采样点数据的链表
     */
    public List<Point> points = new ArrayList<Point>();

    /**
     * 控制数据压缩精度的极差
     */
    private static final double D = 0.0001;


    public Douglas(List<Point> points) {
        this.points = points;
    }


    public void compress(Point from, Point to) {

        //压缩算法的开关量
        boolean switchvalue = false;

        double A = (from.getLongitude() - to.getLongitude()) / Math.sqrt(Math.pow((from.getLongitude() - to.getLongitude()), 2) + Math.pow((from.getLatitude() - to.getLatitude()), 2));
        double B = (to.getLatitude() - from.getLatitude()) / Math.sqrt(Math.pow((from.getLongitude() - to.getLongitude()), 2) + Math.pow((from.getLatitude() - to.getLatitude()), 2));
        double C = (from.getLatitude() * to.getLongitude() - to.getLatitude() * from.getLongitude()) / Math.sqrt(Math.pow((from.getLongitude() - to.getLongitude()), 2) + Math.pow((from.getLatitude() - to.getLatitude()), 2));

        double d = 0;
        double dmax = 0;
        int m = points.indexOf(from);
        int n = points.indexOf(to);
        if (n == m + 1)
            return;
        Point middle = null;
        List<Double> distance = new ArrayList<Double>();
        for (int i = m + 1; i < n; i++) {
            d = Math.abs(A * (points.get(i).getLatitude()) + B * (points.get(i).getLongitude()) + C) / Math.sqrt(Math.pow(A, 2) + Math.pow(B, 2));
            distance.add(d);
        }
        dmax = distance.get(0);
        for (int j = 1; j < distance.size(); j++) {
            if (distance.get(j) > dmax)
                dmax = distance.get(j);
        }
        if (dmax > D)
            switchvalue = true;
        else
            switchvalue = false;
        if (!switchvalue) {
            // 删除Points(m,n)内的坐标
            for (int i = m + 1; i < n; i++) {
                points.get(i).setIndex(-1);
            }
        } else {
            for (int i = m + 1; i < n; i++) {
                if ((Math.abs(A * (points.get(i).getLatitude()) + B * (points.get(i).getLongitude()) + C) / Math.sqrt(Math.pow(A, 2) + Math.pow(B, 2)) == dmax))
                    middle = points.get(i);
            }

            compress(from, middle);
            compress(middle, to);
        }
    }
}
