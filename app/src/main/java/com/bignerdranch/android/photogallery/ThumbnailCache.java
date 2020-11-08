package com.bignerdranch.android.photogallery;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

import static android.content.Context.ACTIVITY_SERVICE;

public class ThumbnailCache extends LruCache<String, Bitmap> {

    public ThumbnailCache(int maxSize) {
        super(maxSize);
    }

    public static int getMaxSize(Context context) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(ACTIVITY_SERVICE);
        return activityManager.getMemoryClass() * 1024 / 8;
    }

}