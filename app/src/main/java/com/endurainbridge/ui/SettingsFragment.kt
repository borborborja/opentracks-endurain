package com.endurainbridge.ui

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.endurainbridge.BuildConfig
import com.endurainbridge.R
import com.endurainbridge.data.Settings
import com.endurainbridge.databinding.FragmentSettingsBinding
import com.endurainbridge.endurain.EndurainClient
import com.endurainbridge.endurain.TestResult
import com.endurainbridge.update.ApkInstaller
import com.endurainbridge.update.UpdateChecker
import com.endurainbridge.upload.UploadEnqueuer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsFragment : Fragment() {

    private var _b: FragmentSettingsBinding? = null
    private val b get() = _b!!
    private lateinit var settings: Settings
    private var pendingUpdate: UpdateChecker.Result.Available? = null

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val pickGpx =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) uploadPickedGpx(uri)
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settings = Settings.get(requireContext())
        b.inputServerUrl.setText(settings.serverUrl)
        b.inputApiKey.setText(settings.apiKey)
        b.versionText.text = getString(R.string.update_current_version, BuildConfig.VERSION_NAME)

        b.btnSave.setOnClickListener { save() }
        b.btnTest.setOnClickListener { testConnection() }
        b.btnTest.setOnLongClickListener {
            if (saveGuarded()) pickGpx.launch(arrayOf("application/gpx+xml", "*/*"))
            true
        }
        b.btnCheckUpdate.setOnClickListener {
            val update = pendingUpdate
            if (update == null) checkForUpdate() else installUpdate(update)
        }

        requestNotificationPermission()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun save() {
        if (saveGuarded()) b.statusText.text = getString(R.string.saved)
    }

    private fun saveGuarded(): Boolean {
        settings.serverUrl = b.inputServerUrl.text?.toString().orEmpty()
        settings.apiKey = b.inputApiKey.text?.toString().orEmpty()
        b.inputServerUrl.setText(settings.serverUrl)
        if (!settings.isConfigured) {
            b.statusText.text = getString(R.string.settings_incomplete)
            return false
        }
        return true
    }

    private fun testConnection() {
        if (!saveGuarded()) return
        b.statusText.text = getString(R.string.testing)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { EndurainClient(settings).testConnection() }
            b.statusText.text = when (result) {
                is TestResult.Success -> "✓ ${result.message}"
                is TestResult.Failure -> "✗ ${result.message}"
            }
        }
    }

    private fun uploadPickedGpx(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                val out = File(requireContext().cacheDir, "debug-${System.currentTimeMillis()}.gpx")
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                out
            }
            UploadEnqueuer.enqueue(requireContext(), cached, cached.name, trackUuid = "")
            b.statusText.text = getString(R.string.debug_enqueued)
        }
    }

    // --- update checking / installing ---

    private fun checkForUpdate() {
        b.updateStatus.text = getString(R.string.update_checking)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { UpdateChecker.check() }
            when (result) {
                is UpdateChecker.Result.UpToDate -> {
                    pendingUpdate = null
                    b.updateStatus.text = getString(R.string.update_up_to_date)
                }
                is UpdateChecker.Result.Error -> {
                    pendingUpdate = null
                    b.updateStatus.text = getString(R.string.update_error, result.message)
                }
                is UpdateChecker.Result.Available -> {
                    pendingUpdate = result
                    b.updateStatus.text = getString(R.string.update_available, result.versionName)
                    b.btnCheckUpdate.text = getString(R.string.update_install, result.versionName)
                }
            }
        }
    }

    private fun installUpdate(update: UpdateChecker.Result.Available) {
        val ctx = requireContext()
        if (!ApkInstaller.canInstall(ctx)) {
            b.updateStatus.text = getString(R.string.update_need_permission)
            ApkInstaller.requestInstallPermission(ctx)
            return
        }
        b.updateStatus.text = getString(R.string.update_downloading)
        b.btnCheckUpdate.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val apk = withContext(Dispatchers.IO) { ApkInstaller.downloadApk(ctx, update.apkUrl) }
            b.btnCheckUpdate.isEnabled = true
            if (apk != null) {
                ApkInstaller.install(ctx, apk)
            } else {
                b.updateStatus.text = getString(R.string.update_download_failed)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
