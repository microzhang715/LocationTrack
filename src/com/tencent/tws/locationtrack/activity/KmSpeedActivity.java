package com.tencent.tws.locationtrack.activity;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.tencent.tws.locationtrack.R;
import com.tencent.tws.locationtrack.database.LocationDbHelper;
import com.tencent.tws.locationtrack.domain.KmPoint;
import com.tencent.tws.locationtrack.douglas.DouglasPoint;
import com.tencent.tws.locationtrack.util.DataUtil;
import com.tencent.tws.locationtrack.util.PointsAnalysis;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 配速显示的Activity
 * Created by microzhang on 2015/12/3 at 19:42.
 */
public class KmSpeedActivity extends Activity {
    private static final String TAG = "KmSpeedActivity";

    private ExecutorService fixedThreadExecutor = Executors.newFixedThreadPool(2);
    private SpeedAdapter speedAdapter;
    private List<KmPoint> kmList;
    private Context context;

    String dbName;
    private static LocationDbHelper dbHelper;

    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.km_speed_layout);

        context = getApplicationContext();
        listView = (ListView) findViewById(R.id.km_speed_list);

        String intentDbName = getIntent().getStringExtra("fulldbName");
        if (intentDbName != null && !intentDbName.equals("")) {
            dbName = getIntent().getStringExtra("fulldbName");
            Log.i(TAG, "get intentDbName=" + intentDbName);
            if (dbName != null && !dbName.equals("") && !dbName.equals("0")) {
                dbHelper = new LocationDbHelper(getApplicationContext(), dbName);
            }
        } else {
            Toast.makeText(getApplicationContext(), "数据库文件不存在", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();

        fixedThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (kmList != null) {
                    kmList.clear();
                }

                PointsAnalysis analysis = new PointsAnalysis(getApplicationContext());
                List<DouglasPoint> listPoints = analysis.getAllPointsFromHelper(dbHelper);
                kmList = analysis.getKmSpeed(listPoints);

                Log.i(TAG, "listPoints.size()=" + listPoints.size());
                Log.i(TAG, "kmList.size()=" + kmList.size());
                handler.sendEmptyMessage(0);
            }
        });
    }


    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    Log.i(TAG, "111111");
                    speedAdapter = new SpeedAdapter(kmList);
                    listView.setAdapter(speedAdapter);
                    break;
            }
        }
    };


    class SpeedAdapter extends ArrayAdapter<KmPoint> {

        private List<KmPoint> listPoints;

        public SpeedAdapter(Context context, int resource) {
            super(context, resource);
        }

        public SpeedAdapter(List<KmPoint> listPoints) {
            super(context, R.layout.km_speed_item);
            this.listPoints = listPoints;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.km_speed_item, parent, false);

            TextView dis = (TextView) rowView.findViewById(R.id.km_dis);
            TextView time = (TextView) rowView.findViewById(R.id.km_time);
            TextView avgSpeed = (TextView) rowView.findViewById(R.id.km_speed);
            TextView kmSpeedTime = (TextView) rowView.findViewById(R.id.km_speed_time);

            //公里数
            dis.setText(listPoints.get(position).getDisKm() + "");

            //总共时长,每公里耗时相加的时间长读
            DecimalFormat myformat = new DecimalFormat("#0.00");
            avgSpeed.setText(myformat.format(listPoints.get(position).getAvgSpeed()));

            //每公里的平均速度
            //Log.i(TAG, "position" + position + "   allTime = " + listPoints.get(position).getAllTime());
            String t1 = DataUtil.formatDuring(listPoints.get(position).getAllTime());
            Log.i(TAG, "t1=" + t1);
            time.setText(t1);

            //单公里耗时
            //Log.i(TAG, "position" + position + "   deltTime = " + listPoints.get(position).getTimePreKm());
            String t2 = DataUtil.formatDuring(listPoints.get(position).getTimePreKm());
            kmSpeedTime.setText(t2);

            return rowView;
        }

        @Override
        public int getCount() {
            return listPoints.size();
        }
    }

}
