package com.example.rokucaster.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rokucaster.R
import com.example.rokucaster.domain.model.RokuDevice
import com.example.rokucaster.ui.adapter.RokuDeviceAdapter
import com.example.rokucaster.ui.viewmodel.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * MainActivity - The main UI for the Roku Caster application.
 * 
 * Features:
 * - Device discovery via SSDP
 * - Video URL input
 * - Device selection from RecyclerView
 * - Start/Stop casting controls
 * - Permission handling for Android 13+
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // ViewModel (survives configuration changes)
    private lateinit var viewModel: MainViewModel
    
    // UI Components
    private lateinit var videoUrlEditText: EditText
    private lateinit var discoverButton: Button
    private lateinit var stopDiscoveryButton: Button
    private lateinit var stopCastingButton: Button
    private lateinit var devicesRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    
    // RecyclerView adapter
    private lateinit var deviceAdapter: RokuDeviceAdapter
    
    // Selected device for casting
    private var selectedDevice: RokuDevice? = null
    
    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Notification permission denied. You won't see casting controls in notifications.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        // Initialize UI
        initializeViews()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        
        // Request notification permission on Android 13+
        requestNotificationPermission()
    }
    
    /**
     * Initialize all view references.
     */
    private fun initializeViews() {
        videoUrlEditText = findViewById(R.id.videoUrlEditText)
        discoverButton = findViewById(R.id.discoverButton)
        stopDiscoveryButton = findViewById(R.id.stopDiscoveryButton)
        stopCastingButton = findViewById(R.id.stopCastingButton)
        devicesRecyclerView = findViewById(R.id.devicesRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        
        // Set initial state
        stopDiscoveryButton.isEnabled = false
        stopCastingButton.isEnabled = false
    }
    
    /**
     * Setup the RecyclerView for displaying Roku devices.
     */
    private fun setupRecyclerView() {
        deviceAdapter = RokuDeviceAdapter { device ->
            onDeviceSelected(device)
        }
        
        devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }
    }
    
    /**
     * Setup click listeners for buttons.
     */
    private fun setupListeners() {
        discoverButton.setOnClickListener {
            viewModel.startDiscovery()
        }
        
        stopDiscoveryButton.setOnClickListener {
            viewModel.stopDiscovery()
        }
        
        stopCastingButton.setOnClickListener {
            viewModel.stopCasting(this)
        }
    }
    
    /**
     * Observe ViewModel state changes and update UI accordingly.
     */
    private fun observeViewModel() {
        // Observe discovered devices
        lifecycleScope.launch {
            viewModel.discoveredDevices.collect { devices ->
                deviceAdapter.submitList(devices)
                
                if (devices.isEmpty() && !viewModel.isDiscovering.value) {
                    Toast.makeText(
                        this@MainActivity,
                        "No Roku devices found. Make sure you're on the same network.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        
        // Observe discovery state
        lifecycleScope.launch {
            viewModel.isDiscovering.collect { isDiscovering ->
                progressBar.visibility = if (isDiscovering) ProgressBar.VISIBLE else ProgressBar.GONE
                discoverButton.isEnabled = !isDiscovering
                stopDiscoveryButton.isEnabled = isDiscovering
            }
        }
        
        // Observe casting state
        lifecycleScope.launch {
            viewModel.isCasting.collect { isCasting ->
                stopCastingButton.isEnabled = isCasting
                discoverButton.isEnabled = !isCasting
                videoUrlEditText.isEnabled = !isCasting
                
                if (isCasting) {
                    Toast.makeText(
                        this@MainActivity,
                        "Casting started! You can minimize the app - it will continue in the background.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        
        // Observe error messages
        lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                error?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }
    
    /**
     * Called when a device is selected from the RecyclerView.
     * Shows a confirmation dialog before starting to cast.
     */
    private fun onDeviceSelected(device: RokuDevice) {
        selectedDevice = device
        
        val videoUrl = videoUrlEditText.text.toString().trim()
        
        if (videoUrl.isEmpty()) {
            Toast.makeText(this, "Please enter a video URL first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show confirmation dialog
        MaterialAlertDialogBuilder(this)
            .setTitle("Start Casting?")
            .setMessage("Cast video to ${device.name}?\n\nURL: $videoUrl")
            .setPositiveButton("Cast") { _, _ ->
                viewModel.startCasting(this, device, videoUrl)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Request notification permission on Android 13+ (API 33+).
     * This is required to show the foreground service notification.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show explanation to user
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Notification Permission Needed")
                        .setMessage("This app needs notification permission to show casting controls when the app is in the background.")
                        .setPositiveButton("OK") { _, _ ->
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                else -> {
                    // Request permission
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // If we're finishing (not just rotating), clean up discovery
        if (isFinishing && !viewModel.isCasting.value) {
            viewModel.stopDiscovery()
        }
        
        // Note: We do NOT stop the casting service here because it should
        // continue running even if the activity is destroyed. This is the
        // whole point of using a foreground service!
    }
}
