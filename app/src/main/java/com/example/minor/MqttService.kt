package com.example.minor

import android.app.Service
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Binder
import android.os.IBinder
import android.widget.Toast
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MqttService : Service() {
    private lateinit var mqttAndroidClient: MqttAndroidClient
    private lateinit var nsdManager: NsdManager
    private val clientId = "AndroidClient"
    private var serverUri: String? = null
    private val serviceType = "_mqtt._tcp." // Adjust based on your service

    // List of topics to subscribe to
    private val subscriptionTopics = listOf("DAQ", "esp/devices")

    inner class LocalBinder : Binder() {
        fun getService(): MqttService = this@MqttService
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        nsdManager = getSystemService(NsdManager::class.java) as NsdManager
        discoverNsdService()  // Start NSD discovery for MQTT server
        return START_STICKY
    }

    private fun discoverNsdService() {
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Toast.makeText(this@MqttService, "NSD Discovery Started", Toast.LENGTH_SHORT).show()
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType == serviceType) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Toast.makeText(this@MqttService, "NSD Resolve Failed", Toast.LENGTH_SHORT).show()
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host.hostAddress  // Get the IP address of `eeg.local`
                            val port = serviceInfo.port
                            serverUri = "tcp://$host:$port"  // Set MQTT URI based on resolved IP and port
                            setupMqttClient()
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Toast.makeText(this@MqttService, "NSD Service Lost", Toast.LENGTH_SHORT).show()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Toast.makeText(this@MqttService, "NSD Discovery Stopped", Toast.LENGTH_SHORT).show()
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        })
    }

    private fun setupMqttClient() {
        if (serverUri == null) {
            Toast.makeText(this, "Server URI not resolved yet", Toast.LENGTH_SHORT).show()
            return
        }

        mqttAndroidClient = MqttAndroidClient(applicationContext, serverUri!!, clientId)
        val mqttConnectOptions = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = false
            connectionTimeout = 10
            keepAliveInterval = 60
        }

        mqttAndroidClient.connect(mqttConnectOptions, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken) {
                subscribeToTopics()  // Subscribe to multiple topics
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Toast.makeText(this@MqttService, "Failed to connect: $exception", Toast.LENGTH_LONG).show()
            }
        })

        mqttAndroidClient.setCallback(object : MqttCallback {
            override fun messageArrived(topic: String, message: MqttMessage) {
                val payload = message.toString()

                // Broadcast the message
                val intent = Intent("com.example.minor.MQTT_MESSAGE")
                intent.putExtra("topic", topic)
                intent.putExtra("message", payload)
                sendBroadcast(intent)
            }

            override fun connectionLost(cause: Throwable?) {
                // Handle connection loss
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Handle message delivery completion
            }
        })
    }

    // Subscribe to multiple topics
    private fun subscribeToTopics() {
        subscriptionTopics.forEach { topic ->
            mqttAndroidClient.subscribe(topic, 0, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Toast.makeText(this@MqttService, "Subscribed to $topic", Toast.LENGTH_SHORT).show()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Toast.makeText(this@MqttService, "Subscription to $topic failed", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    // Publish messages to a given topic
    fun publishMessage(topic: String, message: String) {
        if (mqttAndroidClient.isConnected) {
            try {
                val mqttMessage = MqttMessage()
                mqttMessage.payload = message.toByteArray()
                mqttAndroidClient.publish(topic, mqttMessage)
            } catch (e: MqttException) {
                e.printStackTrace()
                Toast.makeText(this, "Error Publishing: $e", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Not connected to MQTT broker", Toast.LENGTH_SHORT).show()
        }
    }
}
