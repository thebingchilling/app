package dev.tidewall.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tidewall.data.Profile
import dev.tidewall.data.ProfileKind
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.Dropdown
import dev.tidewall.ui.common.SwitchRow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Shadowsocks ciphers mihomo supports. Stream ciphers are legacy and insecure. */
object SsCiphers {
    val aead = listOf(
        "aes-128-gcm", "aes-192-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305",
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
    )
    val legacy = listOf(
        "rc4-md5", "aes-128-cfb", "aes-192-cfb", "aes-256-cfb", "aes-128-ctr", "aes-192-ctr", "aes-256-ctr",
        "chacha20-ietf", "xchacha20", "chacha20", "none",
    )
    val all = aead + legacy
    fun isLegacy(c: String) = c in legacy
}

/** Adds a Shadowsocks server by hand, to a new or existing proxy profile. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowsocksEditorScreen(vm: MainViewModel, onBack: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val targets: List<Profile?> = listOf<Profile?>(null) + profiles.filter { it.kind == ProfileKind.CLASH }
    var target by remember { mutableStateOf<Profile?>(null) }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("8388") }
    var cipher by remember { mutableStateOf("rc4-md5") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var udp by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val portNum = port.toIntOrNull()
    val valid = server.isNotBlank() && portNum != null && portNum in 1..65535 && (password.isNotEmpty() || cipher == "none")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shadowsocks server") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Dropdown(
                    "Add to", target, targets,
                    display = { it?.name ?: "New profile" },
                    onSelect = { target = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, placeholder = { Text(server.ifBlank { "My server" }) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        server, { server = it.trim() }, label = { Text("Server") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.weight(2f),
                    )
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = port.isNotEmpty() && (portNum == null || portNum !in 1..65535),
                        modifier = Modifier.weight(1f),
                    )
                }
                Dropdown(
                    "Cipher", cipher, SsCiphers.all,
                    display = { if (SsCiphers.isLegacy(it) && it != "none") "$it (legacy)" else it },
                    onSelect = { cipher = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (SsCiphers.isLegacy(cipher)) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                if (cipher == "none") "No encryption: anyone on the path can read and modify this traffic."
                                else "$cipher is a legacy stream cipher without integrity protection. Traffic can be tampered with and is easy to fingerprint. Use it only for servers you cannot upgrade.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    password, { password = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showPassword) "Hide" else "Show")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow("UDP relay", udp, { udp = it }, "Forward UDP (DNS, QUIC, games) through the server")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Button(
                    enabled = valid,
                    onClick = {
                        val proxyName = name.trim().ifEmpty { server }
                        // JSON is valid YAML, so the engine parses this as a proxy mapping.
                        val proxy = buildJsonObject {
                            put("name", proxyName)
                            put("type", "ss")
                            put("server", server)
                            put("port", portNum ?: 0)
                            put("cipher", cipher)
                            put("password", password)
                            put("udp", udp)
                        }.toString()
                        scope.launch {
                            error = vm.addProxy(target, proxy, proxyName)
                            if (error == null) {
                                vm.toast("Added $proxyName")
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save") }
            }
        }
    }
}
