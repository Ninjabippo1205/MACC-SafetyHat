package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class ManagermenuActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_menu)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view_manager)
        navigationView.itemIconTintList = null

        findViewById<ImageView>(R.id.manager_menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val managerCF = intent.getStringExtra("managerCF")

        // Impostazioni della navigazione tramite il menu
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_account_info_manager -> {
                    val intent = Intent(this, ManagerInfoActivity::class.java)
                    intent.putExtra("managerCF", managerCF)
                    startActivity(intent)
                    finish()
                }
                R.id.nav_logout_manager -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Inizializza gli elementi dell'interfaccia e imposta i listener
        val sitesInfoText = findViewById<LinearLayout>(R.id.sites_manager_info_field)
        val alertsText = findViewById<LinearLayout>(R.id.alerts_field)

        sitesInfoText.setOnClickListener {
            val intent = Intent(this, SitesOverviewActivity::class.java)
            intent.putExtra("managerCF", managerCF)
            startActivity(intent)
            finish()
        }

        alertsText.setOnClickListener {
            val intent = Intent(this, CreatesiteActivity::class.java)
            intent.putExtra("managerCF", managerCF)
            startActivity(intent)
            finish()
        }
    }
}
