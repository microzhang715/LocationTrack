package com.tencent.tws.locationtrack.database;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


/**
 * Created by microzhang on 2015/10/30 at 15:33.
 */
public class LocationDbHelper extends SQLiteOpenHelper {
	private static final String TAG = "LocationDbHelper";

	public static final String DATABASE_NAME = "location.db";
	public static final int DATABASE_VERSION = 1;
	public static final String TABLE_NAME = "locations";

	public static final String ID = "id";
	public static final String LATITUDE = "latitude";
	public static final String LONGITUDE = "longitude";
	public static final String INS_SPEED = "ins_speed";
	public static final String BEARING = "bearing";
	public static final String ALTITUDE = "altitude";
	public static final String ACCURACY = "accuracy";
	public static final String TIME = "times";
	public static final String DISTANCE = "distance";
	public static final String AVG_SPEED = "avg_speed";
	public static final String KCAL = "kcal";

	public static final String DEFAULT_ORDERBY = "id ASC";

	protected static final String SQL_CREATE_LOCATION_TABLE =
			"create table " + TABLE_NAME + " ("
					+ "id integer primary key autoincrement, "
					+ "latitude double not null, "
					+ "longitude double not null,"
					+ "ins_speed double not null, "
					+ "bearing float not null,"
					+ "altitude double not null,"
					+ "accuracy float not null,"
					+ "times long not null,"
					+ "distance float not null,"
					+ "avg_speed float not null, "
					+ "kcal float not null"
					+ ");";

	public LocationDbHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		Log.i("kermit", "LocationDbHelper");
	}

	@Override
	public void onCreate(SQLiteDatabase database) {
		Log.i("kermit", "LocationDbHelper onCreate");
		try {
			database.execSQL(SQL_CREATE_LOCATION_TABLE);
		} catch (SQLException e) {
			Log.e(TAG, e.getMessage());
		}
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

	}
}
