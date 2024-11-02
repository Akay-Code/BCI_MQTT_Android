package com.example.minor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import android.widget.ToggleButton
import org.eclipse.paho.client.mqttv3.MqttMessage

class MainActivity2 : AppCompatActivity() {

    private lateinit var largeButton1: Button
    private lateinit var largeButton2: Button
    private lateinit var smallButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mqttService: MqttService
    private var isBound = false

    private var isButton1Visible = true
    private var isButton2Visible = true
    private var isSmallButtonVisible = true
    private lateinit var mqttReceiver: BroadcastReceiver
    private val messageHandler = Handler(Looper.getMainLooper())

    private lateinit var toggleBtn: ToggleButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main2)

        val deviceSelected = intent.getStringExtra("deviceName")
        val espId = intent.getStringExtra("espId")
        val deviceName = findViewById<TextView>(R.id.deviceNameTextView)
        deviceName.text = "$espId\n$deviceSelected"

        toggleBtn = findViewById(R.id.stateToggleButton)

        val sharedPref = getSharedPreferences("device_states", Context.MODE_PRIVATE)
        val savedState = sharedPref.getBoolean("$espId/$deviceSelected", false)
        toggleBtn.isChecked = savedState // Set the toggle button state

        toggleBtn.setOnCheckedChangeListener { _, isChecked ->
            if (isBound) {
                val message = if (isChecked) "1" else "0" // 1 for ON, 0 for OFF
                mqttService.publishMessage("$espId/$deviceSelected", message)

                val sharedPref = getSharedPreferences("device_states", Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    putBoolean("$espId/$deviceSelected", isChecked)
                    apply()
                }
            } else {
                Toast.makeText(this, "Service not bound", Toast.LENGTH_SHORT).show()
            }
        }

        largeButton1 = findViewById(R.id.largeButton1)
        largeButton2 = findViewById(R.id.largeButton2)
        smallButton = findViewById(R.id.backButton)

        startFlicker13Hz()
        startFlicker17Hz()
        startFlicker21hz()

        Intent(this, MqttService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        mqttReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val topic = intent?.getStringExtra("topic")
                val message = intent?.getStringExtra("message")

                messageHandler.post {
                    handleMqttMessage(topic, message)
                }
            }
        }
    }

    private fun handleMqttMessage(topic: String?, message: String?) {
        if (topic == "DAQ") {
            when (message) {
                "0" -> {
                    // If toggle button is ON, perform click to turn it OFF
                    if (toggleBtn.isChecked) {
                        toggleBtn.performClick()
                        Toast.makeText(this, "Received 0: Light Turned Off", Toast.LENGTH_SHORT).show()
                    }
                }
                "1" -> {
                    // If toggle button is OFF, perform click to turn it ON
                    if (!toggleBtn.isChecked) {
                        toggleBtn.performClick()
                        Toast.makeText(this, "Received 1: Light Turned On", Toast.LENGTH_SHORT).show()
                    }
                }
                "2" -> {
                    Toast.makeText(this,"Received 2: Going Back",Toast.LENGTH_SHORT).show()
                    finish()
                } // Go back to MainActivity
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MqttService.LocalBinder
            mqttService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onResume() {
        super.onResume()
        val intentFilter = IntentFilter("com.example.minor.MQTT_MESSAGE")
        registerReceiver(mqttReceiver, intentFilter, RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mqttReceiver)
    }

    private fun startFlicker13Hz() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                largeButton1.setBackgroundColor(if (isButton1Visible) Color.TRANSPARENT else getColor(R.color.buttonColor))
                isButton1Visible = !isButton1Visible
                handler.postDelayed(this, 76L)
            }
        }, 76L)
    }

    private fun startFlicker17Hz() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                largeButton2.setBackgroundColor(if (isButton2Visible) Color.TRANSPARENT else getColor(R.color.buttonColor))
                isButton2Visible = !isButton2Visible
                handler.postDelayed(this, 58L)
            }
        }, 58L)
    }

    private fun startFlicker21hz(){
        handler.postDelayed(object : Runnable {
            override fun run() {
                smallButton.setBackgroundColor(if (isSmallButtonVisible) Color.TRANSPARENT else getColor(R.color.buttonColor))
                isSmallButtonVisible = !isSmallButtonVisible
                handler.postDelayed(this, 47L)
            }
        }, 47L)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
