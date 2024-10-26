package com.safetyhat.macc

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
import android.content.Intent
import java.util.Calendar
import android.widget.ImageView


class RegistrationActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val backButton = findViewById<ImageView>(R.id.back_icon)
        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Chiude la RegistrationActivity
        }

        val firstNameField = findViewById<EditText>(R.id.first_name_registration_field)
        val lastNameField = findViewById<EditText>(R.id.last_name_registration_field)
        //val birthdateField = findViewById<EditText>(R.id.birthdate_registration_field)
        val phoneField = findViewById<EditText>(R.id.phone_registration_field)
        val cfField = findViewById<EditText>(R.id.cf_registration_field)
        val passwordField = findViewById<EditText>(R.id.password_registration_field)
        val submitButton = findViewById<Button>(R.id.submit_registration_button)

        val birthdateField = findViewById<EditText>(R.id.birthdate_registration_field)
        birthdateField.setOnClickListener {
            showDatePickerDialog(birthdateField)
        }

        submitButton.setOnClickListener {
            val firstName = firstNameField.text.toString()
            val lastName = lastNameField.text.toString()
            val birthdate = birthdateField.text.toString()
            val phone = phoneField.text.toString()
            val cf = cfField.text.toString()
            val password = passwordField.text.toString()

            if (firstName.isNotEmpty() && lastName.isNotEmpty() && birthdate.isNotEmpty() &&
                phone.isNotEmpty() && cf.isNotEmpty() && password.isNotEmpty()) {
                registerWorker(firstName, lastName, birthdate, phone, cf, password)
            } else {
                Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
            }
        }
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

    private fun registerWorker(
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        cf: String,
        password: String
    ) {

        val originalFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val targetFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formattedBirthdate = try {
            val date = originalFormat.parse(birthdate)
            targetFormat.format(date)
        } catch (e: Exception) {
            birthdate
        }

        val url = "https://noemigiustini01.pythonanywhere.com/worker/create"
        val json = JSONObject()
        json.put("FirstName", firstName)
        json.put("LastName", lastName)
        json.put("BirthDate", formattedBirthdate)
        json.put("PhoneNumber", phone)
        json.put("CF", cf)
        json.put("Password", password)
        json.put("Presence", "false")

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        // Ottieni il messaggio di errore dall'eccezione
                        val errorMessage = e.localizedMessage ?: "Unknown error occurred"

                        // Mostra un toast con il messaggio dettagliato
                        Toast.makeText(this@RegistrationActivity, "Failed to register: $errorMessage", Toast.LENGTH_LONG).show()

                        // Puoi anche loggare l'errore per una diagnosi più dettagliata
                        Log.e("RegistrationError", "Failed to register worker: $errorMessage", e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        val responseBody = response.body?.string() // Ottieni il corpo della risposta come stringa
                        if (response.isSuccessful) {
                            // Se la richiesta è andata a buon fine
                            Toast.makeText(this@RegistrationActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            // Se c'è un errore, mostra il messaggio di errore
                            val errorMessage = responseBody ?: "Unknown error"
                            Toast.makeText(this@RegistrationActivity, "Failed to register: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    }
                }

            })
        }
    }
}
