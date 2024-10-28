package com.safetyhat.macc

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject

class QRGenerationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_generation)

        val startDate = intent.getStringExtra("StartDate")
        val estimatedEndDate = intent.getStringExtra("EstimatedEndDate")
        val totalWorkers = intent.getStringExtra("TotalWorkers")
        val scaffoldingCount = intent.getStringExtra("ScaffoldingCount")
        val address = intent.getStringExtra("Address")
        val siteRadius = intent.getStringExtra("SiteRadius")
        val securityCode = intent.getStringExtra("SecurityCode")
        val managerCF = intent.getStringExtra("ManagerCF")

        val jsonInfo = JSONObject().apply {
            put("StartDate", startDate)
            put("EstimatedEndDate", estimatedEndDate)
            put("TotalWorkers", totalWorkers)
            put("ScaffoldingCount", scaffoldingCount)
            put("Address", address)
            put("SiteRadius", siteRadius)
            put("SecurityCode", securityCode)
            put("ManagerCF", managerCF)
        }

        val qrCodeImageView = findViewById<ImageView>(R.id.qr_code_frame)
        val qrBitmap = generateQRCode(jsonInfo.toString())
        qrCodeImageView.setImageBitmap(qrBitmap)

        val downloadButton = findViewById<Button>(R.id.download_qr_button)
        downloadButton.setOnClickListener {
            saveQRCodeToGallery(qrBitmap)
        }

        val menuIcon = findViewById<ImageView>(R.id.menu_icon)
        menuIcon.setOnClickListener {
            val intent = Intent(this, ManagermenuActivity::class.java)
            intent.putExtra("managerCF", managerCF)
            startActivity(intent)
        }
    }

    private fun generateQRCode(data: String): Bitmap {
        val width = 350
        val height = 430
        val qrCodeWriter = QRCodeWriter()
        val bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    private fun saveQRCodeToGallery(bitmap: Bitmap) {
        val filename = "QRCode_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/QRCodeImages")
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            contentResolver.openOutputStream(uri).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    Toast.makeText(this, "QR Code saved to gallery", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to save QR Code", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Failed to save QR Code", Toast.LENGTH_SHORT).show()
        }
    }
}
