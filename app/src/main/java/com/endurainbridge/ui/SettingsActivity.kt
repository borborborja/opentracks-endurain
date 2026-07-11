package com.endurainbridge.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.endurainbridge.data.Settings
import com.endurainbridge.databinding.ActivitySettingsBinding
import com.endurainbridge.endurain.EndurainClient
import com.endurainbridge.endurain.TestResult
import com.endurainbridge.upload.UploadEnqueuer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settings: Settings

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Debug helper (M1 verification): pick a .gpx and upload it end-to-end without OpenTracks.
    private val pickGpx =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) uploadPickedGpx(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        settings = Settings.get(this)

        binding.inputServerUrl.setText(settings.serverUrl)
        binding.inputApiKey.setText(settings.apiKey)

        binding.btnSave.setOnClickListener { save() }
        binding.btnTest.setOnClickListener { testConnection() }

        // Long-press "Probar conexión" to run the debug GPX upload.
        binding.btnTest.setOnLongClickListener {
            if (saveGuarded()) pickGpx.launch(arrayOf("application/gpx+xml", "*/*"))
            true
        }

        requestNotificationPermission()
    }

    private fun save() {
        if (saveGuarded()) binding.statusText.text = getString(com.endurainbridge.R.string.saved)
    }

    /** Persists the fields; returns false (with a status message) if incomplete. */
    private fun saveGuarded(): Boolean {
        settings.serverUrl = binding.inputServerUrl.text?.toString().orEmpty()
        settings.apiKey = binding.inputApiKey.text?.toString().orEmpty()
        binding.inputServerUrl.setText(settings.serverUrl) // reflect normalization
        if (!settings.isConfigured) {
            binding.statusText.text = "Introduce la URL del servidor y la API key"
            return false
        }
        return true
    }

    private fun testConnection() {
        if (!saveGuarded()) return
        binding.statusText.text = "Probando…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { EndurainClient(settings).testConnection() }
            binding.statusText.text = when (result) {
                is TestResult.Success -> "✓ ${result.message}"
                is TestResult.Failure -> "✗ ${result.message}"
            }
        }
    }

    private fun uploadPickedGpx(uri: Uri) {
        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                val out = File(cacheDir, "debug-${System.currentTimeMillis()}.gpx")
                contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                out
            }
            UploadEnqueuer.enqueue(this@SettingsActivity, cached, cached.name, trackUuid = "")
            binding.statusText.text = "GPX de prueba encolado para subida"
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
