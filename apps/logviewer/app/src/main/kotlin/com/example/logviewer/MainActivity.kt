package com.example.logviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

private data class RunInfo(
    val id: Long,
    val title: String,
    val workflow: String,
    val status: String,
    val conclusion: String,
    val createdAt: String
)

private fun fetch(url: String): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Accept", "text/plain")
        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
            throw IOException("HTTP ${conn.responseCode}: $err")
        }
        return conn.inputStream.readBytes().toString(Charsets.UTF_8)
    } finally {
        conn.disconnect()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen() {
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf("http://localhost:8787") }
    var runs by remember { mutableStateOf<List<RunInfo>>(emptyList()) }
    var logs by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var selectedRun by remember { mutableStateOf<RunInfo?>(null) }
    var failedOnly by remember { mutableStateOf(false) }
    var panelVisible by remember { mutableStateOf(false) }

    fun loadRuns() {
        scope.launch {
            loading = true
            error = null
            try {
                val body = withContext(Dispatchers.IO) { fetch("$baseUrl/runs") }
                val arr = JSONObject(body).getJSONArray("runs")
                runs = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    RunInfo(
                        id = o.getLong("databaseId"),
                        title = o.getString("displayTitle"),
                        workflow = o.getString("workflowName"),
                        status = o.getString("status"),
                        conclusion = if (o.isNull("conclusion")) "" else o.getString("conclusion"),
                        createdAt = o.getString("createdAt")
                    )
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun loadLogs(run: RunInfo, failed: Boolean = failedOnly) {
        scope.launch {
            loading = true
            error = null
            try {
                val suffix = if (failed) "&failed=1" else ""
                logs = withContext(Dispatchers.IO) { fetch("$baseUrl/log?run=${run.id}$suffix") }
                selectedRun = run
                panelVisible = true
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadRuns()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val parentW = constraints.maxWidth
        val parentH = constraints.maxHeight

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Text("GH Workflow Logs", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.size(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Server") },
                    singleLine = true
                )
                Spacer(Modifier.size(8.dp))
                FilledTonalButton(onClick = { baseUrl = "http://10.0.2.2:8787" }) {
                    Text("10.0.2.2")
                }
                Spacer(Modifier.size(8.dp))
                FilledTonalButton(onClick = { baseUrl = "http://localhost:8787" }) {
                    Text("localhost")
                }
            }
            Spacer(Modifier.size(12.dp))

            RunPicker(
                runs = runs,
                selected = selectedRun,
                onRefreshRuns = { loadRuns() },
                onPickRun = { run -> loadLogs(run) }
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "Logs open in a draggable/resizable floating panel (drag header, resize bottom-right).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (loading) {
            CircularProgressIndicator(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
            )
        }

        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp)
            )
        }

        if (panelVisible && selectedRun != null) {
            FloatingLogPanel(
                logs = logs,
                runLabel = "${selectedRun!!.workflow} #${selectedRun!!.id}",
                failedOnly = failedOnly,
                onToggleFailed = { failed ->
                    failedOnly = failed
                    loadLogs(selectedRun!!, failed)
                },
                onClose = { panelVisible = false },
                onRefresh = { loadLogs(selectedRun!!) },
                parentW = parentW,
                parentH = parentH
            )
        }
    }
}

@Composable
private fun RunPicker(
    runs: List<RunInfo>,
    selected: RunInfo?,
    onRefreshRuns: () -> Unit,
    onPickRun: (RunInfo) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected?.let { "#${it.id} ${it.workflow}" } ?: "Select a run")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Refresh run list") },
                onClick = {
                    expanded = false
                    onRefreshRuns()
                }
            )
            runs.forEach { run ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "#${run.id}  ${run.workflow}\n" +
                                "${run.title}\n" +
                                "${run.status} ${run.conclusion}  ${run.createdAt}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    onClick = {
                        expanded = false
                        onPickRun(run)
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingLogPanel(
    logs: String,
    runLabel: String,
    failedOnly: Boolean,
    onToggleFailed: (Boolean) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    parentW: Int,
    parentH: Int
) {
    val density = LocalDensity.current
    val minW = with(density) { 220.dp.toPx() }
    val minH = with(density) { 140.dp.toPx() }
    val headerH = with(density) { 48.dp.toPx() }
    val margin = with(density) { 12.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var panelW by remember { mutableFloatStateOf(0f) }
    var panelH by remember { mutableFloatStateOf(0f) }
    var minimized by remember { mutableStateOf(false) }

    LaunchedEffect(parentW, parentH) {
        if (panelW == 0f && panelH == 0f) {
            panelW = (parentW * 0.55f).coerceIn(minW, parentW.toFloat())
            panelH = (parentH * 0.5f).coerceIn(minH, parentH.toFloat())
            offsetX = (parentW - panelW - margin).coerceAtLeast(0f)
            offsetY = margin
        }
    }

    val displayW = with(density) { panelW.toDp() }
    val displayH = with(density) { (if (minimized) headerH else panelH).toDp() }

    Surface(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(displayW, displayH),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offsetX = (offsetX + drag.x)
                                .coerceIn(0f, (parentW - panelW).coerceAtLeast(0f))
                            val limit = parentH - (if (minimized) headerH else panelH)
                            offsetY = (offsetY + drag.y).coerceIn(0f, limit.coerceAtLeast(0f))
                        }
                    }
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    runLabel,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = failedOnly,
                    onCheckedChange = onToggleFailed
                )
                Text(
                    if (failedOnly) "failed" else "all",
                    style = MaterialTheme.typography.labelSmall
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh logs")
                }
                IconButton(onClick = { minimized = !minimized }) {
                    Icon(
                        if (minimized) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (minimized) "Expand" else "Minimize"
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            if (!minimized) {
                LogBody(
                    logs = logs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(18.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                panelW = (panelW + drag.x).coerceIn(minW, parentW.toFloat())
                                panelH = (panelH + drag.y).coerceIn(minH, parentH.toFloat())
                                offsetX = offsetX.coerceIn(0f, (parentW - panelW).coerceAtLeast(0f))
                                offsetY = offsetY.coerceIn(0f, (parentH - panelH).coerceAtLeast(0f))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("╱╱", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun LogBody(logs: String, modifier: Modifier = Modifier) {
    val lines = remember(logs) { logs.lineSequence().toList() }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    SelectionContainer(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            items(lines) { line ->
                Text(
                    line,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
