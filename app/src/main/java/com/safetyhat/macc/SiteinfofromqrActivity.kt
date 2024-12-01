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

class SiteinfofromqrActivity : AppCompatActivity() {
    private lateinit var inserted_sc: EditText
    private lateinit var verifyButton: Button
    private val client = OkHttpClient()
    private var workerCF: String = ""
    private var siteID: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_site_info_from_qr)

            findViewById<ImageView>(R.id.back_icon).setOnClickListener {
                val intent = Intent(this, QrScanningActivity::class.java)
                startActivity(intent)
                finish()
            }

            val jsonStringID = intent.getStringExtra("qr_scanned_text")
            if (jsonStringID != null) {
                try {
                    val jsonObjectID = JSONObject(jsonStringID)
                    siteID = jsonObjectID.getInt("ID")
                } catch (e: JSONException) {
                    Log.e("SiteinfofromqrActivity", "Invalid QR code data: ${e.message}")
                    Toast.makeText(this, "Invalid QR code data", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
            } else {
                Toast.makeText(this, "No QR code data found", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            workerCF = intent.getStringExtra("workerCF") ?: ""
            if (workerCF.isEmpty()) {
                Toast.makeText(this, "Worker CF not found", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Controllo se il worker è già associato al sito
            checkWorkerSiteAssociation(workerCF, siteID)

            findViewById<TextView>(R.id.site_id_sc_text).text = siteID.toString()

            verifyButton = findViewById(R.id.verify_sc_button)
            inserted_sc = findViewById(R.id.insert_sc_field)

            verifyButton.setOnClickListener {
                val sc = inserted_sc.text.toString()
                if (sc.isNotEmpty()) {
                    verifySecurityCode(workerCF, siteID, sc)
                } else {
                    Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("SiteinfofromqrActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred during initialization", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun verifySecurityCode(workerCF: String, siteID: Int, sc: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/site/read_security_code?id=$siteID"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SiteinfofromqrActivity", "Network error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@SiteinfofromqrActivity,
                        "Network error. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    val jsonObject = JSONObject(responseData ?: "")

                    runOnUiThread {
                        if (response.isSuccessful && !jsonObject.has("message")) {
                            if (jsonObject.has("SecurityCode")) {
                                val storedSC = jsonObject.getString("SecurityCode")
                                if (storedSC == sc) {
                                    updateWorkerSite(siteID, workerCF)
                                } else {
                                    Toast.makeText(
                                        this@SiteinfofromqrActivity,
                                        "Incorrect Security Code",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    this@SiteinfofromqrActivity,
                                    "No Security Code exists for this site",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                this@SiteinfofromqrActivity,
                                "Error on Response",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("SiteinfofromqrActivity", "JSON parsing error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@SiteinfofromqrActivity,
                            "Error processing data.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("SiteinfofromqrActivity", "Unexpected error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@SiteinfofromqrActivity,
                            "An unexpected error occurred.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun checkWorkerSiteAssociation(workerCF: String, siteID: Int) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/read?cf=$workerCF"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SiteinfofromqrActivity", "Network error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@SiteinfofromqrActivity,
                        "Failed to check worker site association.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    val jsonObject = JSONObject(responseData ?: "")

                    runOnUiThread {
                        if (response.isSuccessful && !jsonObject.has("message")) {
                            val workerSiteCode = if (jsonObject.has("SiteCode") && !jsonObject.isNull("SiteCode")) {
                                jsonObject.getInt("SiteCode")
                            } else {
                                -1
                            }

                            if (workerSiteCode == siteID) {
                                // Il worker è già associato al sito corrente
                                val intent = Intent(
                                    this@SiteinfofromqrActivity,
                                    WorkermenuActivity::class.java
                                )
                                intent.putExtra("workerCF", workerCF)
                                intent.putExtra("siteID", siteID.toString())
                                startActivity(intent)
                                finish()
                            } else {
                                // Il worker non è associato o è associato a un altro sito
                                // Richiedi l'inserimento del codice di sicurezza
                                Toast.makeText(
                                    this@SiteinfofromqrActivity,
                                    "Please enter the Security Code to associate with the site.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            // Worker non trovato o errore
                            Toast.makeText(
                                this@SiteinfofromqrActivity,
                                "Worker not found. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("SiteinfofromqrActivity", "JSON parsing error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@SiteinfofromqrActivity,
                            "Error processing data.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("SiteinfofromqrActivity", "Unexpected error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@SiteinfofromqrActivity,
                            "An unexpected error occurred.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }


    private fun updateWorkerSite(siteID: Int, workerCF: String?) {
        if (workerCF == null || workerCF.isEmpty()) {
            runOnUiThread {
                Toast.makeText(
                    this@SiteinfofromqrActivity,
                    "Worker CF is missing.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/update_sitecode/$workerCF"
        val requestBody = JSONObject()
        requestBody.put("SiteCode", siteID)
        val body = requestBody.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .put(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SiteinfofromqrActivity", "Network error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@SiteinfofromqrActivity,
                        "Failed to update site code. Try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(
                            this@SiteinfofromqrActivity,
                            "Site Code is correct",
                            Toast.LENGTH_SHORT
                        ).show()
                        val intent = Intent(
                            this@SiteinfofromqrActivity,
                            WorkermenuActivity::class.java
                        )
                        intent.putExtra("workerCF", workerCF)
                        intent.putExtra("siteID", siteID.toString())
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(
                            this@SiteinfofromqrActivity,
                            "Failed to update Site Code",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }
}
