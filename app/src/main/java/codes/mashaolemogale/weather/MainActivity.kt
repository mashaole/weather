package codes.mashaolemogale.weather;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

class MainActivity:AppCompatActivity(), LocationListener {
    internal lateinit var preferences:SharedPreferences
    internal var currentTheme:Int = 0
    internal var currentSky:Int = 0
    private val panelToday:RelativeLayout
    private val panelForecast:LinearLayout
    private val txtCurrentTemp:TextView
    private val txtCurrentSky:TextView
    internal var currentWeather:JSONObject
    internal var locationManager:LocationManager
    internal var provider:String
    protected fun onCreate(savedInstanceState:Bundle) {
        super.onCreate(savedInstanceState)
        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE)
        getSupportActionBar().hide()
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        currentTheme = preferences.getInt(THEME, THEME_SEA)
        setContentView(R.layout.activity_main)
        panelToday = findViewById(R.id.panel_today) as RelativeLayout
        panelForecast = findViewById(R.id.panel_forecast) as LinearLayout
        txtCurrentTemp = findViewById(R.id.text_current_temp) as TextView
        txtCurrentSky = findViewById(R.id.text_current_sky) as TextView
        try
        {
            currentWeather = JSONObject(preferences.getString(PREFERENCE_CURRENT_WEATHER, ""))
            updateCurrentWeather()
        }
        catch (e:JSONException) {
            e.printStackTrace()
        }
    }
    @SuppressLint("MissingPermission")
    fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !== PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,
                    arrayOf<String>(Manifest.permission.ACCESS_COARSE_LOCATION),
                    MY_PERMISSIONS_REQUEST_ACESS_COARSE_LOCATION)
        }
        else
        {
            getCurrentWeather()
        }
    }
    protected fun onPause() {
        super.onPause()
        locationManager.removeUpdates(this)
    }
    @SuppressLint("MissingPermission")
    private fun getCurrentWeather() {
        val criteria = Critseria()
        provider = locationManager.getBestProvider(criteria, false)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 200, this)
        val location = locationManager.getLastKnownLocation(provider)
        if (location != null)
        {
            onLocationChanged(location)
        }
        else
        {
            Toast.makeText(this, "Location is unavailable", Toast.LENGTH_SHORT).show()
        }
    }
    private inner class CurrentWeatherTask:AsyncTask<Double, Void, JSONObject>() {
        protected fun doInBackground(vararg doubles:Double):JSONObject {
            try
            {
                //https://api.openweathermap.org/data/2.5/weather
                val builder = Uri.Builder()
                builder.scheme(API_URL_SCHEME).authority(API_URL_AUTHORITY)
                for (path in API_URL_PATH)
                {
                    builder.appendPath(path)
                }
                builder.appendQueryParameter(API_URL_KEY_APPID, API_APPID)
                        .appendQueryParameter(API_URL_KEY_LAT, doubles[0] + "")
                        .appendQueryParameter(API_URL_KEY_LON, doubles[1] + "")
                val apiUrl = builder.build().toString()
                val url = URL(apiUrl)
                val httpURLConnection = url.openConnection() as HttpURLConnection
                val stream = BufferedInputStream(httpURLConnection.getInputStream())
                val bufferedReader = BufferedReader(InputStreamReader(stream))
                val stringBuilder = StringBuilder()
                val inputString:String
                while ((inputString = bufferedReader.readLine()) != null)
                {
                    stringBuilder.append(inputString)
                }
                val topLevel = JSONObject(stringBuilder.toString())
                Log.d(TAG, "Weather api returned: " + topLevel.toString())
                return topLevel
            }
            catch (e:MalformedURLException) {
                e.printStackTrace()
            }
            catch (e:IOException) {
                e.printStackTrace()
            }
            catch (e:JSONException) {
                e.printStackTrace()
            }
            return null
        }
        protected fun onPostExecute(jsonObject:JSONObject) {
            super.onPostExecute(jsonObject)
            if (jsonObject != null)
            {
                currentWeather = jsonObject
                preferences.edit().putString(PREFERENCE_CURRENT_WEATHER, jsonObject.toString()).apply()
                updateCurrentWeather()
            }
        }
    }
    private fun updateCurrentWeather() {
        try
        {
            val main = currentWeather.getJSONObject("main")
            val temp = kelvinToCelcius(main.getDouble("temp"))
            val intTemp = Math.round(temp).toInt()
            txtCurrentTemp.setText(intTemp + "º")
            val weather = currentWeather.getJSONArray("weather")
            val objWeather = weather.getJSONObject(0)
            val weatherCode = objWeather.getInt("id")
            val sky = getSky(weatherCode)
            txtCurrentSky.setText(getSkyName(sky))
            currentSky = sky
            updateBackground()
        }
        catch (e:JSONException) {
            e.printStackTrace()
        }
    }
    private fun getSkyName(sky:Int):String {
        if (sky == SKY_CLOUDY)
        {
            return "CLOUDY"
        }
        else if (sky == SKY_RAINY)
        {
            return "RAINY"
        }
        else
        {
            return "SUNNY"
        }
    }
    fun onRequestPermissionsResult(requestCode:Int,
                                   permissions:Array<String>, grantResults:IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_ACESS_COARSE_LOCATION -> {
                if ((grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED))
                {
                    getCurrentWeather()
                }
                else
                {
                    val alertBuilder = AlertDialog.Builder(this)
                    alertBuilder.setCancelable(true)
                    alertBuilder.setMessage("The app needs to access your coarse location to check which city you are in.\n\nPlease allow access now to use the app.")
                    alertBuilder.setPositiveButton(android.R.string.yes, object:DialogInterface.OnClickListener() {
                        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
                        fun onClick(dialog:DialogInterface, which:Int) {
                            ActivityCompat.requestPermissions(this@MainActivity,
                                    arrayOf<String>(Manifest.permission.ACCESS_COARSE_LOCATION),
                                    MY_PERMISSIONS_REQUEST_ACESS_COARSE_LOCATION)
                        }
                    })
                    alertBuilder.show()
                }
                return
            }
        }
    }
    private fun updateBackground() {
        val statusColor:Int
        val backgroundDrawable:Int
        val forecastBackgroundColor:Int
        when (currentTheme) {
            THEME_FOREST ->
                when (currentSky) {
                    SKY_CLOUDY -> {
                        statusColor = R.color.forest_cloudy_status_blue
                        backgroundDrawable = R.drawable.forest_cloudy
                        forecastBackgroundColor = R.color.forest_cloudy_blue
                    }
                    SKY_RAINY -> {
                        statusColor = R.color.forest_rainy_status_grey
                        backgroundDrawable = R.drawable.forest_rainy
                        forecastBackgroundColor = R.color.forest_rainy_gray
                    }
                    else -> {
                        statusColor = R.color.forest_sunny_status_orange
                        backgroundDrawable = R.drawable.forest_sunny
                        forecastBackgroundColor = R.color.forest_sunny_green
                    }
                }
            else -> when (currentSky) {
                SKY_CLOUDY -> {
                    statusColor = R.color.sea_cloudy_status_blue
                    backgroundDrawable = R.drawable.sea_cloudy
                    forecastBackgroundColor = R.color.sea_cloudy_blue
                }
                SKY_RAINY -> {
                    statusColor = R.color.sea_rainy_status_grey
                    backgroundDrawable = R.drawable.sea_rainy
                    forecastBackgroundColor = R.color.sea_rainy_gray
                }
                else -> {
                    statusColor = R.color.sea_sunny_status_yellow
                    backgroundDrawable = R.drawable.sea_sunny
                    forecastBackgroundColor = R.color.sea_sunny_blue
                }
            }
        }
        panelToday.setBackground(ContextCompat.getDrawable(this, backgroundDrawable))
        panelForecast.setBackgroundColor(ContextCompat.getColor(this, forecastBackgroundColor))
        getWindow().setStatusBarColor(ContextCompat.getColor(this, statusColor))
    }
    fun onLocationChanged(location:Location) {
        val lat = location.getLatitude()
        val lng = location.getLongitude()
        val latlng = arrayOf<Double>(lat, lng)
        val currentWeatherTask = CurrentWeatherTask()
        currentWeatherTask.execute(latlng)
    }
    fun onStatusChanged(provider:String, status:Int, extras:Bundle) {
    }
    fun onProviderEnabled(provider:String) {
    }
    fun onProviderDisabled(provider:String) {
    }
    fun kelvinToCelcius(kelvin:Double):Double {
        return kelvin - 273.15
    }
    fun getSky(weatherCode:Int):Int {
        for (i in WEATHER_COND_RAINY)
        {
            if (i == weatherCode)
            {
                return SKY_RAINY
            }
        }
        for (i in WEATHER_COND_CLOUDY)
        {
            if (i == weatherCode)
            {
                return SKY_CLOUDY
            }
        }
        return SKY_SUNNY
    }
    companion object {
        private val TAG = MainActivity::class.java!!.getSimpleName()
        private val THEME = "THEME"
        private val PREFERENCE_CURRENT_WEATHER = "CURRENT_WEATHER"
        private val THEME_FOREST = 0
        private val THEME_SEA = 1
        private val SKY_CLOUDY = 0
        private val SKY_SUNNY = 1
        private val SKY_RAINY = 2
        private val MY_PERMISSIONS_REQUEST_ACESS_COARSE_LOCATION = 252
        private val API_URL_SCHEME = "https"
        private val API_URL_AUTHORITY = "api.openweathermap.org"
        private val API_URL_PATH = arrayOf<String>("data", "2.5", "weather")
        private val API_URL_KEY_LAT = "lat"
        private val API_URL_KEY_LON = "lon"
        private val API_APPID = "d58f73abc992aa3eec2f39ebda4a61e2"
        private val API_URL_KEY_APPID = "APPID"
        private val WEATHER_COND_CLEAR_SKY = 800
        private val WEATHER_COND_FEW_CLOUDS = 801
        private val WEATHER_COND_SCATTERED_CLOUDS = 802
        private val WEATHER_COND_BROKEN_CLOUDS = 803
        private val WEATHER_COND_OVERCAST_CLOUDS = 804
        private val WEATHER_COND_LIGHT_RAIN = 500
        private val WEATHER_COND_MODERATE_RAIN = 501
        private val WEATHER_COND_HEAVY_INTENSITY_RAIN = 502
        private val WEATHER_COND_VERY_HEAVY_RAIN = 503
        private val WEATHER_COND_EXTREME_RAIN = 504
        private val WEATHER_COND_FREEZING_RAIN = 511
        private val WEATHER_COND_LIGHT_INTENSITY_SHOWER_RAIN = 520
        private val WEATHER_COND_SHOWER_RAIN = 521
        private val WEATHER_COND_HEAVY_INTENSITY_SHOWER_RAIN = 522
        private val WEATHER_COND_RAGGED_SHOWER_RAIN = 531
        private val WEATHER_COND_RAINY = intArrayOf(WEATHER_COND_LIGHT_RAIN, WEATHER_COND_MODERATE_RAIN, WEATHER_COND_HEAVY_INTENSITY_RAIN, WEATHER_COND_VERY_HEAVY_RAIN, WEATHER_COND_EXTREME_RAIN, WEATHER_COND_FREEZING_RAIN, WEATHER_COND_LIGHT_INTENSITY_SHOWER_RAIN, WEATHER_COND_SHOWER_RAIN, WEATHER_COND_HEAVY_INTENSITY_SHOWER_RAIN, WEATHER_COND_RAGGED_SHOWER_RAIN)
        private val WEATHER_COND_CLOUDY = intArrayOf(WEATHER_COND_FEW_CLOUDS, WEATHER_COND_SCATTERED_CLOUDS, WEATHER_COND_BROKEN_CLOUDS, WEATHER_COND_OVERCAST_CLOUDS)
    }
}