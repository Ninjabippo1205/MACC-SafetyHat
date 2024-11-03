package com.safetyhat.macc

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
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
        setContentView(R.layout.activity_create_site)

        val managerCF = intent.getStringExtra("managerCF").toString()

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
    }

    private fun setupAutocomplete() {
        val addressInput = findViewById<AutoCompleteTextView>(R.id.address_field)
        val token = AutocompleteSessionToken.newInstance()

        // Inizializza il TextWatcher per il completamento automatico
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
                            Log.e("CreatesiteActivity", "Error getting autocomplete predictions: $exception")
                        }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        // Aggiungi il TextWatcher all'indirizzo input
        addressInput.addTextChangedListener(textWatcher)

        // Gestisci il click sul suggerimento
        addressInput.setOnItemClickListener { parent, _, position, _ ->
            val selectedAddress = parent?.getItemAtPosition(position) as? String
            if (selectedAddress != null) {
                // Rimuovi temporaneamente il TextWatcher per evitare che i suggerimenti riappaiano
                addressInput.removeTextChangedListener(textWatcher)

                // Imposta il testo selezionato e nascondi il menu dei suggerimenti
                addressInput.setText(selectedAddress)
                isAddressSelected = true
                addressInput.dismissDropDown()

                // Riattiva il TextWatcher quando l'utente modifica il testo
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

        val request = Request.Builder()
            .url(url)
            .build()

        val securityCodeField = findViewById<EditText>(R.id.security_code_field)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Unable to generate security code", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    val json = JSONObject(it)
                    val securityCode = json.getString("security_code")

                    runOnUiThread {
                        securityCodeField.setText(securityCode)
                    }
                }
            }
        })
    }

    private fun showDatePickerDialog(birthdateField: EditText) {
        val calendar = Calendar.getInstance()

        val datePickerDialog = DatePickerDialog(
            this,
            R.style.CustomDatePickerDialog,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, dayOfMonth)

                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                birthdateField.setText(dateFormat.format(selectedDate.time))
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

        // Verifica che l'indirizzo sia lungo al massimo 100 caratteri
        if (address.length > 500) {
            Toast.makeText(this, "Address should be at most 100 characters long", Toast.LENGTH_SHORT).show()
            return false
        }

        val numericPattern = "^\\d{1,}$"
        if (!Pattern.matches(numericPattern, maxWorkers)) {
            Toast.makeText(this, "Number of workers should contain only numbers with at least 1 digit", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Pattern.matches(numericPattern, scaffolding)) {
            Toast.makeText(this, "Number of scaffolding should contain only numbers with at least 1 digit", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Pattern.matches(numericPattern, siteRadius)) {
            Toast.makeText(this, "Radius of the site should contain only numbers with at least 1 digit", Toast.LENGTH_SHORT).show()
            return false
        }

        val datePattern = "^\\d{2}/\\d{2}/\\d{4}$"
        if (!Pattern.matches(datePattern, startDate)) {
            Toast.makeText(this, "Invalid start date format (use dd/mm/yyyy)", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Pattern.matches(datePattern, endDate)) {
            Toast.makeText(this, "Invalid end date format (use dd/mm/yyyy)", Toast.LENGTH_SHORT).show()
            return false
        }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val start = dateFormat.parse(startDate)
        val end = dateFormat.parse(endDate)
        if (start != null && end != null && start.after(end)) {
            Toast.makeText(this, "Start date must be before end date", Toast.LENGTH_SHORT).show()
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

        val originalFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val targetFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formattedStartDate = try {
            val startDate = originalFormat.parse(startdate)
            targetFormat.format(startDate)
        } catch (e: Exception) {
            startdate
        }
        val formattedEndDate = try {
            val endDate = originalFormat.parse(enddate)
            targetFormat.format(endDate)
        } catch (e: Exception) {
            enddate
        }

        val url = "https://noemigiustini01.pythonanywhere.com/site/create"
        val json = JSONObject()
        json.put("StartDate", formattedStartDate)
        json.put("EstimatedEndDate", formattedEndDate)
        json.put("TotalWorkers", maxWorkers)
        json.put("ScaffoldingCount", scaffolding)
        json.put("Address", address)
        json.put("SiteRadius", siteRadius)
        json.put("SecurityCode", securityCode)
        json.put("ManagerCF", managerCF)

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        val errorMessage = e.localizedMessage ?: "Unknown error occurred"
                        Toast.makeText(this@CreatesiteActivity, "Failed to register: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && responseBody != null) {
                            try {
                                // Parse JSON response to get the site_id
                                val jsonResponse = JSONObject(responseBody)
                                val siteId = jsonResponse.getInt("site_id")

                                Toast.makeText(this@CreatesiteActivity, "Site created successfully!", Toast.LENGTH_SHORT).show()

                                val intent = Intent(this@CreatesiteActivity, QRGenerationActivity::class.java)
                                intent.putExtra("managerCF", managerCF)
                                intent.putExtra("SiteID", siteId.toString())
                                startActivity(intent)
                                finish()
                            } catch (e: JSONException) {
                                Toast.makeText(this@CreatesiteActivity, "Failed to parse response", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val errorMessage = responseBody ?: "Unknown error"
                            Toast.makeText(this@CreatesiteActivity, "Failed to register: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }

    }
}