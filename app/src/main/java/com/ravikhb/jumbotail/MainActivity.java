package com.ravikhb.jumbotail;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements
        ConnectionCallbacks,
        OnConnectionFailedListener,
        LocationListener,
        ResultCallback<LocationSettingsResult> {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 120000;

    private final static String KEY_LOCATION = "location";
    private final static String KEY_LAST_UPDATED_TIME_STRING = "last-updated-time-string";

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private Location mCurrentLocation;

    private String mLastUpdateTime;

    private Typeface weatherFont;

    private TextView cityField;
    private TextView updatedField;
    private TextView detailsField;
    private TextView currentTemperatureField;
    private TextView maxTemperatureField;
    private TextView minTemperatureField;

    private TextView mWeatherIconDetailedView;
    private TextView mHumidity;
    private TextView mUV;
    private TextView mWindSpeed;

    private String[] uvRange;
    private final List<WeatherInformation> mWeatherInfoList = new ArrayList<>();
    private RecyclerView mRecyclerView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        initializeMixPanel();

        mLastUpdateTime = "";

        cityField = (TextView)findViewById(R.id.city_field);
        updatedField = (TextView)findViewById(R.id.updated_field);
        detailsField = (TextView)findViewById(R.id.description);
        currentTemperatureField = (TextView)findViewById(R.id.current_temperature);
        maxTemperatureField = (TextView)findViewById(R.id.max_temperature);
        minTemperatureField = (TextView)findViewById(R.id.min_temperature);

        mWeatherIconDetailedView = (TextView)findViewById(R.id.weather_icon_detailed_view);
        mHumidity = (TextView)findViewById(R.id.humidity);
        mUV = (TextView)findViewById(R.id.uv);
        mWindSpeed = (TextView)findViewById(R.id.wind_speed);

        weatherFont = Typeface.createFromAsset(getAssets(), "weather.ttf");
        mWeatherIconDetailedView.setTypeface(weatherFont);

        mRecyclerView = (RecyclerView)findViewById(R.id.next_days_recycler_view);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setAdapter(new RecyclerViewAdapter());

        uvRange = getResources().getStringArray(R.array.uv_range_desc);
        if(Utils.checkGooglePlayServices(this, 1) && Utils.isNetConnected(this)) {
            buildGoogleApiClient();
            createLocationRequest();
            buildLocationSettingsRequest();
        } else {
            Toast.makeText(this, "Error occured !", Toast.LENGTH_SHORT).show();
        }

    }

    void initializeMixPanel() {
        String projectToken = "d4a80853dc551a9dbf10bf81ba444b1d";
        MixpanelAPI mixpanel = MixpanelAPI.getInstance(this, projectToken);
        try {
            JSONObject props = new JSONObject();
            props.put("JumboTail", "MainActivity");
            mixpanel.track("onCreate - called", props);
        } catch (JSONException e) {
            Crashlytics.logException(e);
        }
    }

    private synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }


    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void buildLocationSettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    private void checkLocationSettings() {
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(
                        mGoogleApiClient,
                        mLocationSettingsRequest
                );
        result.setResultCallback(this);
    }

    @Override
    public void onResult(LocationSettingsResult locationSettingsResult) {
        final Status status = locationSettingsResult.getStatus();
        switch (status.getStatusCode()) {
            case LocationSettingsStatusCodes.SUCCESS:
                if( mGoogleApiClient.isConnected())
                    startLocationUpdates();
                break;
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                try {
                    status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                } catch (IntentSender.SendIntentException e) {
                    Crashlytics.logException(e);
                }
                break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        startLocationUpdates();
                        break;
                    case Activity.RESULT_CANCELED:
                        break;
                }
                break;
        }
    }

    private void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient,
                mLocationRequest,
                this
        ).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
            }
        });

    }

    private void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient,
                this
        ).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public void onResume() {
        super.onResume();
        checkLocationSettings();

    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGoogleApiClient.isConnected()) {
            stopLocationUpdates();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        if (mCurrentLocation == null) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        if(mCurrentLocation != null) {
            downloadWeatherData();
            downloadForecastData();
            downloadUVdata();
        }
        Toast.makeText(this, getResources().getString(R.string.location_updated_message),
                Toast.LENGTH_SHORT).show();
    }

    private void downloadWeatherData() {

        // http://api.openweathermap.org/data/2.5/weather?lat=12.96&lon=77.64&appid=ff7cac0e3fe1f439b287622eff868d6d&units=metric

        String currentWeatherCoOrd = Utils.BASE_URL_WEATHER + Utils.params.LAT + mCurrentLocation.getLatitude()
                + Utils.AND + Utils.params.LON + mCurrentLocation.getLongitude()
                + Utils.AND + Utils.params.APPID + Utils.API_KEY
                + Utils.AND + Utils.params.UNITS ;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, currentWeatherCoOrd, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        renderWeather(jsonObject);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
            }
        });
        CustomVolley.getInstance().addToRequestQueue(request, "");
    }

    private void downloadForecastData() {

        // http://api.openweathermap.org/data/2.5/forecast?lat=12.96&lon=77.64&appid=ff7cac0e3fe1f439b287622eff868d6d&units=metric

        String currentWeatherCoOrd = Utils.BASE_URL_FORECAST + Utils.params.LAT + mCurrentLocation.getLatitude()
                + Utils.AND + Utils.params.LON + mCurrentLocation.getLongitude()
                + Utils.AND + Utils.params.APPID + Utils.API_KEY
                + Utils.AND + Utils.params.UNITS ;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, currentWeatherCoOrd, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        renderForecast(jsonObject);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
            }
        });
        CustomVolley.getInstance().addToRequestQueue(request, "");
    }

    private void downloadUVdata() {

        // http://api.owm.io/air/1.0/uvi/current?lat=55&lon=37&appid=ff7cac0e3fe1f439b287622eff868d6d

        String uvURL = Utils.BASE_URL_UV + Utils.params.LAT + mCurrentLocation.getLatitude()
                + Utils.AND + Utils.params.LON + mCurrentLocation.getLongitude()
                + Utils.AND + Utils.params.APPID + Utils.API_KEY;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, uvURL, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject jsonObject) {
                        UVInformation(jsonObject);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
            }
        });
        CustomVolley.getInstance().addToRequestQueue(request, "");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "Connection suspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putParcelable(KEY_LOCATION, mCurrentLocation);
        savedInstanceState.putString(KEY_LAST_UPDATED_TIME_STRING, mLastUpdateTime);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void renderWeather(JSONObject json){
        Resources res = getResources();
        try {
            cityField.setText(json.getString("name").toUpperCase(Locale.US) +
                    ", " +
                    json.getJSONObject("sys").getString("country"));

            JSONObject details = json.getJSONArray("weather").getJSONObject(0);
            JSONObject main = json.getJSONObject("main");
            detailsField.setText(
                    details.getString("description").toUpperCase(Locale.US));

            currentTemperatureField.setText(
                    String.format("%.2f", main.getDouble("temp"))+ " ℃");

            DateFormat df = DateFormat.getDateTimeInstance();
            String updatedOn = df.format(new Date(json.getLong("dt") * 1000));
            updatedField.setText( res.getString(R.string.last_update) + updatedOn);

            setWeatherIcon(details.getInt("id"),
                    json.getJSONObject("sys").getLong("sunrise") * 1000,
                    json.getJSONObject("sys").getLong("sunset") * 1000);

            mHumidity.setText(res.getString(R.string.humidity) + main.getString("humidity"));

            JSONObject wind = json.getJSONObject("wind");
            mWindSpeed.setText(res.getString(R.string.speed) + wind.getString("speed"));

        } catch (Exception e) {
            Crashlytics.logException(e);
        }
    }

    private void renderForecast(JSONObject json){
        Resources res = getResources();
        try {

            JSONObject todayObject = json.getJSONArray("list").getJSONObject(0);
            JSONObject currentWeatherMain = todayObject.getJSONObject("main");

            currentTemperatureField.setText(String.format("%.2f", currentWeatherMain.getDouble("temp"))+ " ℃");
            minTemperatureField.setText(res.getString(R.string.min_temp) + String.format("%.2f", currentWeatherMain.getDouble("temp_min"))+ " ℃");
            maxTemperatureField.setText(res.getString(R.string.max_temp) + String.format("%.2f", currentWeatherMain.getDouble("temp_max")) + " ℃");

            populateList(json);

        }catch(Exception e){
            Crashlytics.logException(e);
        }
    }

    private void UVInformation(JSONObject json){
        Resources res = getResources();
        String intensity = res.getString(R.string.uv_intensity);
        try {
            int uvValue = json.getInt("value");
            if(uvValue >= 0 && uvValue <= 2.9) {
                mUV.setText(intensity+ uvRange[0]);
            } else if(uvValue >= 3 && uvValue <= 5.9){
                mUV.setText(intensity + uvRange[1]);
            } else if(uvValue >= 6 && uvValue <= 7.9){
                mUV.setText(intensity + uvRange[2]);
            } else if(uvValue >= 8 && uvValue <= 10.9){
                mUV.setText(intensity + uvRange[3]);
            } else {
                mUV.setText(intensity + uvRange[4]);
            }
        } catch (JSONException e) {
            Crashlytics.logException(e);
        }
    }

    private void setWeatherIcon(int actualId, long sunrise, long sunset){
        int id = actualId / 100;
        String icon = "";
        if(actualId == 800){
            long currentTime = new Date().getTime();
            if (currentTime >= sunrise && currentTime < sunset) {
                icon = getString(R.string.weather_sunny);
            } else {
                icon = getString(R.string.weather_clear_night);
            }
        } else {
            switch(id) {
                case 2 : icon = getString(R.string.weather_thunder);
                    break;
                case 3 : icon = getString(R.string.weather_drizzle);
                    break;
                case 7 : icon = getString(R.string.weather_foggy);
                    break;
                case 8 : icon = getString(R.string.weather_cloudy);
                    break;
                case 6 : icon = getString(R.string.weather_snowy);
                    break;
                case 5 : icon = getString(R.string.weather_rainy);
                    break;
            }
        }

        mWeatherIconDetailedView.setText(icon);
    }

    private void populateList(JSONObject jsonObject) {

        Calendar calendarObj = Calendar.getInstance();
        Date currentDate = calendarObj.getTime();

        if(mWeatherInfoList != null)
            mWeatherInfoList.clear();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        SimpleDateFormat dateFormatDate = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

        try {
            JSONArray jsonArray = jsonObject.getJSONArray("list");
            for(int i = 0; i< jsonArray.length(); i++) {
                WeatherInformation weatherInfo = new WeatherInformation();
                JSONObject objectIndex = jsonArray.getJSONObject(i);
                String time = objectIndex.getString("dt_txt");
                try {
                    Date dateEntry = dateFormat.parse(time);
                    if(dateEntry.getTime() >= currentDate.getTime() && time.contains("06:00:00")) {
                        JSONObject main = objectIndex.getJSONObject("main");
                        weatherInfo.mMinTemp = main.getDouble("temp_min") + "";
                        weatherInfo.mMaxTemp = main.getDouble("temp_max") + "";
                        weatherInfo.mDay = dateFormatDate.format(dateEntry)+"";
                        JSONObject weather = objectIndex.getJSONArray("weather").getJSONObject(0);
                        weatherInfo.mImageCode  = weather.getInt("id");
                        mWeatherInfoList.add(weatherInfo);
                    }
                } catch (ParseException e) {
                    Crashlytics.logException(e);
                }
            }
        } catch (JSONException e) {
            Crashlytics.logException(e);
        }
    }


    private class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.Weather> {

        @Override
        public Weather onCreateViewHolder(ViewGroup viewGroup, int i) {
            View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.recycler_view_layout, viewGroup, false);
            return new Weather(view);
        }

        @Override
        public void onBindViewHolder(Weather weather, int i) {
            WeatherInformation weatherItem = mWeatherInfoList.get(i);
            weather.mDay.setText(weatherItem.mDay);
            weather.mIcon.setText(imageType(weatherItem.mImageCode));
            weather.mMinTempItem.setText(weatherItem.mMinTemp);
            weather.mMaxTempItem.setText(weatherItem.mMaxTemp);
        }

        @Override
        public int getItemCount() {
            if(mWeatherInfoList != null)
                return mWeatherInfoList.size();
            return 0;
        }

        public String imageType(int id){
            int value = id / 100;
            String icon = "";
            switch(value) {
                case 1: icon = getString(R.string.weather_sunny);
                    break;
                case 2 : icon = getString(R.string.weather_thunder);
                    break;
                case 3 : icon = getString(R.string.weather_drizzle);
                    break;
                case 7 : icon = getString(R.string.weather_foggy);
                    break;
                case 8 : icon = getString(R.string.weather_cloudy);
                    break;
                case 6 : icon = getString(R.string.weather_snowy);
                    break;
                case 5 : icon = getString(R.string.weather_rainy);
                    break;
            }
            return icon;
        }

        class Weather extends RecyclerView.ViewHolder {

            TextView mDay;
            TextView mIcon;
            TextView mMinTempItem;
            TextView mMaxTempItem;

            public Weather(View itemView) {
                super(itemView);
                mDay = (TextView)itemView.findViewById(R.id.day);
                mIcon = (TextView)itemView.findViewById(R.id.image_icon);
                mIcon.setTypeface(weatherFont);
                mMinTempItem = (TextView)itemView.findViewById(R.id.min_temp_recycler);
                mMaxTempItem = (TextView)itemView.findViewById(R.id.max_temp_recycler);
            }
        }
    }

}
