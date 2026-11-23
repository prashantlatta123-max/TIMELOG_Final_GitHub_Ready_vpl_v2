package com.latta.timelog

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.latta.timelog.data.AppUsage
import com.latta.timelog.data.UsageRepository
import com.latta.timelog.alerts.UsageAlertManager
import com.latta.timelog.tracking.SleepStateStore
import com.latta.timelog.tracking.SleepDetector
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TimeLogApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeLogApp() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val request = PeriodicWorkRequestBuilder<com.latta.timelog.tracking.UsageLimitWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "timelog_usage_limit_check", ExistingPeriodicWorkPolicy.KEEP, request
        )
        val sleepRequest = PeriodicWorkRequestBuilder<com.latta.timelog.tracking.SleepDetectionWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "timelog_sleep_detection", ExistingPeriodicWorkPolicy.KEEP, sleepRequest
        )
    }
    val prefs = remember { context.getSharedPreferences("timelog_settings", android.content.Context.MODE_PRIVATE) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark_theme", true)) }
    var page by remember { mutableStateOf("Dashboard") }
    var usage by remember { mutableStateOf(emptyList<AppUsage>()) }
    var hasAccess by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val repo = remember { UsageRepository(context) }
    val sleep = remember { SleepStateStore(context) }
    val alertManager = remember { UsageAlertManager(context) }

    fun setTheme(value: Boolean) {
        dark = value
        prefs.edit().putBoolean("dark_theme", value).apply()
    }

    val bg = if (dark) Color(0xFF080808) else Color(0xFFF5F6F8)
    val surface = if (dark) Color(0xFF151515) else Color.White.copy(alpha = 0.90f)
    val text = if (dark) Color(0xFFF7F7F7) else Color(0xFF171717)
    val muted = if (dark) Color(0xFF9B9B9B) else Color(0xFF737373)
    val accent = if (dark) Color(0xFFFF365B) else Color(0xFFB51236)
    val border = if (dark) Color(0xFF303030) else Color(0xFFE5E5E5)

    LaunchedEffect(refresh) {
        hasAccess = repo.hasUsageAccess()
        if (hasAccess) {
            usage = withContext(Dispatchers.IO) { repo.todayUsage() }
            repo.persistTodaySnapshot()
            withContext(Dispatchers.IO) { SleepDetector(context).refreshLast24Hours() }
            evaluateUsageAlerts(context, usage, repo, alertManager)
        }
    }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = accent, background = bg, surface = surface,
            onBackground = text, onSurface = text
        ) else lightColorScheme(
            primary = accent, background = bg, surface = surface,
            onBackground = text, onSurface = text
        )
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            scrimColor = Color.Black.copy(alpha = 0.48f),
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = surface,
                    drawerContentColor = text,
                    modifier = Modifier.widthIn(min = 260.dp, max = 300.dp)
                ) {
                    Column(Modifier.fillMaxHeight().padding(horizontal = 14.dp)) {
                        Spacer(Modifier.height(28.dp))
                        Text("TIMELOG", color = text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("DIGITAL WELLBEING", color = muted, fontSize = 10.sp, letterSpacing = 2.sp)
                        Spacer(Modifier.height(22.dp))
                        HorizontalDivider(color = border)
                        Spacer(Modifier.height(12.dp))
                        val menu = listOf(
                            "Dashboard" to Icons.Default.Dashboard,
                            "App Usage" to Icons.Default.Smartphone,
                            "Hourly Timeline" to Icons.Default.Schedule,
                            "Usage History" to Icons.Default.History,
                            "Analytics" to Icons.Default.BarChart,
                            "Sleep Tracking" to Icons.Default.Bedtime,
                            "Sleep History" to Icons.Default.History,
                            "Smart Alerts" to Icons.Default.Notifications,
                            "Usage Limits" to Icons.Default.Flag,
                            "Quick Search" to Icons.Default.Search,
                            "Privacy Dashboard" to Icons.Default.Lock
                        )
                        menu.forEach { (label, icon) ->
                            NavigationDrawerItem(
                                icon = { Icon(icon, null) },
                                label = { Text(label, fontSize = 14.sp) },
                                selected = page == label,
                                onClick = { page = label },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = accent.copy(alpha = 0.13f),
                                    selectedIconColor = accent,
                                    selectedTextColor = accent,
                                    unselectedTextColor = muted,
                                    unselectedIconColor = muted
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = border)
                        Spacer(Modifier.height(8.dp))
                        Text("APPEARANCE", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 4.dp))
                        NavigationDrawerItem(
                            icon = { Icon(if (dark) Icons.Default.DarkMode else Icons.Default.LightMode, null) },
                            label = { Text(if (dark) "Theme · Dark" else "Theme · Light", fontSize = 14.sp) },
                            selected = false,
                            onClick = { setTheme(!dark) },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedTextColor = muted, unselectedIconColor = muted
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = border)
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "Backup & Restore" to Icons.Default.Storage,
                            "About" to Icons.Default.Info
                        ).forEach { (label, icon) ->
                            NavigationDrawerItem(
                                icon = { Icon(icon, null) },
                                label = { Text(label, fontSize = 14.sp) },
                                selected = page == label,
                                onClick = { page = label },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = accent.copy(alpha = 0.13f),
                                    selectedIconColor = accent,
                                    selectedTextColor = accent,
                                    unselectedTextColor = muted,
                                    unselectedIconColor = muted
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text("SAROTECH", color = muted, fontSize = 10.sp,
                            modifier = Modifier.padding(start = 14.dp, bottom = 4.dp))
                        Text("By Prashant Latta", color = muted, fontSize = 11.sp,
                            modifier = Modifier.padding(start = 14.dp, bottom = 22.dp))
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = bg,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("TIMELOG", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = text)
                                Text(page.uppercase(), fontSize = 9.sp, color = muted, letterSpacing = 1.4.sp)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Open menu", tint = text)
                            }
                        },
                        actions = {
                            IconButton(onClick = { refresh++ }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = muted)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = bg)
                    )
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (page) {
                        "Dashboard" -> DashboardScreen(
                            usage = usage, hasAccess = hasAccess, repo = repo, sleep = sleep,
                            text = text, muted = muted, surface = surface, accent = accent,
                            border = border, onOpen = { page = it }, onRefresh = { refresh++ }
                        )
                        "App Usage" -> AppUsageScreen(usage, text, muted, surface, accent)
                        "Hourly Timeline" -> HourlyTimelinePage(repo, text, muted, surface, accent)
                        "Usage History" -> UsageHistoryPage(repo, text, muted, surface, accent)
                        "Analytics" -> AnalyticsPage(usage, repo, text, muted, surface, accent)
                        "Sleep Tracking" -> SleepPage(sleep, text, muted, surface, accent)
                        "Sleep History" -> SleepHistoryPage(context, text, muted, surface, accent)
                        "Smart Alerts", "Usage Limits" -> AlertSettingsPage(context, usage, text, muted, surface, accent, page)
                        "Quick Search" -> QuickSearchPage(usage, text, muted, surface, accent)
                        "Privacy Dashboard" -> PrivacyPage(hasAccess, text, muted, surface, accent, repo)
                        "Backup & Restore" -> BackupRestorePage(context, text, muted, surface, accent)
                        "About" -> AboutPage(text, muted, surface, accent)
                        else -> DashboardScreen(
                            usage, hasAccess, repo, sleep, text, muted, surface, accent,
                            border, { page = it }, { refresh++ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    usage: List<AppUsage>,
    hasAccess: Boolean,
    repo: UsageRepository,
    sleep: SleepStateStore,
    text: Color,
    muted: Color,
    surface: Color,
    accent: Color,
    border: Color,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val total = usage.sumOf { it.usageMillis }
    val maxDailyMillis = 24L * 60L * 60L * 1000L
    val progress = (total.toFloat() / maxDailyMillis.toFloat()).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(10.dp))
        Text("ACTIVE APP TIME", color = muted, fontSize = 11.sp,
            letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.size(218.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 13.dp.toPx()
                drawArc(
                    color = border,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatDuration(total), color = text, fontSize = 31.sp, fontWeight = FontWeight.Bold)
                Text("TODAY", color = muted, fontSize = 10.sp, letterSpacing = 2.sp)
                if (!hasAccess) {
                    Spacer(Modifier.height(5.dp))
                    Text("Permission needed", color = accent, fontSize = 10.sp)
                }
            }
        }
        if (!hasAccess) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { repo.openUsageAccessSettings() }) {
                Text("Allow Usage Access", color = accent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasAccess) "DATA QUALITY  ·  Usage access available; OEM background limits may create gaps"
            else "DATA QUALITY  ·  Usage access required",
            color = muted, fontSize = 9.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = border, thickness = 1.dp)
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = border, thickness = 1.dp)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(
                modifier = Modifier.weight(1f),
                title = "Sleep",
                subtitle = if (sleep.sleepStart() > 0) formatTime(sleep.sleepStart()) else "Sleep details",
                icon = Icons.Default.Bedtime,
                text = text, muted = muted, surface = surface, accent = accent,
                onClick = { onOpen("Sleep Tracking") }
            )
            QuickTile(
                modifier = Modifier.weight(1f),
                title = "All Apps",
                subtitle = "${usage.size} apps tracked",
                icon = Icons.Default.Apps,
                text = text, muted = muted, surface = surface, accent = accent,
                onClick = { onOpen("App Usage") }
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(
                modifier = Modifier.weight(1f),
                title = "Timeline",
                subtitle = "Hourly activity",
                icon = Icons.Default.Schedule,
                text = text, muted = muted, surface = surface, accent = accent,
                onClick = { onOpen("Hourly Timeline") }
            )
            QuickTile(
                modifier = Modifier.weight(1f),
                title = "More Details",
                subtitle = "Usage history",
                icon = Icons.Default.MoreHoriz,
                text = text, muted = muted, surface = surface, accent = accent,
                onClick = { onOpen("Usage History") }
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("MOST USED APPS", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold)
            Text("VIEW ALL", color = accent, fontSize = 10.sp, modifier = Modifier.clickable { onOpen("App Usage") })
        }
        Spacer(Modifier.height(8.dp))
        usage.take(3).forEach { app ->
            AppUsageRow(app, text, muted, surface, accent)
            Spacer(Modifier.height(7.dp))
        }
        if (usage.isEmpty() && hasAccess) {
            Text("No app usage data available yet.", color = muted, fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 18.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun QuickTile(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: Color,
    muted: Color,
    surface: Color,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Column {
                Text(title, color = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = muted, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AppUsageScreen(
    usage: List<AppUsage>, text: Color, muted: Color, surface: Color, accent: Color
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Today's app activity", color = muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        usage.forEach { app ->
            AppUsageRow(app, text, muted, surface, accent)
            Spacer(Modifier.height(8.dp))
        }
        if (usage.isEmpty()) Text("Usage data will appear after granting Usage Access.", color = muted)
    }
}

@Composable
private fun AppUsageRow(app: AppUsage, text: Color, muted: Color, surface: Color, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.appName, color = text, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Text(
                    "${app.launchCount} openings  ·  first ${formatTime(app.firstOpenedAt)}  ·  last ${formatTime(app.lastUsed)}",
                    color = muted, fontSize = 9.sp, maxLines = 1
                )
            }
            Text(formatDuration(app.usageMillis), color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}



private fun evaluateUsageAlerts(
    context: android.content.Context,
    usage: List<AppUsage>,
    repo: UsageRepository,
    alerts: UsageAlertManager
) {
    val prefs = context.getSharedPreferences("timelog_alerts", android.content.Context.MODE_PRIVATE)
    val rules = try { org.json.JSONObject(prefs.getString("rules_json", "{}").orEmpty()) }
        catch (_: Exception) { org.json.JSONObject() }
    val keys = rules.keys()
    while (keys.hasNext()) {
        val packageName = keys.next()
        val rule = rules.optJSONObject(packageName) ?: continue
        val threshold = rule.optInt("threshold", 0)
        val app = usage.firstOrNull { it.packageName == packageName } ?: continue
        if (alerts.shouldNotify(packageName, repo.dateKey(), app.launchCount, threshold)) {
            alerts.notifyLimitReached(
                app.appName, rule.optString("message"), app.launchCount, threshold
            )
        }
    }
}

@Composable
private fun HourlyTimelinePage(
    repo: UsageRepository, text: Color, muted: Color, surface: Color, accent: Color
) {
    var rows by remember { mutableStateOf(emptyList<com.latta.timelog.data.HourlyAppUsage>()) }
    LaunchedEffect(Unit) { rows = withContext(Dispatchers.IO) { repo.hourlyAppUsageForLast24Hours() } }
    val grouped = rows.groupBy { it.hourStart }.toSortedMap()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Last 24 hours · app-wise foreground time", color = muted, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        if (grouped.isEmpty()) {
            Text("No foreground app events found in this period.", color = muted, fontSize = 12.sp)
        }
        grouped.forEach { (hourStart, items) ->
            val hourLabel = SimpleDateFormat("HH:00", Locale.getDefault()).format(Date(hourStart))
            Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(hourLabel, color = text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(formatDuration(items.sumOf { it.usageMillis }), color = accent, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                    items.sortedByDescending { it.usageMillis }.take(4).forEach { app ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(app.appName, color = muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            Text(formatDuration(app.usageMillis), color = text, fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
        }
        Text("Incomplete or unavailable Android UsageEvents can cause gaps; background sync and notifications are not counted as app foreground time.", color = muted, fontSize = 10.sp)
    }
}

@Composable
private fun UsageHistoryPage(
    repo: UsageRepository, text: Color, muted: Color, surface: Color, accent: Color
) {
    val context = LocalContext.current
    var days by remember { mutableStateOf(emptyList<Pair<String, Long>>()) }
    LaunchedEffect(Unit) {
        days = withContext(Dispatchers.IO) {
            val all = com.latta.timelog.data.TimeLogDatabase.get(context).dao().allUsage()
            all.groupBy { it.dateKey }
                .map { (date, rows) -> date to rows.sumOf { it.usageMillis } }
                .sortedByDescending { it.first }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Saved daily history · stored locally", color = muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        days.forEach { (day, millis) ->
            Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(day, color = text, fontSize = 13.sp)
                    Text(formatDuration(millis), color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (days.isEmpty()) Text("No saved history yet. Daily records are collected after Usage Access is granted and background work runs.", color = muted, fontSize = 12.sp)
    }
}

@Composable
private fun AnalyticsPage(
    today: List<AppUsage>, repo: UsageRepository, text: Color, muted: Color, surface: Color, accent: Color
) {
    var weekly by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        weekly = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val start = now - 7L * 24L * 60L * 60L * 1000L
            repo.usageBetween(start, now).sumOf { it.usageMillis }
        }
    }
    val total = today.sumOf { it.usageMillis }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        StatCard("TODAY", formatDuration(total), "Total foreground app time", text, muted, surface)
        Spacer(Modifier.height(10.dp))
        StatCard("TOP APP", today.firstOrNull()?.appName ?: "No data",
            today.firstOrNull()?.let { formatDuration(it.usageMillis) } ?: "Grant Usage Access",
            text, muted, surface)
        Spacer(Modifier.height(10.dp))
        StatCard("LAST 7 DAYS QUERY", formatDuration(weekly),
            "UsageStats summary; Android may aggregate this value", text, muted, surface)
        Spacer(Modifier.height(16.dp))
        Text("Most used apps today", color = muted, fontSize = 11.sp)
        today.take(5).forEach {
            AppUsageRow(it, text, muted, surface, accent)
            Spacer(Modifier.height(7.dp))
        }
    }
}


@Composable
private fun SleepHistoryPage(
    context: android.content.Context, text: Color, muted: Color, surface: Color, accent: Color
) {
    var records by remember { mutableStateOf(emptyList<com.latta.timelog.data.SleepRecordEntity>()) }
    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) {
            com.latta.timelog.data.TimeLogDatabase.get(context).dao().allSleepRecords()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Saved sleep estimates", color = muted, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        records.forEach { record ->
            Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text(java.text.SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(Date(record.sleepStart)),
                        color = text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("${formatTime(record.sleepStart)}  →  ${formatTime(record.awakenAt)}",
                        color = accent, fontSize = 13.sp)
                    Text("Window: ${formatDuration(record.sleepWindowMillis)} · ${record.confidence}",
                        color = muted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (records.isEmpty()) Text("No confirmed sleep estimates yet. Grant Usage Access and use the phone normally.", color = muted, fontSize = 12.sp)
    }
}

@Composable
private fun QuickSearchPage(
    usage: List<AppUsage>, text: Color, muted: Color, surface: Color, accent: Color
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var savedRows by remember { mutableStateOf(emptyList<com.latta.timelog.data.DailyAppUsageEntity>()) }
    LaunchedEffect(Unit) {
        savedRows = withContext(Dispatchers.IO) {
            com.latta.timelog.data.TimeLogDatabase.get(context).dao().allUsage()
        }
    }
    val todayMatches = usage.filter {
        it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }
    val savedMatches = savedRows.filter {
        it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true) ||
            it.dateKey.contains(query, ignoreCase = true)
    }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Search apps or date (YYYY-MM-DD)") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("TODAY", color = muted, fontSize = 10.sp, letterSpacing = 1.2.sp)
            todayMatches.forEach { AppUsageRow(it, text, muted, surface, accent); Spacer(Modifier.height(6.dp)) }
            Text("SAVED HISTORY", color = muted, fontSize = 10.sp, letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            savedMatches.forEach { row ->
                Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(row.appName, color = text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("${row.dateKey} · ${row.launchCount} openings", color = muted, fontSize = 10.sp)
                        }
                        Text(formatDuration(row.usageMillis), color = accent, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (todayMatches.isEmpty() && savedMatches.isEmpty()) {
                Text("No matching records found.", color = muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AlertSettingsPage(
    context: android.content.Context, usage: List<AppUsage>, text: Color, muted: Color,
    surface: Color, accent: Color, page: String
) {
    val prefs = remember { context.getSharedPreferences("timelog_alerts", android.content.Context.MODE_PRIVATE) }
    var packageName by remember { mutableStateOf("") }
    var threshold by remember { mutableStateOf("20") }
    var message by remember { mutableStateOf("You have opened this app many times today. Take a short break.") }
    var rulesJson by remember { mutableStateOf(prefs.getString("rules_json", "{}").orEmpty()) }
    var saved by remember { mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(if (page == "Smart Alerts") "Notification alert settings" else "App opening limit",
            color = text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text("Select or enter the exact Android package name of the app to monitor. TIMELOG sends a notification only; it never draws over other apps.", color = muted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = packageName, onValueChange = { packageName = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("App package name") }, singleLine = true
        )
        val matching = usage.firstOrNull { it.packageName == packageName }
        if (matching != null) Text("Detected app: ${matching.appName}", color = accent, fontSize = 11.sp)
        if (usage.isNotEmpty()) {
            Text("Choose from recently used apps", color = muted, fontSize = 10.sp)
            usage.take(5).forEach { app ->
                TextButton(
                    onClick = { packageName = app.packageName },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("${app.appName}  ·  ${app.packageName}", color = accent, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = threshold, onValueChange = { threshold = it.filter(Char::isDigit).take(4) },
            modifier = Modifier.fillMaxWidth(), label = { Text("Daily opening threshold") }, singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = message, onValueChange = { message = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Your custom notification message") },
            minLines = 3
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val key = packageName.trim()
            if (key.isNotBlank()) {
                val rules = org.json.JSONObject(rulesJson)
                rules.put(key, org.json.JSONObject()
                    .put("threshold", threshold.toIntOrNull() ?: 20)
                    .put("message", message.trim()))
                rulesJson = rules.toString()
                prefs.edit().putString("rules_json", rulesJson).apply()
                saved = true
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Save alert rule") }
        if (saved) Text("Alert rule saved on this device.", color = accent, fontSize = 12.sp)
        val savedRules = remember(rulesJson) { org.json.JSONObject(rulesJson) }
        if (savedRules.length() > 0) {
            Spacer(Modifier.height(8.dp))
            Text("SAVED APP RULES", color = muted, fontSize = 10.sp, letterSpacing = 1.2.sp)
            val keys = savedRules.keys().asSequence().toList()
            keys.forEach { key ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(key, color = text, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        savedRules.remove(key)
                        rulesJson = savedRules.toString()
                        prefs.edit().putString("rules_json", rulesJson).apply()
                    }) { Text("Remove", color = accent) }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            OutlinedButton(
                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Allow notifications") }
        } else {
            Text("Notification permission is available. Rules are checked periodically (minimum 15-minute WorkManager interval; Android may defer delivery).", color = muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PrivacyPage(
    hasAccess: Boolean, text: Color, muted: Color, surface: Color, accent: Color, repo: UsageRepository
) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("USAGE ACCESS", if (hasAccess) "Granted" else "Not granted",
            "Android Usage Access permission", text, muted, surface)
        Text("Core tracking and backups are stored locally. No account, advertising SDK, or cloud upload is configured in this project.", color = muted, fontSize = 12.sp)
        OutlinedButton(onClick = { repo.openUsageAccessSettings() }, modifier = Modifier.fillMaxWidth()) {
            Text("Open Usage Access Settings")
        }
    }
}

@Composable
private fun BackupRestorePage(
    context: android.content.Context, text: Color, muted: Color, surface: Color, accent: Color
) {
    val scope = rememberCoroutineScope()
    val manager = remember { com.latta.timelog.data.LocalBackupManager(context) }
    var status by remember { mutableStateOf("Backups are saved locally using the Android file picker.") }
    var confirmRestore by remember { mutableStateOf(false) }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch {
            status = try {
                withContext(Dispatchers.IO) {
                    UsageRepository(context).takeIf { it.hasUsageAccess() }?.persistTodaySnapshot()
                    SleepDetector(context).refreshLast24Hours()
                    manager.exportTo(uri)
                }
                "Backup exported successfully."
            } catch (e: Exception) { "Backup failed: ${e.message}" }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            status = try {
                withContext(Dispatchers.IO) { manager.importFrom(uri) }
                "Backup restored. Reopen the app to refresh all views."
            } catch (e: Exception) { "Restore failed: ${e.message}" }
        }
    }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("LOCAL BACKUP", "On-device", "Export or restore a TIMELOG JSON backup", text, muted, surface)
        Button(onClick = { exportLauncher.launch("TIMELOG-backup-${System.currentTimeMillis()}.json") },
            modifier = Modifier.fillMaxWidth()) { Text("Create / Export Backup") }
        OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Choose Backup to Restore")
        }
        Text(status, color = muted, fontSize = 11.sp)
        Text("Restore replaces the locally stored TIMELOG history. Export a current backup first if you need to preserve it.", color = muted, fontSize = 10.sp)
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore backup?") },
            text = { Text("This replaces TIMELOG's saved local history with the selected backup. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    importLauncher.launch(arrayOf("application/json", "text/json"))
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SleepPage(sleep: SleepStateStore, text: Color, muted: Color, surface: Color, accent: Color) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("SLEEP", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                Text(formatTime(sleep.sleepStart()), color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Estimated sleep start", color = muted, fontSize = 11.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("AWAKEN", color = muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(6.dp))
                Text(formatTime(sleep.awakenAt()), color = text, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Estimated first active unlock", color = muted, fontSize = 11.sp)
            }
        }
        Text(
            "Sleep is estimated from phone inactivity. Notifications and network activity are not treated as a phone unlock. This record is not a medical sleep measurement.",
            color = muted, fontSize = 12.sp, lineHeight = 18.sp
        )
    }
}

@Composable
private fun AboutPage(text: Color, muted: Color, surface: Color, accent: Color) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.timelog_logo_master),
            contentDescription = "TIMELOG app logo",
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(26.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(18.dp))
        Text("TIMELOG", color = text, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("DIGITAL WELLBEING", color = muted, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(28.dp))
        Card(colors = CardDefaults.cardColors(containerColor = surface), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SAROTECH", color = accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(7.dp))
                Text("By Prashant Latta", color = text, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text("Other Details", color = muted, fontSize = 11.sp)
                Text("Version 1.0.0", color = muted, fontSize = 11.sp)
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0m"
    val mins = ms / 60000
    val hours = mins / 60
    val minutes = mins % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatTime(ms: Long): String =
    if (ms <= 0) "—" else SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(ms))
