package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class VerifyCodeActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var CF: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_code)

        val enteredCodeEditText = findViewById<EditText>(R.id.enteredCodeEditText)
        val verifyButton = findViewById<Button>(R.id.verifyButton)

        CF = intent.getStringExtra("CF").toString()

        verifyButton.setOnClickListener {
            val enteredCode = enteredCodeEditText.text.toString()

            if (enteredCode.isEmpty()) {
                Toast.makeText(this, "Please enter a valid code.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Send API request to verify the code
            verifyResetCode(enteredCode)
        }
    }

    /**
     * Sends an API request to verify the reset code
     */
    private fun verifyResetCode(enteredCode: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/check_reset_code/$CF"

        // Prepare the request body with the entered code
        val jsonBody = JSONObject()
        jsonBody.put("ResetCode", enteredCode)
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@VerifyCodeActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                Log.e("VerifyCode", "API request error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (response.isSuccessful) {
                    try {
                        val jsonResponse = JSONObject(responseBody ?: "{}")
                        val status = jsonResponse.optString("status")
                        val message = jsonResponse.optString("message")

                        runOnUiThread {
                            Toast.makeText(this@VerifyCodeActivity, message, Toast.LENGTH_SHORT).show()
                        }

                        if (status == "success") {
                            // Navigate to the ResetPasswordActivity if the code is valid
                            navigateToResetPassword()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@VerifyCodeActivity, "Error parsing response.", Toast.LENGTH_SHORT).show()
                        }
                        Log.e("VerifyCode", "Error parsing API response: ${e.message}")
                    }
                } else {
                    // Handle API response errors
                    runOnUiThread {
                        Toast.makeText(this@VerifyCodeActivity, "Invalid code. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                    Log.e("VerifyCode", "Invalid response: ${response.message}")
                }
            }
        })
    }

    /**
     * Navigates to the Reset Password Activity
     */
    private fun navigateToResetPassword() {
        val intent = Intent(this@VerifyCodeActivity, ResetPasswordActivity::class.java)
        intent.putExtra("CF", CF)
        startActivity(intent)
        finish() // Finish current activity
    }
}