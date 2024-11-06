package com.safetyhat.macc

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.safetyhat.macc.ManagermenuActivity.Communication
import com.safetyhat.macc.ManagermenuActivity.CommunicationsAdapter
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import android.os.Handler

class WorkermenuActivity : AppCompatActivity() {
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
        setContentView(R.layout.activity_worker_menu)

        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view_worker)
        navigationView.itemIconTintList = null

        // Apre il drawer menu al clic sull'icona di menu
        findViewById<ImageView>(R.id.worker_menu_icon).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val workerCF = intent.getStringExtra("workerCF")
        val siteID = intent.getStringExtra("siteID")

        // Impostazioni della navigazione tramite il menu
        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_account_info_worker -> {
                    val intent = Intent(this, WorkerinfoActivity::class.java)
                    intent.putExtra("workerCF", workerCF)
                    intent.putExtra("siteID", siteID.toString())
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

        // Inizializza gli elementi dell'interfaccia e imposta i listener
        val sitesInfoText = findViewById<LinearLayout>(R.id.sites_worker_info_field)
        val alertsText = findViewById<LinearLayout>(R.id.alerts_field)

        sitesInfoText.setOnClickListener {
            val intent = Intent(this, SiteInfoActivity::class.java)
            intent.putExtra("workerCF", workerCF)
            intent.putExtra("siteID", siteID.toString())
            startActivity(intent)
            finish()
        }

        alertsText.setOnClickListener {
            val intent = Intent(this, AlertActivity::class.java)
            intent.putExtra("workerCF", workerCF)
            intent.putExtra("siteID", siteID.toString())
            startActivity(intent)
            finish()
        }

        communicationsRecyclerView = findViewById(R.id.communications_recycler_view)
        communicationsRecyclerView.layoutManager = LinearLayoutManager(this)
        communicationsAdapter = CommunicationsAdapter(communicationsList)
        communicationsRecyclerView.isNestedScrollingEnabled = true
        communicationsRecyclerView.adapter = communicationsAdapter

        handler = Handler(mainLooper)

        // Definisci il runnable
        runnable = Runnable {
            if (siteID != null && workerCF != null) {
                fetchCommunications(siteID.toInt(), workerCF)
            }
            handler.postDelayed(runnable, 10000)
        }

        handler.post(runnable)
    }

    private fun fetchCommunications(siteID: Int, workerCF: String) {
        val url = "https://noemigiustini01.pythonanywhere.com/communication/read_site_all?SiteID=$siteID"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@WorkermenuActivity, "Failed to load communications", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val jsonArray = JSONArray(responseBody)
                        val communicationsList = mutableListOf<Communication>()

                        for (i in 0 until jsonArray.length()) {
                            val communicationJson = jsonArray.getJSONObject(i)
                            val text = communicationJson.getString("Text")
                            val communicationID = communicationJson.getInt("ID")
                            val communication = Communication(text, 0, communicationID, workerCF, siteID.toString())
                            communicationsList.add(communication)
                        }

                        // Aggiorna l'adattatore sul thread principale
                        runOnUiThread {
                            communicationsAdapter = CommunicationsAdapter(communicationsList)
                            communicationsRecyclerView.adapter = communicationsAdapter
                        }
                    } catch (e: JSONException) {
                        runOnUiThread {
                            Toast.makeText(this@WorkermenuActivity, "Failed to parse communication data", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    data class Communication(
        val message: String,
        val thumbsUpCount: Int,
        val communicationID: Int,
        val workerCF: String,
        val siteID: String
    )

    class CommunicationsAdapter(private val communications: List<Communication>) :
        RecyclerView.Adapter<CommunicationsAdapter.CommunicationViewHolder>() {
        private val client = OkHttpClient()

        inner class CommunicationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val communicationTextView: TextView = view.findViewById(R.id.communication_text_view)
            private val thumbsUp: Button = view.findViewById(R.id.thumbs_up_icon)
            val viewCountTextView: TextView = view.findViewById(R.id.view_count_text_view)

            init {
                thumbsUp.setOnClickListener {
                    val context = itemView.context
                    val communicationID = communications[adapterPosition].communicationID
                    val workerCF = communications[adapterPosition].workerCF
                    val siteID = communications[adapterPosition].siteID

                    checkAndCreateVisualization(context, communicationID.toString(), workerCF, siteID)
                }
            }

            private fun checkAndCreateVisualization(context: Context, communicationID: String, workerCF: String, siteID: String) {
                // URL per controllare se esiste già una visualizzazione
                val checkUrl = "https://noemigiustini01.pythonanywhere.com/visualization/check?CommunicationID=$communicationID&WorkerCF=$workerCF"
                val checkRequest = Request.Builder().url(checkUrl).build()

                client.newCall(checkRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        (context as? AppCompatActivity)?.runOnUiThread {
                            Toast.makeText(context, "Failed to check visualization", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        (context as? AppCompatActivity)?.runOnUiThread {
                            if (response.isSuccessful) {
                                val responseBody = response.body?.string()

                                // Verifica se il corpo della risposta è nullo e logga l'output
                                if (responseBody == null) {
                                    Toast.makeText(context, "Empty response body", Toast.LENGTH_SHORT).show()
                                    return@runOnUiThread
                                }

                                // Converte la risposta in booleano
                                val exists = responseBody.trim()
                                if (exists == "true") {
                                    Toast.makeText(context, "Communication already visualized", Toast.LENGTH_SHORT).show()
                                } else if (exists == "false") {
                                    createVisualization(context, communicationID, workerCF, siteID)
                                } else {
                                    Toast.makeText(context, "Unexpected response format", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Gestione del caso in cui la risposta non sia un successo (es. codice 404 o 500)
                                Toast.makeText(context, "Unsuccessful response", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                })
            }

            private fun createVisualization(context: Context, communicationID: String, workerCF: String, siteID: String) {
                val createUrl = "https://noemigiustini01.pythonanywhere.com/visualization/create"

                val json = JSONObject().apply {
                    put("CommunicationID", communicationID)
                    put("WorkerCF", workerCF)
                }
                val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val createRequest = Request.Builder()
                    .url(createUrl)
                    .post(requestBody)
                    .build()

                client.newCall(createRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        (context as? AppCompatActivity)?.runOnUiThread {
                            Toast.makeText(context, "Failed to create visualization", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            update_visualizations(context, communicationID, workerCF, siteID)
                        }
                    }
                })
            }

            private fun update_visualizations(context: Context, communicationID: String, workerCF: String, siteID: String) {
                // Esegui una chiamata API per ottenere il numero di visualizzazioni per il CommunicationID
                val countUrl = "https://noemigiustini01.pythonanywhere.com/visualization/count?CommunicationID=${communicationID}"
                val countRequest = Request.Builder().url(countUrl).build()

                client.newCall(countRequest).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        (itemView.context as? AppCompatActivity)?.runOnUiThread {
                            Toast.makeText(itemView.context, "Failed to load view count", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            response.body?.string()?.let { responseBody ->
                                val json = JSONObject(responseBody)
                                val viewCount = json.optInt("count", 0)  // Estrai il valore di 'count' dalla risposta

                                (itemView.context as? AppCompatActivity)?.runOnUiThread {
                                    viewCountTextView.text = viewCount.toString()
                                }
                            }
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