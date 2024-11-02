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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import okhttp3.*

class SiteInfoActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private val client = OkHttpClient()

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
                R.id.nav_alert_worker -> {
                    val intent = Intent(this, AlertActivity::class.java)
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

        fetchWorkerInfo(workerCF.toString())

    }

    private fun fetchWorkerInfo(cf: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/worker/read?cf=$cf"
        val request = Request.Builder().url(url).get().build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@SiteInfoActivity,
                        "Network error. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData ?: "")

                runOnUiThread {
                    if (response.isSuccessful && !jsonObject.has("message")) {
                        val SiteCode = jsonObject.optString("SiteCode", "N/A")
                        fetchSiteInfo(SiteCode.toInt())
                    } else {
                        Toast.makeText(
                            this@SiteInfoActivity,
                            "Worker not found.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun fetchSiteInfo(ID: Int) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/site/read?id=$ID"
        val request = Request.Builder().url(url).get().build()


        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@SiteInfoActivity,
                        "Network error. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData ?: "")

                runOnUiThread {
                    if (response.isSuccessful && !jsonObject.has("message")) {

                        val Address = jsonObject.optString("Address", "N/A")
                        val ManagerCF = jsonObject.optString("ManagerCF", "N/A")
                        val ScaffoldingCount = jsonObject.optString("ScaffoldingCount", "N/A")
                        val EstimatedEndDate = jsonObject.optString("EstimatedEndDate", "N/A")
                        val formattedEstimatedEndDate = EstimatedEndDate.split(" ")[1] + " " + EstimatedEndDate.split(" ")[2] + " " + EstimatedEndDate.split(" ")[3]
                        val StartDate = jsonObject.optString("StartDate", "N/A")
                        val formattedStartDate = StartDate.split(" ")[1] + " " + StartDate.split(" ")[2] + " " + StartDate.split(" ")[3]
                        val TotalWorkers = jsonObject.optString("TotalWorkers", "N/A")
                        val SiteRadius = jsonObject.optString("SiteRadius", "N/A")

                        findViewById<TextView>(R.id.site_id_text).text = ID.toString()
                        findViewById<TextView>(R.id.start_date_text).text = formattedStartDate
                        findViewById<TextView>(R.id.end_date_text).text = formattedEstimatedEndDate
                        findViewById<TextView>(R.id.total_worker_text).text = TotalWorkers
                        findViewById<TextView>(R.id.scaffolding_number_text).text = ScaffoldingCount
                        findViewById<TextView>(R.id.site_address_text).text = Address
                        findViewById<TextView>(R.id.site_radius_text).text = SiteRadius
                        findViewById<TextView>(R.id.site_manager_info_text).text = ManagerCF
                    } else {
                        Toast.makeText(
                            this@SiteInfoActivity,
                            "Site not found.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }
}
