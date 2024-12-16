package com.safetyhat.macc

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.math.min

class SiteInfoActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_site_info)

            drawerLayout = findViewById(R.id.drawer_layout)
            navigationView = findViewById(R.id.navigation_view_worker)
            navigationView.itemIconTintList = null

            findViewById<ImageView>(R.id.menu_icon_worker).setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

            val workerCF = intent.getStringExtra("workerCF") ?: ""
            val siteID = intent.getStringExtra("siteID") ?: ""

            navigationView.setNavigationItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_home_worker -> {
                        val intent = Intent(this, WorkermenuActivity::class.java)
                        intent.putExtra("workerCF", workerCF)
                        intent.putExtra("siteID", siteID)
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_account_info_worker -> {
                        val intent = Intent(this, WorkerinfoActivity::class.java)
                        intent.putExtra("workerCF", workerCF)
                        intent.putExtra("siteID", siteID)
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_alert_worker -> {
                        val intent = Intent(this, AlertActivity::class.java)
                        intent.putExtra("workerCF", workerCF)
                        intent.putExtra("siteID", siteID)
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_ar_measuring_tool_worker -> {
                        val intent = Intent(this, ArMeasureActivity::class.java)
                        intent.putExtra("workerCF", workerCF)
                        intent.putExtra("siteID", siteID)
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_logout_worker -> {
                        val stopServiceIntent = Intent(this, AlertService::class.java)
                        stopService(stopServiceIntent)

                        // Cancel all notifications using NotificationManager
                        val notificationManager = getSystemService(NotificationManager::class.java)
                        notificationManager?.cancelAll()

                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
                drawerLayout.closeDrawer(GravityCompat.START)
                true
            }

            fetchWorkerInfo(workerCF)
        } catch (e: Exception) {
            Log.e("SiteInfoActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred during initialization.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fetchWorkerInfo(cf: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/read?cf=$cf"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SiteInfoActivity", "Network error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@SiteInfoActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    val jsonObject = JSONObject(responseData ?: "")

                    runOnUiThread {
                        if (response.isSuccessful && !jsonObject.has("message")) {
                            val siteCode = jsonObject.optString("SiteCode", "N/A")
                            if (siteCode != "N/A") {
                                fetchSiteInfo(siteCode.toInt())
                            } else {
                                Toast.makeText(this@SiteInfoActivity, "No site code assigned to worker.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@SiteInfoActivity, "Worker not found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("SiteInfoActivity", "JSON parsing error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@SiteInfoActivity, "Error processing data.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun fetchSiteInfo(ID: Int) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/site/read?id=$ID"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SiteInfoActivity", "Network error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@SiteInfoActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    val jsonObject = JSONObject(responseData ?: "")

                    val locationKey = jsonObject.optString("LocationKey", "")

                    runOnUiThread {
                        if (response.isSuccessful && !jsonObject.has("message")) {
                            findViewById<TextView>(R.id.site_id_text).text = jsonObject.optString("id", "N/A")
                            findViewById<TextView>(R.id.start_date_text).text = jsonObject.optString("StartDate", "N/A")
                            findViewById<TextView>(R.id.end_date_text).text = jsonObject.optString("EstimatedEndDate", "N/A")
                            findViewById<TextView>(R.id.total_worker_text).text = jsonObject.optString("TotalWorkers", "N/A")
                            findViewById<TextView>(R.id.scaffolding_number_text).text = jsonObject.optString("ScaffoldingCount", "N/A")
                            findViewById<TextView>(R.id.site_address_text).text = jsonObject.optString("Address", "N/A")
                            findViewById<TextView>(R.id.site_radius_text).text = jsonObject.optString("SiteRadius", "N/A")
                            findViewById<TextView>(R.id.site_manager_info_text).text = jsonObject.optString("ManagerCF", "N/A")

                            if (locationKey.isNotEmpty()) {
                                fetchWeatherByCityKey(locationKey)
                            } else {
                                Toast.makeText(this@SiteInfoActivity, "No location key available for weather data.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@SiteInfoActivity, "Site not found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("SiteInfoActivity", "JSON parsing error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@SiteInfoActivity, "Error processing data.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun fetchWeatherByCityKey(cityKey: String) {
        val apiKey = getString(R.string.accuweather_api_key)
        val forecastUrl = "https://dataservice.accuweather.com/forecasts/v1/hourly/12hour/$cityKey"

        val forecastParams = forecastUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("apikey", apiKey)
            ?.addQueryParameter("language", "en-us")
            ?.addQueryParameter("details", "false")
            ?.addQueryParameter("metric", "true")
            ?.build()

        if (forecastParams == null) {
            Log.e("SiteInfoActivity", "Invalid forecast URL")
            runOnUiThread {
                Toast.makeText(this, "Failed to retrieve weather information.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val forecastRequest = Request.Builder().url(forecastParams.toString()).get().build()

        client.newCall(forecastRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SiteInfoActivity", "Weather API error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@SiteInfoActivity, "Failed to retrieve weather information.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val forecastData = response.body?.string()
                    val forecastArray = JSONArray(forecastData ?: "")
                    val weatherForecasts = mutableListOf<WeatherForecast>()

                    for (i in 0 until min(8, forecastArray.length())) {
                        val forecast = forecastArray.getJSONObject(i)
                        val temperature = forecast.getJSONObject("Temperature").getDouble("Value").toString()
                        val dateTime = forecast.getString("DateTime")
                        val hour = if (dateTime.length >= 16) dateTime.substring(11, 16) else "N/A"
                        val weatherIcon = forecast.getInt("WeatherIcon")
                        weatherForecasts.add(WeatherForecast(hour, "$temperature°C", weatherIcon))
                    }

                    runOnUiThread {
                        val recyclerView = findViewById<RecyclerView>(R.id.weather_recycler_view)
                        recyclerView.layoutManager = LinearLayoutManager(this@SiteInfoActivity)
                        recyclerView.adapter = WeatherAdapter(weatherForecasts)
                    }
                } catch (e: JSONException) {
                    Log.e("SiteInfoActivity", "JSON parsing error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@SiteInfoActivity, "Error processing weather data.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    data class WeatherForecast(val hour: String, val temperature: String, val weatherIcon: Int)

    class WeatherAdapter(private val forecasts: List<WeatherForecast>) :
        RecyclerView.Adapter<WeatherAdapter.WeatherViewHolder>() {

        inner class WeatherViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val hourText: TextView = view.findViewById(R.id.hour_text)
            val temperatureText: TextView = view.findViewById(R.id.temperature_text)
            val weatherIcon: ImageView = view.findViewById(R.id.weather_icon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeatherViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.weather_item, parent, false)
            return WeatherViewHolder(view)
        }

        override fun onBindViewHolder(holder: WeatherViewHolder, position: Int) {
            val forecast = forecasts[position]
            holder.hourText.text = forecast.hour
            holder.temperatureText.text = forecast.temperature

            // Set the weather icon based on WeatherIcon ID
            val iconResourceId = getWeatherIconResource(forecast.weatherIcon)
            holder.weatherIcon.setImageResource(iconResourceId)
        }

        override fun getItemCount() = forecasts.size

        private fun getWeatherIconResource(weatherIcon: Int): Int {
            return when (weatherIcon) {
                1 -> R.mipmap.sunny_foreground
                2 -> R.mipmap.mostly_sunny_foreground
                3 -> R.mipmap.partly_sunny_foreground
                4 -> R.mipmap.intermittent_clouds_foreground
                5 -> R.mipmap.hazy_sunshine_foreground
                6 -> R.mipmap.mostly_cloudy_foreground
                7 -> R.mipmap.cloudy_foreground
                8 -> R.mipmap.dreary_overcast_foreground
                11 -> R.mipmap.fog_foreground
                12 -> R.mipmap.showers_foreground
                13 -> R.mipmap.mostly_cloudy_with_showers_foreground
                14 -> R.mipmap.partly_sunny_with_showers_foreground
                15 -> R.mipmap.t_storms_foreground
                16 -> R.mipmap.mostly_cloudy_with_t_storms_foreground
                17 -> R.mipmap.partly_sunny_with_t_storms_foreground
                18 -> R.mipmap.rain_foreground
                19 -> R.mipmap.flurries_foreground
                20 -> R.mipmap.mostly_cloudy_with_furries_foreground
                21 -> R.mipmap.partly_sunny_with_flurries_foreground
                22 -> R.mipmap.snow_foreground
                23 -> R.mipmap.mostly_cloudy_with_snow_foreground
                24 -> R.mipmap.ice_foreground
                25 -> R.mipmap.sleet_foreground
                26 -> R.mipmap.freezing_rain_foreground
                29 -> R.mipmap.rain_and_snow_foreground
                30 -> R.mipmap.hot_foreground
                31 -> R.mipmap.cold_foreground
                32 -> R.mipmap.windy_foreground
                33 -> R.mipmap.clear_night_foreground
                34 -> R.mipmap.mostly_clear_night_foreground
                35 -> R.mipmap.partly_cloudy_night_foreground
                36 -> R.mipmap.intermittent_clouds_night_foreground
                37 -> R.mipmap.hazy_moonlight_night_foreground
                38 -> R.mipmap.mostly_cloudy_night_foreground
                39 -> R.mipmap.partly_cloudy_with_showers_night_foreground
                40 -> R.mipmap.mostly_cloudy_with_showers_night_foreground
                41 -> R.mipmap.partly_cloudy_with_t_storms_night_foreground
                42 -> R.mipmap.mostly_cloudy_with_t_storms_night_foreground
                43 -> R.mipmap.mostly_cloudy_with_flurries_night_foreground
                44 -> R.mipmap.mostly_cloudy_with_snow_night_foreground
                else -> R.drawable.ic_launcher_background
            }
        }
    }
}
