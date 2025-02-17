package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class ForgotPasswordActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_forgot_password)

            val cfEditText = findViewById<EditText>(R.id.cfEditText)
            val recoverButton = findViewById<Button>(R.id.recoverButton)

            val backButton = findViewById<ImageView>(R.id.back_icon_login)
            backButton.setOnClickListener {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }

            recoverButton.setOnClickListener {
                val cf = cfEditText.text.toString().trim().uppercase(Locale.getDefault())

                // Validate that the CF field is not empty and is syntactically correct
                if (cf.isEmpty()) {
                    Toast.makeText(this, "CF is required.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!isValidCF(cf)) {
                    Toast.makeText(this, "Invalid CF format.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Send the API request
                sendResetCodeRequest(cf)
            }
        } catch (e: Exception) {
            Log.e("ForgotPassword", "Error in onCreate: ${e.message}")
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
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
    /**
     * Validates the CF (Codice Fiscale)
     */
    private fun isValidCF(cf: String): Boolean {
        // Regular expression for a valid Italian CF (16 alphanumeric characters)
        val cfRegex = "^[A-Z0-9]{16}$".toRegex()
        return cf.matches(cfRegex)
    }

    /**
     * Sends the API request to update the reset code
     */
    private fun sendResetCodeRequest(cf: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/update_reset_code/$cf"

        val request = Request.Builder()
            .url(url)
            .put("".toRequestBody(null)) // Empty body for PUT request
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Gestione errore di rete
                runOnUiThread {
                    Toast.makeText(this@ForgotPasswordActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("ForgotPassword", "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // L'API ha restituito 200
                    runOnUiThread {
                        Toast.makeText(this@ForgotPasswordActivity, "Reset code generated successfully.", Toast.LENGTH_SHORT).show()
                    }

                    // Procedi alla prossima attività
                    val intent = Intent(this@ForgotPasswordActivity, VerifyCodeActivity::class.java)
                    intent.putExtra("CF", cf) // Passa il CF alla prossima attività
                    startActivity(intent)
                    finish()
                } else {
                    // L'API ha restituito un codice di errore
                    runOnUiThread {
                        val errorMessage = when (response.code) {
                            404 -> "User not found."
                            500 -> "Server error. Please try again."
                            else -> "Error: ${response.code} ${response.message}"
                        }
                        Toast.makeText(this@ForgotPasswordActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                    Log.e("ForgotPassword", "API error: ${response.code} ${response.message}")
                }
            }
        })
    }
}
