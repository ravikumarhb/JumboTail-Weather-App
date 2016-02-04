package com.ravikhb.jumbotail;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.WindowManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

/**
 * Created by ravikhb on 01/02/16.
 */
public class Utils {

    public static final String BASE_URL_UV = "http://api.owm.io/air/1.0/uvi/current?";
    public static final String BASE_URL_FORECAST = "http://api.openweathermap.org/data/2.5/forecast?";
    public static final String BASE_URL_WEATHER = "http://api.openweathermap.org/data/2.5/weather?";
    public static final String API_KEY = "ff7cac0e3fe1f439b287622eff868d6d";
    public static final String AND = "&";

    public static class  params {
        public static final String LAT = "lat=";
        public static final String LON = "lon=";
        public static final String APPID = "appid=";
        public static final String UNITS = "units=metric";
    }
    public static boolean isNetConnected(Context context) {
        if (context != null) {
            final ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context
                    .CONNECTIVITY_SERVICE);
            final NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            return !(networkInfo == null || !networkInfo.isConnectedOrConnecting());
        } else {
            return false;
        }
    }

    public static boolean checkGooglePlayServices(final Context context, int requestCode) {
        final int googlePlayServicesCheck = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
        switch (googlePlayServicesCheck) {
            case ConnectionResult.SUCCESS:
                return true;
            case ConnectionResult.SERVICE_DISABLED:
            case ConnectionResult.SERVICE_INVALID:
            case ConnectionResult.SERVICE_MISSING:
                return false;
            case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
                try {
                    if (context instanceof Activity) {
                        Activity activity = (Activity) context;
                        Dialog dialog = GooglePlayServicesUtil.getErrorDialog(googlePlayServicesCheck, activity, requestCode);
                        dialog.setCancelable(false);
                        dialog.show();
                    }
                } catch (WindowManager.BadTokenException e) {
                }
        }
        return false;
    }

}
