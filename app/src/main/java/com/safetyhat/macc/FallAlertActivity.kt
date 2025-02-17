package com.safetyhat.macc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import android.telephony.SmsManager
import androidx.activity.OnBackPressedCallback

class FallAlertActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var timer: CountDownTimer
    private var countdownTime = 10 // Tempo in secondi per il conto alla rovescia
    private val client = OkHttpClient()
    private val activityScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var siteID: Int = -1 // Si assume che siteID venga passato tramite Intent
    private var workerCF: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_fall_alert)

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            timerTextView = findViewById(R.id.fall_alert_timer)
            val btnImOk = findViewById<Button>(R.id.btn_im_ok)

            // Recupera siteID e workerCF dall'Intent
            siteID = intent.getIntExtra("siteID", -1)
            workerCF = intent.getStringExtra("workerCF")

            if (siteID == -1 || workerCF == null) {
                Toast.makeText(this, "Missing siteID or workerCF.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Inizializza il conto alla rovescia
            timer = object : CountDownTimer((countdownTime * 1000).toLong(), 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secondsLeft = (millisUntilFinished / 1000).toInt()
                    timerTextView.text = secondsLeft.toString()
                }

                override fun onFinish() {
                    Log.d("FallAlertActivity", "Conto alla rovescia terminato.")
                    // Recupera sia i numeri dei worker che quello del manager e invia l'SMS d'emergenza
                    fetchContactsBySiteID(siteID)
                    // Torna alla pagina precedente
                    finish()
                }
            }

            timer.start()

            btnImOk.setOnClickListener {
                timer.cancel()
                Toast.makeText(this, "Felice di sapere che stai bene!", Toast.LENGTH_SHORT).show()
                finish() // Chiude l'Activity
            }

            // Disabilita il pulsante indietro
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Non fare nulla (consume il back press)
                }
            })

        } catch (e: Exception) {
            Log.e("FallAlertActivity", "Errore in onCreate: ${e.message}")
            Toast.makeText(this, "Si è verificato un errore.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * Recupera i contatti (worker e manager) associati al sito e invia l'SMS d'emergenza.
     */
    private fun fetchContactsBySiteID(siteID: Int) {
        // Prima, recupera i worker
        val workersUrl = "https://NoemiGiustini01.pythonanywhere.com/worker/read_all_by_siteid"
        val workersRequestUrl = workersUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("siteID", siteID.toString())
            ?.build()

        if (workersRequestUrl == null) {
            Log.e("FallAlertActivity", "URL dei worker malformato.")
            return
        }

        val workersRequest = Request.Builder()
            .url(workersRequestUrl)
            .get()
            .build()

        client.newCall(workersRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FallAlertActivity", "Recupero worker fallito: ${e.message}")
                // Se fallisce il recupero dei worker, prova a recuperare comunque il manager
                fetchManagerPhoneBySiteID(siteID) { managerPhone ->
                    if (managerPhone != null) {
                        sendEmergencyMessage(listOf(managerPhone), "A fall has been detected on site. Please provide assistance immediately.")
                    } else {
                        Log.d("FallAlertActivity", "Nessun contatto trovato per il sito $siteID.")
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("FallAlertActivity", "Risposta non positiva per i worker: ${response.code}")
                    // Prova comunque a recuperare il manager
                    fetchManagerPhoneBySiteID(siteID) { managerPhone ->
                        if (managerPhone != null) {
                            sendEmergencyMessage(listOf(managerPhone), "A fall has been detected on site. Please provide assistance immediately.")
                        } else {
                            Log.d("FallAlertActivity", "Nessun contatto trovato per il sito $siteID.")
                        }
                    }
                    return
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.e("FallAlertActivity", "Corpo della risposta vuoto per i worker.")
                    // Prova a recuperare il manager se la risposta è vuota
                    fetchManagerPhoneBySiteID(siteID) { managerPhone ->
                        if (managerPhone != null) {
                            sendEmergencyMessage(listOf(managerPhone), "A fall has been detected on site. Please provide assistance immediately.")
                        } else {
                            Log.d("FallAlertActivity", "Nessun contatto trovato per il sito $siteID.")
                        }
                    }
                    return
                }

                try {
                    // Estrae i numeri dei worker
                    val phoneNumbers = parseWorkerResponse(responseBody).toMutableList()
                    // Recupera il numero del manager e lo aggiunge alla lista
                    fetchManagerPhoneBySiteID(siteID) { managerPhone ->
                        if (managerPhone != null) {
                            phoneNumbers.add(managerPhone)
                        } else {
                            Log.d("FallAlertActivity", "Nessun manager trovato per il sito $siteID.")
                        }
                        if (phoneNumbers.isNotEmpty()) {
                            sendEmergencyMessage(
                                phoneNumbers,
                                "A fall has been detected on site. Please provide assistance immediately."
                            )
                        } else {
                            Log.d("FallAlertActivity", "Nessun contatto trovato per il sito $siteID.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FallAlertActivity", "Errore nel parsing della risposta dei worker: ${e.message}")
                }
            }
        })
    }

    /**
     * Estrae i numeri di telefono dai dati JSON ricevuti per i worker.
     */
    private fun parseWorkerResponse(response: String): List<String> {
        val phoneNumbers = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val worker = jsonArray.getJSONObject(i)
                val phoneNumber = worker.optString("PhoneNumber")
                if (phoneNumber.isNotEmpty()) {
                    phoneNumbers.add(phoneNumber)
                }
            }
        } catch (e: JSONException) {
            Log.e("FallAlertActivity", "Errore nel parsing JSON: ${e.message}")
        }
        return phoneNumbers
    }

    /**
     * Invia l'SMS d'emergenza ai numeri ricevuti. Se possibile, include la posizione corrente.
     */
    private fun sendEmergencyMessage(phoneNumbers: List<String>, message: String) {
        // Elimina eventuali duplicati
        val uniquePhoneNumbers = phoneNumbers.distinct()

        // Verifica i permessi per la localizzazione
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("FallAlertActivity", "Permesso per la localizzazione non concesso")
            val fallbackMessage = "$message\nLocation: [Unable to retrieve location]"
            sendSmsToNumbers(uniquePhoneNumbers, fallbackMessage)
            return
        }

        try {
            // Recupera la posizione corrente
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude

                    // Crea un link a Google Maps con le coordinate
                    val locationLink = "https://maps.google.com/?q=$latitude,$longitude"
                    val messageWithLocation = "$message\nLocation: $locationLink"

                    // Invia l'SMS includendo la posizione
                    sendSmsToNumbers(uniquePhoneNumbers, messageWithLocation)
                } else {
                    Log.e("FallAlertActivity", "Impossibile recuperare la posizione")
                    val fallbackMessage = "$message\nLocation: [Unable to retrieve location]"
                    sendSmsToNumbers(uniquePhoneNumbers, fallbackMessage)
                }
            }.addOnFailureListener { e ->
                Log.e("FallAlertActivity", "Errore nel recupero della posizione: ${e.message}")
                val fallbackMessage = "$message\nLocation: [Unable to retrieve location]"
                sendSmsToNumbers(uniquePhoneNumbers, fallbackMessage)
            }
        } catch (e: SecurityException) {
            Log.e("FallAlertActivity", "SecurityException: ${e.message}")
            val fallbackMessage = "$message\nLocation: [Unable to retrieve location]"
            sendSmsToNumbers(uniquePhoneNumbers, fallbackMessage)
        }
    }

    /**
     * Invia un SMS ai numeri indicati.
     */
    private fun sendSmsToNumbers(phoneNumbers: List<String>, message: String) {
        val smsManager = SmsManager.getDefault()
        for (phone in phoneNumbers) {
            try {
                smsManager.sendTextMessage(phone, null, message, null, null)
                Log.d("FallAlertActivity", "SMS inviato a $phone")
            } catch (e: Exception) {
                Log.e("FallAlertActivity", "Impossibile inviare SMS a $phone: ${e.message}")
            }
        }
    }

    /**
     * Recupera il numero di telefono del manager a partire dal siteID.
     *
     * Effettua prima una chiamata per ottenere il ManagerCF dal sito,
     * poi una seconda chiamata per ottenere il numero di telefono del manager.
     */
    private fun fetchManagerPhoneBySiteID(siteID: Int, onResult: (String?) -> Unit) {
        // Chiamata per recuperare il ManagerCF dal sito
        val managerCFUrl = "https://NoemiGiustini01.pythonanywhere.com/site/read"
        val requestUrl = managerCFUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("id", siteID.toString())
            ?.build()

        if (requestUrl == null) {
            Log.e("FallAlertActivity", "URL ManagerCF malformato.")
            onResult(null)
            return
        }

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FallAlertActivity", "Recupero ManagerCF fallito: ${e.message}")
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("FallAlertActivity", "Risposta non positiva per ManagerCF: ${response.code}")
                    onResult(null)
                    return
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.e("FallAlertActivity", "Corpo della risposta vuoto per ManagerCF.")
                    onResult(null)
                    return
                }

                try {
                    val jsonObject = JSONObject(responseBody)
                    val managerCF = jsonObject.optString("ManagerCF", null)
                    if (managerCF == null) {
                        Log.d("FallAlertActivity", "ManagerCF non trovato per siteID $siteID.")
                        onResult(null)
                        return
                    }
                    // Recupera il numero di telefono del manager usando il ManagerCF
                    fetchManagerPhone(managerCF, onResult)
                } catch (e: JSONException) {
                    Log.e("FallAlertActivity", "Errore nel parsing della risposta ManagerCF: ${e.message}")
                    onResult(null)
                }
            }
        })
    }

    /**
     * Recupera il numero di telefono del manager a partire dal ManagerCF.
     */
    private fun fetchManagerPhone(managerCF: String, onResult: (String?) -> Unit) {
        val managerUrl = "https://NoemiGiustini01.pythonanywhere.com/manager/read"
        val requestUrl = managerUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("cf", managerCF)
            ?.build()

        if (requestUrl == null) {
            Log.e("FallAlertActivity", "URL ManagerPhone malformato.")
            onResult(null)
            return
        }

        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .build()

            client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("FallAlertActivity", "Recupero ManagerPhone fallito: ${e.message}")
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e("FallAlertActivity", "Risposta non positiva per ManagerPhone: ${response.code}")
                    onResult(null)
                    return
                }

                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.e("FallAlertActivity", "Corpo della risposta vuoto per ManagerPhone.")
                    onResult(null)
                    return
                }

                try {
                    val jsonObject = JSONObject(responseBody)
                    val managerPhone = jsonObject.optString("Telephone", null)
                    onResult(managerPhone)
                } catch (e: JSONException) {
                    Log.e("FallAlertActivity", "Errore nel parsing della risposta ManagerPhone: ${e.message}")
                    onResult(null)
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
        activityScope.cancel()
    }
}
