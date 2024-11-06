package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.toolbox.JsonArrayRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import android.os.Handler

class ManagermenuActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val client = OkHttpClient()

    private lateinit var communicationsRecyclerView: RecyclerView
    private lateinit var communicationsAdapter: CommunicationsAdapter
    private val communicationsList = mutableListOf<Communication>()
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

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

        fetchSites(managerCF.toString())
        val communicationTextField = findViewById<EditText>(R.id.communication_text)
        val siteIDField = findViewById<Spinner>(R.id.site_id_spinner)
        val submitButton = findViewById<Button>(R.id.send_communication_button)

        submitButton.setOnClickListener {
            val communicationText = communicationTextField.text.toString()
            val siteID = siteIDField.selectedItem.toString()

            if (communicationText.isNotBlank() && siteID.isNotBlank()) {
                createCommunication(communicationText, managerCF.toString(), siteID)
            } else {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_LONG).show()
            }
        }

        communicationsRecyclerView = findViewById(R.id.communications_recycler_view)
        communicationsRecyclerView.layoutManager = LinearLayoutManager(this)
        communicationsAdapter = CommunicationsAdapter(communicationsList)
        communicationsRecyclerView.isNestedScrollingEnabled = true
        communicationsRecyclerView.adapter = communicationsAdapter

        handler = Handler(mainLooper)

        // Definisci il runnable
        runnable = Runnable {
            if (managerCF != null) {
                fetchCommunications(managerCF)
            }
            handler.postDelayed(runnable, 10000)
        }

        handler.post(runnable)
    }

    private fun fetchCommunications(managerCF: String) {
        val url = "https://noemigiustini01.pythonanywhere.com/communication/read_all?CF=$managerCF"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ManagermenuActivity, "Failed to load communications", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val jsonArray = JSONArray(responseBody)
                        val newCommunicationsList = mutableListOf<Communication>()

                        for (i in 0 until jsonArray.length()) {
                            val communicationJson = jsonArray.getJSONObject(i)
                            val text = communicationJson.getString("Text")
                            val communicationID = communicationJson.getInt("ID")
                            val communication = Communication(text, 0, communicationID)
                            newCommunicationsList.add(communication)
                        }

                        // Aggiorna l'adattatore sul thread principale
                        runOnUiThread {
                            updateCommunicationsList(newCommunicationsList)
                        }
                    } catch (e: JSONException) {
                        runOnUiThread {
                            Toast.makeText(this@ManagermenuActivity, "Failed to parse communication data", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    private fun updateCommunicationsList(newCommunications: List<Communication>) {
        // Mantieni la visualizzazione corrente nel RecyclerView
        val existingIds = communicationsList.map { it.communicationID }.toSet()

        // Aggiungi o aggiorna le comunicazioni esistenti
        newCommunications.forEach { newCommunication ->
            if (existingIds.contains(newCommunication.communicationID)) {
                // Trova l'indice della comunicazione esistente e aggiornala
                val index = communicationsList.indexOfFirst { it.communicationID == newCommunication.communicationID }
                if (index != -1) {
                    communicationsList[index] = newCommunication // Aggiorna la comunicazione esistente
                    communicationsAdapter.notifyItemChanged(index) // Notifica che l'elemento è stato aggiornato
                }
            } else {
                // Se è una nuova comunicazione, aggiungila alla lista
                communicationsList.add(newCommunication)
                communicationsAdapter.notifyItemInserted(communicationsList.size - 1) // Notifica che è stata aggiunta una nuova comunicazione
            }
        }
    }

    private fun fetchSites(managerCF: String) {
        val url = "https://noemigiustini01.pythonanywhere.com/site/read_all?CF=$managerCF"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ManagermenuActivity, "Failed to load sites", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val jsonArray = JSONArray(responseBody)
                        val siteIds = mutableListOf<String>()

                        for (i in 0 until jsonArray.length()) {
                            val site = jsonArray.getJSONObject(i)
                            siteIds.add(site.getString("ID")) // Aggiungi solo l'ID
                        }

                        // Popola lo spinner sul thread principale
                        runOnUiThread {
                            val siteIDField = findViewById<Spinner>(R.id.site_id_spinner)
                            val adapter = ArrayAdapter(this@ManagermenuActivity, android.R.layout.simple_spinner_item, siteIds)
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            siteIDField.adapter = adapter
                        }
                    } catch (e: JSONException) {
                        runOnUiThread {
                            Toast.makeText(this@ManagermenuActivity, "Failed to parse site data", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }




    private fun createCommunication(text: String, managerCF: String, siteID: String) {
        val url = "https://noemigiustini01.pythonanywhere.com/communication/create"
        val json = JSONObject()
        json.put("Text", text)
        json.put("ManagerCF", managerCF)
        json.put("SiteID", siteID)

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@ManagermenuActivity, "Failed to create communication: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && responseBody != null) {
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                val communicationID = jsonResponse.getInt("communication_id")

                                Toast.makeText(this@ManagermenuActivity, "Communication created successfully!", Toast.LENGTH_SHORT).show()

                                val intent = Intent(this@ManagermenuActivity, ManagermenuActivity::class.java)
                                intent.putExtra("managerCF", managerCF)
                                startActivity(intent)
                                finish()
                            } catch (e: JSONException) {
                                Toast.makeText(this@ManagermenuActivity, "Failed to parse response", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this@ManagermenuActivity, "Failed to create communication: ${responseBody ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }
    }

    data class Communication(
        val message: String,
        val thumbsUpCount: Int,
        val communicationID: Int,
    )

    class CommunicationsAdapter(private val communications: List<Communication>) :
        RecyclerView.Adapter<CommunicationsAdapter.CommunicationViewHolder>() {
        private val client = OkHttpClient()

        inner class CommunicationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val communicationTextView: TextView = view.findViewById(R.id.communication_text_view)
            val thumbsUpIcon: ImageView = view.findViewById(R.id.thumbs_up_icon)
            val viewCountTextView: TextView = view.findViewById(R.id.view_count_text_view)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommunicationViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_communication, parent, false)
            return CommunicationViewHolder(view)
        }

        override fun onBindViewHolder(holder: com.safetyhat.macc.ManagermenuActivity.CommunicationsAdapter.CommunicationViewHolder, position: Int) {
            val communication = communications[position]
            holder.communicationTextView.text = communication.message  // Mostra il testo della comunicazione

            // Esegui una chiamata API per ottenere il numero di visualizzazioni per il CommunicationID
            val countUrl = "https://noemigiustini01.pythonanywhere.com/visualization/count?CommunicationID=${communication.communicationID}"
            val countRequest = Request.Builder().url(countUrl).build()

            client.newCall(countRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    (holder.itemView.context as? AppCompatActivity)?.runOnUiThread {
                        Toast.makeText(holder.itemView.context, "Failed to load view count", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        response.body?.string()?.let { responseBody ->
                            val json = JSONObject(responseBody)
                            val viewCount = json.optInt("count", 0)  // Estrai il valore di 'count' dalla risposta

                            // Aggiorna viewCountTextView sul thread principale
                            (holder.itemView.context as? AppCompatActivity)?.runOnUiThread {
                                holder.viewCountTextView.text = viewCount.toString()
                            }
                        }
                    }
                }
            })
        }


        override fun getItemCount(): Int = communications.size
    }

}
