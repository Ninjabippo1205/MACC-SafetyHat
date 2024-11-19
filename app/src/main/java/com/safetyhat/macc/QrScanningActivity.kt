package com.safetyhat.macc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.budiyev.android.codescanner.AutoFocusMode
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class QrScanningActivity : AppCompatActivity() {
    private lateinit var codeScanner: CodeScanner
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanning)

        findViewById<ImageView>(R.id.back_button_icon).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        val workerCF = intent.getStringExtra("workerCF")

        val intent = Intent(this@QrScanningActivity, WorkermenuActivity::class.java)
        intent.putExtra("workerCF", workerCF)
        intent.putExtra("siteID", "64")
        startActivity(intent)
        finish()

        // Inizializza lo scanner direttamente, assumendo che il permesso sia già stato concesso
        //initializeScanner()
    }

    // Inizializza il CodeScanner
    private fun initializeScanner() {
        val scannerView = findViewById<CodeScannerView>(R.id.scanner_view)
        codeScanner = CodeScanner(this, scannerView)
        val workerCF = intent.getStringExtra("workerCF")

        // Configurazione dei parametri CodeScanner
        codeScanner.camera = CodeScanner.CAMERA_BACK
        codeScanner.formats = CodeScanner.ALL_FORMATS
        codeScanner.autoFocusMode = AutoFocusMode.SAFE
        codeScanner.scanMode = ScanMode.SINGLE
        codeScanner.isAutoFocusEnabled = true
        codeScanner.isFlashEnabled = false

        // Callback per la scansione e gestione errori
        codeScanner.decodeCallback = DecodeCallback {
            runOnUiThread {
                // Chiudi la fotocamera
                releaseScanner()
                val jsonObjectID = JSONObject(it.text)
                val siteID = jsonObjectID.getInt("ID")
                getCurrentWorkerSite(workerCF.toString(), siteID, it.text)
            }
        }

        codeScanner.errorCallback = ErrorCallback {
            runOnUiThread {
                Toast.makeText(this, "Camera initialization error: ${it.message}", Toast.LENGTH_LONG).show()
                releaseScanner() // Chiudi la fotocamera in caso di errore
            }
        }

        // Inizia la scansione al click
        scannerView.setOnClickListener {
            codeScanner.startPreview()
        }
    }

    private fun getCurrentWorkerSite(workerCF: String, siteID: Int, scannedText: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/read?cf=$workerCF"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@QrScanningActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData ?: "")

                runOnUiThread {
                    if (response.isSuccessful && !jsonObject.has("message")) {
                        val currentSiteID = jsonObject.optString("SiteCode", "")
                        if(currentSiteID != "" ){
                            val intent = Intent(this@QrScanningActivity, SiteinfofromqrActivity::class.java)
                            intent.putExtra("qr_scanned_text", scannedText)
                            intent.putExtra("workerCF", workerCF)
                            startActivity(intent)
                            finish()
                        }
                        if (currentSiteID.toInt() == siteID) {
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
                        Toast.makeText(this@QrScanningActivity, "Worker not found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // Rilascia risorse della fotocamera
    private fun releaseScanner() {
        if (::codeScanner.isInitialized) {
            codeScanner.releaseResources() // Ferma l'anteprima e rilascia definitivamente le risorse
        }
    }

    override fun onResume() {
        super.onResume()
        if (::codeScanner.isInitialized) {
            codeScanner.startPreview()
        }

        // Opzionale: Verifica se il permesso della fotocamera è ancora concesso
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission has been revoked.", Toast.LENGTH_SHORT).show()
            // Puoi decidere di terminare l'attività o disabilitare funzionalità specifiche
            finish()
        }
    }

    override fun onPause() {
        releaseScanner()
        super.onPause()
    }
}