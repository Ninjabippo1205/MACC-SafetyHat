package com.safetyhat.macc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
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
import com.google.android.material.navigation.NavigationView
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
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val sitesList = mutableListOf<Site>()
    private var isMapInitialized = false

    // Adding the snapHelper as a class variable
    private lateinit var snapHelper: LinearSnapHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_sites_overview)

            // Configure the RecyclerView
            sitesRecyclerView = findViewById(R.id.sitesRecyclerView)
            sitesRecyclerView.layoutManager = LinearLayoutManager(this)

            val managerCF = intent.getStringExtra("managerCF") ?: ""
            if (managerCF.isEmpty()) {
                Toast.makeText(this, "Manager CF is missing.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            drawerLayout = findViewById(R.id.drawer_layout)
            navigationView = findViewById(R.id.navigation_view_manager)
            navigationView.itemIconTintList = null

            findViewById<ImageView>(R.id.menu_icon).setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

            navigationView.setNavigationItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.nav_home_manager -> {
                        val intent = Intent(this, ManagermenuActivity::class.java)
                        intent.putExtra("managerCF", managerCF)
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_account_info_manager -> {
                        val intent = Intent(this, ManagerInfoActivity::class.java)
                        intent.putExtra("managerCF", managerCF)
                        startActivity(intent)
                        finish()
                    }
                    R.id.nav_create_site_manager -> {
                        val intent = Intent(this, CreatesiteActivity::class.java)
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

            sitesAdapter = SitesAdapter(sitesList, managerCF)
            sitesRecyclerView.adapter = sitesAdapter

            // Initialize the LinearSnapHelper to center the item
            snapHelper = TopLinearSnapHelper()
            snapHelper.attachToRecyclerView(sitesRecyclerView)

            // Add OnScrollListener to the RecyclerView
            sitesRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val layoutManager = recyclerView.layoutManager
                        val snapView = snapHelper.findSnapView(layoutManager)
                        if (snapView != null) {
                            val position = recyclerView.getChildAdapterPosition(snapView)
                            if (position != RecyclerView.NO_POSITION) {
                                val site = sitesList[position]
                                centerMapOnSiteIfValid(site)
                            }
                        }
                    }
                }
            })

            // Load the map
            val mapFragment = supportFragmentManager
                .findFragmentById(R.id.mapFragment) as? SupportMapFragment
            if (mapFragment != null) {
                mapFragment.getMapAsync(this)
            } else {
                Toast.makeText(this, "Error loading map fragment", Toast.LENGTH_SHORT).show()
            }

            // Load the site data
            fetchSites(managerCF)
        } catch (e: Exception) {
            Log.e("SitesOverviewActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred during initialization.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        super.onBackPressed()
        val managerCF = intent.getStringExtra("managerCF") ?: ""
        navigateBack(managerCF)
    }

    private fun navigateBack(managerCF: String) {
        val intent = Intent(this, ManagermenuActivity::class.java)
        intent.putExtra("managerCF", managerCF)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    class TopLinearSnapHelper : LinearSnapHelper() {
        override fun calculateDistanceToFinalSnap(
            layoutManager: RecyclerView.LayoutManager,
            targetView: View
        ): IntArray? {
            if (layoutManager is LinearLayoutManager) {
                val distances = super.calculateDistanceToFinalSnap(layoutManager, targetView)
                distances?.let {
                    // Modify the vertical distance to align to the top
                    it[1] = targetView.top - layoutManager.paddingTop
                }
                return distances
            }
            return super.calculateDistanceToFinalSnap(layoutManager, targetView)
        }
    }

    private fun fetchSites(managerCF: String) {
        val url = "https://noemigiustini01.pythonanywhere.com/site/read_all?CF=$managerCF"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SitesOverviewActivity", "Failed to load sites: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@SitesOverviewActivity,
                        "Failed to load sites",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.string()?.let { responseBody ->
                        parseSites(responseBody)
                        runOnUiThread {
                            sitesAdapter.notifyDataSetChanged()
                            if (isMapInitialized) {
                                addMarkersToMap()
                            }
                        }
                    } ?: runOnUiThread {
                        Toast.makeText(
                            this@SitesOverviewActivity,
                            "Empty response from server",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("SitesOverviewActivity", "Error parsing sites: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@SitesOverviewActivity,
                            "Error loading sites",
                            Toast.LENGTH_SHORT
                        ).show()
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
                val id = jsonObject.getInt("ID")
                val address = jsonObject.getString("Address")
                val siteRadius = jsonObject.getDouble("SiteRadius")
                val totalWorkers = jsonObject.optInt("TotalWorkers", 0)
                val scaffoldingCount = jsonObject.optInt("ScaffoldingCount", 0)
                val startDate = jsonObject.optString("StartDate", "")
                val estimatedEndDate = jsonObject.optString("EstimatedEndDate", "")
                val securityCode = jsonObject.optString("SecurityCode", "")

                geocodeAddress(
                    id,
                    address,
                    siteRadius,
                    totalWorkers,
                    scaffoldingCount,
                    startDate,
                    estimatedEndDate,
                    securityCode
                )
            }
        } catch (e: JSONException) {
            Log.e("SitesOverviewActivity", "JSON parsing error: ${e.message}")
            runOnUiThread {
                Toast.makeText(
                    this@SitesOverviewActivity,
                    "Error parsing site data",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun geocodeAddress(
        id: Int,
        address: String,
        siteRadius: Double,
        totalWorkers: Int,
        scaffoldingCount: Int,
        startDate: String,
        estimatedEndDate: String,
        securityCode: String
    ) {
        val encodedAddress = java.net.URLEncoder.encode(address, "UTF-8")
        val geocodeUrl =
            "https://maps.googleapis.com/maps/api/geocode/json?address=${encodedAddress}&key=${getString(R.string.google_maps_key)}"
        val request = Request.Builder().url(geocodeUrl).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SitesOverviewActivity", "Failed to geocode address: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@SitesOverviewActivity,
                        "Failed to geocode address",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.body?.string()?.let { responseBody ->
                        val jsonObject = JSONObject(responseBody)
                        val results = jsonObject.getJSONArray("results")
                        val lat: Double
                        val lng: Double

                        if (results.length() > 0) {
                            val location = results.getJSONObject(0)
                                .getJSONObject("geometry")
                                .getJSONObject("location")
                            lat = location.getDouble("lat")
                            lng = location.getDouble("lng")
                        } else {
                            lat = 0.0
                            lng = 0.0
                        }

                        val site = Site(
                            id = id,
                            address = address,
                            siteRadius = siteRadius,
                            lat = lat,
                            lng = lng,
                            isValidLocation = results.length() > 0,
                            totalWorkers = totalWorkers,
                            scaffoldingCount = scaffoldingCount,
                            startDate = startDate,
                            estimatedEndDate = estimatedEndDate,
                            securityCode = securityCode
                        )
                        sitesList.add(site)

                        runOnUiThread {
                            // Sort the sitesList by siteID
                            sitesList.sortBy { it.id }
                            sitesAdapter.notifyDataSetChanged()
                            if (isMapInitialized) {
                                addMarkersToMap()
                            }
                        }
                    } ?: runOnUiThread {
                        Toast.makeText(
                            this@SitesOverviewActivity,
                            "Empty response from geocoding API",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: JSONException) {
                    Log.e("SitesOverviewActivity", "Error parsing geocode response: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@SitesOverviewActivity,
                            "Error parsing geocode response",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("SitesOverviewActivity", "Unexpected error: ${e.message}")
                    runOnUiThread {
                        Toast.makeText(
                            this@SitesOverviewActivity,
                            "An unexpected error occurred during geocoding",
                            Toast.LENGTH_SHORT
                        ).show()
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
        val sitesCopy = ArrayList(sitesList)
        for (site in sitesCopy) {
            if (site.isValidLocation) {
                val position = LatLng(site.lat, site.lng)
                val marker = mMap.addMarker(
                    MarkerOptions()
                        .position(position)
                        .title("Site: ${site.address}")
                )
                marker?.tag = site.id
                mMap.addCircle(
                    CircleOptions()
                        .center(position)
                        .radius(site.siteRadius)
                        .strokeColor(0xFF0000FF.toInt())
                        .fillColor(0x300000FF)
                )
            }
        }

        mMap.setOnMarkerClickListener { marker ->
            val siteId = marker.tag as? Int
            val position = sitesList.indexOfFirst { it.id == siteId }
            if (position != -1) {
                sitesRecyclerView.smoothScrollToPosition(position)
            }
            true
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
            val position = LatLng(site.lat, site.lng)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
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

    inner class SitesAdapter(private val sites: List<Site>, private val managerCF: String) :
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
            val qrButton: Button = view.findViewById(R.id.qrButton)
            val binButton: ImageButton = view.findViewById(R.id.binButton)

            init {
                view.setOnClickListener {
                    val position = adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        centerItem(position)
                    }
                }
            }
        }

        private fun centerItem(position: Int) {
            val layoutManager = sitesRecyclerView.layoutManager as LinearLayoutManager
            val scroller = object : LinearSmoothScroller(this@SitesOverviewActivity) {
                override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                    return 0.1f
                }

                override fun getVerticalSnapPreference(): Int {
                    return SNAP_TO_START
                }
            }
            scroller.targetPosition = position
            layoutManager.startSmoothScroll(scroller)

            // Center the map on the corresponding site
            centerMapOnSiteIfValid(sites[position])
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.site_item, parent, false)
            return SiteViewHolder(view)
        }

        override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
            val site = sites[position]
            holder.id.text = "${site.id}"
            holder.address.text = "${site.address}"
            holder.radius.text = "${site.siteRadius} meters"
            holder.totalWorkers.text = "${site.totalWorkers}"
            holder.scaffoldingCount.text = "${site.scaffoldingCount}"
            holder.startDate.text = "${site.startDate}"
            holder.estimatedEndDate.text = "${site.estimatedEndDate}"
            holder.securityCode.text = "${site.securityCode}"

            val layoutParams = holder.itemView.layoutParams as ViewGroup.MarginLayoutParams
            if (position == sites.size - 1) {
                // Add extra margin to the last item
                layoutParams.bottomMargin = 300 // Adjust this value as needed
            } else {
                layoutParams.bottomMargin = 0
            }
            holder.itemView.layoutParams = layoutParams

            holder.qrButton.setOnClickListener {
                val managerCF =
                    this@SitesOverviewActivity.intent.getStringExtra("managerCF") ?: ""
                if (managerCF.isNotEmpty()) {
                    val intent = Intent(
                        this@SitesOverviewActivity,
                        QRGenerationActivity::class.java
                    )
                    intent.putExtra("managerCF", managerCF)
                    intent.putExtra("SiteID", site.id.toString())
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(
                        this@SitesOverviewActivity,
                        "ManagerCF is missing.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            holder.binButton.setOnClickListener {
                val context = holder.itemView.context
                val siteId = site.id
                Toast.makeText(context, "Deleting site: $siteId", Toast.LENGTH_SHORT).show()

                val url = "https://noemigiustini01.pythonanywhere.com/communication/delete_communication_site/$siteId"
                val request = Request.Builder()
                    .url(url)
                    .delete()
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        (context as? Activity)?.runOnUiThread {
                            Toast.makeText(
                                context,
                                "Failed to Site's Communication: ${e.localizedMessage}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        (context as? Activity)?.runOnUiThread {
                            if (response.isSuccessful) {
                                val url2 = "https://noemigiustini01.pythonanywhere.com/site/delete/$siteId"
                                val request2 = Request.Builder()
                                    .url(url2)
                                    .delete()
                                    .build()

                                client.newCall(request2).enqueue(object : Callback {
                                    override fun onFailure(call: Call, e: IOException) {
                                        (context as? Activity)?.runOnUiThread {
                                            Toast.makeText(
                                                context,
                                                "Failed to delete site: ${e.localizedMessage}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }

                                    override fun onResponse(call: Call, response: Response) {
                                        (context as? Activity)?.runOnUiThread {
                                            if (response.isSuccessful) {
                                                Toast.makeText(
                                                    context,
                                                    "Site deleted successfully",
                                                    Toast.LENGTH_SHORT
                                                ).show()

                                                // Remove the site from the list and update the RecyclerView
                                                val position = holder.adapterPosition
                                                sitesList.removeAt(position)
                                                notifyItemRemoved(position)
                                                notifyItemRangeChanged(position, sitesList.size)
                                                addMarkersToMap()
                                            } else {
                                                val errorMessage =
                                                    response.body?.string() ?: "Unknown error"
                                                Toast.makeText(
                                                    context,
                                                    "Failed to delete site: $errorMessage",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                })

                            } else {
                                val errorMessage =
                                    response.body?.string() ?: "Unknown error"
                                Toast.makeText(
                                    context,
                                    "Failed to delete site's communication: $errorMessage",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                })
            }
        }

        override fun getItemCount() = sites.size
    }
}
