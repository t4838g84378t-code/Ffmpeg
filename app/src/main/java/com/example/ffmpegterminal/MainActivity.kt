package com.example.ffmpegterminal

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00FF00),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FFmpegTerminalScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegTerminalScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Dedicated subfolders inside external storage visible to file browsers
    val appBaseDir = context.getExternalFilesDir(null) ?: context.filesDir
    val inputDir = remember { File(appBaseDir, "inputs").apply { if (!exists()) mkdirs() } }
    val outputDir = remember { File(appBaseDir, "outputs").apply { if (!exists()) mkdirs() } }

    var commandInput by remember { mutableStateOf("-version") }
    var consoleLogs by remember { mutableStateOf("--- Terminal Started. Try typing a command! ---\n") }
    var isRunning by remember { mutableStateOf(false) }
    var outputRefreshTrigger by remember { mutableStateOf(0) }

    // Auto scroll to latest output logs
    LaunchedEffect(consoleLogs) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // File Picker launcher to copy any picked media into the inputs directory
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { pickedUri ->
            scope.launch(Dispatchers.IO) {
                try {
                    val fileName = getFileName(context, pickedUri) ?: "input_media.mp4"
                    val destFile = File(inputDir, fileName)
                    
                    context.contentResolver.openInputStream(pickedUri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        consoleLogs += ">>> File successfully imported to inputs directory:\n${destFile.absolutePath}\n"
                        // Paste input path automatically at the start of command field for fast editing
                        commandInput = "-i \"${destFile.absolutePath}\" -c:v mpeg4 -y \"${outputDir.absolutePath}/output_${System.currentTimeMillis()}.mp4\""
                        Toast.makeText(context, "File imported successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        consoleLogs += ">>> Error importing file: ${e.message}\n"
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "FFmpeg Terminal Console",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00FF00),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Workspace Directories display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text("Workspace Directories:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text("Inputs: ${inputDir.absolutePath}", fontSize = 9.sp, color = Color.LightGray)
            Text("Outputs: ${outputDir.absolutePath}", fontSize = 9.sp, color = Color.LightGray)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Actions and Imports
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0055AA)),
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Import Media File", fontSize = 11.sp, color = Color.White)
            }

            Button(
                onClick = { 
                    commandInput = "-f lavfi -i testsrc=duration=5:size=640x360:rate=30 -c:v mpeg4 -y \"${outputDir.absolutePath}/test_video.mp4\"" 
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Gen Test Video", fontSize = 11.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dynamic output listing section
        OutputsList(
            outputDir = outputDir,
            refreshTrigger = outputRefreshTrigger,
            onFileSelected = { selectedFile ->
                commandInput = "-i \"${selectedFile.absolutePath}\" "
            },
            onExportFinished = { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            },
            onExportFailed = {
                Toast.makeText(context, "Export failed.", Toast.LENGTH_SHORT).show()
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Retro Console output display
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black, shape = RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = consoleLogs,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF00FF00)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Visual preview of the command to execute
        Text(
            text = "Command queue: ffmpeg ${cleanCommandInput(commandInput)}",
            color = Color.Yellow,
            fontSize = 11.sp,
            maxLines = 2,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )

        // User Input Field
        OutlinedTextField(
            value = commandInput,
            onValueChange = { commandInput = it },
            label = { Text("Command line options") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFF00FF00),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00FF00)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color.White
            ),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Execution Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { consoleLogs = "--- Log cleared ---\n" },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Clear Logs")
            }

            Button(
                onClick = {
                    if (isRunning) {
                        FFmpegKit.cancel()
                        consoleLogs += "\n>>> ABORTED BY USER <<<\n"
                        isRunning = false
                    } else {
                        isRunning = true
                        
                        // Capture the command state right now to prevent thread sync capturing bugs
                        val activeCommand = cleanCommandInput(commandInput)
                        
                        consoleLogs += "\n>>> Executing: ffmpeg $activeCommand\n"
                        
                        // Capture standard out
                        FFmpegKitConfig.enableLogCallback { log ->
                            consoleLogs += log.message
                        }

                        scope.launch(Dispatchers.IO) {
                            try {
                                val session = FFmpegKit.execute(activeCommand)
                                val returnCode = session.returnCode
                                val state = session.state

                                withContext(Dispatchers.Main) {
                                    consoleLogs += "\n>>> Completed with Return Code: $returnCode, State: $state\n"
                                    isRunning = false
                                    outputRefreshTrigger++ // Refreshes outputs folder UI list
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    consoleLogs += "\n>>> Runtime Execution Error: ${e.message}\n"
                                    isRunning = false
                                }
                            } finally {
                                FFmpegKitConfig.enableLogCallback(null)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color.Red else Color(0xFF007700)
                ),
                modifier = Modifier.weight(1.5f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(if (isRunning) "Cancel" else "Run Command")
            }
        }
    }
}

@Composable
fun OutputsList(
    outputDir: File,
    refreshTrigger: Int,
    onFileSelected: (File) -> Unit,
    onExportFinished: (String) -> Unit,
    onExportFailed: () -> Unit
) {
    val context = LocalContext.current
    var fileList by remember { mutableStateOf(emptyList<File>()) }
    var forceRefresh by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTrigger, forceRefresh) {
        fileList = outputDir.listFiles()?.toList() ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Local Output Files:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Refresh",
                fontSize = 11.sp,
                color = Color(0xFF00FF00),
                modifier = Modifier
                    .clickable { forceRefresh++ }
                    .padding(horizontal = 4.dp)
            )
        }

        if (fileList.isEmpty()) {
            Text(
                text = "No outputs produced yet.",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 110.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                fileList.forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, fontSize = 11.sp, color = Color.White, maxLines = 1)
                            Text("${file.length() / 1024} KB", fontSize = 9.sp, color = Color.Gray)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Select",
                                fontSize = 11.sp,
                                color = Color(0xFF00FF00),
                                modifier = Modifier.clickable { onFileSelected(file) }
                            )
                            Text(
                                text = "Export",
                                fontSize = 11.sp,
                                color = Color.Cyan,
                                modifier = Modifier.clickable {
                                    exportToDownloadsCollection(context, file) { path ->
                                        if (path != null) {
                                            onExportFinished(path)
                                        } else {
                                            onExportFailed()
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
}

// Strips absolute "ffmpeg" program call if the user copy-pastes CLI style commands
fun cleanCommandInput(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("ffmpeg ")) {
        trimmed.substring(7).trim()
    } else {
        trimmed
    }
}

// Safe export of generated application files into the shared Public downloads folder
fun exportToDownloadsCollection(context: Context, file: File, callback: (String?) -> Unit) {
    val coroutineScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    coroutineScope.launch {
        try {
            val result: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, getMimeType(file))
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FFmpegTerminal")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        file.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    "Exported to Downloads/FFmpegTerminal/${file.name}"
                } else {
                    null
                }
            } else {
                val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FFmpegTerminal")
                if (!publicDir.exists()) publicDir.mkdirs()
                val destFile = File(publicDir, file.name)
                file.copyTo(destFile, overwrite = true)
                "Exported to ${destFile.absolutePath}"
            }
            withContext(Dispatchers.Main) {
                callback(result)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                callback(null)
            }
        }
    }
}

fun getMimeType(file: File): String {
    return when (file.extension.lowercase()) {
        "mp4" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "*/*"
    }
}

fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = it.getString(index)
                }
            }
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != null && cut != -1) {
            result = result.substring(cut + 1)
        }
    }
    return result
}
