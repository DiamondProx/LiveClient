package com.jiang.liveclient.ui.webrtc

import android.Manifest
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiang.liveclient.ui.theme.Primary
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebRTCScreen(
    viewModel: WebRTCViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val sessionId by viewModel.sessionId.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var selectedFileName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { message: String ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            showError = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LiveTalking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val isConnected = connectionState == "connected"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isConnected) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isConnected) Color(0xFF10B981) else Color(0xFFEF4444),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "已连接" else "未连接",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConnected) Color(0xFF065F46) else Color(0xFF991B1B)
                            )
                        }
                    }
                    Text(
                        text = "SID: ${sessionId ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Primary
                )
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ConnectionCardWebRTC(
                    avatarId = viewModel.avatarId.value,
                    onAvatarIdChange = { newValue -> viewModel.avatarId.value = newValue },
                    refAudio = viewModel.refAudio.value,
                    onRefAudioChange = { newValue -> viewModel.refAudio.value = newValue },
                    refText = viewModel.refText.value,
                    onRefTextChange = { newValue -> viewModel.refText.value = newValue },
                    connectionState = connectionState,
                    errorMessage = errorMessage,
                    showError = showError,
                    onDismissError = { showError = false },
                    onStartClick = { viewModel.startConnection() },
                    onStopClick = { viewModel.stopConnection() }
                )

                VideoCardWebRTC(
                    remoteVideoTrack = remoteVideoTrack,
                    eglBaseContext = viewModel.eglBaseContext
                )

                QuickLinksCard()
            }

            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextDriverCardWebRTC(
                    textInput = viewModel.textInput.value,
                    onTextChange = { newValue -> viewModel.textInput.value = newValue },
                    textType = viewModel.textType.value,
                    onTypeChange = { newValue -> viewModel.textType.value = newValue },
                    interruptEnabled = viewModel.interruptEnabled.value,
                    onInterruptChange = { newValue -> viewModel.interruptEnabled.value = newValue },
                    onSendClick = { viewModel.sendText() },
                    onInterruptClick = { viewModel.interrupt() },
                    enabled = connectionState == "connected"
                )

                AudioDriverCardWebRTC(
                    selectedFileName = selectedFileName,
                    onSelectClick = { audioPickerLauncher.launch("audio/*") },
                    onUploadClick = {
                        viewModel.sendAudio()
                        selectedFileName = ""
                    },
                    enabled = connectionState == "connected"
                )

                RecordingCardWebRTC(
                    isRecording = viewModel.isRecording.value,
                    status = viewModel.recordingStatus.value,
                    onToggleClick = { viewModel.toggleRecording() },
                    onDownloadClick = { viewModel.downloadRecording() },
                    enabled = connectionState == "connected"
                )

                AudiotypeCardWebRTC(
                    audioType = viewModel.audioType.value,
                    onAudioTypeChange = { newValue -> viewModel.audioType.value = newValue },
                    onSetClick = { viewModel.setAudiotype() },
                    enabled = connectionState == "connected"
                )
            }
        }
    }
}

@Composable
fun ConnectionCardWebRTC(
    avatarId: String,
    onAvatarIdChange: (String) -> Unit,
    refAudio: String,
    onRefAudioChange: (String) -> Unit,
    refText: String,
    onRefTextChange: (String) -> Unit,
    connectionState: String,
    errorMessage: String?,
    showError: Boolean,
    onDismissError: () -> Unit,
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
                text = "WebRTC 连接",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = avatarId,
                onValueChange = onAvatarIdChange,
                label = { Text("Avatar ID") },
                placeholder = { Text("wav2lip256_avatar1") },
                modifier = Modifier.fillMaxWidth(),
                enabled = connectionState != "connecting",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = refAudio,
                onValueChange = onRefAudioChange,
                label = { Text("参考音频") },
                placeholder = { Text("zh-CN-YunxiaNeural") },
                modifier = Modifier.fillMaxWidth(),
                enabled = connectionState != "connecting",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = refText,
                onValueChange = onRefTextChange,
                label = { Text("参考音频文本") },
                placeholder = { Text("参考文本（可选）") },
                modifier = Modifier.fillMaxWidth(),
                enabled = connectionState != "connecting",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    enabled = connectionState == "new" || connectionState == "closed" || connectionState == "failed",
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("开始连接")
                }
                OutlinedButton(
                    onClick = onStopClick,
                    enabled = connectionState == "connected" || connectionState == "connecting",
                    modifier = Modifier.weight(1f)
                ) {
                    Text("断开连接")
                }
            }

            if (showError && errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("X", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoCardWebRTC(
    remoteVideoTrack: VideoTrack?,
    eglBaseContext: org.webrtc.EglBase.Context?
) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    LaunchedEffect(remoteVideoTrack) {
        renderer?.let { r ->
            remoteVideoTrack?.addSink(r)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            renderer?.release()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(eglBaseContext, null)
                            setMirror(false)
                            setEnableHardwareScaler(true)
                        }.also { renderer = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (remoteVideoTrack == null) {
                    Text(
                        text = "等待视频...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun QuickLinksCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { }) { Text("Avatar", color = Primary) }
            TextButton(onClick = { }) { Text("管理后台", color = Primary) }
            TextButton(onClick = { }) { Text("TTS", color = Primary) }
        }
    }
}

@Composable
fun TextDriverCardWebRTC(
    textInput: String,
    onTextChange: (String) -> Unit,
    textType: String,
    onTypeChange: (String) -> Unit,
    interruptEnabled: Boolean,
    onInterruptChange: (Boolean) -> Unit,
    onSendClick: () -> Unit,
    onInterruptClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "文本驱动 (POST /human)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = textInput,
                onValueChange = onTextChange,
                label = { Text("输入文本") },
                placeholder = { Text("输入要播报的文本...") },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                minLines = 2,
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("类型:", style = MaterialTheme.typography.bodySmall)
                FilterChip(
                    selected = textType == "echo",
                    onClick = { onTypeChange("echo") },
                    label = { Text("Echo") },
                    enabled = enabled
                )
                FilterChip(
                    selected = textType == "chat",
                    onClick = { onTypeChange("chat") },
                    label = { Text("Chat") },
                    enabled = enabled
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("打断", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = interruptEnabled,
                        onCheckedChange = onInterruptChange,
                        enabled = enabled,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSendClick,
                    enabled = enabled && textInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("发送")
                }
                OutlinedButton(
                    onClick = onInterruptClick,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("打断")
                }
            }
        }
    }
}

@Composable
fun AudioDriverCardWebRTC(
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
                text = "音频驱动 (POST /humanaudio)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSelectClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (selectedFileName.isBlank()) "选择音频文件" else selectedFileName,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onUploadClick,
                enabled = enabled && selectedFileName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("上传并播放")
            }
        }
    }
}

@Composable
fun RecordingCardWebRTC(
    isRecording: Boolean,
    status: String,
    onToggleClick: () -> Unit,
    onDownloadClick: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "录制控制 (POST /record)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleClick,
                    enabled = enabled,
                    colors = if (isRecording) {
                        ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isRecording) "停止录制" else "开始录制")
                }

                OutlinedButton(
                    onClick = onDownloadClick,
                    enabled = enabled && !isRecording && status.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("下载录像")
                }
            }

            if (status.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AudiotypeCardWebRTC(
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
                text = "动作编排 (POST /set_audiotype)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = audioType.toString(),
                    onValueChange = { it.toIntOrNull()?.let(onAudioTypeChange) },
                    label = { Text("Audiotype") },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    singleLine = true
                )
                Button(
                    onClick = onSetClick,
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("切换")
                }
            }
        }
    }
}
