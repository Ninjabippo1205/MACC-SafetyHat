package com.safetyhat.macc

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.navigation.NavigationView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class AlertActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var mediaRecorder: MediaRecorder
    private val safetyThreshold = 85
    private var audioRecord: AudioRecord? = null
    private lateinit var alertAdapter: AlertAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var lastAlertTime: Long = 0
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private val client = OkHttpClient()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val bufferSize = AudioRecord.getMinBufferSize(
        44100,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    companion object {
        const val RECORD_AUDIO_PERMISSION_CODE = 100
        const val ALERT_DURATION = 60000L
        const val FALL_ALERT_DURATION = 60000L
    }

    private val fallAccelerationThreshold = 2.0
    private val impactThreshold = 12.0
    private val rotationThreshold = 4.0
    private var isFalling = false
    private var orientationChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert)

        drawerLayout = findViewById(R.id.drawer_layout)
        val workerCF = intent.getStringExtra("workerCF")
        val siteID = intent.getStringExtra("siteID")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        navigationView = findViewById(R.id.navigation_view_worker)
        navigationView.itemIconTintList = null

        findViewById<ImageView>(R.id.worker_menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

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
                R.id.nav_site_info_worker -> {
                    val intent = Intent(this, SiteInfoActivity::class.java)
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

        val recyclerView = findViewById<RecyclerView>(R.id.alert_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        alertAdapter = AlertAdapter(mutableListOf())
        recyclerView.adapter = alertAdapter

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
        } else {
            startMonitoring()
        }

        fetchSiteInfo(siteID.toString().toInt())

    }

    private fun getTargetLocation(address: String, radius: Double) {
        // Converti l'indirizzo in LatLng usando Google Geocoding API
        val myKey = getString(R.string.google_maps_key)
        Thread {
            try {
                val response = client.newCall(
                    Request.Builder()
                        .url("https://maps.googleapis.com/maps/api/geocode/json?address=${address.replace(" ", "+")}&key=$myKey")
                        .build()
                ).execute()

                val json = JSONObject(response.body?.string())
                val location = json.getJSONArray("results")
                    .getJSONObject(0)
                    .getJSONObject("geometry")
                    .getJSONObject("location")

                runOnUiThread {
                    Toast.makeText(this@AlertActivity, "2222222222", Toast.LENGTH_SHORT).show()
                }

                val targetLat = location.getDouble("lat")
                val targetLng = location.getDouble("lng")

                // Controlla la posizione dell'utente in tempo reale
                monitorLocation(LatLng(targetLat, targetLng), radius)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun monitorLocation(targetLatLng: LatLng, radius: Double) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    val distance = calculateDistance(currentLatLng, targetLatLng)

                    if (distance <= radius) {
                        val alertMessage = "Wear Personal Protective Equipment"
                        alertAdapter.addAlert(alertMessage, R.mipmap.safety_vest_foreground)
                    }
                }
            }
        }catch (e: SecurityException) {
            Toast.makeText(this, "Unable to access the gps. Permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateDistance(loc1: LatLng, loc2: LatLng): Double {
        val earthRadius = 6371000.0 // metri
        val dLat = Math.toRadians(loc2.latitude - loc1.latitude)
        val dLng = Math.toRadians(loc2.longitude - loc1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(loc1.latitude)) * cos(Math.toRadians(loc2.latitude)) *
                sin(dLng / 2) * sin(dLng / 2)

        return 2 * earthRadius * acos(sin(Math.toRadians(loc1.latitude)) * sin(Math.toRadians(loc2.latitude)) + cos(Math.toRadians(loc1.latitude)) * cos(Math.toRadians(loc2.latitude)) * cos(dLng))
    }

    private fun fetchSiteInfo(ID: Int) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/site/read?id=$ID"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@AlertActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData ?: "")

                runOnUiThread {
                    if (response.isSuccessful && !jsonObject.has("message")) {
                        val address = jsonObject.optString("Address", "N/A")
                        val radius = jsonObject.optString("SiteRadius", "N/A").toString().toDouble()

                        if (ActivityCompat.checkSelfPermission(this@AlertActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this@AlertActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                        } else {
                            getTargetLocation(address, radius)
                        }

                    } else {
                        Toast.makeText(this@AlertActivity, "Site not found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Metodo obbligatorio, ma lasciato vuoto se non è necessario
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event)
            Sensor.TYPE_GYROSCOPE -> handleGyroscope(event)
        }
    }

    private fun handleAccelerometer(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val totalAcceleration = sqrt((x * x + y * y + z * z).toDouble())

        // Rileva inizio della caduta libera
        if (totalAcceleration < fallAccelerationThreshold) isFalling = true

        // Rileva impatto dopo la caduta
        if (isFalling && totalAcceleration > impactThreshold) {
            if (orientationChanged) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAlertTime >= FALL_ALERT_DURATION) {
                    lastAlertTime = currentTime
                    val alertMessage = "Fall detected"
                    alertAdapter.addAlert(alertMessage, R.mipmap.falling_foreground)
                    handler.postDelayed({
                        alertAdapter.removeAlert()
                        lastAlertTime = 0
                    }, FALL_ALERT_DURATION)
                }
            }
            isFalling = false
            orientationChanged = false
        }
    }

    private fun handleGyroscope(event: SensorEvent) {
        val rotationMagnitude = sqrt((event.values[0] * event.values[0] +
                event.values[1] * event.values[1] +
                event.values[2] * event.values[2]).toDouble())
        if (rotationMagnitude > rotationThreshold) orientationChanged = true
    }

    private fun startMonitoring() {
        try {
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            audioRecord?.startRecording()

            Thread {
                val buffer = ShortArray(bufferSize)
                while (true) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        val amplitude = buffer.maxOrNull()?.toInt() ?: 0
                        val decibelLevel = -20 * log10(amplitude.toDouble() / 32767.0)
                        val currentTime = System.currentTimeMillis()

                        // Crea un alert solo se non esiste già un alert attivo
                        if (decibelLevel > safetyThreshold && decibelLevel.isFinite() && currentTime - lastAlertTime >= ALERT_DURATION) {
                            lastAlertTime = currentTime
                            runOnUiThread {
                                val alertMessage = "High noise level detected: ${decibelLevel.toInt()} dB"
                                alertAdapter.addAlert(alertMessage, R.mipmap.headphones_foreground)

                                // Rimuovi l'alert dopo 1 minuto
                                handler.postDelayed({
                                    alertAdapter.removeAlert()
                                    lastAlertTime = 0  // Reset dell'ultimo alert
                                }, ALERT_DURATION)
                            }
                        }
                    }
                    Thread.sleep(60000)  // intervallo di controllo
                }
            }.start()

        } catch (e: SecurityException) {
            Toast.makeText(this, "Unable to access the microphone. Permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMonitoring()
            } else {
                Toast.makeText(this, "Microphone permission is required to monitor noise levels.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecord?.stop()
        audioRecord?.release()
    }
}

class AlertAdapter(private val alertList: MutableList<Pair<String, Int>>) : RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

    class AlertViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val alertTextView: TextView = view.findViewById(R.id.view_count_text_view)
        val alertIcon: ImageView = view.findViewById(R.id.thumbs_up_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.alert_item, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        val (message, imageResId) = alertList[position]
        holder.alertTextView.text = message
        holder.alertIcon.setImageResource(imageResId)
    }

    override fun getItemCount(): Int {
        return alertList.size
    }

    fun addAlert(alertMessage: String, @DrawableRes imageResId: Int) {
        alertList.add(Pair(alertMessage, imageResId))
        notifyItemInserted(alertList.size - 1)
    }

    fun removeAlert() {
        if (alertList.isNotEmpty()) {
            alertList.removeAt(0)
            notifyItemRemoved(0)
        }
    }
}
