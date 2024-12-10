package com.safetyhat.macc

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.node.ModelNode
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

class ArMeasureActivity : AppCompatActivity() {

    private lateinit var arSceneView: ARSceneView
    val TAG = "AR_DEBUG"

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_measure)

        // Collega l'ARSceneView dal layout
        arSceneView = findViewById(R.id.ar_scene_view)
        // Configura la sessione ARCore
        configureARSceneView()

        // Gestione del tocco dell'utente
        arSceneView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                handleTouch(event)
            }
            true
        }
    }

    private fun configureARSceneView() {
        val session = arSceneView.session
        session?.let {
            val config = it.config.apply {
                planeFindingMode = com.google.ar.core.Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                depthMode = com.google.ar.core.Config.DepthMode.AUTOMATIC
            }
            it.configure(config)
            Log.d(TAG, "Configurazione sessione completata")
        }
    }

    private fun handleTouch(event: MotionEvent) {
        val frame = arSceneView.session?.update()

        Log.d(TAG, "Touch rilevato: X=${event.x}, Y=${event.y}")

        if (frame != null) {
            // Esegui il raycasting verso la posizione del tocco
            val hitResults = frame.hitTest(event.x, event.y)

            if (hitResults.isEmpty()) {
                Log.d(TAG, "Nessun risultato di hitTest trovato.")
                return
            }

            Log.d(TAG, "Risultati di hitTest: ${hitResults.size}")

            hitResults.firstOrNull { hit ->
                // Filtra per i trackable che sono piani e in stato di tracking
                val trackable = hit.trackable
                trackable is Plane && trackable.trackingState == TrackingState.TRACKING
            }?.let { hitResult ->
                val trackable = hitResult.trackable as Plane
                val pose = hitResult.hitPose
                Log.d(TAG,"Piano trovato - Tipo: ${trackable.type}, Posizione: (${pose.tx()}, ${pose.ty()}, ${pose.tz()})"
                )

                placeObject(hitResult)
            } ?: run {
                Log.d(TAG, "Nessun piano valido trovato nella posizione del tocco.")
            }
        } else {
            Log.e(TAG, "Impossibile ottenere il frame corrente.")
        }
    }

    private fun placeObject(hitResult: HitResult) {
        try {
            val filePath = "models/sphere.glb"

            // Verifica il file
            assets.open(filePath).use {
                Log.d(TAG, "Il file $filePath è accessibile e correttamente posizionato.")
            }

            // Caricamento del modello utilizzando loadModelAsync
            arSceneView.modelLoader.loadModelAsync(
                fileLocation = filePath,
                onResult = { model ->
                    if (model != null) {
                        Log.d(TAG, "Modello caricato con successo: $model")

                        // Creazione di un ModelNode
                        val modelNode = ModelNode(modelInstance = model.instance)

                        // Conversione della posizione
                        val translation = hitResult.hitPose.translation
                        val position = dev.romainguy.kotlin.math.Float3(
                            translation[0], // X
                            translation[1], // Y
                            translation[2]  // Z
                        )
                        modelNode.position = position

                        // Conversione entità in IntArray
                        val entities = model.instance.entities

                        // Controllo entità
                        if (entities.isEmpty()) {
                            Log.e(TAG, "Nessuna entità trovata nel modello.")
                        } else {
                            arSceneView.scene?.addEntities(entities) // Usa direttamente entities
                            Log.d(TAG, "Entità aggiunte alla scena: ${entities.size}")
                        }

                    } else {
                        Log.e(TAG, "Errore: il modello caricato è null.")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante il posizionamento del modkello: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView.destroy()
    }
}

fun debugClassMethodsAndProperties(obj: Any) {
    val clazz = obj::class
    val className = clazz.simpleName
    Log.d("DEBUG_CLASS", "Class: $className")

    // Elenca i metodi
    val methods = clazz.members.filter { it is KFunction<*> }
    Log.d("DEBUG_CLASS", "Methods:")
    methods.forEach { method ->
        Log.d("DEBUG_CLASS", " - ${method.name}")
    }

    // Elenca le proprietà
    val properties = clazz.members.filter { it is KProperty<*> }
    Log.d("DEBUG_CLASS", "Properties:")
    properties.forEach { property ->
        Log.d("DEBUG_CLASS", " - ${property.name}")
    }
}

