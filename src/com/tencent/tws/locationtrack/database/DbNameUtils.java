package com.tencent.tws.locationtrack.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by microzhang on 2015/11/11 at 16:06.
 */
public class DbNameUtils {
    private Context context;
    private SharedPreferences sharedPreferences;
    private static final String TAG = "DbNameUtils";

    public DbNameUtils(Context context) {
        this.context = context;
    }


    public ArrayList<String> getDbNames() {
        String dbPath = "/data/data/com.tencent.tws.locationtrack/databases";
        Log.i(TAG, getFiles(dbPath, "db", false).toString());
        ArrayList<String> fullNames = getFiles(dbPath, "db", true);
        ArrayList<String> resultNames = new ArrayList<String>();

        long[] tmp = new long[fullNames.size()];

        for (int i = 0; i < fullNames.size(); i++) {
            String name = fullNames.get(i).substring(fullNames.get(i).lastIndexOf("/") + 1);
            String[] tempString = name.split("_");
            try {
                tmp[i] = Long.parseLong(tempString[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Arrays.sort(tmp);

        for (int i = tmp.length - 1; i >= 0; i--) {
            if (tmp[i] != 0) {
                resultNames.add(String.valueOf(tmp[i]));
            }
        }
        return resultNames;
    }

    /**
     * @param Path        ??????
     * @param Extension   ?????
     * @param IsIterative ?????????????
     */
    public ArrayList<String> getFiles(String Path, String Extension, boolean IsIterative)  //??????????????????????????
    {
        ArrayList<String> lstFile = new ArrayList<String>();

        File[] files = new File(Path).listFiles();

        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                if (f.isFile()) {
                    if (f.getPath().substring(f.getPath().length() - Extension.length()).equals(Extension))  //?ж??????
                        lstFile.add(f.getPath());

                    if (!IsIterative)
                        break;
                } else if (f.isDirectory() && f.getPath().indexOf("/.") == -1)
                    getFiles(f.getPath(), Extension, IsIterative);
            }
        }

        return lstFile;
    }

    public boolean deleteFile(String fulldbName) {
        boolean flag = false;
        File file = new File(fulldbName);
        // 路径为文件且不为空则进行删除
        if (file.isFile() && file.exists()) {
            file.delete();
            flag = true;
        }
        return flag;
    }
}
