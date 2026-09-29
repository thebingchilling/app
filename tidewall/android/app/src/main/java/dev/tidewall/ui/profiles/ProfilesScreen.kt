package dev.tidewall.ui.profiles

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.tidewall.data.Profile
import dev.tidewall.data.ProfileKind
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.Badge
import dev.tidewall.ui.common.EmptyState
import dev.tidewall.ui.common.formatBytes
import dev.tidewall.ui.common.formatDate
import dev.tidewall.ui.common.formatRelative
import dev.tidewall.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(vm: MainViewModel, openEditor: (Profile) -> Unit, openShadowsocks: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by vm.vpn.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var sheet by remember { mutableStateOf(false) }
    var urlDialog by remember { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Profile?>(null) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var interval by remember { mutableStateOf<Profile?>(null) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importUri(uri)
    }
    val scan = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        if (text.startsWith("http://") || text.startsWith("https://")) vm.importUrl(text) else vm.importText(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Profiles") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { sheet = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (profiles.isEmpty()) {
                EmptyState(
                    Icons.Rounded.Source,
                    "No profiles",
                    "Add a subscription URL, paste share links, scan a QR code, or import a .yaml, .ovpn or WireGuard .conf file.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(profiles, key = { it.id }) { p ->
                        ProfileCard(
                            p,
                            selected = p.id == settings.selectedProfileId,
                            active = state.active && state.profileId == p.id,
                            onSelect = { vm.selectProfile(p) },
                            onUpdate = { vm.updateProfile(p) },
                            onEdit = { openEditor(p) },
                            onRename = { renaming = p },
                            onDelete = { deleting = p },
                            onConvert = { vm.convertToProxy(p) },
                            onCredentials = { vm.editCredentials(p) },
                            onInterval = { interval = p },
                        )
                    }
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Text("Add profile", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SheetItem(Icons.Rounded.Link, "Subscription URL", "Clash/mihomo or base64 link list, updates automatically") {
                sheet = false; urlDialog = true
            }
            SheetItem(Icons.Rounded.ContentPaste, "Paste", "Share links (vless://, vmess://, ss://, trojan://…), YAML, .ovpn or .conf text") {
                sheet = false; pasteDialog = clipboardText(context)
            }
            SheetItem(Icons.Rounded.QrCodeScanner, "Scan QR code", "A share link or subscription URL") {
                sheet = false
                scan.launch(ScanOptions().setPrompt("Scan a share link or config QR code").setBeepEnabled(false).setOrientationLocked(false))
            }
            SheetItem(Icons.Rounded.FileOpen, "Import file", ".yaml, .ovpn (OpenVPN) or .conf (WireGuard / AmneziaWG)") {
                sheet = false; openFile.launch(arrayOf("*/*"))
            }
            SheetItem(Icons.Rounded.VpnKey, "Shadowsocks server", "Enter server details by hand (all ciphers, including rc4-md5)") {
                sheet = false; openShadowsocks()
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }

    if (urlDialog) {
        var url by remember { mutableStateOf(clipboardText(context).takeIf { it.startsWith("http") }.orEmpty()) }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { urlDialog = false },
            title = { Text("Add subscription") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = url.isNotBlank(), onClick = { urlDialog = false; vm.importUrl(url, name) }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { urlDialog = false }) { Text("Cancel") } },
        )
    }

    pasteDialog?.let { initial ->
        var text by remember { mutableStateOf(initial) }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasteDialog = null },
            title = { Text("Paste profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        text, { text = it },
                        label = { Text("Links, YAML, .ovpn or .conf") },
                        textStyle = MonoStyle.merge(MaterialTheme.typography.bodySmall),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 280.dp),
                    )
                    OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { pasteDialog = null; vm.importText(text, name) }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pasteDialog = null }) { Text("Cancel") } },
        )
    }

    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { renaming = null; vm.renameProfile(p, name) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${p.name}?") },
            text = { Text("The profile and its file are removed from this phone.") },
            confirmButton = { TextButton(onClick = { deleting = null; vm.deleteProfile(p) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }

    interval?.let { p ->
        val options = listOf(0 to "Never", 1 to "Every hour", 6 to "Every 6 hours", 12 to "Every 12 hours", 24 to "Daily", 72 to "Every 3 days")
        AlertDialog(
            onDismissRequest = { interval = null },
            title = { Text("Auto-update") },
            text = {
                Column {
                    options.forEach { (h, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            RadioButton(selected = p.updateIntervalHours == h, onClick = { interval = null; vm.setUpdateInterval(p, h) })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { interval = null }) { Text("Close") } },
        )
    }
}

private fun clipboardText(context: Context): String {
    val cm = context.getSystemService(ClipboardManager::class.java)
    return runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() }.getOrNull().orEmpty()
}

@Composable
private fun SheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

@Composable
private fun ProfileCard(
    p: Profile,
    selected: Boolean,
    active: Boolean,
    onSelect: () -> Unit,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onConvert: () -> Unit,
    onCredentials: () -> Unit,
    onInterval: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Badge(p.badge)
                    if (active) Badge("Active", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    Text(p.kind.engine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (p.summary.isNotBlank()) {
                    Text(p.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                p.subscription?.let { s ->
                    if (s.total > 0) {
                        val used = s.upload + s.download
                        LinearProgressIndicator(
                            progress = { (used.toFloat() / s.total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 2.dp),
                        )
                        Text(
                            "${formatBytes(used)} of ${formatBytes(s.total)}" + if (s.expire > 0) " · expires ${formatDate(s.expire * 1000)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (p.url != null) {
                    Text(
                        "Updated ${formatRelative(p.updatedAt)}" + if (p.updateIntervalHours == 0) " · auto-update off" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (p.url != null) {
                        DropdownMenuItem(text = { Text("Update now") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { menu = false; onUpdate() })
                        DropdownMenuItem(text = { Text("Auto-update…") }, leadingIcon = { Icon(Icons.Rounded.Schedule, null) }, onClick = { menu = false; onInterval() })
                    }
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                    if (p.kind == ProfileKind.OPENVPN) {
                        DropdownMenuItem(text = { Text("Login…") }, leadingIcon = { Icon(Icons.Rounded.Key, null) }, onClick = { menu = false; onCredentials() })
                    }
                    if (p.kind != ProfileKind.CLASH) {
                        DropdownMenuItem(
                            text = { Text("Copy as Proxy-mode profile") },
                            leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) },
                            onClick = { menu = false; onConvert() },
                        )
                    }
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/** Username/password prompt for OpenVPN servers that use auth-user-pass. */
@Composable
fun CredentialsDialog(title: String, initialUser: String, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Key, null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This OpenVPN server asks for a username and password. They are stored in Tidewall's private storage on this phone.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pass, { pass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = user.isNotBlank(), onClick = { onConfirm(user, pass) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
