package com.safetyhat.macc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import okhttp3.*
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mindrot.jbcrypt.BCrypt
import java.io.IOException
import java.util.regex.Pattern

class WorkerinfoActivity : AppCompatActivity(), OnMapReadyCallback {
    private val locationPermissionRequestCode = 1
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

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home_worker -> {
                    startActivity(Intent(this, WorkermenuActivity::class.java))
                }
                R.id.nav_account_info_worker -> {
                    startActivity(Intent(this, WorkerinfoActivity::class.java))
                }
                R.id.nav_logout_worker -> {
                    startActivity(Intent(this, MainActivity::class.java))
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkLocationPermission()

        val cf = intent.getStringExtra("workerCF")
        fetchWorkerInfo(cf.toString())

        val changePasswordButton = findViewById<Button>(R.id.change_password_button)
        changePasswordButton.setOnClickListener {
            val newPassword = findViewById<EditText>(R.id.new_password_worker_field).text.toString()
            if (newPassword.isNotEmpty()) {
                val passwordPattern = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$"
                if (!Pattern.matches(passwordPattern, newPassword)) {
                    Toast.makeText(this, "Password must be at least 8 characters, with uppercase, lowercase, number, and one of [@#$%^&+=!\\]", Toast.LENGTH_LONG).show()
                } else {
                    val hashedPassword = hashPassword(newPassword)
                    updatePassword(cf.toString(), hashedPassword)
                }
            } else {
                Toast.makeText(this, "Please enter a new password", Toast.LENGTH_SHORT).show()
            }
        }
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
                    Toast.makeText(this@WorkerinfoActivity, "Failed to update password. Try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@WorkerinfoActivity, "Password updated successfully", Toast.LENGTH_SHORT).show()
                        findViewById<EditText>(R.id.new_password_worker_field).text.clear()
                    } else {
                        Toast.makeText(this@WorkerinfoActivity, "Failed to update password", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@WorkerinfoActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
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
                        val formattedBirthDate = birthday.split(" ")[1] + " " + birthday.split(" ")[2] + " " + birthday.split(" ")[3]
                        val phoneNumber = jsonObject.optString("PhoneNumber", "N/A")

                        findViewById<TextView>(R.id.first_name_worker_text).text = firstName
                        findViewById<TextView>(R.id.last_name_worker_text).text = lastName
                        findViewById<TextView>(R.id.cf_worker_text).text = cf
                        findViewById<TextView>(R.id.birthday_worker_text).text = formattedBirthDate
                        findViewById<TextView>(R.id.telephone_worker_text).text = phoneNumber
                    } else {
                        Toast.makeText(this@WorkerinfoActivity, "Worker not found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Abilita i controlli di zoom
        mMap.uiSettings.isZoomControlsEnabled = true

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)

                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    mMap.addMarker(MarkerOptions().position(currentLatLng).title("You are here"))
                }
            }
        }

        // Aggiungi i POI sulla mappa con i rispettivi cerchi e colori automatici
        val address1 = "2000 N Shoreline Blvd, Mountain View, CA 94043, Stati Uniti"
        val address2 = "2195 N Shoreline Blvd, Mountain View, CA 94043, Stati Uniti"

        addPOIFromAddress(address1, 200.0)
        addPOIFromAddress(address2, 150.0)
    }

    private val circleColors = listOf(
        0x2200FF00.toInt(), // Verde trasparente
        0x22FF0000.toInt(), // Rosso trasparente
        0x220000FF.toInt(), // Blu trasparente
        0x22FFFF00.toInt()  // Giallo trasparente
    )
    private var colorIndex = 0

    // Funzione per aggiungere un POI e disegnare un cerchio con un colore assegnato automaticamente
    private fun addPOIFromAddress(address: String, radius: Double) {
        val geocoder = Geocoder(this, Locale.getDefault())
        geocoder.getFromLocationName(address, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                if (addresses.isNotEmpty()) {
                    val location = addresses[0]
                    val latLng = LatLng(location.latitude, location.longitude)

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
                    Log.e("MapError", "Indirizzo non trovato.")
                }
            }

            override fun onError(errorMessage: String?) {
                Log.e("GeocoderError", "Errore di geocodifica: $errorMessage")
            }
        })
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == locationPermissionRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    mMap.isMyLocationEnabled = true
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
