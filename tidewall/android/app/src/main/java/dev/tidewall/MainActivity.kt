package dev.tidewall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import dev.tidewall.data.ContentDetector
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.TidewallUi

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) vm.connect() else vm.toast("VPN permission is needed to connect")
    }

    // Asked once, right before the first connect; the VPN consent follows.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        continueConnect()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TidewallUi(vm, onConnect = ::requestConnect) }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Asks for the notification and VPN permissions if needed, one after the other, then connects. */
    fun requestConnect() {
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("asked_notifications", false)
        ) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        continueConnect()
    }

    private fun continueConnect() {
        val prepare = VpnService.prepare(this)
        if (prepare != null) vpnPermission.launch(prepare) else vm.connect()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_CONNECT -> requestConnect()
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                val t = text.trim()
                if (t.startsWith("http://") || t.startsWith("https://")) vm.importUrl(t) else vm.importText(t)
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                when (data.scheme) {
                    "clash", "clashmeta", "tidewall" -> {
                        val (url, name) = ContentDetector.installConfigUrl(data.toString()) ?: run {
                            vm.toast("The link has no subscription URL")
                            return
                        }
                        vm.importUrl(url, name)
                    }
                    "content", "file" -> vm.importUri(data)
                }
            }
        }
        // Consume the intent so rotation doesn't import twice.
        setIntent(Intent(this, MainActivity::class.java))
    }

    companion object {
        const val ACTION_CONNECT = "dev.tidewall.CONNECT"
    }
}
