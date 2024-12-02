package com.safetyhat.macc

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class WorkermenuActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val client = OkHttpClient()

    private lateinit var communicationsRecyclerView: RecyclerView
    private lateinit var communicationsAdapter: CommunicationsAdapter
    private val communicationsList = mutableListOf<Communication>()
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private var workerCF: String = ""
    private var siteID: String = ""

    private val foregroundPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    private val backgroundPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_worker_menu)

            drawerLayout = findViewById(R.id.drawer_layout)
            navigationView = findViewById(R.id.navigation_view_worker)
            navigationView.itemIconTintList = null

            findViewById<ImageView>(R.id.worker_menu_icon).setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

            workerCF = intent.getStringExtra("workerCF") ?: ""
            siteID = intent.getStringExtra("siteID") ?: ""

            if (workerCF.isEmpty() || siteID.isEmpty()) {
                Toast.makeText(
                    this,
                    "Missing worker CF or site ID. Please log in again.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return
            }

            navigationView.setNavigationItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_account_info_worker -> {
                        val intent = Intent(this, WorkerinfoActivity::class.java)
                        intent.putExtra("workerCF", workerCF)
                        intent.putExtra("siteID", siteID)
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_logout_worker -> {
                        val stopServiceIntent = Intent(this, AlertService::class.java)
                        stopService(stopServiceIntent)

                        // Cancel all notifications using NotificationManager
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

            // Initialize UI elements and set listeners
            val sitesInfoText = findViewById<LinearLayout>(R.id.sites_worker_info_field)
            val alertsText = findViewById<LinearLayout>(R.id.alerts_field)

            sitesInfoText.setOnClickListener {
                val intent = Intent(this, SiteInfoActivity::class.java)
                intent.putExtra("workerCF", workerCF)
                intent.putExtra("siteID", siteID)
                startActivity(intent)
                finish()
            }

            alertsText.setOnClickListener {
                val intent = Intent(this, AlertActivity::class.java)
                intent.putExtra("workerCF", workerCF)
                intent.putExtra("siteID", siteID)
                startActivity(intent)
                finish()
            }

            communicationsRecyclerView = findViewById(R.id.communications_recycler_view)
            communicationsRecyclerView.layoutManager = LinearLayoutManager(this)
            communicationsAdapter = CommunicationsAdapter(communicationsList)
            communicationsRecyclerView.isNestedScrollingEnabled = true
            communicationsRecyclerView.adapter = communicationsAdapter

            handler = Handler(mainLooper)

            // Define the runnable
            runnable = Runnable {
                fetchCommunications()
                handler.postDelayed(runnable, 10000)
            }

            handler.post(runnable)

            if (hasNecessaryPermissions()) {
                startAlertService()
            } else {
                Toast.makeText(this, "Required permissions not granted.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("WorkermenuActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(
                this,
                "An unexpected error occurred during initialization.",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private fun startAlertService() {
        if (hasNecessaryPermissions()) {
            val serviceIntent = Intent(this, AlertService::class.java)
            serviceIntent.putExtra("siteID", siteID)
            serviceIntent.putExtra("workerCF", workerCF)
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun hasNecessaryPermissions(): Boolean {
        val permissions = foregroundPermissions
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun fetchCommunications() {
        val url =
            "https://noemigiustini01.pythonanywhere.com/communication/read_site_all?SiteID=$siteID"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("WorkermenuActivity", "Failed to load communications: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@WorkermenuActivity,
                        "Failed to load communications",
                        Toast.LENGTH_SHORT
                    ).show()
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
                            val timestamp = communicationJson.getString("Timestamp")
                            val communication = Communication(
                                message = text,
                                communicationID = communicationID,
                                workerCF = workerCF,
                                siteID = siteID,
                                timestamp = timestamp
                            )
                            newCommunicationsList.add(communication)
                        }

                        runOnUiThread {
                            updateCommunicationsList(newCommunicationsList)
                        }
                    } ?: runOnUiThread {
                        updateCommunicationsList(emptyList())
                    }
                } catch (e: JSONException) {
                    Log.e("WorkermenuActivity", "JSON parsing error: ${e.message}")
                    runOnUiThread {
                        updateCommunicationsList(emptyList())
                    }
                } catch (e: Exception) {
                    Log.e("WorkermenuActivity", "Unexpected error: ${e.message}")
                    runOnUiThread {
                        updateCommunicationsList(emptyList())
                    }
                }
            }
        })
    }

    private fun updateCommunicationsList(newCommunications: List<Communication>) {
        val noCommunicationsText = findViewById<TextView>(R.id.no_communications_text)

        if (newCommunications.isEmpty()) {
            // Show "No communications" message and hide RecyclerView
            noCommunicationsText.visibility = View.VISIBLE
            communicationsRecyclerView.visibility = View.GONE
        } else {
            // Hide "No communications" message and show RecyclerView
            noCommunicationsText.visibility = View.GONE
            communicationsRecyclerView.visibility = View.VISIBLE

            // Update data in the adapter
            communicationsList.clear()
            communicationsList.addAll(newCommunications)
            communicationsAdapter.notifyDataSetChanged()
        }
    }

    data class Communication(
        val message: String,
        val communicationID: Int,
        val workerCF: String,
        val siteID: String,
        val timestamp: String
    )

    class CommunicationsAdapter(private val communications: List<Communication>) :
        RecyclerView.Adapter<CommunicationsAdapter.CommunicationViewHolder>() {
        private val client = OkHttpClient()

        inner class CommunicationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val communicationTextView: TextView =
                view.findViewById(R.id.communication_text_view)
            private val thumbsUp: Button = view.findViewById(R.id.thumbs_up_icon)
            val viewCountTextView: TextView = view.findViewById(R.id.view_count_text_view)
            val siteIDTextView: TextView = view.findViewById(R.id.siteID_text_view)

            init {
                thumbsUp.setOnClickListener {
                    val context = itemView.context
                    val communicationID = communications[adapterPosition].communicationID
                    val workerCF = communications[adapterPosition].workerCF
                    val siteID = communications[adapterPosition].siteID

                    checkAndCreateVisualization(
                        context,
                        communicationID.toString(),
                        workerCF,
                        siteID
                    )
                }
            }

            private fun checkAndCreateVisualization(
                context: Context,
                communicationID: String,
                workerCF: String,
                siteID: String
            ) {
                val checkUrl =
                    "https://noemigiustini01.pythonanywhere.com/visualization/check?CommunicationID=$communicationID&WorkerCF=$workerCF"
                val checkRequest = Request.Builder().url(checkUrl).build()

                client.newCall(checkRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("CommunicationsAdapter", "Failed to check visualization: ${e.message}")
                        (context as? AppCompatActivity)?.runOnUiThread {
                            Toast.makeText(
                                context,
                                "Failed to check visualization",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        (context as? AppCompatActivity)?.runOnUiThread {
                            try {
                                if (response.isSuccessful) {
                                    val responseBody = response.body?.string()
                                    if (responseBody == null) {
                                        Toast.makeText(
                                            context,
                                            "Empty response body",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@runOnUiThread
                                    }

                                    val exists = responseBody.trim()
                                    if (exists == "true") {
                                        Toast.makeText(
                                            context,
                                            "Communication already visualized",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else if (exists == "false") {
                                        createVisualization(
                                            context,
                                            communicationID,
                                            workerCF,
                                            siteID
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Unexpected response format",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Unsuccessful response",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Log.e(
                                    "CommunicationsAdapter",
                                    "Error processing visualization check: ${e.message}"
                                )
                                Toast.makeText(
                                    context,
                                    "An error occurred.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                })
            }

            private fun createVisualization(
                context: Context,
                communicationID: String,
                workerCF: String,
                siteID: String
            ) {
                val createUrl = "https://noemigiustini01.pythonanywhere.com/visualization/create"

                val json = JSONObject().apply {
                    put("CommunicationID", communicationID)
                    put("WorkerCF", workerCF)
                }
                val requestBody =
                    json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val createRequest = Request.Builder()
                    .url(createUrl)
                    .post(requestBody)
                    .build()

                client.newCall(createRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("CommunicationsAdapter", "Failed to create visualization: ${e.message}")
                        (context as? AppCompatActivity)?.runOnUiThread {
                            Toast.makeText(
                                context,
                                "Failed to create visualization",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            updateVisualizations(context, communicationID)
                        } else {
                            val errorBody = response.body?.string()
                            Log.e("CommunicationsAdapter", "Error creating visualization: $errorBody")
                            (context as? AppCompatActivity)?.runOnUiThread {
                                Toast.makeText(
                                    context,
                                    "Failed to create visualization",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                })
            }

            private fun updateVisualizations(context: Context, communicationID: String) {
                val countUrl =
                    "https://noemigiustini01.pythonanywhere.com/visualization/count?CommunicationID=${communicationID}"
                val countRequest = Request.Builder().url(countUrl).build()

                client.newCall(countRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e("CommunicationsAdapter", "Failed to load view count: ${e.message}")
                        (context as? AppCompatActivity)?.runOnUiThread {
                            Toast.makeText(
                                context,
                                "Failed to load view count",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            if (response.isSuccessful) {
                                response.body?.string()?.let { responseBody ->
                                    val json = JSONObject(responseBody)
                                    val viewCount = json.optInt("count", 0)

                                    (context as? AppCompatActivity)?.runOnUiThread {
                                        viewCountTextView.text = viewCount.toString()
                                    }
                                }
                            } else {
                                val errorBody = response.body?.string()
                                Log.e("CommunicationsAdapter", "Error loading view count: $errorBody")
                            }
                        } catch (e: JSONException) {
                            Log.e("CommunicationsAdapter", "JSON parsing error: ${e.message}")
                        } catch (e: Exception) {
                            Log.e("CommunicationsAdapter", "Unexpected error: ${e.message}")
                        }
                    }
                })
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommunicationViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_communication_for_worker, parent, false)
            return CommunicationViewHolder(view)
        }

        override fun onBindViewHolder(holder: CommunicationViewHolder, position: Int) {
            val communication = communications[position]

            holder.communicationTextView.text = communication.message
            holder.siteIDTextView.text = "SiteID: ${communication.siteID}"
            holder.itemView.findViewById<TextView>(R.id.timestamp_text_view).text =
                if (communication.timestamp.isNotEmpty()) {
                    "${communication.timestamp}"
                } else {
                    "Timestamp not available"
                }

            val countUrl =
                "https://noemigiustini01.pythonanywhere.com/visualization/count?CommunicationID=${communication.communicationID}"
            val countRequest = Request.Builder().url(countUrl).build()

            client.newCall(countRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("CommunicationsAdapter", "Failed to load view count: ${e.message}")
                    (holder.itemView.context as? AppCompatActivity)?.runOnUiThread {
                        Toast.makeText(
                            holder.itemView.context,
                            "Failed to load view count",
                            Toast.LENGTH_SHORT
                        ).show()
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
                            val errorBody = response.body?.string()
                            Log.e("CommunicationsAdapter", "Error loading view count: $errorBody")
                        }
                    } catch (e: JSONException) {
                        Log.e("CommunicationsAdapter", "JSON parsing error: ${e.message}")
                    } catch (e: Exception) {
                        Log.e("CommunicationsAdapter", "Unexpected error: ${e.message}")
                    }
                }
            })
        }

        override fun getItemCount(): Int = communications.size

    }
}
