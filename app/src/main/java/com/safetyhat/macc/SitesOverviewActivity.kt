package com.safetyhat.macc

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class SitesOverviewActivity : AppCompatActivity(), OnMapReadyCallback {

    private val client = OkHttpClient()
    private lateinit var sitesRecyclerView: RecyclerView
    private lateinit var sitesAdapter: SitesAdapter
    private lateinit var mMap: GoogleMap
    private val sitesList = mutableListOf<Site>()
    private var isMapInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sites_overview)

        // Configura la RecyclerView
        sitesRecyclerView = findViewById(R.id.sitesRecyclerView)
        sitesRecyclerView.layoutManager = LinearLayoutManager(this)
        sitesAdapter = SitesAdapter(sitesList)
        sitesRecyclerView.adapter = sitesAdapter

        val managerCF = intent.getStringExtra("managerCF").toString()

        // Imposta il LinearSnapHelper per centrare l'elemento
        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(sitesRecyclerView)

        // Listener per il centro automatico sulla mappa
        sitesRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val centerView = snapHelper.findSnapView(sitesRecyclerView.layoutManager)
                    val position = sitesRecyclerView.getChildAdapterPosition(centerView!!)
                    centerMapOnSiteIfValid(sitesList[position])
                }
            }
        })

        // Carica la mappa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Carica i dati dei siti
        fetchSites(managerCF)
    }

    private fun fetchSites(managerCF: String) {
        val url = "https://noemigiustini01.pythonanywhere.com/site/read_all?CF=$managerCF"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SitesOverviewActivity, "Failed to load sites", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    parseSites(responseBody)
                    runOnUiThread {
                        sitesAdapter.notifyDataSetChanged()
                        if (isMapInitialized) {
                            addMarkersToMap()
                        }
                    }
                }
            }
        })
    }

    private fun parseSites(json: String) {
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val address = jsonObject.getString("Address")
                val siteRadius = jsonObject.getDouble("SiteRadius")
                val totalWorkers = jsonObject.optInt("TotalWorkers", 0)
                val scaffoldingCount = jsonObject.optInt("ScaffoldingCount", 0)
                val startDate = jsonObject.optString("StartDate", "")
                val estimatedEndDate = jsonObject.optString("EstimatedEndDate", "")
                val securityCode = jsonObject.optString("SecurityCode", "")

                // Passa tutti i valori a geocodeAddress
                geocodeAddress(address, siteRadius, totalWorkers, scaffoldingCount, startDate, estimatedEndDate, securityCode)
            }
        } catch (e: JSONException) {
            runOnUiThread {
                Toast.makeText(this@SitesOverviewActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun geocodeAddress(address: String, siteRadius: Double, totalWorkers: Int, scaffoldingCount: Int, startDate: String, estimatedEndDate: String, securityCode: String) {
        val geocodeUrl = "https://maps.googleapis.com/maps/api/geocode/json?address=${address}&key=${getString(R.string.google_maps_key)}"
        val request = Request.Builder().url(geocodeUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SitesOverviewActivity, "Failed to geocode address", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    try {
                        val jsonObject = JSONObject(responseBody)
                        val results = jsonObject.getJSONArray("results")
                        if (results.length() > 0) {
                            val location = results.getJSONObject(0)
                                .getJSONObject("geometry")
                                .getJSONObject("location")
                            val lat = location.getDouble("lat")
                            val lng = location.getDouble("lng")

                            val site = Site(
                                id = sitesList.size + 1,
                                address = address,
                                siteRadius = siteRadius,
                                lat = lat,
                                lng = lng,
                                isValidLocation = true,
                                totalWorkers = totalWorkers,
                                scaffoldingCount = scaffoldingCount,
                                startDate = startDate,
                                estimatedEndDate = estimatedEndDate,
                                securityCode = securityCode
                            )
                            sitesList.add(site)

                            runOnUiThread {
                                sitesAdapter.notifyDataSetChanged()
                                if (isMapInitialized) {
                                    addMarkersToMap()
                                }
                            }
                        } else {
                            val site = Site(
                                id = sitesList.size + 1,
                                address = address,
                                siteRadius = siteRadius,
                                lat = 0.0,
                                lng = 0.0,
                                isValidLocation = false,
                                totalWorkers = totalWorkers,
                                scaffoldingCount = scaffoldingCount,
                                startDate = startDate,
                                estimatedEndDate = estimatedEndDate,
                                securityCode = securityCode
                            )
                            sitesList.add(site)

                            runOnUiThread {
                                sitesAdapter.notifyDataSetChanged()
                                Toast.makeText(this@SitesOverviewActivity, "Address not found: $address", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: JSONException) {
                        runOnUiThread {
                            Toast.makeText(this@SitesOverviewActivity, "Error parsing geocode response", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        isMapInitialized = true
        if (sitesList.isNotEmpty()) {
            addMarkersToMap()
        }
    }

    private fun addMarkersToMap() {
        mMap.clear()
        val sitesCopy = ArrayList(sitesList) // Creazione di una copia della lista
        for (site in sitesCopy) {
            if (site.isValidLocation) {
                val position = LatLng(site.lat, site.lng)
                mMap.addMarker(MarkerOptions().position(position).title("Site: ${site.address}"))
                mMap.addCircle(
                    CircleOptions()
                        .center(position)
                        .radius(site.siteRadius)
                        .strokeColor(0xFF0000FF.toInt())
                        .fillColor(0x300000FF)
                )
            }
        }
        val firstValidSite = sitesCopy.firstOrNull { it.isValidLocation }
        if (firstValidSite != null) {
            centerMapOnSite(firstValidSite)
        }
    }

    private fun centerMapOnSite(site: Site) {
        val position = LatLng(site.lat, site.lng)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
    }

    private fun centerMapOnSiteIfValid(site: Site) {
        if (site.isValidLocation) {
            centerMapOnSite(site)
        }
    }

    data class Site(
        val id: Int,
        val address: String,
        val siteRadius: Double,
        val lat: Double,
        val lng: Double,
        val isValidLocation: Boolean,
        val totalWorkers: Int,
        val scaffoldingCount: Int,
        val startDate: String,
        val estimatedEndDate: String,
        val securityCode: String
    )

    inner class SitesAdapter(private val sites: List<Site>) :
        RecyclerView.Adapter<SitesAdapter.SiteViewHolder>() {

        inner class SiteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val id: TextView = view.findViewById(R.id.siteID)
            val address: TextView = view.findViewById(R.id.siteAddress)
            val radius: TextView = view.findViewById(R.id.siteRadius)
            val totalWorkers: TextView = view.findViewById(R.id.totalWorkers)
            val scaffoldingCount: TextView = view.findViewById(R.id.scaffoldingCount)
            val startDate: TextView = view.findViewById(R.id.startDate)
            val estimatedEndDate: TextView = view.findViewById(R.id.estimatedEndDate)
            val securityCode: TextView = view.findViewById(R.id.securityCode)
            val viewDetailsButton: Button = view.findViewById(R.id.viewDetailsButton)
            val qrButton: Button = view.findViewById(R.id.qrButton)
            val binButton: ImageButton = view.findViewById(R.id.binButton)

            init {
                view.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        sitesRecyclerView.clearOnScrollListeners() // Rimuovi i listener temporaneamente
                        centerItem(position, view.width)
                    }
                }
            }
        }

        private fun centerItem(position: Int, viewWidth: Int) {
            val layoutManager = sitesRecyclerView.layoutManager as LinearLayoutManager
            // Aggiungi un piccolo offset per posizionare l'elemento selezionato leggermente più in basso del centro
            val centerOffset = layoutManager.width / 2 - viewWidth / 2 // 50 è il valore extra; puoi modificarlo a tuo piacimento

            smoothScrollToPositionWithOffset(position, centerOffset)
            centerMapOnSiteIfValid(sites[position])

            sitesRecyclerView.postDelayed({
                attachOnScrollListener() // Riaggiungi il listener dopo l'animazione
            }, 500) // Regola il tempo in base alla durata dell'animazione
        }

        private fun smoothScrollToPositionWithOffset(position: Int, offset: Int) {
            val layoutManager = sitesRecyclerView.layoutManager as LinearLayoutManager
            val scroller = object : LinearSmoothScroller(this@SitesOverviewActivity) {
                override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                    return 0.1f // Velocità dello scroll (puoi regolarla)
                }

                override fun getVerticalSnapPreference(): Int {
                    return SNAP_TO_START // Allinea l'elemento alla posizione iniziale del layout
                }
            }
            scroller.targetPosition = position
            layoutManager.startSmoothScroll(scroller)

            // Applica l'offset per centrare l'elemento una volta raggiunta la posizione
            sitesRecyclerView.postDelayed({
                layoutManager.scrollToPositionWithOffset(position, offset)
            }, 300) // Ritardo per garantire che il smooth scroll si completi prima dell'offset finale
        }



        private fun attachOnScrollListener() {
            // Controlla se è già presente un OnFlingListener prima di allegare il SnapHelper
            if (sitesRecyclerView.onFlingListener == null) {
                val snapHelper = LinearSnapHelper()
                snapHelper.attachToRecyclerView(sitesRecyclerView)
            }

            sitesRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val centerView = (recyclerView.layoutManager as LinearLayoutManager)
                            .findViewByPosition((recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition())
                        val position = recyclerView.getChildAdapterPosition(centerView!!)
                        centerMapOnSiteIfValid(sitesList[position])
                    }
                }
            })
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.site_item, parent, false)
            return SiteViewHolder(view)
        }

        override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
            val site = sites[position]
            holder.id.text = "ID: ${site.id}"
            holder.address.text = "Address: ${site.address}"
            holder.radius.text = "Radius: ${site.siteRadius} meters"
            holder.totalWorkers.text = "Total Workers: ${site.totalWorkers}"
            holder.scaffoldingCount.text = "Scaffolding Count: ${site.scaffoldingCount}"
            holder.startDate.text = "Start Date: ${site.startDate}"
            holder.estimatedEndDate.text = "Estimated End Date: ${site.estimatedEndDate}"
            holder.securityCode.text = "Security Code: ${site.securityCode}"

            holder.viewDetailsButton.setOnClickListener {
                Toast.makeText(holder.itemView.context, "Viewing details for site: ${site.id}", Toast.LENGTH_SHORT).show()
            }

            holder.qrButton.setOnClickListener {
                Toast.makeText(holder.itemView.context, "Generating QR for site: ${site.id}", Toast.LENGTH_SHORT).show()
            }

            holder.binButton.setOnClickListener {
                Toast.makeText(holder.itemView.context, "Deleting site: ${site.id}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount() = sites.size
    }
}