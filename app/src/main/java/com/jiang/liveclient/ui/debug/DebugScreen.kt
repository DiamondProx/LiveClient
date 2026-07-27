package com.jiang.liveclient.ui.debug

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    viewModel: DebugViewModel = viewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingStatus by viewModel.recordingStatus.collectAsState()
    val avatarId by viewModel.avatarId.collectAsState()
    val textInput by viewModel.textInput.collectAsState()
    val textType by viewModel.textType.collectAsState()
    val audioType by viewModel.audioType.collectAsState()

    var selectedFileName by remember { mutableStateOf("") }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileName = it.lastPathSegment ?: "audio.wav"
            viewModel.setAudioUri(it, selectedFileName)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.INTERNET,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LiveTalking Debug") },
                actions = {
                    Text(
                        text = connectionState,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Video Section
            item {
                VideoSectionCard(remoteVideoTrack = remoteVideoTrack)
            }

            // Connection Card
            item {
                ConnectionCard(
                    connectionState = connectionState,
                    avatarId = avatarId,
                    onAvatarIdChange = { viewModel.avatarId.value = it },
                    onStartClick = { viewModel.startConnection() },
                    onStopClick = { viewModel.stopConnection() }
                )
            }

            // Text Driver Card
            item {
                TextDriverCard(
                    textInput = textInput,
                    onTextChange = { viewModel.textInput.value = it },
                    textType = textType,
                    onTypeChange = { viewModel.textType.value = it },
                    onSendClick = { viewModel.sendText() },
                    enabled = connectionState == "connected"
                )
            }

            // Audio Driver Card
            item {
                AudioDriverCard(
                    selectedFileName = selectedFileName,
                    onSelectClick = { audioPickerLauncher.launch("audio/*") },
                    onUploadClick = {
                        viewModel.sendAudio()
                        selectedFileName = ""
                    },
                    enabled = connectionState == "connected"
                )
            }

            // Recording Card
            item {
                RecordingCard(
                    isRecording = isRecording,
                    status = recordingStatus,
                    onToggleClick = { viewModel.toggleRecording() },
                    enabled = connectionState == "connected"
                )
            }

            // Audiotype Card
            item {
                AudiotypeCard(
                    audioType = audioType,
                    onAudioTypeChange = { viewModel.audioType.value = it },
                    onSetClick = { viewModel.setAudiotype() },
                    enabled = connectionState == "connected"
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun VideoSectionCard(remoteVideoTrack: VideoTrack?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Video",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                if (remoteVideoTrack != null) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceViewRenderer(ctx).apply {
                                setMirror(false)
                                setEnableHardwareScaler(true)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { renderer ->
                            remoteVideoTrack.addSink(renderer)
                        }
                    )
                } else {
                    Text(
                        text = "No video track",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionCard(
    connectionState: String,
    avatarId: String,
    onAvatarIdChange: (String) -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "WebRTC Connection",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = avatarId,
                onValueChange = onAvatarIdChange,
                label = { Text("Avatar ID") },
                modifier = Modifier.fillMaxWidth(),
                enabled = connectionState != "connecting"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    enabled = connectionState == "new" || connectionState == "closed" || connectionState == "failed",
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start")
                }
                OutlinedButton(
                    onClick = onStopClick,
                    enabled = connectionState == "connected" || connectionState == "connecting",
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }
        }
    }
}

@Composable
fun TextDriverCard(
    textInput: String,
    onTextChange: (String) -> Unit,
    textType: String,
    onTypeChange: (String) -> Unit,
    onSendClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Text Driver (POST /human)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChange,
                label = { Text("Text") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                minLines = 2,
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Type:", modifier = Modifier.padding(end = 8.dp))
                FilterChip(
                    selected = textType == "echo",
                    onClick = { onTypeChange("echo") },
                    label = { Text("Echo") },
                    enabled = enabled
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = textType == "chat",
                    onClick = { onTypeChange("chat") },
                    label = { Text("Chat") },
                    enabled = enabled
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSendClick,
                enabled = enabled && textInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun AudioDriverCard(
    selectedFileName: String,
    onSelectClick: () -> Unit,
    onUploadClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Audio Driver (POST /humanaudio)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onSelectClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedFileName.isBlank()) "Select Audio File" else selectedFileName)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onUploadClick,
                enabled = enabled && selectedFileName.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Upload")
            }
        }
    }
}

@Composable
fun RecordingCard(
    isRecording: Boolean,
    status: String,
    onToggleClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Recording (POST /record)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (status.isNotBlank()) status else (if (isRecording) "Recording" else "Not recording"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = onToggleClick,
                    enabled = enabled,
                    colors = if (isRecording) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (isRecording) "Stop" else "Start")
                }
            }
        }
    }
}

@Composable
fun AudiotypeCard(
    audioType: Int,
    onAudioTypeChange: (Int) -> Unit,
    onSetClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Action Orchestration (POST /set_audiotype)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = audioType.toString(),
                    onValueChange = { it.toIntOrNull()?.let(onAudioTypeChange) },
                    label = { Text("Audiotype") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    enabled = enabled
                )
                Button(
                    onClick = onSetClick,
                    enabled = enabled
                ) {
                    Text("Set")
                }
            }
        }
    }
}
