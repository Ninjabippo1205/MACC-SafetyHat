package com.safetyhat.macc

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import android.os.Handler
import android.view.MotionEvent
import android.widget.ArrayAdapter
import android.widget.ScrollView
import android.widget.Spinner

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
        try {
            setContentView(R.layout.activity_manager_menu)

            drawerLayout = findViewById(R.id.drawer_layout)
            navigationView = findViewById(R.id.navigation_view_manager)
            navigationView.itemIconTintList = null

            findViewById<ImageView>(R.id.manager_menu_icon).setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

            val managerCF = intent.getStringExtra("managerCF") ?: ""

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

            fetchSites(managerCF)
            val communicationTextField = findViewById<TextView>(R.id.communication_text)
            val siteIDField = findViewById<Spinner>(R.id.site_id_spinner)
            val submitButton = findViewById<Button>(R.id.send_communication_button)

            submitButton.setOnClickListener {
                val communicationText = communicationTextField.text.toString()
                val siteID = siteIDField.selectedItem.toString()

                if (communicationText.isNotBlank() && siteID.isNotBlank()) {
                    createCommunication(communicationText, managerCF, siteID)
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
            runnable = object : Runnable {
                override fun run() {
                    try {
                        if (managerCF.isNotEmpty()) {
                            fetchCommunications(managerCF)
                        }
                    } catch (e: Exception) {
                        Log.e("ManagermenuActivity", "Error in fetchCommunications: ${e.message}")
                    } finally {
                        handler.postDelayed(this, 10000)
                    }
                }
            }

            handler.post(runnable)
        } catch (e: Exception) {
            Log.e("ManagermenuActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred during initialization.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fetchCommunications(managerCF: String) {
        val url = "https://noemigiustini01.pythonanywhere.com/communication/read_all?CF=$managerCF"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ManagermenuActivity", "Failed to load communications: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@ManagermenuActivity, "Failed to load communications", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.string()?.let { responseBody ->
                        val jsonArray = JSONArray(responseBody)
                        val newCommunicationsList = mutableListOf<Communication>()

                        for (i in 0 until jsonArray.length()) {
                            val communicationJson = jsonArray.getJSONObject(i)
                            val text = communicationJson.getString("Text")
                            val communicationID = communicationJson.getInt("ID")
                            val siteID = communicationJson.getInt("SiteID")
                            val timestamp = communicationJson.getString("Timestamp")

                            val communication = Communication(text, 0, communicationID, siteID, timestamp)
                            newCommunicationsList.add(communication)
                        }

                        runOnUiThread {
                            updateCommunicationsList(newCommunicationsList)
                        }
                    } ?: runOnUiThread {
                        updateCommunicationsList(emptyList())
                    }
                } catch (e: JSONException) {
                    Log.e("ManagermenuActivity", "JSON parsing error: ${e.message}")
                    runOnUiThread {
                        updateCommunicationsList(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e("ManagermenuActivity", "Error in fetchCommunications: ${e.message}")
                }
            }
        })
    }

    private fun updateCommunicationsList(newCommunications: List<Communication>) {
        val noCommunicationsText = findViewById<TextView>(R.id.no_communications_text)

        if (newCommunications.isEmpty()) {
            noCommunicationsText.visibility = View.VISIBLE
            communicationsRecyclerView.visibility = View.GONE
        } else {
            noCommunicationsText.visibility = View.GONE
            communicationsRecyclerView.visibility = View.VISIBLE

            val existingIds = communicationsList.map { it.communicationID }.toSet()

            newCommunications.forEach { newCommunication ->
                if (existingIds.contains(newCommunication.communicationID)) {
                    val index = communicationsList.indexOfFirst { it.communicationID == newCommunication.communicationID }
                    if (index != -1) {
                        communicationsList[index] = newCommunication
                        communicationsAdapter.notifyItemChanged(index)
                    }
                } else {
                    communicationsList.add(newCommunication)
                    communicationsAdapter.notifyItemInserted(communicationsList.size - 1)
                }
            }
        }
    }

    private fun fetchSites(managerCF: String) {
        val url = "https://noemigiustini01.pythonanywhere.com/site/read_all?CF=$managerCF"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ManagermenuActivity", "Failed to load sites: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@ManagermenuActivity, "Failed to load sites", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.string()?.let { responseBody ->
                        val jsonArray = JSONArray(responseBody)
                        val siteIds = mutableListOf<String>()

                        for (i in 0 until jsonArray.length()) {
                            val site = jsonArray.getJSONObject(i)
                            siteIds.add(site.getString("ID"))
                        }

                        runOnUiThread {
                            val siteIDField = findViewById<Spinner>(R.id.site_id_spinner)
                            val adapter = ArrayAdapter(this@ManagermenuActivity, android.R.layout.simple_spinner_item, siteIds)
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            siteIDField.adapter = adapter
                        }
                    }
                } catch (e: JSONException) {
                    Log.e("ManagermenuActivity", "JSON parsing error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(this@ManagermenuActivity, "Error loading site IDs", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("ManagermenuActivity", "Error in fetchSites: ${e.message}")
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

        client.newCall(Request.Builder().url(url).post(requestBody).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("ManagermenuActivity", "Failed to create communication: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this@ManagermenuActivity, "Failed to create communication: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@ManagermenuActivity, "Communication created successfully!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@ManagermenuActivity, ManagermenuActivity::class.java)
                        intent.putExtra("managerCF", managerCF)
                        startActivity(intent)
                        finish()
                    } else {
                        Log.e("ManagermenuActivity", "Failed to create communication: ${response.message}")
                        Toast.makeText(this@ManagermenuActivity, "Failed to create communication", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    data class Communication(
        val message: String,
        val thumbsUpCount: Int,
        val communicationID: Int,
        val siteID: Int = -1,
        val timestamp: String
    )

    class CommunicationsAdapter(private val communications: List<Communication>) :
        RecyclerView.Adapter<CommunicationsAdapter.CommunicationViewHolder>() {
        private val client = OkHttpClient()

        inner class CommunicationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val communicationTextView: TextView = view.findViewById(R.id.communication_text_view)
            val thumbsUpIcon: ImageView = view.findViewById(R.id.thumbs_up_icon)
            val viewCountTextView: TextView = view.findViewById(R.id.view_count_text_view)
            val siteIDTextView: TextView = view.findViewById(R.id.siteID_text_view)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommunicationViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_communication, parent, false)
            return CommunicationViewHolder(view)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: CommunicationViewHolder, position: Int) {
            val communication = communications[position]

            holder.communicationTextView.text = communication.message
            holder.siteIDTextView.text = "Site ${communication.siteID}"
            holder.itemView.findViewById<TextView>(R.id.timestamp_text_view).text = communication.timestamp

            // Abilita lo scroll solo quando l'utente interagisce con la ScrollView interna
            holder.itemView.findViewById<ScrollView>(R.id.scrollView2).setOnTouchListener { v, event ->
                v.parent.requestDisallowInterceptTouchEvent(true)
                if (event.action == MotionEvent.ACTION_UP) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
                false
            }

            val countUrl = "https://noemigiustini01.pythonanywhere.com/visualization/count?CommunicationID=${communication.communicationID}"
            val countRequest = Request.Builder().url(countUrl).build()

            client.newCall(countRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("CommunicationsAdapter", "Failed to load view count: ${e.message}")
                    (holder.itemView.context as? AppCompatActivity)?.runOnUiThread {
                        Toast.makeText(holder.itemView.context, "Failed to load view count", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (response.isSuccessful) {
                            response.body?.string()?.let { responseBody ->
                                val json = JSONObject(responseBody)
                                val viewCount = json.optInt("count", 0)

                                (holder.itemView.context as? AppCompatActivity)?.runOnUiThread {
                                    holder.viewCountTextView.text = viewCount.toString()
                                }
                            }
                        } else {
                            Log.e("CommunicationsAdapter", "Failed to get view count: ${response.message}")
                        }
                    } catch (e: JSONException) {
                        Log.e("CommunicationsAdapter", "JSON parsing error: ${e.message}")
                    }
                }
            })
        }


        override fun getItemCount(): Int = communications.size
    }

}