package com.safetyhat.macc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
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

class ManagerInfoActivity : AppCompatActivity(), OnMapReadyCallback {

    private val locationPermissionRequestCode = 1
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Lista di colori predefiniti per le circonferenze
    private val circleColors = listOf(
        0x2200FF00, // Verde trasparente
        0x22FF0000, // Rosso trasparente
        0x220000FF, // Blu trasparente
        0x22FFFF00  // Giallo trasparente
    )
    private var colorIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_info)

        val backButton = findViewById<ImageView>(R.id.menu_icon)
        backButton.setOnClickListener {
            val intent = Intent(this, ManagermenuActivity::class.java)
            startActivity(intent)
            finish()
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkLocationPermission()

        // Dati hardcoded per i test
        val firstName = "John"
        val lastName = "Doe"
        val cf = "JDOE123456789"
        val birthday = "01/01/1990"
        val phoneNumber = "+39 123 456 7890"

        findViewById<TextView>(R.id.first_name_text).text = firstName
        findViewById<TextView>(R.id.last_name_text).text = lastName
        findViewById<TextView>(R.id.cf_text).text = cf
        findViewById<TextView>(R.id.birthday_text).text = birthday
        findViewById<TextView>(R.id.telephone_text).text = phoneNumber
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
