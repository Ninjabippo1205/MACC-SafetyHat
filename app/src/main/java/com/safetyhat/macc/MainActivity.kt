package com.safetyhat.macc

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        // Imposta padding per tenere conto delle barre di sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // TaskDescription personalizzata con nome, icona e colore di sfondo
        val taskDescription = ActivityManager.TaskDescription.Builder()
            .setLabel("SafetyHat")  // Nome dell'app per la miniatura
            .setIcon(R.mipmap.safety_hat_foreground)  // Icona della miniatura (resource ID)
            .setPrimaryColor(getColor(R.color.miniature_background))  // Colore di sfondo della miniatura
            .build()

        window.statusBarColor = getColor(R.color.status_bar_color)
        setTaskDescription(taskDescription)

        // Imposta il listener per il bottone "Login"
        val loginButton = findViewById<Button>(R.id.signInButton)
        loginButton.setOnClickListener {
            // Crea l'intent per andare a LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

        val registerButton = findViewById<Button>(R.id.registerButton)
        registerButton.setOnClickListener {
            // Crea l'intent per andare a ManagerInfoActivity
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
