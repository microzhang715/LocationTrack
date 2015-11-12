package com.tencent.tws.locationtrack;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.tencent.tws.locationtrack.database.DbNameUtils;

import java.util.ArrayList;

public class HistoryActivity extends Activity implements AdapterView.OnItemClickListener {
    private static final String TAG = "HistoryActivity";

    private Context context;

    private ListView listView;
    private DatebaseAdapter datebaseAdapter;
    private DbNameUtils dbNameUtils;

    private ArrayList<String> dbaNamesList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.records);

        this.context = getApplicationContext();
        dbNameUtils = new DbNameUtils(context);

        this.listView = (ListView) findViewById(R.id.records_list);
        if (dbNameUtils.getDbNames().size() > 0) {
            dbaNamesList = dbNameUtils.getDbNames();
            this.datebaseAdapter = new DatebaseAdapter(dbaNamesList);
            this.listView.setAdapter(datebaseAdapter);
            this.listView.setOnItemClickListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (datebaseAdapter != null) {
            datebaseAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
//        closeArchives();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String tmpdbName = dbaNamesList.get(position);
        String fulldbName = tmpdbName + "_location.db";

        Intent intent = new Intent(this, HisLocationActivity.class);
        intent.putExtra("fulldbName", fulldbName);
        startActivity(intent);

        Log.i(TAG, "fulldbName=" + fulldbName);
    }


    //    @Override
//    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
////        Archiver archiver = archives.get(i);
////        Intent intent = new Intent(this, DetailActivity.class);
////        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
////        intent.putExtra(INTENT_ARCHIVE_FILE_NAME, archiver.getName());
////        startActivity(intent);
//    }

    public class DatebaseAdapter extends ArrayAdapter<String> {
        private ArrayList<String> dbNames;

        public DatebaseAdapter(ArrayList<String> dbNames) {
            super(context, R.layout.record_row, dbNames);
            this.dbNames = dbNames;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.db_names, parent, false);

            TextView dbName = (TextView) rowView.findViewById(R.id.db_name);
            dbName.setText(dbNames.get(position));

            return rowView;
        }
    }

}
