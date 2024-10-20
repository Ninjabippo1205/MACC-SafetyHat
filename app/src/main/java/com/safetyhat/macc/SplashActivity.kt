package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Delay per mostrare la splash screen per 3 secondi
        Handler(Looper.getMainLooper()).postDelayed({
            // Dopo il delay, naviga alla MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Chiudi la SplashActivity
        }, 3000) // 3000 ms = 3 secondi
    }
}
