package com.ravikhb.jumbotail;

import android.app.Application;
import android.text.TextUtils;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.crashlytics.android.Crashlytics;
import io.fabric.sdk.android.Fabric;

/**
 * Created by ravikhb on 01/02/16.
 */
public class CustomVolley extends Application {
    private static final String TAG = CustomVolley.class.getSimpleName();
    private RequestQueue mRequestQueue;
    private static CustomVolley mCustomVolley;

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());
        mCustomVolley = this;
    }

    public static CustomVolley getInstance() {
        return  mCustomVolley;
    }

    private RequestQueue getRequestQueue() {
        if (mRequestQueue == null) {
            mRequestQueue = Volley.newRequestQueue(getApplicationContext());
        }
        return mRequestQueue;
    }

    public void addToRequestQueue(Request request, String tag) {
        request.setTag(TextUtils.isEmpty(tag) ? TAG : tag);
        getRequestQueue().add(request);
    }

}
