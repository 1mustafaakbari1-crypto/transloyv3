package com.mustafoy.transloy

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mustafoy.transloy.data.AppSettings
import com.mustafoy.transloy.data.UserPreferences
import com.mustafoy.transloy.domain.HybridTranslationEngine
import com.mustafoy.transloy.model.OcrScriptMode
import com.mustafoy.transloy.model.SupportedLanguages
import com.mustafoy.transloy.service.TransloyAccessibilityService
import com.mustafoy.transloy.ui.theme.TransloyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = UserPreferences(applicationContext)
        val engine = HybridTranslationEngine(applicationContext)

        setContent {
            TransloyTheme {
                TransloyDashboard(prefs = prefs, engine = engine)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransloyDashboard(
    prefs: UserPreferences,
    engine: HybridTranslationEngine
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by prefs.settingsFlow.collectAsState(initial = AppSettings())
    val liveConnected by TransloyAccessibilityService.isServiceConnected.collectAsState()

    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var downloadedPacks by remember { mutableStateOf<Set<String>>(emptySet()) }
    var downloadingCode by remember { mutableStateOf<String?>(null) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showScriptSheet by remember { mutableStateOf(false) }
    var showOfflineManagerSheet by remember { mutableStateOf(false) }

    // Refresh service status & offline models whenever user returns to app
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = isAccessibilityEnabled(context)
                scope.launch {
                    downloadedPacks = runCatching { engine.getDownloadedModelCodes() }
                        .getOrDefault(emptySet())
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isReadyAndRunning = (accessibilityEnabled || liveConnected) && settings.bubbleEnabled

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("TRANSLOY", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            // 1. HERO POWER CARD
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isReadyAndRunning)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isReadyAndRunning) "Ghost Bubble Active" else "Translator Ready",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isReadyAndRunning) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (!accessibilityEnabled && !liveConnected)
                                        "One-time setup required • Zero popups forever"
                                    else if (settings.bubbleEnabled)
                                        "Tap the edge bubble on any app, manga, or game"
                                    else
                                        "Floating bubble is currently paused",
                                    fontSize = 13.5.sp,
                                    color = if (isReadyAndRunning) Color.White.copy(alpha = 0.85f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }

                            Switch(
                                checked = isReadyAndRunning,
                                onCheckedChange = { wantOn ->
                                    if (!accessibilityEnabled && !liveConnected) {
                                        openAccessibilitySettings(context)
                                    } else {
                                        scope.launch { prefs.setBubbleEnabled(wantOn) }
                                    }
                                }
                            )
                        }

                        if (!accessibilityEnabled && !liveConnected) {
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { openAccessibilitySettings(context) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.VerifiedUser, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Enable 1-Time Screen Access", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 2. LANGUAGE & AUTO-DETECT CARD
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Translation Workflow",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(14.dp))

                        // Auto Source Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                .clickable { showScriptSheet = true }
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Original Screen Language", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Text("Auto-Detect (${settings.ocrScriptMode.label})", fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.Rounded.Tune, contentDescription = null)
                        }

                        Spacer(Modifier.height(10.dp))

                        // Default Target Language Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f))
                                .clickable { showLanguageSheet = true }
                                .padding(16.dp)
                        ) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Default Translate Into", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Text(
                                    SupportedLanguages.nameFor(settings.targetLanguage),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                            }
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                }
            }

            // 3. HYBRID ONLINE + OFFLINE BRAIN MANAGER CARD
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.OfflineBolt, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Hybrid Online + Offline Engine", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Uses fast Cloud AI when online and automatically switches to On-Device Offline AI with zero internet.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        )

                        Spacer(Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Auto-save offline packs when online", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = settings.autoDownloadOfflinePacks,
                                onCheckedChange = { scope.launch { prefs.setAutoDownloadOffline(it) } }
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { showOfflineManagerSheet = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.DownloadForOffline, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Manage Offline Language Packs (${downloadedPacks.size} Ready)")
                        }
                    }
                }
            }

            // 4. BUILT-IN INSTANT TEST PLAYGROUND CARD
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Instant Screen Test Area", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap your floating Ghost Bubble right now to see these translate in-place:", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.height(12.dp))
                        Text("• こんにちは！画面上のすべてのテキストを自動翻訳します。", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text("• La traduction hors ligne et en ligne fonctionne parfaitement.", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text("• ¡Toca cualquier lugar para cerrar la superposición!", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // BOTTOM SHEET 1: Default Target Language Picker
    if (showLanguageSheet) {
        ModalBottomSheet(onDismissRequest = { showLanguageSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Select Default Target Language", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(SupportedLanguages.list) { lang ->
                        val selected = lang.code.equals(settings.targetLanguage, ignoreCase = true)
                        ListItem(
                            headlineContent = { Text("${lang.name} (${lang.nativeName})", fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                            trailingContent = {
                                if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        prefs.setTargetLanguage(lang.code)
                                        showLanguageSheet = false
                                    }
                                }
                        )
                    }
                }
            }
        }
    }

    // BOTTOM SHEET 2: OCR Script Optimization Selector
    if (showScriptSheet) {
        ModalBottomSheet(onDismissRequest = { showScriptSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Smart OCR Recognition Mode", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                OcrScriptMode.entries.forEach { mode ->
                    val selected = mode == settings.ocrScriptMode
                    ListItem(
                        headlineContent = { Text(mode.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        trailingContent = {
                            if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch {
                                    prefs.setOcrScriptMode(mode)
                                    showScriptSheet = false
                                }
                            }
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    // BOTTOM SHEET 3: Offline Language Pack Downloader
    if (showOfflineManagerSheet) {
        ModalBottomSheet(onDismissRequest = { showOfflineManagerSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Offline Language Packs (~30 MB each)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text("Downloaded languages work in Airplane Mode with zero internet.", fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))

                LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    items(SupportedLanguages.list) { lang ->
                        val isDownloaded = downloadedPacks.contains(lang.code)
                        val isDownloading = downloadingCode == lang.code

                        ListItem(
                            headlineContent = { Text("${lang.name} (${lang.nativeName})") },
                            supportingContent = {
                                Text(if (isDownloaded) "Ready Offline ✓" else "Online only (Tap to download)")
                            },
                            trailingContent = {
                                when {
                                    isDownloading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                    isDownloaded -> IconButton(onClick = {
                                        scope.launch {
                                            engine.deleteOfflineModel(lang.code)
                                            downloadedPacks = engine.getDownloadedModelCodes()
                                        }
                                    }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete")
                                    }
                                    else -> IconButton(onClick = {
                                        scope.launch {
                                            downloadingCode = lang.code
                                            engine.downloadOfflineModel(lang.code)
                                            downloadedPacks = engine.getDownloadedModelCodes()
                                            downloadingCode = null
                                        }
                                    }) {
                                        Icon(Icons.Rounded.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, TransloyAccessibilityService::class.java)
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    return enabledServices.split(':').any {
        ComponentName.unflattenFromString(it) == expected
    }
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
