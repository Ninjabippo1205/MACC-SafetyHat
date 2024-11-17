package com.safetyhat.macc

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.math.*

class AlertService : Service(), SensorEventListener {

    companion object {
        @Volatile
        private var isRunning: Boolean = false
    }

    // Sensor Management
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Audio Monitoring
    private var audioRecord: AudioRecord? = null
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    // Handlers and Runnables
    private val handler = Handler(Looper.getMainLooper())
    private val weatherUpdateHandler = Handler(Looper.getMainLooper())

    // Networking
    private val client = OkHttpClient()

    // Location Management
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient

    // Alert Management
    private var lastAlertTime: Long = 0
    private val safetyThreshold = 85
    private val ALERT_DURATION = 60000L
    private val FALL_ALERT_DURATION = 60000L

    // Fall Detection Thresholds
    private val fallAccelerationThreshold = 2.0
    private val impactThreshold = 12.0
    private val rotationThreshold = 4.0
    private var isFalling = false
    private var orientationChanged = false

    // Weather Monitoring
    private var lastLocationKey: String = ""
    private val weatherUpdateRunnable = object : Runnable {
        override fun run() {
            if (lastLocationKey.isNotEmpty()) {
                fetchWeatherByCityKey(lastLocationKey)
            }
            weatherUpdateHandler.postDelayed(this, 3600000L) // 1 ora = 3600000L in millisecondi
        }
    }

    // Geoposition Monitoring
    private var siteLatitude: Double? = null
    private var siteLongitude: Double? = null
    private var siteRadius: Double? = null
    private var siteID: Int? = null
    private var workerCF: String? = null
    private val locationCheckInterval = 60000L
    private val locationCheckHandler = Handler(Looper.getMainLooper())

    // Coroutine Scope
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val alertChannelId = "test_channel_id"
    private val alertChannelName = "Test Channel"

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                alertChannelId,
                alertChannelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Test Channel for Debugging"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    override fun onCreate() {
        super.onCreate()

        if (!hasNecessaryPermissions()) {
            Toast.makeText(this, "Missing permissions. Waiting for permission to be granted.", Toast.LENGTH_LONG).show()
            startForegroundServiceWithWaitingNotification()
            monitorPermissions()
            return
        }

