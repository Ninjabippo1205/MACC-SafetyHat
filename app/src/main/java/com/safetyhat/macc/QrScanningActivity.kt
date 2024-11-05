package com.safetyhat.macc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
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

class QrScanningActivity : AppCompatActivity() {
    private lateinit var codeScanner: CodeScanner
    private val CAMERA_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanning)

        findViewById<ImageView>(R.id.back_button_icon).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        val workerCF = intent.getStringExtra("workerCF")
        val intent = Intent(this@QrScanningActivity, SiteinfofromqrActivity::class.java)
        intent.putExtra("qr_scanned_text", "{'ID'=34}")
        intent.putExtra("workerCF", workerCF)
        startActivity(intent)
        finish()

        // Verifica e richiesta del permesso fotocamera
        /*if (checkCameraPermission()) {
            initializeScanner()
        }*/
    }

    // Funzione per controllare e richiedere il permesso della fotocamera
    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_CODE)
            false
        } else {
            true
        }
    }

    // Inizializza il CodeScanner dopo che i permessi sono stati concessi
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
                val intent = Intent(this@QrScanningActivity, SiteinfofromqrActivity::class.java)
                intent.putExtra("qr_scanned_text", it.text)
                intent.putExtra("workerCF", workerCF)
                startActivity(intent)
                finish()
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

    // Rilascia risorse della fotocamera
    private fun releaseScanner() {
        if (::codeScanner.isInitialized) {
            codeScanner.releaseResources() // Ferma l'anteprima e rilascia definitivamente le risorse
        }
    }

    // Callback per il risultato della richiesta permessi
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                initializeScanner() // Inizializza CodeScanner dopo aver ottenuto il permesso
            } else {
                Toast.makeText(this, "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::codeScanner.isInitialized) {
            codeScanner.startPreview()
        }
    }

    override fun onPause() {
        releaseScanner()
        super.onPause()
    }
}