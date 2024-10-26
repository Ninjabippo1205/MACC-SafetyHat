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
import java.util.regex.Pattern
import org.mindrot.jbcrypt.BCrypt

class RegistrationActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val firstNameField = findViewById<EditText>(R.id.first_name_registration_field)
        val lastNameField = findViewById<EditText>(R.id.last_name_registration_field)
        val birthdateField = findViewById<EditText>(R.id.birthdate_registration_field)
        val phoneField = findViewById<EditText>(R.id.phone_registration_field)
        val cfField = findViewById<EditText>(R.id.cf_registration_field)
        val passwordField = findViewById<EditText>(R.id.password_registration_field)
        val submitButton = findViewById<Button>(R.id.submit_registration_button)

        submitButton.setOnClickListener {
            val firstName = firstNameField.text.toString()
            val lastName = lastNameField.text.toString()
            val birthdate = birthdateField.text.toString()
            val phone = phoneField.text.toString()
            val cf = cfField.text.toString().uppercase()
            val password = passwordField.text.toString()

            if (isInputValid(firstName, lastName, birthdate, phone, cf, password)) {
                val hashedPassword = hashPassword(password)
                registerWorker(firstName, lastName, birthdate, phone, cf, hashedPassword)
            }
        }
    }

    private fun isInputValid(
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        cf: String,
        password: String
    ): Boolean {
        val namePattern = "^[a-zA-Z]{1,50}$"
        if (!Pattern.matches(namePattern, firstName)) {
            Toast.makeText(this, "First name should contain only letters (max 50 characters)", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Pattern.matches(namePattern, lastName)) {
            Toast.makeText(this, "Last name should contain only letters (max 50 characters)", Toast.LENGTH_SHORT).show()
            return false
        }

        val phonePattern = "^\\d{1,9}$"
        if (!Pattern.matches(phonePattern, phone)) {
            Toast.makeText(this, "Phone number should contain only numbers (max 9 digits)", Toast.LENGTH_SHORT).show()
            return false
        }

        val cfPattern = "^[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]$"
        if (!Pattern.matches(cfPattern, cf)) {
            Toast.makeText(this, "Invalid CF format (6 letters, 2 digits, 1 letter, 2 digits, 1 letter, 3 digits, 1 letter)", Toast.LENGTH_SHORT).show()
            return false
        }

        val birthdatePattern = "^\\d{2}/\\d{2}/\\d{4}$"
        if (!Pattern.matches(birthdatePattern, birthdate)) {
            Toast.makeText(this, "Invalid birthdate format (use dd/mm/yyyy)", Toast.LENGTH_SHORT).show()
            return false
        }

        val passwordPattern = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$"
        if (!Pattern.matches(passwordPattern, password)) {
            Toast.makeText(this, "Password must be at least 8 characters,with uppercase, lowercase, number, and one of [@#$%^&+=!\\]", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun registerWorker(
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        cf: String,
        hashedPassword: String
    ) {
        val url = "https://noemigiustini01.pythonanywhere.com/worker/create"
        val json = JSONObject()
        json.put("FirstName", firstName)
        json.put("LastName", lastName)
        json.put("BirthDate", birthdate)
        json.put("PhoneNumber", phone)
        json.put("CF", cf)
        json.put("Password", hashedPassword)
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
                        val errorMessage = e.localizedMessage ?: "Unknown error occurred"
                        Toast.makeText(this@RegistrationActivity, "Failed to register: $errorMessage", Toast.LENGTH_LONG).show()
                        Log.e("RegistrationError", "Failed to register worker: $errorMessage", e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        val responseBody = response.body?.string()
                        if (response.isSuccessful) {
                            Toast.makeText(this@RegistrationActivity, "Account created successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            val errorMessage = responseBody ?: "Unknown error"
                            Toast.makeText(this@RegistrationActivity, "Failed to register: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    }
                }

            })
        }
    }
}