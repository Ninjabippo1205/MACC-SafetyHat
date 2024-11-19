package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class ForgotPasswordActivity : AppCompatActivity() {

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val cfEditText = findViewById<EditText>(R.id.cfEditText)
        val recoverButton = findViewById<Button>(R.id.recoverButton)

        recoverButton.setOnClickListener {
            val cf = cfEditText.text.toString().trim()

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
    }

    /**
     * Validates the CF (Codice Fiscale)
     */
    //TODO: update the regex with the already existing one
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
                // Handle errors in case the request fails
                runOnUiThread {
                    Toast.makeText(this@ForgotPasswordActivity, "Error sending API request: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("ForgotPassword", "API request error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val responseData = response.body?.string()

                        val intent = Intent(this@ForgotPasswordActivity, VerifyCodeActivity::class.java)
                        intent.putExtra("CF", cf) // Pass the CF to the next activity
                        startActivity(intent)
                        finish()
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@ForgotPasswordActivity, "Error parsing API response.", Toast.LENGTH_SHORT).show()
                        }
                        Log.e("ForgotPassword", "Error parsing API response: ${e.message}")
                    }
                } else {
                    // Handle errors for unsuccessful responses
                    runOnUiThread {
                        Toast.makeText(this@ForgotPasswordActivity, "Error: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                    Log.e("ForgotPassword", "Error in response: ${response.message}")
                }
            }
        })
    }
}