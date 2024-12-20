package com.safetyhat.macc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import android.telephony.SmsManager
import androidx.activity.OnBackPressedCallback

class FallAlertActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var timer: CountDownTimer
    private var countdownTime = 10 // Countdown time in seconds
    private val client = OkHttpClient()
    private val activityScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var siteID: Int = -1 // Assuming siteID is passed via Intent
    private var workerCF: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_fall_alert)

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            timerTextView = findViewById(R.id.fall_alert_timer)
            val btnImOk = findViewById<Button>(R.id.btn_im_ok)

            // Retrieve siteID and workerCF from Intent
            siteID = intent.getIntExtra("siteID", -1)
            workerCF = intent.getStringExtra("workerCF")

            if (siteID == -1 || workerCF == null) {
                Toast.makeText(this, "Missing siteID or workerCF.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Initialize the countdown timer
            timer = object : CountDownTimer((countdownTime * 1000).toLong(), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = (millisUntilFinished / 1000).toInt()
                    timerTextView.text = secondsLeft.toString()
                }

                override fun onFinish() {
                    Log.d("FallAlertActivity", "Fall alert countdown finished.")

                    // Simulate emergency logic or send alerts here
                    fetchWorkersBySiteID(siteID)

                    // Return to the previous page
                    finish()
                }

            }

            timer.start()

            btnImOk.setOnClickListener {
                timer.cancel()
                Toast.makeText(this, "Glad to hear you're okay!", Toast.LENGTH_SHORT).show()
                finish() // Close the Activity
            }

            // Disable the back button using OnBackPressedCallback
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Do nothing, consume the back press
                }
            })

        } catch (e: Exception) {
            Log.e("FallAlertActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fetchWorkersBySiteID(siteID: Int) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/read_all_by_siteid"
        val requestUrl = url.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("siteID", siteID.toString())
            ?.build()

        if (requestUrl == null) {
            Log.e("FallAlertActivity", "Malformed URL.")
            return
        }

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FallAlertActivity", "Failed to fetch workers: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("FallAlertActivity", "Unsuccessful response: ${response.code}")
                    return
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.e("FallAlertActivity", "Empty response body.")
                    return
                }

                try {
                    val phoneNumbers = parseWorkerResponse(responseBody)
                    if (phoneNumbers.isNotEmpty()) {
                        sendEmergencyMessage(phoneNumbers, "A fall has been detected on site. Please provide assistance immediately.")
                    } else {
                        Log.d("FallAlertActivity", "No workers found for site ID $siteID.")
                    }
                } catch (e: Exception) {
                    Log.e("FallAlertActivity", "Error parsing workers response: ${e.message}")
                }
            }
        })
    }

    private fun parseWorkerResponse(response: String): List<String> {
        val phoneNumbers = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val worker = jsonArray.getJSONObject(i)
                val phoneNumber = worker.optString("PhoneNumber")
                if (phoneNumber.isNotEmpty()) {
                    phoneNumbers.add(phoneNumber)
                }
            }
        } catch (e: JSONException) {
            Log.e("FallAlertActivity", "JSON Parsing error: ${e.message}")
        }
        return phoneNumbers
    }

    private fun sendEmergencyMessage(phoneNumbers: List<String>, message: String) {
        // Get unique list of phone numbers
        val uniquePhoneNumbers = phoneNumbers.distinct()

        // Check location permissions
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("FallAlertActivity", "Location permission not granted")
            val fallbackMessage = "$message\nLocation: [Unable to retrieve location]"
            sendSmsToNumbers(uniquePhoneNumbers, fallbackMessage)
            return
        }

        try {
            // Get current location
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude

                    // Format coordinates into a Google Maps link
                    val locationLink = "https://maps.google.com/?q=$latitude,$longitude"
                    val messageWithLocation = "$message\nLocation: $locationLink"

                    // Send SMS with location
                    sendSmsToNumbers(uniquePhoneNumbers, messageWithLocation)
                } else {
                    Log.e("FallAlertActivity", "Failed to retrieve location")
                    val fallbackMessage = "$message\nLocation: [Unable to retrieve location]"
                    sendSmsToNumbers(uniquePhoneNumbers, fallbackMessage)
                }
            }.addOnFailureListener { e ->
                Log.e("FallAlertActivity", "Error getting location: ${e.message}")
                val fallbackMessage = "$message\nLocation: [Unable to retrieve location]"
                sendSmsToNumbers(uniquePhoneNumbers, fallbackMessage)
            }
        } catch (e: SecurityException) {
            Log.e("FallAlertActivity", "SecurityException: ${e.message}")
            val fallbackMessage = "$message\nLocation: [Unable to retrieve location]"
            sendSmsToNumbers(uniquePhoneNumbers, fallbackMessage)
        }
    }

    private fun sendSmsToNumbers(phoneNumbers: List<String>, message: String) {
        val smsManager = SmsManager.getDefault()
        for (phone in phoneNumbers) {
            try {
                smsManager.sendTextMessage(phone, null, message, null, null)
                Log.d("FallAlertActivity", "SMS sent to $phone")
            } catch (e: Exception) {
                Log.e("FallAlertActivity", "Failed to send SMS to $phone: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
        activityScope.cancel()
    }
}
