package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    private val splashDelay: Long = 3000 // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_splash)

            // Delay to show the splash screen for 3 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    // After the delay, navigate to MainActivity
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish() // Close the SplashActivity
                } catch (e: Exception) {
                    Log.e("SplashActivity", "Error starting MainActivity: ${e.message}")
                    // Optionally, you can show a Toast or handle the error
                }
            }, splashDelay)
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error in onCreate: ${e.message}")
            // Optionally, you can show a Toast or handle the error
        }
    }
}