        createNotificationChannel()
        startForegroundService()
        initializeSensors()
        initializeAudioMonitoring()
        initializeLocationClient()
    }

    private fun fetchSiteInfo(ID: Int) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/site/read?id=$ID"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    Toast.makeText(this@AlertService, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                if (response.isSuccessful && responseData != null) {
                    try {
                        val jsonObject = JSONObject(responseData)
                        val locationKey = jsonObject.optString("LocationKey")
                        val siteLat = jsonObject.optString("Latitude").toDouble()
                        val siteLng = jsonObject.optString("Longitude").toDouble()
                        val siteRad = jsonObject.optDouble("SiteRadius")

                        if (locationKey.isNotEmpty()) {
                            lastLocationKey = locationKey
                            fetchWeatherByCityKey(locationKey)
                        } else {
                            Log.d("AlertService", "LocationKey not found for site ID: $ID")
                        }

                        siteLatitude = siteLat
                        siteLongitude = siteLng
                        siteRadius = siteRad
                        siteID = ID

                        startLocationMonitoring()

                    } catch (e: JSONException) {
                        Log.d("AlertService", "JSON Parsing error: ${e.message}")
                    }
                } else {
                    Log.d("AlertService", "Failed to retrieve site info: ${response.message}")
                }
            }
        })
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newSiteID = intent?.getStringExtra("siteID")
        val newWorkerCF = intent?.getStringExtra("workerCF")

        // Verifica se il servizio è già in esecuzione con parametri diversi
        if (isRunning) {
            if (newSiteID != siteID?.toString() || newWorkerCF != workerCF) {
                Log.d("AlertService", "Parameters changed. Restarting service with new parameters.")

                // Stop the current instance and restart with new parameters
                stopSelf()
                Intent(this, AlertService::class.java).also {
                    it.putExtra("siteID", newSiteID)
                    it.putExtra("workerCF", newWorkerCF)
                    ContextCompat.startForegroundService(this, it)
                }
                return START_STICKY
            } else {
                Log.d("AlertService", "Service is already running with the same parameters.")
                return START_STICKY
            }
        }

        // Imposta il flag per indicare che il servizio è in esecuzione
        isRunning = true

        // Memorizza i parametri per il confronto futuro
        siteID = newSiteID?.toIntOrNull()
        workerCF = newWorkerCF

        // Avvia il processo normale del servizio
        siteID?.let {
            fetchSiteInfo(it)
        } ?: Log.e("AlertService", "Invalid siteID received: $newSiteID")

        startWeatherUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        // Imposta il flag per indicare che il servizio non è più in esecuzione
        isRunning = false

        // Annulla la registrazione dei listener per i sensori
        sensorManager.unregisterListener(this)

        // Ferma la registrazione audio
        audioRecord?.stop()
        audioRecord?.release()

        // Rimuovi i callback dei gestori
        weatherUpdateHandler.removeCallbacksAndMessages(null)
        locationCheckHandler.removeCallbacksAndMessages(null)

        // Cancella tutte le coroutine
        serviceJob.cancel()

        // Ferma il servizio in modalità foreground
        stopForeground(Service.STOP_FOREGROUND_REMOVE)

        // Rimuovi esplicitamente tutte le notifiche
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()

        Log.d("AlertService", "Service destroyed and all notifications removed")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // ==========================
    // Initializing Sensors
    // ==========================
    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        accelerometer?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        gyroscope?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    // ==========================
    // Initializing Audio Monitoring
    // ==========================
    private fun initializeAudioMonitoring() {
        try {
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            audioRecord?.startRecording()

            serviceScope.launch {
                val buffer = ShortArray(bufferSize)
                while (isActive) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        val amplitude = buffer.maxOrNull()?.toInt() ?: 0
                        val decibelLevel = amplitudeToDecibel(amplitude)
                        val currentTime = System.currentTimeMillis()

                        if (decibelLevel > safetyThreshold && decibelLevel.isFinite() && currentTime - lastAlertTime >= ALERT_DURATION) {
                            lastAlertTime = currentTime
                            sendAlert("High noise level detected: ${decibelLevel.toInt()} dB", R.mipmap.headphones_foreground)
                        }
                    }
                    delay(60000L)
                }
            }

        } catch (e: SecurityException) {
            handler.post {
                Toast.makeText(this, "Unable to access the microphone. Permission required.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun amplitudeToDecibel(amplitude: Int): Double {
        return if (amplitude > 0) {
            20 * log10(amplitude.toDouble() / 32767.0)
        } else {
            0.0
        }
    }

    // ==========================
    // Initializing Location Client
    // ==========================
    private fun initializeLocationClient() {
        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
    }

    // ==========================
    // Weather Monitoring
    // ==========================
    private fun startWeatherUpdates() {
        weatherUpdateHandler.post(weatherUpdateRunnable)
    }

    private fun fetchWeatherByCityKey(cityKey: String) {
        val apiKey = getString(R.string.accuweather_api_key)
        val forecastUrl = "https://dataservice.accuweather.com/forecasts/v1/hourly/12hour/$cityKey"
        val forecastParams = forecastUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("apikey", apiKey)
            ?.addQueryParameter("language", "en-us")
            ?.addQueryParameter("details", "true")
            ?.addQueryParameter("metric", "true")
            ?.build()

        val forecastRequest = Request.Builder().url(forecastParams.toString()).get().build()

        client.newCall(forecastRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    Toast.makeText(this@AlertService, "Network error. Unable to retrieve weather information.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val forecastData = response.body?.string()
                val forecastArray = JSONArray(forecastData ?: "")

                var maxRain = 0.0
                var maxSnow = 0.0
                var maxIce = 0.0
                var maxWindSpeed = 0.0
                var maxTemp = Double.MIN_VALUE
                var minTemp = Double.MAX_VALUE
                var maxUVIndex = 0

                for (i in 0 until forecastArray.length()) {
                    val forecast = forecastArray.getJSONObject(i)
                    val temp = forecast.getJSONObject("Temperature").getDouble("Value")
                    maxTemp = maxOf(maxTemp, temp)
                    minTemp = minOf(minTemp, temp)
                    maxRain = maxOf(maxRain, forecast.optJSONObject("Rain")?.optDouble("Value") ?: 0.0)
                    maxSnow = maxOf(maxSnow, forecast.optJSONObject("Snow")?.optDouble("Value") ?: 0.0)
                    maxIce = maxOf(maxIce, forecast.optJSONObject("Ice")?.optDouble("Value") ?: 0.0)
                    maxWindSpeed = maxOf(maxWindSpeed, forecast.getJSONObject("Wind").getJSONObject("Speed").getDouble("Value"))
                    maxUVIndex = maxOf(maxUVIndex, forecast.optInt("UVIndex", 0))
                }

                handler.post {
                    sendWeatherAlerts(
                        rain = maxRain,
                        snow = maxSnow,
                        ice = maxIce,
                        windSpeed = maxWindSpeed,
                        maxTemp = maxTemp,
                        minTemp = minTemp,
                        uvIndex = maxUVIndex
                    )
                }
            }
        })
    }

    private fun sendWeatherAlerts(
        rain: Double,
        snow: Double,
        ice: Double,
        windSpeed: Double,
        maxTemp: Double,
        minTemp: Double,
        uvIndex: Int
    ) {
        val alerts = mutableListOf<Pair<String, Int>>()

        if (rain >= 0.0) {
            alerts.add("High rain: $rain mm.\nBring an umbrella or raincoat." to R.mipmap.weather_foreground)
        }
        if (snow >= 2.0) {
            alerts.add("High snowfall: $snow mm.\nPrepare winter clothing." to R.mipmap.ice_alert_foreground)
        }
        if (ice > 2.0) {
            alerts.add("High ice: $ice mm.\nBe cautious of icy roads." to R.mipmap.ice_alert_foreground)
        }
        if (windSpeed > 10.0) {
            alerts.add("High wind speed: $windSpeed km/h.\nSecure exposed areas and be cautious." to R.mipmap.wind_alert_foreground)
        }
        if (maxTemp > 30.0) {
            alerts.add("High temperature: $maxTemp°C.\nBring plenty of water and use sun protection." to R.mipmap.hot_alert_foreground)
        }
        if (minTemp < 0.0) {
            alerts.add("Low temperature: $minTemp°C.\nDress warmly for cold weather." to R.mipmap.cold_alert_foreground)
        }

        alerts.forEach { (message, iconResId) ->
            sendAlert(message, iconResId)
        }
    }

    // ==========================
    // Sensors Management
    // ==========================
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

        if (totalAcceleration < fallAccelerationThreshold) isFalling = true

        if (isFalling && totalAcceleration > impactThreshold) {
            if (orientationChanged) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastAlertTime >= FALL_ALERT_DURATION) {
                    lastAlertTime = currentTime
                    sendAlert("Fall detected", R.mipmap.falling_foreground)
                }
            }
            isFalling = false
            orientationChanged = false
        }
    }

    private fun handleGyroscope(event: SensorEvent) {
        val rotationMagnitude = sqrt(
            (event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]).toDouble()
        )
        if (rotationMagnitude > rotationThreshold) orientationChanged = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //Mandatory method, but left empty if not needed
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d("AlertService", "App chiusa dal task manager. Servizio in arresto.")

        // Ferma il servizio e rimuovi la notifica
        stopSelf()
    }

    // ==========================
    // Adding Alerts to the shared queue
    // ==========================
    private fun sendAlert(message: String, iconResId: Int) {
        val duration = getTimerDuration(message) // Ottieni la durata del timer
        AlertMessageQueue.addMessage(message, iconResId, duration)
        sendAndroidNotification(message, iconResId)
    }

    private fun getTimerDuration(message: String): Long {
        val durationMap = mapOf(
            "High rain:" to 3580000L,
            "High snowfall:" to 3580000L,
            "High ice:" to 3580000L,
            "High wind speed:" to 3580000L,
            "High temperature:" to 3580000L,
            "Low temperature:" to 3580000L,
            "High UV index:" to 3580000L,
            "High noise level detected" to 3580000L,
            "Fall detected" to 119000L,
            "You are in site" to 59000L
        )
        return durationMap.entries.firstOrNull { message.startsWith(it.key) }?.value ?: 30000L
    }


    private fun sendAndroidNotification(message: String, iconResId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Creare l'intent per aprire AlertActivity con parametri aggiuntivi
        val intent = Intent(this, AlertActivity::class.java).apply {
            putExtra("workerCF", workerCF) // Passa il valore di workerCF
            putExtra("siteID", siteID?.toString()) // Passa il valore di siteID come Stringa
            putExtra("fromNotification", true)
        }

        // Crea il PendingIntent per la notifica
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Creare la notifica
        val notification = NotificationCompat.Builder(this, alertChannelId)
            .setContentTitle("Safety Alert")
            .setContentText(message)
            .setSmallIcon(iconResId) // Icona personalizzata per l'alert
            .setColor(ContextCompat.getColor(this, R.color.orange_safety_hat))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Notifica con alta priorità
            .setAutoCancel(true) // La notifica scompare al tocco
            .setContentIntent(pendingIntent) // Collega l'intent
            .build()

        // Mostrare la notifica
        val notificationId = System.currentTimeMillis().toInt() // ID unico per ogni notifica
        notificationManager.notify(notificationId, notification)
    }


    // ==========================
    // Configuring Foreground Service
    // ==========================
    private fun startForegroundService() {
        val channelId = "AlertServiceChannel"
        val channelName = "Alert Service Channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val stopIntent = Intent(this, AlertService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafetyHat")
            .setContentText("Monitoring safety alerts in background")
            .setSmallIcon(R.drawable.alert_icon_notification)
            .setColor(ContextCompat.getColor(this, R.color.orange_safety_hat))
            .build()

        startForeground(1, notification)
    }

    private fun startForegroundServiceWithWaitingNotification() {
        val channelId = "AlertServiceChannel"
        val channelName = "Alert Service Channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("SafetyHat - Waiting for Permissions")
            .setContentText("The service is waiting for the necessary permissions to be granted.")
            .setSmallIcon(R.drawable.alert_icon_notification)
            .setColor(ContextCompat.getColor(this, R.color.orange_safety_hat))
            .build()

        startForeground(1, notification)
    }

    // ==========================
    // Permission Management
    // ==========================
    private fun monitorPermissions() {
        serviceScope.launch {
            while (isActive) {
                if (hasNecessaryPermissions()) {
                    startForegroundService()
                    initializeSensors()
                    initializeAudioMonitoring()
                    initializeLocationClient()
                    break
                }
                delay(5000L)
            }
        }
    }

    private fun hasNecessaryPermissions(): Boolean {
        val locationPermissionFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val locationPermissionCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val audioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val foregroundServicePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return (locationPermissionFine || locationPermissionCoarse) &&
                audioPermission &&
                backgroundLocationPermission &&
                foregroundServicePermission
    }

    private fun startLocationMonitoring() {
        locationCheckHandler.post(object : Runnable {
            override fun run() {
                if (siteLatitude != null && siteLongitude != null && siteRadius != null) {
                    checkUserLocationInRadius(siteLatitude!!, siteLongitude!!, siteRadius!!)
                }
                locationCheckHandler.postDelayed(this, locationCheckInterval)
            }
        })
    }

    private fun checkUserLocationInRadius(targetLat: Double, targetLng: Double, radius: Double) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        val userLatLng = LatLng(it.latitude, it.longitude)
                        val targetLatLng = LatLng(targetLat, targetLng)
                        val distance = calculateDistance(userLatLng, targetLatLng)

                        if (distance <= radius) {
                            sendAlert("You are in site $siteID.\n Wear all the safety equipment", R.mipmap.safety_vest_foreground)
                        }
                    } ?: Log.d("AlertService", "Failed to retrieve current location")
                }
            } catch (e: SecurityException) {
                Log.d("AlertService", "SecurityException: ${e.message}")
            }
        } else {
            Log.d("AlertService", "Location permissions are not granted.")
        }
    }

    private fun calculateDistance(loc1: LatLng, loc2: LatLng): Double {
        val earthRadius = 6371000.0 // Raggio della Terra in metri
        val dLat = Math.toRadians(loc2.latitude - loc1.latitude)
        val dLng = Math.toRadians(loc2.longitude - loc1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(loc1.latitude)) * cos(Math.toRadians(loc2.latitude)) *
                sin(dLng / 2) * sin(dLng / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}