package com.tencent.tws.locationtrack.database;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by microzhang on 2015/11/3 at 16:26.
 */
public class SPUtils {
	private static SharedPreferences sp;
	private static final String SP_NAME = "exit_sp";
	private static final String DB_NAME = "db_name";

	public static void writeSp(Context context, String dbName) {
		sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
		sp.edit().putString(DB_NAME, dbName).commit();
	}

	public static String readSp(Context context) {
		sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
		String dbName = sp.getString(DB_NAME, "");
		return dbName;
	}

	public static void clearSp(Context context) {
		sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
		sp.edit().putString(DB_NAME, "").commit();
	}
}
