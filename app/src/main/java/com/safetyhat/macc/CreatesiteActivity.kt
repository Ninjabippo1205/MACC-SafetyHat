package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import android.app.DatePickerDialog
import java.util.Calendar
import android.widget.ImageView
import java.util.regex.Pattern

class CreatesiteActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_site)

        val managerCF = intent.getStringExtra("managerCF").toString()

        val backButton = findViewById<ImageView>(R.id.menu_icon)
        backButton.setOnClickListener {
            val intent = Intent(this, ManagermenuActivity::class.java)
            startActivity(intent)
            finish()
        }

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
        val addressField = findViewById<EditText>(R.id.address_field)
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
            if (isInputValid(startDate, endDate, maxWorkers, scaffolding, address, siteRadius, securityCode)) {
                registerSite(startDate, endDate, maxWorkers, scaffolding, address, siteRadius, securityCode, managerCF)
            }
        }
    }

    private fun generateSecurityCode() {
        val url = "https://NoemiGiustini01.pythonanywhere.com/site/generateSecurityCode"

        val request = Request.Builder()
            .url(url)
            .build()

        val securityCodeField = findViewById<EditText>(R.id.security_code_field)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Gestisce l'errore se la richiesta fallisce
                runOnUiThread {
                    Toast.makeText(applicationContext, "Unable to generate security code", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let {
                    val json = JSONObject(it)
                    val securityCode = json.getString("security_code")

                    // Aggiorna il campo di testo con id "securityCodeField"
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

        // Pattern per un indirizzo che accetta lettere, numeri, spazi e massimo 100 caratteri
        val addressPattern = "^[a-zA-Z0-9\\s]{1,100}$"
        if (!Pattern.matches(addressPattern, address)) {
            Toast.makeText(this, "Address should contain only letters, numbers, and spaces (max 100 characters)", Toast.LENGTH_SHORT).show()
            return false
        }

        // Pattern numerico per min. 1 cifra
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

        // Pattern per la data nel formato dd/mm/yyyy
        val datePattern = "^\\d{2}/\\d{2}/\\d{4}$"
        if (!Pattern.matches(datePattern, startDate)) {
            Toast.makeText(this, "Invalid start date format (use dd/mm/yyyy)", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Pattern.matches(datePattern, endDate)) {
            Toast.makeText(this, "Invalid end date format (use dd/mm/yyyy)", Toast.LENGTH_SHORT).show()
            return false
        }

        // Controllo che la data di inizio sia precedente alla data di fine
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
                        Log.e("RegistrationError", "Failed to register site: $errorMessage", e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        val responseBody = response.body?.string()
                        if (response.isSuccessful) {
                            Toast.makeText(this@CreatesiteActivity, "Site created successfully!", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this@CreatesiteActivity, CreatesiteActivity::class.java)
                            startActivity(intent)
                            finish()
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