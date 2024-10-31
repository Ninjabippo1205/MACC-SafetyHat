package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.mindrot.jbcrypt.BCrypt
import java.io.IOException

class SiteinfofromqrActivity : AppCompatActivity(){
    private lateinit var inserted_sc: EditText
    private lateinit var verifyButton: Button
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_site_info_from_qr)

        findViewById<ImageView>(R.id.back_icon).setOnClickListener {
            val intent = Intent(this, QrScanningActivity::class.java)
            startActivity(intent)
            finish()
        }

        val jsonStringID = intent.getStringExtra("qr_scanned_text")
        val jsonObjectID = JSONObject(jsonStringID)
        val siteID = jsonObjectID.getInt("ID")
        val workerCF = intent.getStringExtra("workerCF")

        findViewById<TextView>(R.id.site_id_sc_text).text = siteID.toString()

        verifyButton = findViewById(R.id.verify_sc_button)
        inserted_sc = findViewById(R.id.insert_sc_field)

        verifyButton.setOnClickListener {
            val sc = inserted_sc.text.toString()
            if (sc.isNotEmpty()) {
                verifySecurityCode(workerCF.toString(), siteID, sc)
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun verifySecurityCode(workerCF: String, siteID: Int, sc: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/site/read_security_code?id=$siteID"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SiteinfofromqrActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData)

                runOnUiThread {
                    if (response.isSuccessful && !jsonObject.has("message")) {
                        if (jsonObject.has("SecurityCode")) {
                            val storedSC = jsonObject.getString("SecurityCode")
                            if (storedSC == sc) {
                                updateWorkerSite(siteID, workerCF)
                                val intent = Intent(this@SiteinfofromqrActivity, WorkermenuActivity::class.java)
                                intent.putExtra("workerCF", workerCF)
                                startActivity(intent)
                                finish()
                            } else {
                                Toast.makeText(this@SiteinfofromqrActivity, "Incorrect Security Code", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@SiteinfofromqrActivity, "No Security Code exists for this site", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@SiteinfofromqrActivity, "Error on Response", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

    }

    private fun updateWorkerSite(siteID: Int, workerCF: String?) {
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
                runOnUiThread {
                    Toast.makeText(this@SiteinfofromqrActivity, "Failed to update site code. Try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SiteinfofromqrActivity, "Site Code is correct", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@SiteinfofromqrActivity, "Failed to update Site Code", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}