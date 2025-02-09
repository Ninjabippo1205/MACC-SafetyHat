package com.safetyhat.macc

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mindrot.jbcrypt.BCrypt
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class RegistrationActivity : AppCompatActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_registration)

            findViewById<ImageView>(R.id.back_icon).setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }

            val emailField = findViewById<EditText>(R.id.email_registration_field)
            val firstNameField = findViewById<EditText>(R.id.first_name_registration_field)
            val lastNameField = findViewById<EditText>(R.id.last_name_registration_field)
            val phoneField = findViewById<EditText>(R.id.phone_registration_field)
            val cfField = findViewById<EditText>(R.id.cf_registration_field)
            val passwordField = findViewById<EditText>(R.id.password_registration_field)
            val submitButton = findViewById<Button>(R.id.submit_registration_button)

            val birthdateField = findViewById<EditText>(R.id.birthdate_registration_field)
            birthdateField.setOnClickListener {
                showDatePickerDialog(birthdateField)
            }

            submitButton.setOnClickListener {
                val email = emailField.text.toString()
                val firstName = firstNameField.text.toString()
                val lastName = lastNameField.text.toString()
                val birthdate = birthdateField.text.toString()
                val phone = phoneField.text.toString()
                val cf = cfField.text.toString().uppercase(Locale.getDefault())
                val password = passwordField.text.toString()

                if (isInputValid(email, firstName, lastName, birthdate, phone, cf, password)) {
                    val hashedPassword = hashPassword(password)
                    registerWorker(email, firstName, lastName, birthdate, phone, cf, hashedPassword)
                }
            }
        } catch (e: Exception) {
            Log.e("RegistrationActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred during initialization.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        super.onBackPressed()
        navigateBack()
    }

    private fun navigateBack() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
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
        email: String,
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        cf: String,
        password: String
    ): Boolean {
        if (email.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || birthdate.isEmpty() ||
            phone.isEmpty() || cf.isEmpty() || password.isEmpty()
        ) {
            Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_LONG).show()
            return false
        }

        val emailPattern = "(?=.{1,255}$)[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        if (!Pattern.matches(emailPattern, email)) {
            Toast.makeText(this, "Wrong email format", Toast.LENGTH_LONG).show()
            return false
        }

        val namePattern = "^[a-zA-Z]{1,50}$"
        if (!Pattern.matches(namePattern, firstName)) {
            Toast.makeText(this, "First name should contain only letters (max 50 characters)", Toast.LENGTH_LONG).show()
            return false
        }
        if (!Pattern.matches(namePattern, lastName)) {
            Toast.makeText(this, "Last name should contain only letters (max 50 characters)", Toast.LENGTH_LONG).show()
            return false
        }

        val phonePattern = "^\\d{1,15}$"
        if (!Pattern.matches(phonePattern, phone)) {
            Toast.makeText(this, "Phone number should contain only numbers (max 15 digits)", Toast.LENGTH_LONG).show()
            return false
        }

        val cfPattern = "^[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]$"
        if (!Pattern.matches(cfPattern, cf)) {
            Toast.makeText(this, "Invalid CF format", Toast.LENGTH_LONG).show()
            return false
        }

        val birthdatePattern = "^\\d{2}/\\d{2}/\\d{4}$"
        if (!Pattern.matches(birthdatePattern, birthdate)) {
            Toast.makeText(this, "Invalid birthdate format (use dd/mm/yyyy)", Toast.LENGTH_LONG).show()
            return false
        }

        val passwordPattern = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$"
        if (!Pattern.matches(passwordPattern, password)) {
            Toast.makeText(this, "Password must be at least 8 characters, with uppercase, lowercase, number, and one of [@#\$%^&+=!]", Toast.LENGTH_LONG).show()
            return false
        }

        return true
    }

    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun registerWorker(
        email: String,
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        cf: String,
        hashedPassword: String
    ) {
        val originalFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val targetFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formattedBirthdate = try {
            val date = originalFormat.parse(birthdate)
            targetFormat.format(date)
        } catch (e: Exception) {
            Log.e("RegistrationActivity", "Date parsing error: ${e.message}")
            birthdate
        }

        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/create"
        val json = JSONObject()
        json.put("FirstName", firstName)
        json.put("LastName", lastName)
        json.put("BirthDate", formattedBirthdate)
        json.put("PhoneNumber", phone)
        json.put("CF", cf)
        json.put("Password", hashedPassword)
        json.put("Presence", "no")
        json.put("Email", email)

        val requestBody =
            json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("RegistrationActivity", "Network error: ${e.message}")
                        runOnUiThread {
                            val errorMessage = e.localizedMessage ?: "Unknown error occurred"
                            Toast.makeText(this@RegistrationActivity, "Failed to register: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseBody = response.body?.string()
                        runOnUiThread {
                            if (response.isSuccessful) {
                                Toast.makeText(this@RegistrationActivity, "Account created successfully!", Toast.LENGTH_LONG).show()
                                val intent = Intent(this@RegistrationActivity, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            } else {
                                val errorMessage = responseBody ?: "Unknown error"
                                Log.e("RegistrationActivity", "Server response: $errorMessage")

                                val jsonResponse = try {
                                    JSONObject(responseBody ?: "")
                                } catch (e: Exception) {
                                    null
                                }

                                val error = jsonResponse?.optString("error", "Unknown error")

                                if (response.code == 409 && error?.contains("already exists") == true) {
                                    Toast.makeText(this@RegistrationActivity, error, Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this@RegistrationActivity, "Failed to register: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e("RegistrationActivity", "Error during registration: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@RegistrationActivity, "An unexpected error occurred.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
