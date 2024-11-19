package com.safetyhat.macc

import android.app.NotificationManager
import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView

class AlertActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var alertAdapter: AlertAdapter
    private var alertService: AlertService? = null
    private val handler = Handler(Looper.getMainLooper())
    private val displayedAlertIds = mutableSetOf<String>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AlertService.LocalBinder
            alertService = binder.getService()
            loadAlerts()
            startAlertMonitoring()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            alertService = null
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, AlertService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
        stopAlertMonitoring()
    }

    private fun loadAlerts() {
        alertService?.getCurrentAlerts()?.forEach { alertData ->
            if (!displayedAlertIds.contains(alertData.id)) {
                alertAdapter.addAlert(alertData)
                displayedAlertIds.add(alertData.id)
            }
        }
    }

    private val alertCheckRunnable = object : Runnable {
        override fun run() {
            val currentAlerts = alertService?.getCurrentAlerts() ?: emptyList()
            val currentAlertIds = currentAlerts.map { it.id }.toSet()

            // Aggiungi nuovi alert
            currentAlerts.forEach { alertData ->
                if (!displayedAlertIds.contains(alertData.id)) {
                    alertAdapter.addAlert(alertData)
                    displayedAlertIds.add(alertData.id)
                }
            }

            // Rimuovi alert scaduti
            val iterator = displayedAlertIds.iterator()
            while (iterator.hasNext()) {
                val alertId = iterator.next()
                if (!currentAlertIds.contains(alertId)) {
                    alertAdapter.removeAlertById(alertId)
                    iterator.remove()
                }
            }

            handler.postDelayed(this, 1000)
        }
    }

    private fun startAlertMonitoring() {
        handler.post(alertCheckRunnable)
    }

    private fun stopAlertMonitoring() {
        handler.removeCallbacks(alertCheckRunnable)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert)

        // Imposta il menu laterale
        drawerLayout = findViewById(R.id.drawer_layout)
        val workerCF = intent.getStringExtra("workerCF")
        val siteID = intent.getStringExtra("siteID")
        val fromNotification = intent.getBooleanExtra("fromNotification", false)

        val isServiceRunning = isServiceRunning(AlertService::class.java)
        if (!isServiceRunning && fromNotification) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        navigationView = findViewById(R.id.navigation_view_worker)
        navigationView.itemIconTintList = null

        findViewById<ImageView>(R.id.worker_menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home_worker -> {
                    val intent = Intent(this, WorkermenuActivity::class.java)
                    intent.putExtra("workerCF", workerCF)
                    intent.putExtra("siteID", siteID.toString())
                    startActivity(intent)
                    finish()
                }

                R.id.nav_account_info_worker -> {
                    val intent = Intent(this, WorkerinfoActivity::class.java)
                    intent.putExtra("workerCF", workerCF)
                    intent.putExtra("siteID", siteID.toString())
                    startActivity(intent)
                    finish()
                }

                R.id.nav_site_info_worker -> {
                    val intent = Intent(this, SiteInfoActivity::class.java)
                    intent.putExtra("workerCF", workerCF)
                    intent.putExtra("siteID", siteID.toString())
                    startActivity(intent)
                    finish()
                }

                R.id.nav_logout_worker -> {
                    val stopServiceIntent = Intent(this, AlertService::class.java)
                    stopService(stopServiceIntent)

                    // Elimina tutte le notifiche usando NotificationManager
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager?.cancelAll()
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        val recyclerView = findViewById<RecyclerView>(R.id.alert_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        alertAdapter = AlertAdapter(mutableListOf())
        recyclerView.adapter = alertAdapter
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    inner class AlertAdapter(private val alertList: MutableList<AlertMessageQueue.AlertData>) :
        RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

        inner class AlertViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val alertTextView: TextView = view.findViewById(R.id.view_count_text_view)
            val alertIcon: ImageView = view.findViewById(R.id.thumbs_up_icon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.alert_item, parent, false)
            return AlertViewHolder(view)
        }

        override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
            val alertData = alertList[position]
            holder.alertTextView.text = alertData.message
            holder.alertIcon.setImageResource(alertData.iconResId)
        }

        override fun getItemCount(): Int = alertList.size

        fun addAlert(alertData: AlertMessageQueue.AlertData) {
            alertList.add(alertData)
            notifyItemInserted(alertList.size - 1)
        }

        fun removeAlertById(alertId: String) {
            val index = alertList.indexOfFirst { it.id == alertId }
            if (index != -1) {
                alertList.removeAt(index)
                notifyItemRemoved(index)
            }
        }
    }
}
