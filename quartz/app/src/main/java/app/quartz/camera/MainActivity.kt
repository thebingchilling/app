package app.quartz.camera

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.quartz.camera.ui.CameraScreen
import app.quartz.camera.ui.CameraViewModel
import app.quartz.camera.ui.CaptureIntent
import app.quartz.camera.ui.CaptureOutcome
import app.quartz.camera.ui.ReviewScreen
import app.quartz.camera.ui.Screen
import app.quartz.camera.ui.SettingsScreen
import app.quartz.camera.util.DeviceRotation
import app.quartz.camera.util.LevelSensor
import app.quartz.core.caps.C2
import app.quartz.core.settings.Mode
import app.quartz.core.ui.icons.SymbolIcon
import app.quartz.core.ui.icons.Symbols
import app.quartz.core.ui.theme.QuartzTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: CameraViewModel by viewModels()
    private lateinit var rotation: DeviceRotation
    private lateinit var level: LevelSensor
    private val cameraGranted = mutableStateOf(false)
    private var started = false

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cameraGranted.value = has(Manifest.permission.CAMERA)
        if (cameraGranted.value && started) vm.setActive(true)
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.none { it }) vm.updateSettings { it.copy(location = false) } else if (started) vm.setActive(true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        rotation = DeviceRotation(this)
        level = LevelSensor(this)
        handleIntent(intent)

        cameraGranted.value = has(Manifest.permission.CAMERA)
        if (!cameraGranted.value || (!has(Manifest.permission.RECORD_AUDIO) && savedInstanceState == null)) requestPermissions()

        lifecycleScope.launch { rotation.rotation.collect { vm.deviceRotation.value = it } }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settings.map { it.level }.distinctUntilChanged().collect { if (it) level.start() else level.stop() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.outcomes.collect { finishWith(it) } }
        }

        setContent {
            val ui by vm.ui.collectAsState()
            val roll by level.roll.collectAsState()
            QuartzTheme(forceDark = ui.screen != Screen.SETTINGS) {
                when {
                    !cameraGranted.value -> PermissionScreen { requestPermissions() }
                    ui.screen == Screen.SETTINGS -> SettingsScreen(vm) { requestLocation() }
                    ui.screen == Screen.REVIEW -> ReviewScreen(vm)
                    else -> CameraScreen(vm, roll)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        started = true
        rotation.start()
        if (cameraGranted.value) vm.setActive(true)
    }

    override fun onStop() {
        started = false
        rotation.stop()
        level.stop()
        vm.setActive(false)
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (vm.ui.value.screen == Screen.CAMERA && event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN ->
                    if (vm.onVolumeKey(keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
                KeyEvent.KEYCODE_CAMERA -> {
                    vm.onShutter()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action
        val secure = action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE || action == MediaStore.ACTION_IMAGE_CAPTURE_SECURE
        if (secure) {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        val output = intent?.let { IntentCompat.getParcelableExtra(it, MediaStore.EXTRA_OUTPUT, Uri::class.java) }
        val capture = when (action) {
            MediaStore.ACTION_IMAGE_CAPTURE, MediaStore.ACTION_IMAGE_CAPTURE_SECURE -> CaptureIntent(video = false, output = output)
            MediaStore.ACTION_VIDEO_CAPTURE -> CaptureIntent(video = true, output = output)
            else -> null
        }
        vm.setCaptureIntent(capture, secure = secure && locked)
        when {
            action == MediaStore.INTENT_ACTION_VIDEO_CAMERA || intent?.getStringExtra(EXTRA_SHORTCUT) == "video" ->
                vm.updateSettings { it.copy(mode = Mode.VIDEO) }
            action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA || action == MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE ->
                vm.updateSettings { if (it.mode.video) it.copy(mode = Mode.PHOTO) else it }
            intent?.getStringExtra(EXTRA_SHORTCUT) == "selfie" ->
                vm.updateSettings { it.copy(mode = Mode.PHOTO, facing = C2.FACING_FRONT, lockedLens = null) }
        }
    }

    private fun finishWith(outcome: CaptureOutcome) {
        val intent = vm.ui.value.captureIntent ?: return
        when (outcome) {
            is CaptureOutcome.Done -> {
                val data = Intent()
                if (intent.output != null || intent.video) {
                    data.data = outcome.uri
                    data.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    outcome.thumbnail?.let { data.putExtra("data", scaled(it)) }
                }
                setResult(Activity.RESULT_OK, data)
            }
            CaptureOutcome.Cancelled -> setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    private fun scaled(b: Bitmap): Bitmap {
        val max = 512
        if (b.width <= max && b.height <= max) return b
        val s = max.toFloat() / maxOf(b.width, b.height)
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
    }

    private fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        val list = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= 28) list += Manifest.permission.WRITE_EXTERNAL_STORAGE
        permissions.launch(list.toTypedArray())
    }

    private fun requestLocation() {
        if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) return
        locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    companion object {
        const val EXTRA_SHORTCUT = "app.quartz.camera.SHORTCUT"
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SymbolIcon(Symbols.photoCamera, null, Modifier.size(56.dp), tint = Color.White)
            Text("Quartz needs the camera", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(
                "Photos and videos stay on this phone. Quartz has no internet access and never edits what the sensor sees.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGrant) { Text("Allow camera") }
        }
    }
}
