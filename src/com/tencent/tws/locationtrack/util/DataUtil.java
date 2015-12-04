package com.tencent.tws.locationtrack.util;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by microzhang on 2015/12/4 at 9:50.
 */
public class DataUtil {
    public static String convert(long mill) {
        Date date = new Date(mill);
        String strs = "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            strs = sdf.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return strs;
    }

    public static String convertHM(long mill) {
        Date date = new Date(mill);
        String strs = "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            strs = sdf.format(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return strs;
    }
    //传入毫秒转换为小时和分钟
    public static String formatDuring(long mss) {
        long days = mss / (1000 * 60 * 60 * 24);
        long hours = (mss % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        long minutes = (mss % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (mss % (1000 * 60)) / 1000;

        return hours + ":" + minutes;
//        return days + " days " + hours + " hours " + minutes + " minutes "
//                + seconds + " seconds ";
    }
}
