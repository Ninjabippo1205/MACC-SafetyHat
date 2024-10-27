package com.safetyhat.macc

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView

class ManagermenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_menu)

        //enableEdgeToEdge()

        val sitesInfoText = findViewById<TextView>(R.id.sites_info_text)
        val accountInfoText = findViewById<TextView>(R.id.account_info_text)
        val createSiteText = findViewById<TextView>(R.id.create_site_text)
        val logoutButton = findViewById<Button>(R.id.logout_button)

        val cf = intent.getStringExtra("managerCF")

        sitesInfoText.setOnClickListener {
            val intent = Intent(this, ManagersiteinfoActivity::class.java)
            startActivity(intent)
        }

        accountInfoText.setOnClickListener {
            val intent = Intent(this, ManagerInfoActivity::class.java)
            intent.putExtra("managerCF", cf)
            startActivity(intent)
        }

        createSiteText.setOnClickListener {
            val intent = Intent(this, CreatesiteActivity::class.java)
            startActivity(intent)
        }

        logoutButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

    }
}
