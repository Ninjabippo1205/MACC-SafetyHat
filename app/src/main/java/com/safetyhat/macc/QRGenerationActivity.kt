package com.safetyhat.macc

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.io.IOException

class QRGenerationActivity : AppCompatActivity() {
    private lateinit var qrBitmap: Bitmap
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_qr_generation)

            val siteID = intent.getStringExtra("SiteID")
            val managerCF = intent.getStringExtra("managerCF")

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

            if (siteID != null) {
                try {
                    val jsonInfo = JSONObject().apply {
                        put("ID", siteID.toInt())
                    }
                    val qrCodeImageView = findViewById<ImageView>(R.id.qr_code_frame)
                    qrBitmap = generateQRCode(jsonInfo.toString())
                    qrCodeImageView.setImageBitmap(qrBitmap)
                } catch (e: Exception) {
                    Log.e("QRGenerationActivity", "Error generating QR code: ${e.message}")
                    Toast.makeText(this, "Failed to generate QR Code", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Failed to retrieve site ID", Toast.LENGTH_SHORT).show()
            }

            val downloadButton = findViewById<Button>(R.id.download_qr_button)
            downloadButton.setOnClickListener {
                if (::qrBitmap.isInitialized) {
                    saveQRCodeToGallery(qrBitmap, siteID.toString())
                } else {
                    Toast.makeText(this, "QR Code not available to save", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("QRGenerationActivity", "Error in onCreate: ${e.message}")
            Toast.makeText(this, "An error occurred during initialization.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun generateQRCode(data: String): Bitmap {
        return try {
            val width = 350
            val height = 430
            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x,
                        y,
                        if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    )
                }
            }
            bitmap
        } catch (e: WriterException) {
            Log.e("QRGenerationActivity", "QR Code generation error: ${e.message}")
            Toast.makeText(this, "Error generating QR Code", Toast.LENGTH_SHORT).show()
            Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565) // Return an empty bitmap
        }
    }

    private fun saveQRCodeToGallery(bitmap: Bitmap, siteID: String) {
        try {
            val filename = "QRCode_${siteID}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/QRCodeImages"
                )
            }

            val uri =
                contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri).use { outputStream ->
                    if (outputStream != null) {
                        if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                            Toast.makeText(this, "QR Code saved to gallery", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast.makeText(this, "Failed to save QR Code", Toast.LENGTH_SHORT)
                                .show()
                        }
                    } else {
                        Toast.makeText(this, "Failed to save QR Code", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Failed to save QR Code", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e("QRGenerationActivity", "Error saving QR Code: ${e.message}")
            Toast.makeText(this, "Error saving QR Code", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("QRGenerationActivity", "Unexpected error: ${e.message}")
            Toast.makeText(this, "An unexpected error occurred", Toast.LENGTH_SHORT).show()
        }
    }
}
