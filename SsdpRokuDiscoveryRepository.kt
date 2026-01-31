package com.example.rokucaster.data.repository

import android.util.Log
import com.example.rokucaster.domain.model.RokuDevice
import com.example.rokucaster.domain.repository.RokuDiscoveryRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * SSDP (Simple Service Discovery Protocol) implementation for discovering Roku devices.
 * 
 * This implementation uses multicast UDP to discover devices advertising the
 * "roku:ecp" service type on the local network.
 * 
 * SSDP Discovery Process:
 * 1. Send M-SEARCH multicast packet to 239.255.255.250:1900
 * 2. Listen for responses containing LOCATION header
 * 3. Fetch device description XML from LOCATION URL
 * 4. Parse device info and emit to Flow
 */
class SsdpRokuDiscoveryRepository : RokuDiscoveryRepository {
    
    companion object {
        private const val TAG = "SsdpRokuDiscovery"
        private const val SSDP_ADDRESS = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SSDP_SEARCH_TARGET = "roku:ecp"
        private const val DISCOVERY_TIMEOUT = 5000L
        
        // M-SEARCH message format for SSDP discovery
        private const val SSDP_DISCOVER_MESSAGE = 
            "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 3\r\n" +
            "ST: $SSDP_SEARCH_TARGET\r\n" +
            "\r\n"
    }
    
    private val _discoveredDevices = MutableStateFlow<List<RokuDevice>>(emptyList())
    private val deviceMap = mutableMapOf<String, RokuDevice>()
    
    private var discoveryJob: Job? = null
    private var socket: DatagramSocket? = null
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    override fun discoverDevices(): Flow<List<RokuDevice>> {
        startDiscovery()
        return _discoveredDevices.asStateFlow()
    }
    
