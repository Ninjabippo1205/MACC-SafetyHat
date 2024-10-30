package com.safetyhat.macc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import android.widget.Button
import android.widget.TextView
import android.content.Intent

class WorkermenuActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_menu)

        val workerCF = intent.getStringExtra("workerCF")

        val accountInfoText = findViewById<TextView>(R.id.account_worker_info_text)
        val sitesInfoText = findViewById<TextView>(R.id.sites_worker_info_text)
        val createSiteText = findViewById<TextView>(R.id.alerts_text)
        val logoutButton = findViewById<Button>(R.id.logout_button_worker)

        accountInfoText.setOnClickListener {
            val intent = Intent(this, WorkerinfoActivity::class.java)
            intent.putExtra("workerCF", workerCF)
            startActivity(intent)
        }

        sitesInfoText.setOnClickListener {
            val intent = Intent(this, SiteInfoActivity::class.java)
            intent.putExtra("workerCF", workerCF)
            startActivity(intent)
        }

        createSiteText.setOnClickListener {
            val intent = Intent(this, AlertActivity::class.java)
            intent.putExtra("workerCF", workerCF)
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
