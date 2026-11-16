package com.wowstudio.claudephotosorter

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PhotoSorterScreen { uris, onProgress, onDone ->
                    lifecycleScope.launch {
                        val analyzer = PhotoAnalyzer(this@MainActivity)
                        try {
                            val results = mutableListOf<PhotoItem>()
                            uris.forEachIndexed { index, uri ->
                                val item = withContext(Dispatchers.IO) {
                                    analyzer.analyze(uri, queryName(uri))
                                }
                                results += item
                                onProgress(index + 1, uris.size)
                            }
                            val seen = mutableSetOf<String>()
                            onDone(results.map { item ->
                                val isDuplicate = item.sha256?.let { !seen.add(it) } ?: false
                                item.copy(duplicate = isDuplicate)
                            })
                        } finally {
                            analyzer.close()
                        }
                    }
                }
            }
        }
    }

    private fun queryName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else "Photo"
        } ?: "Photo"
}

@Composable
private fun PhotoSorterScreen(
    onAnalyze: (List<Uri>, (Int, Int) -> Unit, (List<PhotoItem>) -> Unit) -> Unit
) {
    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var results by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var running by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selected = uris
        results = emptyList()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Claude Photo Sorter", style = MaterialTheme.typography.headlineMedium)
        Text("Private, on-device photo analysis")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose photos", style = MaterialTheme.typography.titleMedium)
                Text("Select images from your phone. Original files are not changed.")
                Button(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !running) {
                    Text("Select photos")
                }
                Text("${selected.size} photo(s) selected")
            }
        }
        Button(
            onClick = {
                running = true
                progress = 0 to selected.size
                onAnalyze(selected, { done, total -> progress = done to total }) {
                    results = it
                    running = false
                }
            },
            enabled = selected.isNotEmpty() && !running,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Analyze photos") }

        if (running) {
            CircularProgressIndicator()
            progress?.let { (done, total) -> Text("Analyzing $done of $total") }
        }

        if (results.isNotEmpty()) {
            val counts = results.groupingBy { it.category }.eachCount().toSortedMap()
            Text("Results", style = MaterialTheme.typography.titleLarge)
            Text("${results.size} analyzed • ${results.count { it.duplicate }} exact duplicate(s)")
            counts.forEach { (category, count) -> Text("$category: $count") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(item.error ?: item.category)
                            if (item.duplicate) Text("Exact duplicate in this selection")
                            if (item.labels.isNotEmpty()) Text("Labels: ${item.labels.joinToString()}")
                        }
                    }
                }
            }
        } else if (!running) {
            Text("Choose photos, then analyze to see category suggestions and duplicate matches.")
        }
    }
}
