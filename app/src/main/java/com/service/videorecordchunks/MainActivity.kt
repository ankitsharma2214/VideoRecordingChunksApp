package com.service.videorecordchunks

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.service.videorecordchunks.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var videoService: VideoRecordingService? = null
    private var isBound = false
    private var startAfterConnect = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            val granted = permissions.all { it.value }

            if (!granted) {
                Toast.makeText(this, "Permissions required!", Toast.LENGTH_LONG).show()
            }
        }


    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {

            val localBinder = binder as VideoRecordingService.LocalBinder
            videoService = localBinder.getService()
            isBound = true

            videoService?.attachPreview(binding.previewView.surfaceProvider)

            if (startAfterConnect) {
                startAfterConnect = false
                videoService?.startRecording()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            videoService = null
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvCameraIds.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        fetchCameraIDs()

        binding.btnStart.setOnClickListener {

            if (!hasPermissions()) {
                requestPermissions()
                return@setOnClickListener
            }

            if (!isBound) {
                startAfterConnect = true
                startAndBindService()
            } else {
                videoService?.startRecording()
            }
        }

        binding.btnStop.setOnClickListener {

            videoService?.stopRecording()

            if (isBound) {
                unbindService(serviceConnection)
                isBound = false
            }

            stopService(Intent(this, VideoRecordingService::class.java))
        }

        requestPermissions()
    }

    override fun onStop() {
        super.onStop()

        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun requestPermissions() {

        if (!hasPermissions()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startAndBindService() {

        val selectedCameraId = "0"

        val intent = Intent(this, VideoRecordingService::class.java)
        intent.putExtra("camera_id", selectedCameraId)

        ContextCompat.startForegroundService(this, intent)

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun fetchCameraIDs() {
        val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIds = manager.cameraIdList.toList()

        binding.rvCameraIds.adapter = CameraIdAdapter(cameraIds)

        Toast.makeText(this, "Found ${cameraIds.size} cameras", Toast.LENGTH_SHORT).show()
    }
}