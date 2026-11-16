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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class SortSummary(val copied: Int, val failed: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PhotoSorterScreen(
                    onAnalyze = { uris, onProgress, onDone ->
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
                    },
                    onSort = { items, treeUri, onProgress, onDone ->
                        lifecycleScope.launch {
                            var copied = 0
                            var failed = 0
                            items.forEachIndexed { index, item ->
                                val ok = withContext(Dispatchers.IO) {
                                    copyIntoCategory(Uri.parse(item.uri), item.displayName, item.category, treeUri)
                                }
                                if (ok) copied++ else failed++
                                onProgress(index + 1, items.size)
                            }
                            onDone(SortSummary(copied, failed))
                        }
                    }
                )
            }
        }
    }

    private fun queryName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else "Photo"
        } ?: "Photo"

    private fun copyIntoCategory(source: Uri, displayName: String, category: String, treeUri: Uri): Boolean {
        return try {
            val root = DocumentFile.fromTreeUri(this, treeUri) ?: return false
            val folder = root.findFile(category) ?: root.createDirectory(category) ?: return false
            val mime = contentResolver.getType(source) ?: "application/octet-stream"
            val targetName = uniqueName(folder, displayName)
            val target = folder.createFile(mime, targetName) ?: return false
            val input = contentResolver.openInputStream(source) ?: run {
                target.delete()
                return false
            }
            input.use { inStream ->
                val output = contentResolver.openOutputStream(target.uri, "w") ?: throw IOException("Cannot write destination file")
                output.use { outStream -> inStream.copyTo(outStream) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun uniqueName(folder: DocumentFile, original: String): String {
        val dot = original.lastIndexOf('.')
        val base = if (dot > 0) original.substring(0, dot) else original
        val extension = if (dot > 0) original.substring(dot) else ""
        var candidate = original
        var index = 1
        while (folder.findFile(candidate) != null) {
            candidate = "$base ($index)$extension"
            index++
        }
        return candidate
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSorterScreen(
    onAnalyze: (List<Uri>, (Int, Int) -> Unit, (List<PhotoItem>) -> Unit) -> Unit,
    onSort: (List<PhotoItem>, Uri, (Int, Int) -> Unit, (SortSummary) -> Unit) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selected by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var results by remember { mutableStateOf<List<PhotoItem>>(emptyList()) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var running by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<Uri?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var sortProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var sorting by remember { mutableStateOf(false) }
    var sortSummary by remember { mutableStateOf<SortSummary?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        selected = uris
        results = emptyList()
        selectedCategory = "All"
        sortSummary = null
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { }
            destination = uri
            sortSummary = null
        }
    }

    val counts = results.groupingBy { it.category }.eachCount().toSortedMap()
    val categories = listOf("All") + counts.keys
    val visibleResults = if (selectedCategory == "All") results else results.filter { it.category == selectedCategory }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Claude Photo Sorter", style = MaterialTheme.typography.headlineMedium)
        Text("Private, on-device photo analysis")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose photos", style = MaterialTheme.typography.titleMedium)
                Text("Select images from your phone. Original files are never changed.")
                Button(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !running && !sorting) {
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
            enabled = selected.isNotEmpty() && !running && !sorting,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Analyze photos") }

        if (running) {
            LinearProgressIndicator(progress = { (progress?.first ?: 0).toFloat() / (progress?.second?.coerceAtLeast(1) ?: 1) }, modifier = Modifier.fillMaxWidth())
            progress?.let { (done, total) -> Text("Analyzing $done of $total") }
        }

        if (results.isNotEmpty()) {
            Text("Results", style = MaterialTheme.typography.titleLarge)
            Text("${results.size} analyzed • ${results.count { it.duplicate }} exact duplicate(s)")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(if (category == "All") "All (${results.size})" else "$category (${counts[category] ?: 0})") }
                    )
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sort copies into folders", style = MaterialTheme.typography.titleMedium)
                    Text(destination?.let { "Destination folder selected" } ?: "Choose a destination folder on your phone.")
                    OutlinedButton(onClick = { folderPicker.launch(null) }, enabled = !sorting) {
                        Text("Choose destination folder")
                    }
                    Button(
                        onClick = {
                            destination?.let { tree ->
                                sorting = true
                                sortProgress = 0 to results.size
                                onSort(results, tree, { done, total -> sortProgress = done to total }) {
                                    sortSummary = it
                                    sorting = false
                                }
                            }
                        },
                        enabled = destination != null && results.isNotEmpty() && !sorting,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sort all analyzed photos") }
                    if (sorting) {
                        LinearProgressIndicator(progress = { (sortProgress?.first ?: 0).toFloat() / (sortProgress?.second?.coerceAtLeast(1) ?: 1) }, modifier = Modifier.fillMaxWidth())
                        sortProgress?.let { (done, total) -> Text("Copying $done of $total") }
                    }
                    sortSummary?.let {
                        Text("Sorting finished: ${it.copied} copied, ${it.failed} failed. Originals were not changed.")
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleResults) { item ->
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

