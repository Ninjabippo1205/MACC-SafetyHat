package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class VerifyCodeActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var CF: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_verification_code)

            val enteredCodeEditText = findViewById<EditText>(R.id.enteredCodeEditText)
            val verifyButton = findViewById<Button>(R.id.verifyButton)

            val backButton = findViewById<ImageView>(R.id.back_icon_login)
            backButton.setOnClickListener {
                val intent = Intent(this, ForgotPasswordActivity::class.java)
                startActivity(intent)
                finish()
            }

            CF = intent.getStringExtra("CF") ?: ""
            if (CF.isEmpty()) {
                Toast.makeText(this, "CF is missing.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            verifyButton.setOnClickListener {
                val enteredCode = enteredCodeEditText.text.toString()

                if (enteredCode.isEmpty()) {
                    Toast.makeText(this, "Please enter a valid code.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Send API request to verify the code
                verifyResetCode(enteredCode)
            }
        } catch (e: Exception) {
            Log.e("VerifyCodeActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        super.onBackPressed()
        navigateBack()
    }

    private fun navigateBack() {
        val intent = Intent(this, ForgotPasswordActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
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
                Log.e("VerifyCodeActivity", "API request error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@VerifyCodeActivity,
                        "Network error. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (response.isSuccessful) {
                    try {
                        val jsonResponse = JSONObject(responseBody ?: "{}")
                        val status = jsonResponse.optString("status")
                        val message = jsonResponse.optString("message")

                        runOnUiThread {
                            Toast.makeText(this@VerifyCodeActivity, message, Toast.LENGTH_SHORT)
                                .show()
                        }

                        if (status == "success") {
                            // Navigate to the ResetPasswordActivity if the code is valid
                            navigateToResetPassword()
                        }
                    } catch (e: JSONException) {
                        Log.e("VerifyCodeActivity", "Error parsing API response: ${e.message}")
                        runOnUiThread {
                            Toast.makeText(
                                this@VerifyCodeActivity,
                                "Error processing response.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    // Handle API response errors
                    Log.e("VerifyCodeActivity", "Invalid response: ${response.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@VerifyCodeActivity,
                            "Invalid code. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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
