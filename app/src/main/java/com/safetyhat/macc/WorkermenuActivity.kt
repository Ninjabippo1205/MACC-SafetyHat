package com.safetyhat.macc

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class WorkermenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_worker_menu)

        val qr_scanned_text = intent.getStringExtra("qr_scanned_text")
        Toast.makeText(this, "Scan result on WorkermenuActivity: ${qr_scanned_text}", Toast.LENGTH_LONG).show()

    }
}