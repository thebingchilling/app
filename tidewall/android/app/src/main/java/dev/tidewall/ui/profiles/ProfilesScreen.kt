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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import dev.tidewall.data.Profile
import dev.tidewall.data.ProfileKind
import dev.tidewall.ui.LoginRequest
import dev.tidewall.ui.MainViewModel
import dev.tidewall.ui.common.Corner
import dev.tidewall.ui.common.FlCard
import dev.tidewall.ui.common.GridSpacing
import dev.tidewall.ui.common.NullStatus
import dev.tidewall.ui.common.formatBytes
import dev.tidewall.ui.common.formatDate
import dev.tidewall.ui.common.formatRelative
import dev.tidewall.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(vm: MainViewModel, openEditor: (Profile) -> Unit, openShadowsocks: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
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
            TopAppBar(
                title = { Text("Profiles") },
                actions = {
                    if (profiles.any { it.url != null }) {
                        IconButton(onClick = vm::updateAll) { Icon(Icons.Filled.Sync, "Update") }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { sheet = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add profile") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (profiles.isEmpty()) {
                NullStatus("No profiles yet, please add one first", Icons.Filled.Folder)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(GridSpacing),
                ) {
                    items(profiles, key = { it.id }) { p ->
                        ProfileCard(
                            p,
                            selected = p.id == selected?.id,
                            active = state.active && state.profileId == p.id,
                            onSelect = { vm.selectProfile(p) },
                            onUpdate = { vm.updateProfile(p) },
                            onEdit = { openEditor(p) },
                            onRename = { renaming = p },
                            onDelete = { deleting = p },
                            onConvert = { vm.convertToProxy(p) },
                            onLogin = { vm.editLogin(p) },
                            onInterval = { interval = p },
                            onCopyLink = { p.url?.let { copyText(context, it) }; vm.toast("Copied") },
                        )
                    }
                }
            }
        }
    }

    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            Text("Add profile", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SheetItem(Icons.Filled.QrCode, "QR code", "Scan a QR code to obtain a profile") {
                sheet = false
                scan.launch(ScanOptions().setPrompt("Scan a share link or config QR code").setBeepEnabled(false).setOrientationLocked(false))
            }
            SheetItem(Icons.Filled.UploadFile, "File", "Upload a profile file directly (.yaml, .ovpn, WireGuard .conf)") {
                sheet = false; openFile.launch(arrayOf("*/*"))
            }
            SheetItem(Icons.Filled.CloudDownload, "URL", "Obtain a profile from a URL") {
                sheet = false; urlDialog = true
            }
            SheetItem(Icons.Filled.ContentPaste, "Paste", "Share links (vless://, vmess://, ss://, trojan://…), YAML, .ovpn or .conf text") {
                sheet = false; pasteDialog = clipboardText(context)
            }
            SheetItem(Icons.Filled.VpnKey, "Shadowsocks server", "Enter server details by hand (all ciphers, including rc4-md5)") {
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
            title = { Text("Import from URL") },
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
            title = { Text("Tip") },
            text = { Text("Are you sure you want to delete ${p.name}?") },
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

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("url", text))
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
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp),
    )
}

/** FlClash ProfileItem: an outlined card, tinted when selected, with a ⋮ menu. */
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
    onLogin: () -> Unit,
    onInterval: () -> Unit,
    onCopyLink: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val lighter = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    FlCard(Modifier.fillMaxWidth(), selected = selected, radius = Corner.xl, onClick = onSelect) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(
                    listOf(if (active) "Active" else null, p.badge, p.summary.ifBlank { null }).filterNotNull().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (p.missingLogin) {
                    Text("Login needed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                p.subscription?.takeIf { it.total > 0 }?.let { s ->
                    val used = s.upload + s.download
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatBytes(used)} / ${formatBytes(s.total)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text(if (s.expire > 0) formatDate(s.expire * 1000) else "Never expires", style = MaterialTheme.typography.bodySmall, color = lighter)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (used.toFloat() / s.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        drawStopIndicator = {},
                        gapSize = 0.dp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(formatRelative(p.updatedAt).replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodySmall, color = lighter)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Preview") }, leadingIcon = { Icon(Icons.Outlined.Visibility, null) }, onClick = { menu = false; onEdit() })
                    if (p.url != null) {
                        DropdownMenuItem(text = { Text("Sync") }, leadingIcon = { Icon(Icons.Filled.SyncAlt, null) }, onClick = { menu = false; onUpdate() })
                    }
                    HorizontalDivider()
                    if (p.kind == ProfileKind.OPENVPN) {
                        DropdownMenuItem(text = { Text("Login") }, leadingIcon = { Icon(Icons.Outlined.Key, null) }, onClick = { menu = false; onLogin() })
                    }
                    if (p.url != null) {
                        DropdownMenuItem(text = { Text("Auto update") }, leadingIcon = { Icon(Icons.Outlined.Schedule, null) }, onClick = { menu = false; onInterval() })
                        DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; onCopyLink() })
                    }
                    if (p.kind != ProfileKind.CLASH) {
                        DropdownMenuItem(
                            text = { Text("Copy as proxy profile") },
                            leadingIcon = { Icon(Icons.Filled.SwapHoriz, null) },
                            onClick = { menu = false; onConvert() },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/**
 * Username/password prompt for OpenVPN servers that use auth-user-pass. Shown
 * after importing such a profile, when connecting without a saved login, and
 * when the server rejects the login.
 */
@Composable
fun LoginDialog(req: LoginRequest, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var user by remember(req) { mutableStateOf(req.profile.username.orEmpty()) }
    var pass by remember(req) { mutableStateOf(req.profile.password.orEmpty()) }
    var show by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Key, null) },
        title = { Text("OpenVPN login") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (req.reason != null) {
                    Text(req.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "${req.profile.name} asks for a username and password (auth-user-pass). Use the OpenVPN credentials " +
                        "from your VPN provider — many providers issue separate \"OpenVPN\" or \"service\" credentials " +
                        "that differ from your website login. They are stored only on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    pass, { pass = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { show = !show }) {
                            Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Hide password" else "Show password")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = user.isNotBlank(), onClick = { onConfirm(user, pass) }) {
                Text(if (req.connectAfter) "Save and connect" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (req.connectAfter) "Cancel" else "Later") } },
    )
}
