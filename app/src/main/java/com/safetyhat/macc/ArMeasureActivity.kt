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
import dev.romainguy.kotlin.math.*
import java.lang.Math.copySign
import kotlin.math.*

class ArMeasureActivity : AppCompatActivity() {
    private lateinit var arSceneView: ARSceneView
    private val TAG = "AR_DEBUG"
    private val spherePositions = mutableListOf<Float3>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_measure)

        arSceneView = findViewById(R.id.ar_scene_view)
        configureARSceneView()

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
        }
    }

    private fun handleTouch(event: MotionEvent) {
        val frame = arSceneView.session?.update()

        if (frame != null) {
            val hitResults = frame.hitTest(event.x, event.y)
            if (hitResults.isEmpty()) {
                return
            }

            hitResults.firstOrNull { hit ->
                val trackable = hit.trackable
                trackable is Plane && trackable.trackingState == TrackingState.TRACKING
            }?.let { hitResult ->
                val pose = hitResult.hitPose
                val position = Float3(pose.tx(), pose.ty(), pose.tz())
                val plane = hitResult.trackable as Plane
                val planeNormal = when {
                    plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING -> Float3(0f, 1f, 0f)
                    plane.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING -> Float3(0f, -1f, 0f)
                    else -> Float3(0f, 0f, -1f) // Per piani verticali
                }
                placeSphere(position, planeNormal)
            } ?: run {
                Log.d(TAG, "Nessun piano valido trovato nella posizione del tocco.")
            }
        } else {
            Log.e(TAG, "Impossibile ottenere il frame corrente.")
        }
    }

    private fun placeSphere(position: Float3, planeNormal: Float3) {
        val filePath = "models/sphere2.glb"
        try {
            arSceneView.modelLoader.loadModelAsync(
                fileLocation = filePath,
                onResult = { model ->
                    if (model != null) {
                        val modelNode = ModelNode(modelInstance = model.instance)
                        modelNode.position = position
                        modelNode.scale = Float3(1.5f, 1.5f, 1.5f)
                        val entities = model.instance.entities
                        if (entities.isEmpty()) {
                            Log.e(TAG, "Nessuna entità trovata nel modello sfera.")
                        } else {
                            arSceneView.scene?.addEntities(entities)
                            spherePositions.add(position)
                            // Se c'è più di una sfera, disegna il cilindro tra l'ultima e la penultima
                            if (spherePositions.size > 1) {
                                val lastIndex = spherePositions.size - 1
                                val start = spherePositions[lastIndex - 1]
                                val end = spherePositions[lastIndex]
                                placeCylinderBetweenPoints(start, end, planeNormal)
                                // Calcola la distanza e la logga
                                val dist = distance(start, end)
                            }
                        }
                    } else {
                        Log.e(TAG, "Errore: il modello della sfera è null.")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante il posizionamento della sfera: ${e.message}", e)
        }
    }

    /**
     * Inserisce un cilindro tra due punti e lo orienta correttamente con un quaternion.
     */
    private fun placeCylinderBetweenPoints(start: Float3, end: Float3, planeNormal: Float3) {
        val cylinderPath = "models/cylinder.glb"
        try {
            arSceneView.modelLoader.loadModelAsync(
                fileLocation = cylinderPath,
                onResult = { model ->
                    if (model != null) {
                        val modelNode = ModelNode(modelInstance = model.instance)

                        // Punto medio (centro) del cilindro
                        val mid = (start + end) * 0.5f
                        modelNode.position = mid

                        // Direzione e lunghezza
                        val dir = end - start
                        val length = distance(start, end)
                        val dirNorm = normalize(dir)

                        // Proietta dirNorm sul piano parallelo a planeNormal
                        val projectedDirUnnormalized = dirNorm - planeNormal * dot(dirNorm, planeNormal)
                        val projectedDir = if (length(projectedDirUnnormalized) > 1e-6f) {
                            normalize(projectedDirUnnormalized)
                        } else {
                            // Direzione quasi parallela alla normal, scegli un asse ortogonale
                            if (abs(planeNormal.x) > abs(planeNormal.z)) {
                                normalize(cross(planeNormal, Float3(0f, 0f, 1f)))
                            } else {
                                normalize(cross(planeNormal, Float3(1f, 0f, 0f)))
                            }
                        }

                        // Calcola il quaternion che ruota (0,1,0) -> projectedDir
                        val up = Float3(0f, 1f, 0f)
                        val qRot = calculateQuaternionBetween(up, projectedDir)

                        // Converti il quaternion in angoli di Eulero (gradi)
                        val eulerAngles = quaternionToEulerAngles(qRot)
                        modelNode.rotation = eulerAngles

                        // Scala: la "linea" avrà spessore 0.02, e altezza pari a "length"
                        modelNode.scale = Float3(0.005f, length, 0.001f)

                        // Aggiungi il cilindro alla scena
                        val entities = model.instance.entities
                        if (entities.isEmpty()) {
                            Log.e(TAG, "Nessuna entità trovata nel modello cilindro.")
                        } else {
                            arSceneView.scene?.addEntities(entities)
                        }
                    } else {
                        Log.e(TAG, "Errore: il modello del cilindro è null.")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Errore durante il posizionamento del cilindro: ${e.message}", e)
        }
    }

    /**
     * Calcola il quaternion che ruota l'asse `from` verso l'asse `to`.
     * Restituisce un Float4(x, y, z, w) come [qx, qy, qz, qw].
     */
    private fun calculateQuaternionBetween(from: Float3, to: Float3): Float4 {
        val EPSILON = 1e-6f
        val f = normalize(from)
        val t = normalize(to)
        val dotVal = dot(f, t)

        return when {
            // Se i vettori sono quasi paralleli (angolo ~ 0°)
            dotVal > (1f - EPSILON) -> {
                // Niente rotazione
                Float4(0f, 0f, 0f, 1f)
            }
            // Se i vettori sono quasi opposti (angolo ~ 180°)
            dotVal < (-1f + EPSILON) -> {
                // Ruota di 180° attorno a un asse ortogonale a `f`
                val orthAxis = if (abs(f.x) > abs(f.z)) {
                    normalize(cross(f, Float3(0f, 0f, 1f)))
                } else {
                    normalize(cross(f, Float3(1f, 0f, 0f)))
                }
                fromAxisAngle(orthAxis, dev.romainguy.kotlin.math.PI.toFloat())
            }
            else -> {
                val axis = cross(f, t)
                val angle = acos(dotVal)
                fromAxisAngle(axis, angle)
            }
        }
    }

    /**
     * Costruisce un quaternion (x,y,z,w) da un asse di rotazione normalizzato e un angolo in radianti.
     */
    private fun fromAxisAngle(axis: Float3, angle: Float): Float4 {
        val normAxis = normalize(axis)
        val halfAngle = angle / 2f
        val s = sin(halfAngle)
        val w = cos(halfAngle)
        val x = normAxis.x * s
        val y = normAxis.y * s
        val z = normAxis.z * s
        return Float4(x, y, z, w)
    }

    /**
     * Converte un quaternion (x, y, z, w) in angoli di Eulero (pitch, yaw, roll) in gradi.
     * Ritorna Float3(rotX, rotY, rotZ).
     */
    private fun quaternionToEulerAngles(q: Float4): Float3 {
        // q = (x, y, z, w)
        // Equazioni standard di conversione quat->euler (intrinsic Tait-Bryan angles):
        val sinr_cosp = 2f * (q.w * q.x + q.y * q.z)
        val cosr_cosp = 1f - 2f * (q.x * q.x + q.y * q.y)
        val roll = atan2(sinr_cosp, cosr_cosp) // X

        val sinp = 2f * (q.w * q.y - q.z * q.x)
        val pitch = if (abs(sinp) >= 1f) {
            copySign(dev.romainguy.kotlin.math.PI / 2f, sinp) // usa 90 gradi se fuori range
        } else {
            asin(sinp)
        }

        val siny_cosp = 2f * (q.w * q.z + q.x * q.y)
        val cosy_cosp = 1f - 2f * (q.y * q.y + q.z * q.z)
        val yaw = atan2(siny_cosp, cosy_cosp) // Z

        // Conversione da radianti a gradi
        val radToDeg = 180f / dev.romainguy.kotlin.math.PI.toFloat()
        return Float3(
            roll * radToDeg,
            pitch * radToDeg,
            yaw * radToDeg
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        arSceneView.destroy()
    }

    // Funzione per calcolare la distanza tra due punti
    private fun distance(a: Float3, b: Float3): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val dz = b.z - a.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}