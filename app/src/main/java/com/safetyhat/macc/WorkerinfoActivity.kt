package com.safetyhat.macc

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import okhttp3.*
import android.widget.Toast
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mindrot.jbcrypt.BCrypt
import java.io.IOException
import java.util.regex.Pattern

class WorkerinfoActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val client = OkHttpClient()

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_info)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view_worker)
        navigationView.itemIconTintList = null

        findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        initializeUI()

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
                R.id.nav_site_info_worker -> {
                    val intent = Intent(this, SiteInfoActivity::class.java)
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
                    val stopServiceIntent = Intent(this, AlertService::class.java)
                    stopService(stopServiceIntent)

                    // Elimina tutte le notifiche usando NotificationManager
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

        fetchWorkerInfo(workerCF.toString())
        checkPermissionsAndConfigureUI(workerCF, siteID)

        val changePasswordButton = findViewById<Button>(R.id.change_password_button)
        changePasswordButton.setOnClickListener {
            val newPassword = findViewById<EditText>(R.id.new_password_field).text.toString()
            if (newPassword.isNotEmpty()) {
                val passwordPattern = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$"
                if (!Pattern.matches(passwordPattern, newPassword)) {
                    Toast.makeText(this, "Password must be at least 8 characters,with uppercase, lowercase, number, and one of [@#$%^&+=!\\]", Toast.LENGTH_LONG).show()
                }else {
                    val hashedPassword = hashPassword(newPassword)
                    updatePassword(workerCF.toString(), hashedPassword)
                }
            } else {
                Toast.makeText(this, "Please enter a new password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeUI() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view_worker)
        navigationView.itemIconTintList = null

        findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Disable buttons until permissions are granted
        findViewById<Button>(R.id.change_password_button).isEnabled = false
    }

    private fun checkPermissionsAndConfigureUI(workerCF: String?, siteID: String?) {
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasAudioPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission) {
            initializeMap()
        } else {
            Toast.makeText(this, "Location permission is required for map features.", Toast.LENGTH_SHORT).show()
            val mapView = (supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment).view
            mapView?.visibility = View.GONE
        }

        if (hasCameraPermission && hasAudioPermission) {
            findViewById<Button>(R.id.change_password_button).isEnabled = true
        } else {
            Toast.makeText(this, "Permissions for camera and microphone are required for full functionality.", Toast.LENGTH_LONG).show()
        }

        fetchWorkerInfo(workerCF.toString())
    }

    private fun initializeMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun updatePassword(cf: String, newPassword: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/updatepassword/$cf"
        val requestBody = JSONObject()
        requestBody.put("Password", newPassword)
        val body = requestBody.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .put(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@WorkerinfoActivity,
                        "Failed to update password. Try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@WorkerinfoActivity,
                            "Password updated successfully",
                            Toast.LENGTH_SHORT
                        ).show()
                        findViewById<EditText>(R.id.new_password_field).text.clear()
                    } else {
                        Toast.makeText(
                            this@WorkerinfoActivity,
                            "Failed to update password",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun fetchWorkerInfo(cf: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/read?cf=$cf"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@WorkerinfoActivity,
                        "Network error. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData ?: "")

                runOnUiThread {
                    if (response.isSuccessful && !jsonObject.has("message")) {
                        val firstName = jsonObject.optString("FirstName", "N/A")
                        val lastName = jsonObject.optString("LastName", "N/A")
                        val cf = jsonObject.optString("CF", "N/A")
                        val birthday = jsonObject.optString("BirthDate", "N/A")
                        val formattedBirthDate =
                            birthday.split(" ")[1] + " " + birthday.split(" ")[2] + " " + birthday.split(" ")[3]
                        val phoneNumber = jsonObject.optString("PhoneNumber", "N/A")
                        val SiteID = jsonObject.optString("SiteCode", "N/A")

                        findViewById<TextView>(R.id.first_name_worker_text).text = firstName
                        findViewById<TextView>(R.id.last_name_worker_text).text = lastName
                        findViewById<TextView>(R.id.cf_worker_text).text = cf
                        findViewById<TextView>(R.id.birthday_worker_text).text = formattedBirthDate
                        findViewById<TextView>(R.id.telephone_worker_text).text = phoneNumber

                        fetchSiteInfo(SiteID.toInt())
                    } else {
                        Toast.makeText(this@WorkerinfoActivity, "Worker not found.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(
                        this@WorkerinfoActivity,
                        "Network error. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData ?: "")

                runOnUiThread {
                    if (response.isSuccessful && !jsonObject.has("message")) {
                        val SiteRadius = jsonObject.optDouble("SiteRadius", 0.0)
                        addPOIFromAddress(ID, SiteRadius)
                    } else {
                        Toast.makeText(this@WorkerinfoActivity, "Site not found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true

        // Enable my-location if permission is granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        }
    }

    private val circleColors = listOf(
        0x22FF0000.toInt(), // Rosso trasparente
    )
    private var colorIndex = 0

    // Funzione per aggiungere un POI e disegnare un cerchio con un colore assegnato automaticamente
    private fun addPOIFromAddress(siteID: Int, radius: Double) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/site/read?id=$siteID"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@WorkerinfoActivity,
                        "Failed to retrieve site coordinates",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData ?: "")

                if (response.isSuccessful && !jsonObject.has("message")) {
                    val latitude = jsonObject.optDouble("Latitude")
                    val longitude = jsonObject.optDouble("Longitude")
                    val address = jsonObject.optString("Address", "N/A")

                    if (!latitude.isNaN() && !longitude.isNaN()) {
                        val latLng = LatLng(latitude, longitude)

                        // Assicurati che il codice che interagisce con la mappa venga eseguito nel main thread
                        runOnUiThread {
                            // Aggiungi un marker sulla mappa
                            mMap.addMarker(MarkerOptions().position(latLng).title("POI: $address"))

                            // Seleziona un colore dalla lista e incrementa l'indice
                            val color = circleColors[colorIndex % circleColors.size]
                            colorIndex++

                            // Disegna un cerchio intorno al POI con il colore scelto
                            val circleOptions = CircleOptions()
                                .center(latLng)
                                .radius(radius) // Raggio in metri
                                .strokeColor(color) // Colore del bordo
                                .fillColor(color) // Colore di riempimento
                                .strokeWidth(2f) // Spessore del bordo
                            mMap.addCircle(circleOptions)
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@WorkerinfoActivity,
                                "Coordinates not available for this site.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@WorkerinfoActivity,
                            "Failed to retrieve site info.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            // Disables map if location permission is not granted
            val mapView = (supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment).view
            mapView?.visibility = View.GONE
            Toast.makeText(this, "Location permission has been revoked. Some features will be disabled.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        // Eventuali operazioni di pausa per la mappa possono essere aggiunte qui
    }
}