    /**
     * Starts the SSDP discovery process in a coroutine.
     * Uses a DatagramSocket to send multicast M-SEARCH requests.
     */
    private fun startDiscovery() {
        discoveryJob?.cancel()
        
        discoveryJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create socket bound to any available port
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0))
                }
                
                // Send M-SEARCH request
                sendDiscoveryRequest()
                
                // Listen for responses
                listenForResponses()
                
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error: ${e.message}", e)
            }
        }
    }
    
    /**
     * Sends the SSDP M-SEARCH multicast request to discover Roku devices.
     */
    private fun sendDiscoveryRequest() {
        try {
            val searchMessage = SSDP_DISCOVER_MESSAGE.toByteArray()
            val packet = DatagramPacket(
                searchMessage,
                searchMessage.size,
                InetAddress.getByName(SSDP_ADDRESS),
                SSDP_PORT
            )
            
            socket?.send(packet)
            Log.d(TAG, "Sent SSDP discovery request")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send discovery request: ${e.message}", e)
        }
    }
    
    /**
     * Listens for SSDP responses on the socket.
     * Parses the LOCATION header and fetches device information.
     */
    private suspend fun listenForResponses() {
        val buffer = ByteArray(2048)
        val startTime = System.currentTimeMillis()
        
        socket?.soTimeout = 1000 // 1 second timeout for each receive
        
        while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet)
                
                val response = String(packet.data, 0, packet.length)
                handleSsdpResponse(response)
                
            } catch (e: Exception) {
                // Timeout or other error - continue listening
                if (e !is java.net.SocketTimeoutException) {
                    Log.w(TAG, "Error receiving packet: ${e.message}")
                }
            }
        }
        
        Log.d(TAG, "Discovery timeout reached, found ${deviceMap.size} devices")
    }
    
    /**
     * Parses an SSDP response and extracts the LOCATION header.
     * The LOCATION points to the device description XML.
     */
    private suspend fun handleSsdpResponse(response: String) {
        try {
            // Look for LOCATION header
            val locationPattern = Regex("LOCATION:\\s*(.+)", RegexOption.IGNORE_CASE)
            val match = locationPattern.find(response)
            
            val location = match?.groupValues?.get(1)?.trim() ?: return
            
            Log.d(TAG, "Found device at: $location")
            fetchDeviceInfo(location)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SSDP response: ${e.message}")
        }
    }
    
    /**
     * Fetches and parses the device description XML from the LOCATION URL.
     * Extracts device name, model, IP address, etc.
     */
    private suspend fun fetchDeviceInfo(locationUrl: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(locationUrl)
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                val xml = response.body?.string() ?: return@withContext
                val device = parseDeviceXml(xml, locationUrl)
                
                if (device != null) {
                    deviceMap[device.ipAddress] = device
                    _discoveredDevices.value = deviceMap.values.toList()
                    Log.d(TAG, "Added device: ${device.name} at ${device.ipAddress}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch device info: ${e.message}")
        }
    }
    
    /**
     * Parses the Roku device description XML.
     * Extracts friendlyName, modelName, serialNumber, etc.
     */
    private fun parseDeviceXml(xml: String, locationUrl: String): RokuDevice? {
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))
            
            var friendlyName: String? = null
            var modelName: String? = null
            var serialNumber: String? = null
            
            var eventType = parser.eventType
            var currentTag: String? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim()
                        if (!text.isNullOrEmpty()) {
                            when (currentTag) {
                                "friendlyName" -> friendlyName = text
                                "modelName" -> modelName = text
                                "serialNumber" -> serialNumber = text
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            
            // Extract IP from location URL
            val ipPattern = Regex("http://([^:]+):")
            val ipMatch = ipPattern.find(locationUrl)
            val ipAddress = ipMatch?.groupValues?.get(1) ?: return null
            
            return RokuDevice(
                name = friendlyName ?: "Roku Device",
                ipAddress = ipAddress,
                modelName = modelName,
                serialNumber = serialNumber
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse device XML: ${e.message}")
            return null
        }
    }
    
    override fun stopDiscovery() {
        discoveryJob?.cancel()
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing socket: ${e.message}")
        }
        socket = null
        Log.d(TAG, "Discovery stopped")
    }
    
    /**
     * Casts a video to the Roku device using the ECP (External Control Protocol).
     * 
     * The casting process:
     * 1. POST to /install/{channelId} to ensure the streaming channel is installed
     * 2. Pass contentId (video URL) and mediaType as query parameters
     * 
     * For Roku Media Player (channel 22507), the device will launch and play the URL.
     */
    override suspend fun castVideo(
        device: RokuDevice,
        videoUrl: String,
        mediaType: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = device.getBaseUrl()
            
            // Using Roku Media Player channel (22507) which supports direct URL playback
            val url = "$baseUrl/launch/22507?" +
                    "contentId=${java.net.URLEncoder.encode(videoUrl, "UTF-8")}&" +
                    "mediaType=$mediaType"
            
            Log.d(TAG, "Casting to: $url")
            
            val request = Request.Builder()
                .url(url)
                .post("".toRequestBody("text/plain".toMediaType()))
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                Log.d(TAG, "Successfully cast video to ${device.name}")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to cast: HTTP ${response.code}")
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error casting video: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Sends a keypress command to the Roku device.
     * Common keys: Play, Pause, Home, Back, Select, Left, Right, Up, Down
     */
    override suspend fun sendKeyPress(device: RokuDevice, key: String): Result<Boolean> = 
        withContext(Dispatchers.IO) {
            try {
                val url = "${device.getBaseUrl()}/keypress/$key"
                
                val request = Request.Builder()
                    .url(url)
                    .post("".toRequestBody("text/plain".toMediaType()))
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Log.d(TAG, "Sent keypress '$key' to ${device.name}")
                    Result.success(true)
                } else {
                    Result.failure(Exception("HTTP ${response.code}"))
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending keypress: ${e.message}", e)
                Result.failure(e)
            }
        }
}
