package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import org.mindrot.jbcrypt.BCrypt
import java.io.IOException

class LoginActivity : AppCompatActivity() {
    private lateinit var cfLogin: EditText
    private lateinit var passwordLogin: EditText
    private lateinit var loginButton: Button
    private lateinit var forgotPassword: Button

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_login)

            cfLogin = findViewById(R.id.CFLogin)
            passwordLogin = findViewById(R.id.passwordLogin)
            loginButton = findViewById(R.id.loginButton)
            forgotPassword = findViewById(R.id.forgotPassword)

            val backButton = findViewById<ImageView>(R.id.back_icon_login)
            backButton.setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }

            forgotPassword.setOnClickListener {
                val intent = Intent(this, ForgotPasswordActivity::class.java)
                startActivity(intent)
                finish()
            }

            loginButton.setOnClickListener {
                val intent = Intent(this, ArMeasureActivity::class.java)
                startActivity(intent)
                finish()
/*
                val cf = cfLogin.text.toString().uppercase()
                val password = passwordLogin.text.toString()
                if (cf.isNotEmpty() && password.isNotEmpty()) {
                    verifyManager(cf, password)
                } else {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                }
 */
            }
        } catch (e: Exception) {
            Log.e("LoginActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred during initialization.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun verifyManager(cf: String, password: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/manager/read?cf=$cf"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LoginActivity", "Network error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    if (response.isSuccessful && responseData != null) {
                        val jsonObject = JSONObject(responseData)
                        runOnUiThread {
                            if (!jsonObject.has("message")) {
                                if (jsonObject.has("Password")) {
                                    val storedPasswordHash = jsonObject.getString("Password")
                                    if (BCrypt.checkpw(password, storedPasswordHash)) {
                                        val intent = Intent(this@LoginActivity, ManagermenuActivity::class.java)
                                        intent.putExtra("managerCF", cf)
                                        startActivity(intent)
                                        finish()
                                    } else {
                                        Toast.makeText(this@LoginActivity, "Incorrect password for manager", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(this@LoginActivity, "Password field missing for manager", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else if (response.code == 404) {
                        // Manager not found, check worker
                        verifyWorker(cf, password)
                    } else {
                        Log.e("LoginActivity", "Unexpected response: ${response}")
                        runOnUiThread {
                            Toast.makeText(this@LoginActivity, "An error occurred. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("LoginActivity", "JSON parsing error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Error parsing server response.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Error in verifyManager: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "An unexpected error occurred.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun verifyWorker(cf: String, password: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/read?cf=$cf"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("LoginActivity", "Network error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    if (responseData != null && response.isSuccessful) {
                        val jsonObject = JSONObject(responseData)
                        runOnUiThread {
                            if (!jsonObject.has("message")) {
                                if (jsonObject.has("Password")) {
                                    val storedPasswordHash = jsonObject.getString("Password")
                                    if (BCrypt.checkpw(password, storedPasswordHash)) {
                                        val intent = Intent(this@LoginActivity, QrScanningActivity::class.java)
                                        intent.putExtra("workerCF", cf)
                                        startActivity(intent)
                                        finish()
                                    } else {
                                        Toast.makeText(this@LoginActivity, "Incorrect password for worker", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(this@LoginActivity, "Password field missing for worker", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Se non trovato tra i worker, mostra un messaggio di errore
                                runOnUiThread {
                                    Toast.makeText(this@LoginActivity, "No account found with provided CF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Log.e("LoginActivity", "Invalid response from server")
                        runOnUiThread {
                            Toast.makeText(this@LoginActivity, "An error occurred. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("LoginActivity", "JSON parsing error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Error parsing server response.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("LoginActivity", "Error in verifyWorker: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "An unexpected error occurred.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
