package com.safetyhat.macc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.budiyev.android.codescanner.*
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class QrScanningActivity : AppCompatActivity() {
    private lateinit var codeScanner: CodeScanner
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_qr_scanning)

            findViewById<ImageView>(R.id.back_button_icon).setOnClickListener {
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }

            val workerCF = intent.getStringExtra("workerCF") ?: ""

            // Check for camera permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
/*
                val intent = Intent(this@QrScanningActivity, WorkermenuActivity::class.java)
                intent.putExtra("workerCF", workerCF)
                intent.putExtra("siteID", "64")
                startActivity(intent)
                finish()
*/
                initializeScanner(workerCF)
            } else {
                Toast.makeText(this, "Camera permission not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Log.e("QrScanningActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // Initialize the CodeScanner
    private fun initializeScanner(workerCF: String) {
        if (::codeScanner.isInitialized) {
            codeScanner.startPreview() // Riavvia solo il preview se già inizializzato
            return
        }

        val scannerView = findViewById<CodeScannerView>(R.id.scanner_view)
        codeScanner = CodeScanner(this, scannerView)

        // Configura CodeScanner
        codeScanner.camera = CodeScanner.CAMERA_BACK
        codeScanner.formats = CodeScanner.ALL_FORMATS
        codeScanner.autoFocusMode = AutoFocusMode.SAFE
        codeScanner.scanMode = ScanMode.SINGLE
        codeScanner.isAutoFocusEnabled = true
        codeScanner.isFlashEnabled = false

        codeScanner.decodeCallback = DecodeCallback {
            runOnUiThread {
                releaseScanner()
                handleScannedData(it.text, workerCF)
            }
        }

        codeScanner.errorCallback = ErrorCallback {
            runOnUiThread {
                Log.e("QrScanningActivity", "Camera initialization error: ${it.message}")
                Toast.makeText(this, "Camera initialization error: ${it.message}", Toast.LENGTH_LONG).show()
                releaseScanner()
                finish()
            }
        }

        // Start scanning on click
        scannerView.setOnClickListener {
            codeScanner.startPreview()
        }

        codeScanner.startPreview()
    }

    private fun handleScannedData(scannedText: String, workerCF: String) {
        try {
            val jsonObjectID = JSONObject(scannedText)
            val siteID = jsonObjectID.getInt("ID")
            getCurrentWorkerSite(workerCF, siteID, scannedText)
        } catch (e: JSONException) {
            Log.e("QrScanningActivity", "Error parsing QR code: ${e.message}")
            Toast.makeText(this, "Invalid QR code format.", Toast.LENGTH_SHORT).show()
            initializeScanner(workerCF) // Restart scanning
        }
    }

    private fun getCurrentWorkerSite(workerCF: String, siteID: Int, scannedText: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/read?cf=$workerCF"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("QrScanningActivity", "Network error: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@QrScanningActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                    initializeScanner(workerCF) // Restart scanner
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseData = response.body?.string()
                    val jsonObject = JSONObject(responseData ?: "")

                    runOnUiThread {
                        if (response.isSuccessful && !jsonObject.has("message")) {
                            var currentSiteID = jsonObject.optString("SiteCode", "")

                            if (currentSiteID == "null") {
                                currentSiteID = "-1"
                            }

                            if (currentSiteID.isNotEmpty() && currentSiteID.toInt() == siteID) {
                                val intent = Intent(this@QrScanningActivity, WorkermenuActivity::class.java)
                                intent.putExtra("workerCF", workerCF)
                                intent.putExtra("siteID", siteID.toString())
                                startActivity(intent)
                                finish()
                            } else {
                                val intent = Intent(this@QrScanningActivity, SiteinfofromqrActivity::class.java)
                                intent.putExtra("qr_scanned_text", scannedText)
                                intent.putExtra("workerCF", workerCF)
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            Toast.makeText(this@QrScanningActivity, "Worker not found. Please try again.", Toast.LENGTH_SHORT).show()
                            initializeScanner(workerCF) // Restart scanner
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("QrScanningActivity", "Error parsing response: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@QrScanningActivity, "Error processing data.", Toast.LENGTH_SHORT).show()
                        initializeScanner(workerCF) // Restart scanner
                    }
                } catch (e: Exception) {
                    Log.e("QrScanningActivity", "Unexpected error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@QrScanningActivity, "An unexpected error occurred.", Toast.LENGTH_SHORT).show()
                        initializeScanner(workerCF) // Restart scanner
                    }
                }
            }
        })
    }

    // Release camera resources
    private fun releaseScanner() {
        if (::codeScanner.isInitialized) {
            codeScanner.releaseResources() // Rilascia le risorse in modo sicuro
        }
    }

    override fun onResume() {
        super.onResume()
        if (::codeScanner.isInitialized) {
            codeScanner.startPreview()
        }

        // Optional: Check if camera permission is still granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission has been revoked.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        releaseScanner()
        super.onPause()
    }
}
