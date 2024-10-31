package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class SiteInfoActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_site_info)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view_worker)
        navigationView.itemIconTintList = null

        findViewById<ImageView>(R.id.menu_icon_worker).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val workerCF = intent.getStringExtra("workerCF")

        // Impostazioni della navigazione tramite il menu
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home_worker -> {
                    val intent = Intent(this, WorkermenuActivity::class.java)
                    intent.putExtra("workerCF", workerCF)
                    startActivity(intent)
                    finish()
                }
                R.id.nav_account_info_worker -> {
                    val intent = Intent(this, WorkerinfoActivity::class.java)
                    intent.putExtra("workerCF", workerCF)
                    startActivity(intent)
                    finish()
                }
                R.id.nav_logout_worker -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        // Imposta i dati nei campi di testo
        findViewById<TextView>(R.id.site_id_text).text = "12345"
        findViewById<TextView>(R.id.start_date_text).text = "01/01/2023"
        findViewById<TextView>(R.id.end_date_text).text = "31/12/2023"
        findViewById<TextView>(R.id.total_worker_text).text = "10"
        findViewById<TextView>(R.id.scaffolding_number_text).text = "ABC123"
        findViewById<TextView>(R.id.site_address_text).text = "123 Main St"
        findViewById<TextView>(R.id.site_radius_text).text = "100.0"
        findViewById<TextView>(R.id.site_manager_info_text).text = "John Doe"
    }
}
