package com.tencent.tws.locationtrack.database;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by microzhang on 2015/11/3 at 16:26.
 */
public class SPUtils {
    private static SharedPreferences sp;
    private static final String SP_NAME = "location_track";
    private static final String DB_NAME = "db_name";

    //点击退出按钮标记
    private static final String EXIT_FLAG = "exit_flag";
    private static final String TENCENT_EXIT_FLAG = "tencent_exit_flag";

    //点击退出按钮标记
    private static final String START_FLAG = "start_flag";
    private static final String TENCENT_START_FLAG = "tencent_start_flag";

    //启动activity
    private static final String START_ACTIVITY = "start_activity";


    //DB操作
    public static void writeDBName(Context context, String dbName) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(DB_NAME, dbName).commit();
    }

    public static String readDBName(Context context) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        String dbName = sp.getString(DB_NAME, "");
        return dbName;
    }

    public static void clearDBName(Context context) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(DB_NAME, "").commit();
    }

    //退出标志操作
    public static void writeExitFlag(Context context, boolean isExit) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(EXIT_FLAG, isExit).commit();
    }

    public static boolean readExitFlag(Context context) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        boolean exitFlag = sp.getBoolean(EXIT_FLAG, true);
        return exitFlag;
    }

//    //退出标志操作
//    public static void writeStartFlag(Context context, boolean isExit) {
//        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
//        sp.edit().putBoolean(START_FLAG, isExit).commit();
//    }
//
//    public static boolean readStartFlag(Context context) {
//        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
//        boolean exitFlag = sp.getBoolean(START_FLAG, true);
//        return exitFlag;
//    }


    //腾讯定位退出标志操作
    public static void writeTencentExitFlag(Context context, boolean isExit) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(TENCENT_EXIT_FLAG, isExit).commit();
    }

    public static boolean readTencentExitFlag(Context context) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        boolean exitFlag = sp.getBoolean(TENCENT_EXIT_FLAG, true);
        return exitFlag;
    }

    //启动Activity标记
    public static void writeStartActivity(Context context, String activityName) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(START_ACTIVITY, activityName).commit();
    }

    public static String readStartActivity(Context context) {
        sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getString(START_ACTIVITY, "");
    }
}
