package com.safetyhat.macc

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.view.Surface
import android.media.MediaScannerConnection
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import androidx.exifinterface.media.ExifInterface
import java.io.FileOutputStream
import com.google.mlkit.vision.face.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var faceOverlayView: FaceOverlayView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageCapture: ImageCapture

    // Variabile per salvare i bounding box dei volti rilevati nell'immagine analizzata
    // Questi saranno i box utilizzati per il disegno sul file finale
    private var latestFaceRects: List<RectF> = emptyList()
    // Dimensioni dell'immagine analizzata (per eventuale scaling)
    private var analyzedImageWidth = 0
    private var analyzedImageHeight = 0

    private var workerCF: String = ""
    private var siteID: String = ""

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

        workerCF = intent.getStringExtra("workerCF") ?: ""
        siteID = intent.getStringExtra("siteID") ?: ""

        findViewById<Button>(R.id.button_back).setOnClickListener{
            val intent = Intent(this, WorkermenuActivity::class.java)
            intent.putExtra("workerCF", workerCF)
            intent.putExtra("siteID", siteID)
            startActivity(intent)
            finish()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraPreview.post { startCamera() }
        } else {
            Toast.makeText(this, "Permessi non garantiti per la fotocamera", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.button_picture).setOnClickListener {
            takePhoto()
        }
    }

    @Deprecated("This method has been deprecated in favor of using the\n      {@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n      The OnBackPressedDispatcher controls how back button events are dispatched\n      to one or more {@link OnBackPressedCallback} objects.")
    override fun onBackPressed() {
        super.onBackPressed()
        val workerCF = intent.getStringExtra("workerCF") ?: ""
        val siteID = intent.getStringExtra("siteID") ?: ""
        navigateBack(workerCF, siteID)
    }

    private fun navigateBack(workerCF: String, siteID: String) {
        val intent = Intent(this, WorkermenuActivity::class.java)
        intent.putExtra("workerCF", workerCF)
        intent.putExtra("siteID", siteID)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(cameraPreview.surfaceProvider)


            imageCapture = ImageCapture.Builder()
                .setTargetRotation(Surface.ROTATION_0)
                .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

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

        // Salviamo le dimensioni dell'immagine analizzata
        analyzedImageWidth = image.width
        analyzedImageHeight = image.height

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                // Creiamo i bounding box regolati senza trasformazione per la preview,
                // Li salviamo per disegnare dopo sul file.
                val faceRectsForOverlay = faces.map { face ->
                    val boundingBox = face.boundingBox
                    val extraHeightTop = boundingBox.height() * 0.3f
                    val extraHeightBottom = boundingBox.height() * 0.4f
                    val extraWidth = boundingBox.width() * 0.2f

                    RectF(
                        boundingBox.left.toFloat() - extraWidth,
                        boundingBox.top.toFloat() - extraHeightTop,
                        boundingBox.right.toFloat() + extraWidth,
                        boundingBox.bottom.toFloat() - extraHeightBottom
                    )
                }

                latestFaceRects = faceRectsForOverlay

                // Per la visualizzazione a schermo (preview), applichiamo le trasformazioni.
                val previewWidth = cameraPreview.width.toFloat()
                val previewHeight = cameraPreview.height.toFloat()
                val transformedRects = faceRectsForOverlay.map { originalRect ->
                    val matrix = Matrix()
                    val imageRect = RectF(0f, 0f, analyzedImageWidth.toFloat(), analyzedImageHeight.toFloat())
                    val viewRect = RectF(0f, 0f, previewWidth, previewHeight)
                    matrix.setRectToRect(imageRect, viewRect, Matrix.ScaleToFit.FILL)
                    val faceRect = RectF(originalRect)
                    matrix.mapRect(faceRect)

                    // Offset se necessario
                    faceRect.offset(200f, 0f)
                    faceRect
                }

                faceOverlayView.setFaces(transformedRects)
            }
            .addOnFailureListener {
                it.printStackTrace()
                faceOverlayView.setFaces(emptyList())
                latestFaceRects = emptyList()
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            val dir = File(it, "QRCodeImages")
            dir.mkdirs()
            dir
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun takePhoto() {
        val photoFile = File(
            getOutputDirectory(),
            "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(System.currentTimeMillis())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Ora modifichiamo l'immagine salvata per aggiungere i cappelli
                    addHatsToImage(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@FaceActivity, "Errore nello scattare la foto: ${exception.message}", Toast.LENGTH_SHORT).show()
                    Log.e("FaceActivity", "Errore nella cattura della foto", exception)
                }
            }
        )
    }

    private fun addHatsToImage(photoFile: File) {
        val originalBitmap = BitmapFactory.decodeFile(photoFile.absolutePath) ?: run {
            Toast.makeText(this, "Impossibile caricare l'immagine salvata", Toast.LENGTH_SHORT).show()
            return
        }

        val exif = ExifInterface(photoFile.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val correctedBitmap = rotateBitmapIfNeeded(originalBitmap, orientation)

        val mutableBitmap = correctedBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val hatBitmap = BitmapFactory.decodeResource(resources, R.drawable.hard_hat)

        // Dimensioni dell'immagine finale
        val finalWidth = mutableBitmap.width
        val finalHeight = mutableBitmap.height

        // Calcola i fattori di scala dalle dimensioni analizzate a quelle finali
        val widthScale = finalWidth.toFloat() / analyzedImageWidth.toFloat()
        val heightScale = finalHeight.toFloat() / analyzedImageHeight.toFloat()

        for (faceRect in latestFaceRects) {
            // Scala il rettangolo della faccia
            val scaledRect = RectF(
                faceRect.left * widthScale,
                faceRect.top * heightScale,
                faceRect.right * widthScale,
                faceRect.bottom * heightScale
            )

            // Calcola il centro della faccia
            val faceCenterX = scaledRect.left + (scaledRect.width() / 2)

            // Riduci la larghezza del cappellino (modifica il fattore se necessario)
            val hatScaleFactor = 0.8f
            val hatWidth = scaledRect.width() * hatScaleFactor
            val hatHeight = hatBitmap.height * (hatWidth / hatBitmap.width)

            // Posiziona il cappellino centrato, poi applica un offset per spostarlo a destra
            val horizontalOffset = 180f  // Aumenta questo valore se vuoi spostarlo ulteriormente a destra
            val verticalOffset = 150f
            val hatLeft = faceCenterX - (hatWidth / 2) + horizontalOffset
            val hatTop = scaledRect.top - hatHeight + verticalOffset

            val hatRect = RectF(hatLeft, hatTop, hatLeft + hatWidth, hatTop + hatHeight)

            canvas.drawBitmap(hatBitmap, null, hatRect, paint)
        }

        val fos = FileOutputStream(photoFile)
        mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        fos.flush()
        fos.close()

        MediaScannerConnection.scanFile(
            this,
            arrayOf(photoFile.absolutePath),
            null
        ) { path, uri ->
            Log.d("FaceActivity", "Scansione completata: $path, uri: $uri")
        }

        Toast.makeText(this, "Foto salvata", Toast.LENGTH_SHORT).show()
    }



    // Funzione di supporto per ruotare il bitmap se necessario
    private fun rotateBitmapIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            // ExifInterface.ORIENTATION_NORMAL -> nessuna rotazione
            else -> return bitmap
        }

        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotatedBitmap
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
