package com.tencent.tws.locationtrack.util;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;

/**
 * Created by microzhang on 2015/11/12 at 19:09.
 */
public class ScreenUtils {
    private static final String TAG = "ScreenUtils";

    public static Bitmap takeScreenShot(Activity act) {
        if (act == null || act.isFinishing()) {
            Log.d(TAG, "act参数为空.");
            return null;
        }

        // 获取当前视图的view
        View scrView = act.getWindow().getDecorView();
        scrView.setDrawingCacheEnabled(true);
        scrView.buildDrawingCache(true);

        // 获取状态栏高度
        Rect statuBarRect = new Rect();
        scrView.getWindowVisibleDisplayFrame(statuBarRect);
        int statusBarHeight = statuBarRect.top;
        int width = act.getWindowManager().getDefaultDisplay().getWidth();
        int height = act.getWindowManager().getDefaultDisplay().getHeight();

        Bitmap scrBmp = null;
        try {
            // 去掉标题栏的截图
            scrBmp = Bitmap.createBitmap(scrView.getDrawingCache(), 0, statusBarHeight,
                    width, height - statusBarHeight);
        } catch (IllegalArgumentException e) {
            Log.d("", "#### 旋转屏幕导致去掉状态栏失败");
        }
        scrView.setDrawingCacheEnabled(false);
        scrView.destroyDrawingCache();
        return scrBmp;
    }
}
