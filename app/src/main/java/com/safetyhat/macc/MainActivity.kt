package com.safetyhat.macc

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    // Define the permissions the app requires
    private val foregroundPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,               // Microphone
        Manifest.permission.CAMERA,                     // Camera
        Manifest.permission.ACCESS_FINE_LOCATION,       // Precise location
        Manifest.permission.POST_NOTIFICATIONS,          // Notifications (Android 13+)
        Manifest.permission.SEND_SMS
    )

    // Register the ActivityResultLauncher to request foreground permissions
    private val requestForegroundPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allForegroundGranted = true
        permissions.entries.forEach {
            if (!it.value) {
                allForegroundGranted = false
                Log.d("PermissionCheck", "Permission denied: ${getPermissionFriendlyName(it.key)}")
            }
        }
        if(!allForegroundGranted){
            Toast.makeText(
                this,
                "Some permissions were not granted. Some features may not be available.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Log.d("LayoutCheck", "Landscape layout loaded")
            } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                Log.d("LayoutCheck", "Portrait layout loaded")
            }

            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            // Custom TaskDescription with name, icon, and background color
            val taskDescription = ActivityManager.TaskDescription.Builder()
                .setLabel("SafetyHat")  // App name for the thumbnail
                .setIcon(R.mipmap.safety_hat_foreground)  // Thumbnail icon (resource ID)
                .setPrimaryColor(getColor(R.color.miniature_background))  // Thumbnail background color
                .build()

            window.statusBarColor = getColor(R.color.status_bar_color)
            setTaskDescription(taskDescription)

            // Set listener for the "Login" button
            findViewById<Button>(R.id.signInButton).setOnClickListener {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }

            findViewById<Button>(R.id.registerButton).setOnClickListener {
                val intent = Intent(this, RegistrationActivity::class.java)
                startActivity(intent)
                finish()
            }

            // Check and request necessary permissions
            checkAndRequestPermissions()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred during initialization.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Checks if foreground permissions are granted. If not, it requests them.
     */
    private fun checkAndRequestPermissions() {
        val foregroundPermissionsToRequest = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (foregroundPermissionsToRequest.isNotEmpty()) {
            requestForegroundPermissionsLauncher.launch(foregroundPermissionsToRequest.toTypedArray())
        }
    }

    /**
     * Returns a more readable name for permissions.
     */
    private fun getPermissionFriendlyName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            Manifest.permission.SEND_SMS -> "Send SMS"
            else -> permission
        }
    }
}
