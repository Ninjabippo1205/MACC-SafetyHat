package com.safetyhat.macc

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class CreatesiteActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private lateinit var placesClient: PlacesClient
    private var isAddressSelected = false
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val placeholderText = "@string/address_hint"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_create_site)

            val managerCF = intent.getStringExtra("managerCF") ?: ""

            drawerLayout = findViewById(R.id.drawer_layout)
            navigationView = findViewById(R.id.navigation_view_manager)
            navigationView.itemIconTintList = null

            findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

            navigationView.setNavigationItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_home_manager -> {
                        val intent = Intent(this, ManagermenuActivity::class.java)
                        intent.putExtra("managerCF", managerCF)
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_account_info_manager -> {
                        val intent = Intent(this, ManagerInfoActivity::class.java)
                        intent.putExtra("managerCF", managerCF)
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_site_overview_manager -> {
                        val intent = Intent(this, SitesOverviewActivity::class.java)
                        intent.putExtra("managerCF", managerCF)
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_logout_manager -> {
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
                drawerLayout.closeDrawer(GravityCompat.START)
                true
            }

            if (!Places.isInitialized()) {
                Places.initialize(applicationContext, getString(R.string.google_maps_key))
            }
            placesClient = Places.createClient(this)

            setupAutocomplete()

            val startDateField = findViewById<EditText>(R.id.start_date_field)
            startDateField.setOnClickListener {
                showDatePickerDialog(startDateField)
            }

            val endDateField = findViewById<EditText>(R.id.end_date_field)
            endDateField.setOnClickListener {
                showDatePickerDialog(endDateField)
            }

            val maxWorkersField = findViewById<EditText>(R.id.max_workers_field)
            val scaffoldingField = findViewById<EditText>(R.id.scaffolding_field)
            val addressField = findViewById<AutoCompleteTextView>(R.id.address_field)
            val siteRadiusField = findViewById<EditText>(R.id.site_radius_field)
            val securityCodeField = findViewById<EditText>(R.id.security_code_field)

            val generateCodeButton = findViewById<Button>(R.id.generate_code_button)
            generateCodeButton.setOnClickListener {
                generateSecurityCode()
            }

            val submitButton = findViewById<Button>(R.id.create_site_button)
            submitButton.setOnClickListener {
                val startDate = startDateField.text.toString()
                val endDate = endDateField.text.toString()
                val maxWorkers = maxWorkersField.text.toString()
                val scaffolding = scaffoldingField.text.toString()
                val address = addressField.text.toString()
                val siteRadius = siteRadiusField.text.toString()
                val securityCode = securityCodeField.text.toString()

                if (isAddressSelected && address != placeholderText) {
                    if (containsStreetNumber(address)) {
                        if (isInputValid(startDate, endDate, maxWorkers, scaffolding, address, siteRadius, securityCode)) {
                            registerSite(startDate, endDate, maxWorkers, scaffolding, address, siteRadius, securityCode, managerCF)
                        }
                    } else {
                        Toast.makeText(this, "Please include a street number in the address.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Please select a valid address from the suggestions.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("CreatesiteActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred during initialization.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupAutocomplete() {
        val addressInput = findViewById<AutoCompleteTextView>(R.id.address_field)
        val token = AutocompleteSessionToken.newInstance()

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isAddressSelected) {
                    val request = FindAutocompletePredictionsRequest.builder()
                        .setSessionToken(token)
                        .setQuery(s.toString())
                        .build()

                    placesClient.findAutocompletePredictions(request)
                        .addOnSuccessListener { response ->
                            val suggestions = response.autocompletePredictions.map { prediction ->
                                prediction.getFullText(null).toString()
                            }
                            val adapter = ArrayAdapter(this@CreatesiteActivity, android.R.layout.simple_list_item_1, suggestions)
                            addressInput.setAdapter(adapter)
                            addressInput.showDropDown()
                        }
                        .addOnFailureListener { exception ->
                            Log.e("CreatesiteActivity", "Error getting autocomplete predictions: ${exception.message}")
                        }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        addressInput.addTextChangedListener(textWatcher)

        addressInput.setOnItemClickListener { parent, _, position, _ ->
            val selectedAddress = parent?.getItemAtPosition(position) as? String
            if (selectedAddress != null) {
                addressInput.removeTextChangedListener(textWatcher)
                addressInput.setText(selectedAddress)
                isAddressSelected = true
                addressInput.dismissDropDown()
                addressInput.post {
                    addressInput.addTextChangedListener(textWatcher)
                }
            }
        }
    }

    private fun containsStreetNumber(address: String): Boolean {
        val pattern = Regex("\\b\\d{1,4}\\b(?!\\s?\\d{5})")
        return pattern.containsMatchIn(address)
    }

    private fun generateSecurityCode() {
        val url = "https://NoemiGiustini01.pythonanywhere.com/site/generateSecurityCode"
        val request = Request.Builder().url(url).build()
        val securityCodeField = findViewById<EditText>(R.id.security_code_field)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Unable to generate security code", Toast.LENGTH_SHORT).show()
                }
                Log.e("CreatesiteActivity", "Failed to generate security code: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()
                    val json = JSONObject(responseBody ?: "")
                    val securityCode = json.getString("security_code")

                    runOnUiThread {
                        securityCodeField.setText(securityCode)
                    }
                } catch (e: JSONException) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Error parsing security code", Toast.LENGTH_SHORT).show()
                    }
                    Log.e("CreatesiteActivity", "JSON Parsing error: ${e.message}")
                }
            }
        })
    }

    private fun showDatePickerDialog(dateField: EditText) {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            R.style.CustomDatePickerDialog,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)

                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                dateField.setText(dateFormat.format(selectedDate.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    private fun isInputValid(
        startDate: String,
        endDate: String,
        maxWorkers: String,
        scaffolding: String,
        address: String,
        siteRadius: String,
        securityCode: String
    ): Boolean {
        if (!(maxWorkers.isNotEmpty() && scaffolding.isNotEmpty() && address.isNotEmpty() && siteRadius.isNotEmpty() && securityCode.isNotEmpty())) {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
            return false
        }

        if (address.length > 500) {
            Toast.makeText(this, "Address should be at most 100 characters long", Toast.LENGTH_SHORT).show()
            return false
        }

        val numericPattern = "^\\d{1,}$"
        if (!Pattern.matches(numericPattern, maxWorkers) || !Pattern.matches(numericPattern, scaffolding) || !Pattern.matches(numericPattern, siteRadius)) {
            Toast.makeText(this, "Invalid numeric input", Toast.LENGTH_SHORT).show()
            return false
        }

        val datePattern = "^\\d{2}/\\d{2}/\\d{4}$"
        if (!Pattern.matches(datePattern, startDate) || !Pattern.matches(datePattern, endDate)) {
            Toast.makeText(this, "Invalid date format (use dd/mm/yyyy)", Toast.LENGTH_SHORT).show()
            return false
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        try {
            val start = dateFormat.parse(startDate)
            val end = dateFormat.parse(endDate)
            if (start != null && end != null && start.after(end)) {
                Toast.makeText(this, "Start date must be before end date", Toast.LENGTH_SHORT).show()
                return false
            }
        } catch (e: ParseException) {
            Toast.makeText(this, "Invalid date format", Toast.LENGTH_SHORT).show()
            Log.e("CreatesiteActivity", "Date parsing error: ${e.message}")
            return false
        }

        return true
    }

    private fun registerSite(
        startdate: String,
        enddate: String,
        maxWorkers: String,
        scaffolding: String,
        address: String,
        siteRadius: String,
        securityCode: String,
        managerCF: String
    ) {
        fetchCoordinates(address) { lat, lng ->
            if (lat != null && lng != null) {
                fetchLocationKey(lat, lng) { locationKey ->
                    if (locationKey != null) {
                        try {
                            val originalFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                            val targetFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            val formattedStartDate = originalFormat.parse(startdate)?.let { targetFormat.format(it) } ?: startdate
                            val formattedEndDate = originalFormat.parse(enddate)?.let { targetFormat.format(it) } ?: enddate

                            val url = "https://noemigiustini01.pythonanywhere.com/site/create"
                            val json = JSONObject().apply {
                                put("StartDate", formattedStartDate)
                                put("EstimatedEndDate", formattedEndDate)
                                put("TotalWorkers", maxWorkers)
                                put("ScaffoldingCount", scaffolding)
                                put("Address", address)
                                put("SiteRadius", siteRadius)
                                put("SecurityCode", securityCode)
                                put("ManagerCF", managerCF)
                                put("Latitude", lat)
                                put("Longitude", lng)
                                put("LocationKey", locationKey)
                            }

                            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                            val request = Request.Builder().url(url).post(requestBody).build()

                            client.newCall(request).enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    runOnUiThread {
                                        Toast.makeText(this@CreatesiteActivity, "Failed to register: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                    Log.e("CreatesiteActivity", "Failed to register site: ${e.message}")
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    try {
                                        val responseBody = response.body?.string()
                                        val jsonResponse = JSONObject(responseBody ?: "")
                                        runOnUiThread {
                                            if (response.isSuccessful) {
                                                val siteId = jsonResponse.getInt("site_id")
                                                Toast.makeText(this@CreatesiteActivity, "Site created successfully!", Toast.LENGTH_SHORT).show()
                                                startActivity(Intent(this@CreatesiteActivity, QRGenerationActivity::class.java).apply {
                                                    putExtra("managerCF", managerCF)
                                                    putExtra("SiteID", siteId.toString())
                                                })
                                                finish()
                                            } else {
                                                val errorCode = jsonResponse.optInt("error_code", -1)
                                                val errorMessage = when (errorCode) {
                                                    1 -> "A site with this address already exists."
                                                    else -> jsonResponse.optString("error", "Failed to create site due to an unexpected error.")
                                                }
                                                Toast.makeText(this@CreatesiteActivity, errorMessage, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: JSONException) {
                                        runOnUiThread {
                                            Toast.makeText(this@CreatesiteActivity, "Error parsing server response.", Toast.LENGTH_SHORT).show()
                                        }
                                        Log.e("CreatesiteActivity", "JSON Parsing error: ${e.message}")
                                    }
                                }
                            })
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this@CreatesiteActivity, "An error occurred while creating the site.", Toast.LENGTH_SHORT).show()
                            }
                            Log.e("CreatesiteActivity", "Error in registerSite: ${e.message}")
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Failed to retrieve location key", Toast.LENGTH_SHORT).show()
                        }
                        Log.e("CreatesiteActivity", "Location key is null")
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, "Failed to retrieve coordinates", Toast.LENGTH_SHORT).show()
                }
                Log.e("CreatesiteActivity", "Coordinates are null")
            }
        }
    }


    private fun fetchCoordinates(address: String, callback: (Double?, Double?) -> Unit) {
        val googleApiKey = getString(R.string.google_maps_key)
        val geocodeUrl = "https://maps.googleapis.com/maps/api/geocode/json"
        val geocodeParams = geocodeUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("address", address)
            ?.addQueryParameter("key", googleApiKey)
            ?.build()

        if (geocodeParams == null) {
            Log.e("CreatesiteActivity", "Invalid geocode URL")
            callback(null, null)
            return
        }

        val geocodeRequest = Request.Builder().url(geocodeParams.toString()).get().build()
        client.newCall(geocodeRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, null)
                Log.e("CreatesiteActivity", "Failed to fetch coordinates: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    val geocodeData = JSONObject(responseData ?: "")
                    val resultsArray = geocodeData.getJSONArray("results")
                    if (resultsArray.length() > 0) {
                        val location = resultsArray.getJSONObject(0)
                            .getJSONObject("geometry")
                            .getJSONObject("location")

                        val lat = location.getDouble("lat")
                        val lng = location.getDouble("lng")
                        callback(lat, lng)
                    } else {
                        Log.e("CreatesiteActivity", "No results found for address")
                        callback(null, null)
                    }
                } catch (e: JSONException) {
                    Log.e("CreatesiteActivity", "JSON Parsing error: ${e.message}")
                    callback(null, null)
                }
            }
        })
    }

    private fun fetchLocationKey(lat: Double, lng: Double, callback: (String?) -> Unit) {
        val apiKey = getString(R.string.accuweather_api_key)
        val locationUrl = "https://dataservice.accuweather.com/locations/v1/cities/geoposition/search"
        val locationParams = locationUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", "$lat,$lng")
            ?.addQueryParameter("apikey", apiKey)
            ?.build()

        if (locationParams == null) {
            Log.e("CreatesiteActivity", "Invalid location URL")
            callback(null)
            return
        }

        val locationRequest = Request.Builder().url(locationParams.toString()).get().build()
        client.newCall(locationRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
                Log.e("CreatesiteActivity", "Failed to fetch location key: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    val locationData = JSONObject(responseData ?: "")
                    val locationKey = locationData.optString("Key", null)
                    callback(locationKey)
                } catch (e: JSONException) {
                    Log.e("CreatesiteActivity", "JSON Parsing error: ${e.message}")
                    callback(null)
                }
            }
        })
    }
}
