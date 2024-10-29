package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class ManagermenuActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_menu)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        val menuIcon = findViewById<ImageView>(R.id.Menu_icon)
        navigationView.itemIconTintList = null

        // Recupera il codice fiscale del manager
        val cf = intent.getStringExtra("managerCF")

        // Apre il drawer quando si clicca l'icona del menu
        menuIcon.setOnClickListener {
            drawerLayout.openDrawer(navigationView)
        }

        // Gestisce le selezioni delle voci del menu
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    val intent = Intent(this, ManagermenuActivity::class.java)
                    intent.putExtra("managerCF", cf)
                    startActivity(intent)
                }
                R.id.nav_account_info -> {
                    // Azione per il profilo
                    val intent = Intent(this, ManagerInfoActivity::class.java)
                    intent.putExtra("managerCF", cf)
                    startActivity(intent)
                }
                R.id.nav_sites_overview -> {
                    // Azione per le impostazioni
                    val intent = Intent(this, SitesOverviewActivity::class.java)
                    intent.putExtra("managerCF", cf)
                    startActivity(intent)
                }
                R.id.nav_create_site -> {
                    // Azione per le impostazioni
                    val intent = Intent(this, CreatesiteActivity::class.java)
                    intent.putExtra("managerCF", cf)
                    startActivity(intent)
                }
            }
            drawerLayout.closeDrawer(navigationView) // Chiude il drawer dopo la selezione
            true
        }

        val accountInfoText = findViewById<TextView>(R.id.account_info_text)
        val sitesInfoText = findViewById<TextView>(R.id.sites_info_text)
        val createSiteText = findViewById<TextView>(R.id.create_site_text)
        val logoutButton = findViewById<Button>(R.id.logout_button)

        accountInfoText.setOnClickListener {
            val intent = Intent(this, ManagerInfoActivity::class.java)
            intent.putExtra("managerCF", cf)
            startActivity(intent)
        }

        sitesInfoText.setOnClickListener {
            val intent = Intent(this, SitesOverviewActivity::class.java)
            intent.putExtra("managerCF", cf)
            startActivity(intent)
        }

        createSiteText.setOnClickListener {
            val intent = Intent(this, CreatesiteActivity::class.java)
            intent.putExtra("managerCF", cf)
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
