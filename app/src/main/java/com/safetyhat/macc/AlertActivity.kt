package com.safetyhat.macc

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
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
    private val alertTimers = mutableMapOf<String, Runnable>()
    private val handler = Handler(Looper.getMainLooper())

    private val messageCheckRunnable = object : Runnable {
        override fun run() {
            // Recupera i nuovi messaggi dall'oggetto condiviso
            val newMessages = AlertMessageQueue.getMessages()
            newMessages.forEach { (message, iconResId) ->
                handleIncomingAlert(message, iconResId) // Gestisce i nuovi messaggi
            }
            // Ripete il controllo ogni secondo
            handler.postDelayed(this, 1000)
        }
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
        if(!isServiceRunning && fromNotification){
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

        // Avvia il controllo periodico dei messaggi
        handler.post(messageCheckRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ferma il controllo periodico dei messaggi
        handler.removeCallbacks(messageCheckRunnable)
        handler.removeCallbacksAndMessages(null) // Cancella tutti i timer
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

    private fun setAlertTimer(message: String, iconResId: Int) {
        // Rimuovi il timer esistente, se presente
        alertTimers[message]?.let { handler.removeCallbacks(it) }

        val duration = getTimerDuration(message)
        val runnable = Runnable {
            alertAdapter.removeAlert(message)
            AlertMessageQueue.removeMessage(message) // Rimuovi dalla coda
            alertTimers.remove(message)
        }

        alertTimers[message] = runnable
        handler.postDelayed(runnable, duration)

        // Aggiorna o aggiungi nella coda globale
        AlertMessageQueue.addMessage(message, iconResId, duration)
    }

    private fun handleIncomingAlert(message: String, iconResId: Int) {
        if (!alertAdapter.containsAlert(message)) {
            alertAdapter.addAlert(message, iconResId)
            setAlertTimer(message, iconResId) // Imposta il timer solo se l'alert è nuovo
        }
    }

    private fun getTimerDuration(message: String): Long {
        val durationMap = mapOf(
            "High rain:" to 3580000L,
            "High snowfall:" to 35800L,
            "High ice:" to 3580000L,
            "High wind speed:" to 3580000L,
            "High temperature:" to 3580000L,
            "Low temperature:" to 3580000L,
            "High UV index:" to 3580000L,
            "High noise level detected" to 3580000L,
            "Fall detected" to 119000L,
            "You are in site" to 59000L
        )
        return durationMap.entries.firstOrNull { message.startsWith(it.key) }?.value ?: 30000L
    }

    inner class AlertAdapter(private val alertList: MutableList<Pair<String, Int>>) :
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
            val (message, imageResId) = alertList[position]
            holder.alertTextView.text = message
            holder.alertIcon.setImageResource(imageResId)
        }

        override fun getItemCount(): Int = alertList.size

        fun containsAlert(alertMessage: String): Boolean {
            return alertList.any { it.first == alertMessage }
        }

        fun addAlert(alertMessage: String, imageResId: Int) {
            if (alertList.none { it.first == alertMessage }) {
                alertList.add(alertMessage to imageResId)
                notifyItemInserted(alertList.size - 1)
            }
        }

        fun removeAlert(alertMessage: String) {
            val index = alertList.indexOfFirst { it.first == alertMessage }
            if (index != -1) {
                alertList.removeAt(index)
                notifyItemRemoved(index)
            }
        }
    }
}

object AlertMessageQueue {
    private val messageQueue = mutableMapOf<String, Pair<Int, Runnable>>()
    private val handler = Handler(Looper.getMainLooper()) // Handler globale

    @Synchronized
    fun addMessage(message: String, iconResId: Int, duration: Long) {
        // Controlla se il messaggio esiste già nella coda
        if (messageQueue.containsKey(message)) {
            return // Non aggiungere duplicati
        }

        val runnable = Runnable {
            removeMessage(message) // Rimuovi il messaggio al termine del timer
        }

        // Aggiungi il messaggio e il runnable alla coda
        messageQueue[message] = iconResId to runnable
        handler.postDelayed(runnable, duration)
    }

    @Synchronized
    fun removeMessage(message: String) {
        messageQueue[message]?.second?.let { runnable ->
            handler.removeCallbacks(runnable) // Rimuovi il runnable
        }
        messageQueue.remove(message)
    }

    @Synchronized
    fun getMessages(): List<Pair<String, Int>> {
        return messageQueue.map { it.key to it.value.first }
    }
}
