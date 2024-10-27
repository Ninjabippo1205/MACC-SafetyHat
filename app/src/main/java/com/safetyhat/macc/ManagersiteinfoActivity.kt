package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class ManagersiteinfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_site_info)

        //enableEdgeToEdge()

        val backButton = findViewById<ImageView>(R.id.menu_icon)
        backButton.setOnClickListener {
            val intent = Intent(this, ManagermenuActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}