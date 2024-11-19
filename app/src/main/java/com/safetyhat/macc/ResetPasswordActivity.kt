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
import org.mindrot.jbcrypt.BCrypt
import java.io.IOException

class ResetPasswordActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var CF: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        CF = intent.getStringExtra("CF").toString()

        val newPasswordEditText = findViewById<EditText>(R.id.newPasswordEditText)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordEditText)
        val resetPasswordButton = findViewById<Button>(R.id.resetPasswordButton)

        resetPasswordButton.setOnClickListener {
            val newPassword = newPasswordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                showToast("All fields are required.")
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                showToast("Passwords do not match.")
                return@setOnClickListener
            }

            if (!isPasswordValid(newPassword)) {
                showToast("Password must be at least 8 characters long, and include uppercase, lowercase, number, and special character.")
                return@setOnClickListener
            }

            // Hash the password using BCrypt
            val hashedPassword = hashPassword(newPassword)
            Log.i("ResetPassword", "Hashed password: $hashedPassword")

            // Send the hashed password to the server
            sendPasswordToServer(CF, hashedPassword)
        }
    }

    /**
     * Validates the password according to the specified criteria
     */
    private fun isPasswordValid(password: String): Boolean {
        val passwordPattern = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$"
        return password.matches(Regex(passwordPattern))
    }

    /**
     * Hashes the password using BCrypt
     */
    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    /**
     * Sends the hashed password to the server to reset the user's password
     */
    private fun sendPasswordToServer(cf: String, hashedPassword: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/updatepassword/$cf"
        val json = JSONObject().apply {
            put("Password", hashedPassword)
        }
        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        showToast("Error: ${e.localizedMessage ?: "Unknown error occurred"}")
                        Log.e("ResetPassword", "Error sending request: ${e.localizedMessage}", e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful) {
                        runOnUiThread {
                            showToast("Password reset successfully!")
                            navigateToLogin()
                        }
                        Log.i("ResetPassword", "Password reset successful.")
                    } else {
                        runOnUiThread {
                            val errorMessage = try {
                                JSONObject(responseBody ?: "{}").optString("error", "Unknown error")
                            } catch (e: Exception) {
                                "Unknown error"
                            }
                            showToast("Failed to reset password: $errorMessage")
                            Log.e("ResetPassword", "Error resetting password: $errorMessage")
                        }
                    }
                }
            })
        }
    }

    /**
     * Navigates to the Login Activity
     */
    private fun navigateToLogin() {
        val intent = Intent(this@ResetPasswordActivity, LoginActivity::class.java)
        startActivity(intent)
        finish() // Ensure the user cannot return to the reset password screen
    }

    /**
     * Shows a Toast message
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
