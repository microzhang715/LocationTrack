package com.tencent.tws.locationtrack;


import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import com.tencent.tws.locationtrack.record.ArchiveMeta;
import com.tencent.tws.locationtrack.record.ArchiveNameHelper;
import com.tencent.tws.locationtrack.record.Archiver;
import com.tencent.tws.locationtrack.util.LocationUtil;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class RecordsActivity extends Activity  implements AdapterView.OnItemClickListener{

	private Context context;
	
    private ListView listView;
    private ArrayList<String> archiveFileNames;
    private ArrayList<Archiver> archives;
    public static final String INTENT_ARCHIVE_FILE_NAME = "name";
    public static final String INTENT_SELECT_BY_MONTH = "month";
    
    private ArchiveNameHelper archiveFileNameHelper;
    private ArchivesAdapter archivesAdapter;
    
    private long selectedTime;
	 @Override
	    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
	        Archiver archiver = archives.get(i);
	        Intent intent = new Intent(this, DetailActivity.class);
	        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        intent.putExtra(INTENT_ARCHIVE_FILE_NAME, archiver.getName());
	        startActivity(intent);
	    }
	 
	 
	 /**
	     * ListView Adapter
	     */
	    public class ArchivesAdapter extends ArrayAdapter<Archiver> {

	        public ArchivesAdapter(ArrayList<Archiver> archives) {
	            super(context, R.layout.record_row, archives);
	        }

	        @Override
	        public View getView(int position, View convertView, ViewGroup parent) {
	            Archiver archive = archives.get(position);
	            ArchiveMeta archiveMeta = archive.getMeta();
	            
	            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	            View rowView = inflater.inflate(R.layout.record_row, parent, false);

	            TextView mDescription = (TextView) rowView.findViewById(R.id.description);
	            TextView mCostTime = (TextView) rowView.findViewById(R.id.cost_time);
	            TextView mDistance = (TextView) rowView.findViewById(R.id.distance);

	            mDistance.setText(String.format(getString(R.string.records_formatter),
	                    archiveMeta.getDistance() / ArchiveMeta.TO_KILOMETRE));
	           // mDistance.setText(String.valueOf(archiveMeta.getDistance()));
	            Location location =  archive.getFirstRecord();
	            if(location!=null)
	            {
		            mCostTime.setText(LocationUtil.convert(location.getTime()));
	            }
	            
	            mDescription.setText(R.string.no_description);

	            return rowView;
	        }
	    }
	 
	 
	 @Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.records);

	        this.context = getApplicationContext();
	        this.listView = (ListView) findViewById(R.id.records_list);
	        this.archiveFileNameHelper = new ArchiveNameHelper(context);

	        this.archives = new ArrayList<Archiver>();
	        this.archivesAdapter = new ArchivesAdapter(archives);
	        this.listView.setAdapter(archivesAdapter);
	    }

	  @Override
	    public void onStart() {
	        super.onStart();
	        listView.setOnItemClickListener(this);

	        selectedTime = getIntent().getLongExtra(INTENT_SELECT_BY_MONTH, System.currentTimeMillis());

	        getArchiveFilesByMonth(new Date(selectedTime));
	    }


	    @Override
	    public void onStop() {
	        super.onStop();
	        closeArchives();
	    }

	    @Override
	    public void onResume() {
	        super.onResume();
	        archivesAdapter.notifyDataSetChanged();
	    }

	    @Override
	    public void onPause() {
	        super.onPause();
	    }
	    
	    
	    
	    private void getArchiveFilesByMonth(Date date) {
	        archiveFileNames = archiveFileNameHelper.getArchiveFilesNameByMonth(date);
	        openArchivesFromFileNames();
	    }

	    /**
	     * 从指定目录读取所有已保存的列表
	     */
	    private void openArchivesFromFileNames() {
	        Iterator<String> iterator = archiveFileNames.iterator();
	        while (iterator.hasNext()) {
	            String name = iterator.next();
	            Archiver archive = new Archiver(context, name);

	            if (archive.getFirstRecord()!=null) {
	                archives.add(archive);
	            }
	        }
	    }
	    
	    /**
	     * 清除列表
	     */
	    private void closeArchives() {
	        Iterator<Archiver> iterator = archives.iterator();
	        while (iterator.hasNext()) {
	            Archiver archive = (Archiver) iterator.next();
	            if (archive != null) {
	                archive.close();
	            }
	        }
	        archives.clear();
	    }
	    
	    
	    /**
	     * 获得当前已经记录的距离
	     *
	     * @return
	     */
	    public float getRawDistance(Archiver archive) {
	        ArrayList<Location> locations = archive.fetchAll();
	        Location lastComputedLocation = null;
	        float distance = 0;
	        for (int i = 0; i < locations.size(); i++) {
	            Location location = locations.get(i);
	            if (lastComputedLocation != null) {
	                distance += lastComputedLocation.distanceTo(location);
	            }

	            lastComputedLocation = location;
	        }

	        return distance;
	    }
}
