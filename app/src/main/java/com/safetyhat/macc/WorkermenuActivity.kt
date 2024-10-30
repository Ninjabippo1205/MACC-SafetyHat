package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class WorkermenuActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker_menu)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view_worker)
        navigationView.itemIconTintList = null

        // Apre il drawer menu al clic sull'icona di menu
        findViewById<ImageView>(R.id.worker_menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val workerCF = intent.getStringExtra("workerCF")

        // Impostazioni della navigazione tramite il menu
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home_worker -> {
                    startActivity(Intent(this, MainActivity::class.java))
                }
                R.id.nav_account_info_worker -> {
                    val intent = Intent(this, WorkerinfoActivity::class.java)
                    intent.putExtra("workerCF", workerCF)
                    startActivity(intent)
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Inizializza gli elementi dell'interfaccia e imposta i listener
        val accountInfoText = findViewById<TextView>(R.id.account_worker_info_text)
        val sitesInfoText = findViewById<TextView>(R.id.sites_worker_info_text)
        val alertsText = findViewById<TextView>(R.id.alerts_text)
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

        alertsText.setOnClickListener {
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