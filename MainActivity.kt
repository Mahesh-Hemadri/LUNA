package com.example.luna

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.location.LocationManager
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.content.Context
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var audioManager: AudioManager
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isListening = false
    private var lastShakeTime: Long = 0
    private val shakeThreshold = 20.0f
    private val shakeCooldown = 1000 // 1-second cooldown
    private lateinit var mediaRecorder: MediaRecorder
    private var audioFilePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize components
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Check and request necessary permissions
        checkAndRequestPermissions()

        // Initialize speech recognizer
        initializeSpeechRecognizer()

        // Create notification channel for Android Oreo+
        createNotificationChannel()

        // Button to start listening
        val btnStartListening: Button = findViewById(R.id.btnStartListening)
        btnStartListening.setOnClickListener {
            startContinuousListening()
        }

        // Register accelerometer sensor listener
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            Toast.makeText(this, "Accelerometer not available", Toast.LENGTH_SHORT).show()
        }

        // Start location service
        startLocationService()  // Added location service
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun checkAndRequestPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )

        // Check if permissions are already granted, request them if not
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 1)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Default Channel"
            val description = "Channel for default notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("default", name, importance).apply {
                setDescription(description)
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
    }

    private fun handleSOS() {
        // Get the current location (latitude and longitude)
        getCurrentLocation { location ->
            // Send the SOS message with the location
            sendSOSMessage(location)

            // Start recording audio as part of the SOS action
            startRecording()
        }
    }

    private fun startContinuousListening() {
        if (isListening) return

        muteSystemSounds()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let {
                    if ("help" in it.lowercase()) {
                        Toast.makeText(this@MainActivity, "SOS Triggered!", Toast.LENGTH_SHORT).show()
                        handleSOS()
                    }
                }
                restartListeningWithDelay(intent, 3000)
            }

            override fun onError(error: Int) {
                handleRecognitionError(error, intent)
            }

            override fun onEndOfSpeech() {
                unmuteSystemSounds()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        isListening = true
        speechRecognizer.startListening(intent)
    }

    private fun restartListeningWithDelay(intent: Intent, delayMillis: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (isListening) {
                speechRecognizer.startListening(intent)
            }
        }, delayMillis)
    }

    private fun handleRecognitionError(error: Int, intent: Intent) {
        Toast.makeText(this, "Recognition error: $error. Retrying...", Toast.LENGTH_SHORT).show()
        restartListeningWithDelay(intent, 5000)
    }

    private fun muteSystemSounds() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
    }

    private fun unmuteSystemSounds() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
    }

    private fun showSOSConfirmationNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100 // Request code, you can choose any number
            )
            return
        }

        // Intent for "Yes" action to send SMS
        val yesIntent = Intent(this, SOSBroadcastReceiver::class.java).apply {
            action = "SEND_SOS"
        }
        val yesPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            yesIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Intent for "No" action to cancel notification
        val noIntent = Intent(this, SOSBroadcastReceiver::class.java).apply {
            action = "CANCEL_SOS"
        }
        val noPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            noIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Create the notification
        val notification = NotificationCompat.Builder(this, "default")
            .setSmallIcon(R.drawable.ic_sos)
            .setContentTitle("Shake Detected")
            .setContentText("Do you want to send an SOS?")
            .addAction(R.drawable.ic_yes, "Yes", yesPendingIntent) // Action for "Yes"
            .addAction(R.drawable.ic_no, "No", noPendingIntent)    // Action for "No"
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Automatically dismiss when clicked
            .build()

        // Display the notification
        val notificationId = 1
        NotificationManagerCompat.from(this).notify(notificationId, notification)

        // Automatically send SOS after 5 seconds if no action is taken
        Handler(Looper.getMainLooper()).postDelayed({
            // Get the current location before sending the SOS message
            getCurrentLocation { location ->
                // Check if the notification is still active
                if (NotificationManagerCompat.from(this).activeNotifications.any { it.id == notificationId }) {
                    sendSOSMessage(location)  // Pass the location to sendSOSMessage
                    NotificationManagerCompat.from(this).cancel(notificationId) // Cancel notification
                }
            }
        }, 5000)
    }

    private fun startRecording() {
        audioFilePath = "${externalCacheDir?.absolutePath}/sos_audio_${System.currentTimeMillis()}.mp3"
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFilePath)
            prepare()
            start()
        }
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }

    private fun stopRecording() {
        mediaRecorder.apply {
            stop()
            release()
        }
        Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
    }

    fun sendSOSMessage(location: Location) {
        val message = "SOS! I am in trouble. My location: Lat: ${location.latitude}, Long: ${location.longitude}"
        val phoneNumber = "1234567890" // Example emergency contact number

        val smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
    }

    fun getCurrentLocation(callback: (Location) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let { callback(it) }
                }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }
    }

    // Handling sensor events for shake detection
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                // Calculate the shake force
                val shake = Math.sqrt((x * x + y * y + z * z).toDouble())
                if (shake > shakeThreshold) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastShakeTime > shakeCooldown) {
                        lastShakeTime = currentTime
                        showSOSConfirmationNotification()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        stopRecording()  // Stop recording when the app is paused
    }
}
