package com.safetyhat.macc

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.RectF
import android.Manifest
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var faceOverlayView: FaceOverlayView
    private lateinit var cameraExecutor: ExecutorService

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking() // opzionale
            .build()
        FaceDetection.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face)

        cameraPreview = findViewById(R.id.camera_preview)
        faceOverlayView = findViewById(R.id.faceOverlay)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraPreview.post {
                startCamera()
            }
        } else {
            Toast.makeText(this, "Permessi non garantiti per la fotocamera", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.button_picture).setOnClickListener {
            takePhoto()
        }

    }

    private lateinit var imageCapture: ImageCapture

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Configuriamo l'anteprima
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(cameraPreview.surfaceProvider)

            // Configuriamo ImageCapture
            imageCapture = ImageCapture.Builder().build()

            // Configuriamo ImageAnalysis (già presente nel tuo codice)
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            // Bindiamo tutto
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            cameraProvider.unbindAll()

            try {
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                exc.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(this))
    }


    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                // Convertiamo i bounding box dei volti in coordinate del viewfinder
                val faceRects = faces.map { face ->
                    // Bounding box del volto nell'immagine
                    val boundingBox = face.boundingBox

                    // Calcoliamo le proporzioni per estendere il rettangolo
                    val extraHeightTop = boundingBox.height() * 0.3f  // Aggiungiamo il 30% sopra
                    val extraHeightBottom = boundingBox.height() * 0.4f // Aggiungiamo solo il 30% sotto
                    val extraWidth = boundingBox.width() * 0.2f // Aggiungiamo il 20% in larghezza

                    val adjustedBox = RectF(
                        boundingBox.left.toFloat() - extraWidth, // Estendi a sinistra
                        boundingBox.top.toFloat() - extraHeightTop, // Estendi sopra
                        boundingBox.right.toFloat() + extraWidth, // Estendi a destra
                        boundingBox.bottom.toFloat() - extraHeightBottom // Estendi sotto
                    )

                    // Convertiamo le coordinate dell'immagine in coordinate della view
                    val matrix = Matrix()
                    val previewWidth = cameraPreview.width.toFloat()
                    val previewHeight = cameraPreview.height.toFloat()

                    // Configuriamo la trasformazione
                    val imageRect = RectF(0f, 0f, image.width.toFloat(), image.height.toFloat())
                    val viewRect = RectF(0f, 0f, previewWidth, previewHeight)
                    matrix.setRectToRect(imageRect, viewRect, Matrix.ScaleToFit.FILL)

                    val faceRect = RectF(adjustedBox)
                    matrix.mapRect(faceRect)

                    faceRect.offset(200f, 0f) // Sposta il bounding box di 50 pixel verso destra

                    faceRect
                }

                faceRects.forEach { Log.d("FaceActivity", "Bounding box adattato: $it") }

                faceOverlayView.setFaces(faceRects)
            }
            .addOnFailureListener {
                it.printStackTrace()
                faceOverlayView.setFaces(emptyList())
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun takePhoto() {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/QRCodeImages"
            )
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri == null) {
            Toast.makeText(this, "Errore nel salvataggio della foto", Toast.LENGTH_SHORT).show()
            return
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            resolver, uri, contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@FaceActivity, "Foto salvata nella galleria", Toast.LENGTH_SHORT).show()
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    Toast.makeText(this@FaceActivity, "Errore nello scattare la foto", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}