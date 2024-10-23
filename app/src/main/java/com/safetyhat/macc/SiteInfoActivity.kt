package com.safetyhat.macc
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

class SiteInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_site_info)

        val siteId = "12345"
        val startDate = "01/01/2023"
        val endDate = "31/12/2023"
        val totalWorkers = 10
        val scaffoldingNumber = "ABC123"
        val siteAddress = "123 Main St"
        val siteRadius = 100.0
        val siteManagerInfo = "John Doe"

        findViewById<TextView>(R.id.site_id_text).text = siteId
        findViewById<TextView>(R.id.start_date_text).text = startDate
        findViewById<TextView>(R.id.end_date_text).text = endDate
        findViewById<TextView>(R.id.total_worker_text).text = totalWorkers.toString()
        findViewById<TextView>(R.id.scaffolding_number_text).text = scaffoldingNumber
        findViewById<TextView>(R.id.site_address_text).text = siteAddress
        findViewById<TextView>(R.id.site_radius_text).text = siteRadius.toString()
        findViewById<TextView>(R.id.site_manager_info_text).text = siteManagerInfo
    }
}
