package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class SiteInfoActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_site_info)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view_worker)
        navigationView.itemIconTintList = null

        findViewById<ImageView>(R.id.menu_icon_worker).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val workerCF = intent.getStringExtra("workerCF")
        val siteID = intent.getStringExtra("siteID")

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home_worker -> {
                    val intent = Intent(this, WorkermenuActivity::class.java)
                    intent.putExtra("workerCF", workerCF)
                    intent.putExtra("siteID", siteID.toString())
                    startActivity(intent)
                    finish()
                }
                R.id.nav_account_info_worker -> {
                    val intent = Intent(this, WorkerinfoActivity::class.java)
                    intent.putExtra("workerCF", workerCF)
                    intent.putExtra("siteID", siteID.toString())
                    startActivity(intent)
                    finish()
                }
                R.id.nav_alert_worker -> {
                    val intent = Intent(this, AlertActivity::class.java)
                    intent.putExtra("workerCF", workerCF)
                    intent.putExtra("siteID", siteID.toString())
                    startActivity(intent)
                    finish()
                }
                R.id.nav_logout_worker -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        fetchWorkerInfo(workerCF.toString())
    }

    private fun fetchWorkerInfo(cf: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/read?cf=$cf"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SiteInfoActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData ?: "")

                runOnUiThread {
                    if (response.isSuccessful && !jsonObject.has("message")) {
                        val SiteCode = jsonObject.optString("SiteCode", "N/A")
                        fetchSiteInfo(SiteCode.toInt())
                    } else {
                        Toast.makeText(this@SiteInfoActivity, "Worker not found.", Toast.LENGTH_SHORT).show()
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
                runOnUiThread {
                    Toast.makeText(this@SiteInfoActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData ?: "")

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

                        val address = jsonObject.optString("Address", "N/A")
                        fetchCoordinatesAndWeather(address)
                    } else {
                        Toast.makeText(this@SiteInfoActivity, "Site not found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    data class WeatherForecast(val hour: String, val temperature: String, val weatherIcon: Int)

    class WeatherAdapter(private val forecasts: List<WeatherForecast>) : RecyclerView.Adapter<WeatherAdapter.WeatherViewHolder>() {

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

            // Imposta l'icona del meteo basata sull'ID WeatherIcon
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

    private fun fetchCoordinatesAndWeather(address: String) {
        val googleApiKey = getString(R.string.google_maps_key)
        val geocodeUrl = "https://maps.googleapis.com/maps/api/geocode/json"

        val geocodeParams = geocodeUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("address", address)
            ?.addQueryParameter("key", googleApiKey)
            ?.build()

        val geocodeRequest = Request.Builder().url(geocodeParams.toString()).get().build()

        client.newCall(geocodeRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SiteInfoActivity, "Failed to retrieve coordinates.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val geocodeData = JSONObject(responseData ?: "")
                val location = geocodeData.getJSONArray("results")
                    .getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONObject("location")

                val lat = location.getDouble("lat")
                val lng = location.getDouble("lng")

                fetchWeatherInfoByCoordinates(lat, lng)
            }
        })
    }

    private fun fetchWeatherInfoByCoordinates(lat: Double, lng: Double) {
        val apiKey = getString(R.string.accuweather_api_key)
        val locationUrl = "https://dataservice.accuweather.com/locations/v1/cities/geoposition/search"

        val locationParams = locationUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", "$lat,$lng")
            ?.addQueryParameter("apikey", apiKey)
            ?.addQueryParameter("language", "en-us")
            ?.build()

        val locationRequest = Request.Builder().url(locationParams.toString()).get().build()

        client.newCall(locationRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SiteInfoActivity, "Failed to retrieve location key.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val locationData = JSONObject(responseData ?: "")
                if (locationData.has("Key")) {
                    val cityKey = locationData.getString("Key")
                    fetchWeatherByCityKey(cityKey)
                } else {
                    runOnUiThread {
                        Toast.makeText(this@SiteInfoActivity, "Location key not found in response.", Toast.LENGTH_SHORT).show()
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

        val forecastRequest = Request.Builder().url(forecastParams.toString()).get().build()

        client.newCall(forecastRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SiteInfoActivity, "Failed to retrieve weather information.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val forecastData = response.body?.string()
                val forecastArray = JSONArray(forecastData ?: "")
                val weatherForecasts = mutableListOf<WeatherForecast>()

                for (i in 0 until minOf(8, forecastArray.length())) {
                    val forecast = forecastArray.getJSONObject(i)
                    val temperature = forecast.getJSONObject("Temperature").getDouble("Value").toString()
                    val hour = forecast.getString("DateTime").substring(11, 16)
                    val weatherIcon = forecast.getInt("WeatherIcon")
                    weatherForecasts.add(WeatherForecast(hour, "$temperature°C", weatherIcon))
                }

                runOnUiThread {
                    val recyclerView = findViewById<RecyclerView>(R.id.weather_recycler_view)
                    recyclerView.layoutManager = LinearLayoutManager(this@SiteInfoActivity)
                    recyclerView.adapter = WeatherAdapter(weatherForecasts)
                }
            }
        })
    }
}
