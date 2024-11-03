package com.safetyhat.macc

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import okhttp3.*
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.mindrot.jbcrypt.BCrypt
import java.io.IOException
import java.util.regex.Pattern

class ManagerInfoActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_info)

        val managerCF = intent.getStringExtra("managerCF").toString()

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
                R.id.nav_site_overview_manager -> {
                    val intent = Intent(this, SitesOverviewActivity::class.java)
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

        fetchManagerInfo(managerCF.toString())

        val changePasswordButton = findViewById<Button>(R.id.change_password_button)
        changePasswordButton.setOnClickListener {
            val newPassword = findViewById<EditText>(R.id.new_password_field).text.toString()
            if (newPassword.isNotEmpty()) {
                val passwordPattern = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@#\$%^&+=!]).{8,}$"
                if (!Pattern.matches(passwordPattern, newPassword)) {
                    Toast.makeText(this, "Password must be at least 8 characters,with uppercase, lowercase, number, and one of [@#$%^&+=!\\]", Toast.LENGTH_LONG).show()
                }else {
                    val hashedPassword = hashPassword(newPassword)
                    updatePassword(managerCF.toString(), hashedPassword)
                }
            } else {
                Toast.makeText(this, "Please enter a new password", Toast.LENGTH_SHORT).show()
            }
        }

    }

    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    private fun updatePassword(cf: String, newPassword: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/manager/updatepassword/$cf"
        val requestBody = JSONObject()
        requestBody.put("Password", newPassword)
        val body = requestBody.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .put(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ManagerInfoActivity, "Failed to update password. Try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@ManagerInfoActivity, "Password updated successfully", Toast.LENGTH_SHORT).show()
                        findViewById<EditText>(R.id.new_password_field).text.clear()
                    } else {
                        Toast.makeText(this@ManagerInfoActivity, "Failed to update password", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun fetchManagerInfo(cf: String) {
        val url = "https://NoemiGiustini01.pythonanywhere.com/manager/read?cf=$cf"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ManagerInfoActivity, "Network error. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                val jsonObject = JSONObject(responseData ?: "")

                runOnUiThread {
                    if (response.isSuccessful && !jsonObject.has("message")) {
                        val firstName = jsonObject.optString("FirstName", "N/A")
                        val lastName = jsonObject.optString("LastName", "N/A")
                        val cf = jsonObject.optString("CF", "N/A")
                        val birthday = jsonObject.optString("BirthDate", "N/A")
                        val formattedBirthDate = birthday.split(" ")[1] + " " + birthday.split(" ")[2] + " " + birthday.split(" ")[3]
                        val phoneNumber = jsonObject.optString("Telephone", "N/A")

                        findViewById<TextView>(R.id.first_name_text).text = firstName
                        findViewById<TextView>(R.id.last_name_text).text = lastName
                        findViewById<TextView>(R.id.cf_text).text = cf
                        findViewById<TextView>(R.id.birthday_text).text = formattedBirthDate
                        findViewById<TextView>(R.id.telephone_text).text = phoneNumber
                    } else {
                        Toast.makeText(this@ManagerInfoActivity, "Manager not found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
