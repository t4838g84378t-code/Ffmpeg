package com.example.ffmpegterminal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                    FFmpegTerminalScreen(
                        cacheDir = cacheDir
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FFmpegTerminalScreen(cacheDir: File) {
    var commandInput by remember { mutableStateOf("-version") }
    var consoleLogs by remember { mutableStateOf("--- Terminal Log Initialized ---\n") }
    var isRunning by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val placeholderOutputFile = remember { File(cacheDir, "test_output.mp4") }

    // Autoscroll to bottom as lines accumulate
    LaunchedEffect(consoleLogs) {
        scrollState.animateScrollTo(scrollState.maxValue)
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
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Quick Presets
        Text(
            text = "Quick Presets:",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { commandInput = "-version" },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1f)
            ) {
                Text("-version", fontSize = 11.sp, color = Color.White)
            }
            Button(
                onClick = { commandInput = "-codecs" },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1f)
            ) {
                Text("-codecs", fontSize = 11.sp, color = Color.White)
            }
            Button(
                onClick = { 
                    commandInput = "-f lavfi -i testsrc=duration=5:size=640x360:rate=30 -c:v mpeg4 -y ${placeholderOutputFile.absolutePath}" 
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1.5f)
            ) {
                Text("Gen 5s Video", fontSize = 11.sp, color = Color.White)
            }
        }

        // Retro Console Display
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

        Spacer(modifier = Modifier.height(12.dp))

        // User Input Field
        OutlinedTextField(
            value = commandInput,
            onValueChange = { commandInput = it },
            label = { Text("Command args") },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color(0xFF00FF00),
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color(0xFF00FF00)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color.White
            ),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { consoleLogs = "--- Log cleared ---\n" },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear")
            }

            Button(
                onClick = {
                    if (isRunning) {
                        FFmpegKit.cancel()
                        consoleLogs += "\n>>> ABORTED BY USER <<<\n"
                        isRunning = false
                    } else {
                        isRunning = true
                        consoleLogs += "\n>>> Executing: ffmpeg $commandInput\n"
                        
                        // Stream live logging output onto the terminal display
                        FFmpegKitConfig.enableLogCallback { log ->
                            consoleLogs += log.message
                        }

                        scope.launch(Dispatchers.IO) {
                            try {
                                val session = FFmpegKit.execute(commandInput)
                                val returnCode = session.returnCode
                                val state = session.state

                                withContext(Dispatchers.Main) {
                                    consoleLogs += "\n>>> Finished: Return Code: $returnCode, State: $state\n"
                                    if (commandInput.contains("test_output.mp4") && placeholderOutputFile.exists()) {
                                        consoleLogs += ">>> File Written successfully: ${placeholderOutputFile.absolutePath} (${placeholderOutputFile.length()} bytes)\n"
                                    }
                                    isRunning = false
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    consoleLogs += "\n>>> Runtime Error: ${e.message}\n"
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
                modifier = Modifier.weight(1.5f)
            ) {
                Text(if (isRunning) "Cancel" else "Run Command")
            }
        }
    }
}
