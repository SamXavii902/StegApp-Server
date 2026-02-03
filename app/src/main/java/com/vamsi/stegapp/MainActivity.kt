package com.vamsi.stegapp

import androidx.compose.runtime.mutableIntStateOf

import kotlinx.coroutines.launch
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup

import androidx.compose.ui.window.Popup

import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.composed
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.max
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import coil.compose.AsyncImage
import com.vamsi.stegapp.model.Message
import com.vamsi.stegapp.ui.components.*
import com.vamsi.stegapp.ui.screens.HomeScreen
import com.vamsi.stegapp.ui.screens.LoginScreen
import com.vamsi.stegapp.utils.UserPrefs
import com.vamsi.stegapp.ui.theme.StegAppTheme
import com.vamsi.stegapp.viewmodel.ChatViewModel
import com.vamsi.stegapp.viewmodel.ChatViewModelFactory
import android.content.Intent
import com.vamsi.stegapp.service.SocketService
import androidx.core.content.FileProvider
import java.io.File
import com.vamsi.stegapp.utils.ImageUtils
import com.vamsi.stegapp.utils.TimeUtils
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.foundation.interaction.MutableInteractionSource

enum class ChatMode { HIDE, EXTRACT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start Background Service only if logged in
        if (UserPrefs.isLoggedIn(this)) {
            val serviceIntent = Intent(this, SocketService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            StegAppTheme {
                val isDark = isSystemInDarkTheme()
                val backgroundColor = if (isDark) Color(0xFF000000) else Color(0xFFF2F4F6)
                Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SocketService.isForground = true
        
        // Emit Online Status
        UserPrefs.getUsername(this)?.let { username ->
            com.vamsi.stegapp.network.SocketClient.emitUserStatus(username, "online")
        }
    }

    override fun onPause() {
        super.onPause()
        SocketService.isForground = false
        
        // Emit Offline Status
        UserPrefs.getUsername(this)?.let { username ->
            com.vamsi.stegapp.network.SocketClient.emitUserStatus(username, "offline")
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val startDest = if (UserPrefs.isLoggedIn(context)) "home" else "login"
    
    NavHost(navController = navController, startDestination = startDest) {
        composable("login") { LoginScreen(navController, isSystemInDarkTheme()) }
        composable("home") { HomeScreen(navController, isSystemInDarkTheme()) }
        composable(
            route = "chat/{chatName}?imageUri={imageUri}&messageId={messageId}",
            arguments = listOf(
                navArgument("chatName") { type = NavType.StringType },
                navArgument("imageUri") { type = NavType.StringType; nullable = true },
                navArgument("messageId") { type = NavType.StringType; nullable = true }
            ),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) { backStackEntry ->
            val chatName = backStackEntry.arguments?.getString("chatName") ?: "Chat"
            val imageUri = backStackEntry.arguments?.getString("imageUri")
            val messageId = backStackEntry.arguments?.getString("messageId")
            ChatScreen(navController = navController, chatName = chatName, initialImageUri = imageUri, highlightMessageId = messageId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    chatName: String,
    initialImageUri: String? = null,
    highlightMessageId: String? = null,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(LocalContext.current, chatName))
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val isConnected by viewModel.isConnected.collectAsState(initial = false)
    val isContactOnline by viewModel.contactOnlineStatus.collectAsState(initial = false)
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var textInput by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Message?>(null) } // Reply State
    
    // Zoomable Image State
    var expandedImageUri by remember { mutableStateOf<Uri?>(null) }

    // Initialize with passed image if available
    var selectedImageUri by remember { mutableStateOf<Uri?>(initialImageUri?.let { Uri.parse(it) }) }
    var currentMode by remember { mutableStateOf(ChatMode.HIDE) }
    
    // Stealth & Camouflage State
    var isStealthMode by remember { mutableStateOf(false) }
    var showCamouflageDialog by remember { mutableStateOf(false) }
    var camouflageInput by remember { mutableStateOf("") }

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = Color.Transparent.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
    }
    
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()
    LaunchedEffect(error) { if (error != null) toastMessage = error }
    LaunchedEffect(success) { if (success != null) toastMessage = success }
    
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
        if (uri != null) {
            if (currentMode == ChatMode.EXTRACT) viewModel.receiveMessage(uri) 
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            selectedImageUri = tempCameraUri
             if (currentMode == ChatMode.EXTRACT) viewModel.receiveMessage(tempCameraUri!!)
        }
    }

    // Camouflage Dialog
    if (showCamouflageDialog) {
        AlertDialog(
            onDismissRequest = { showCamouflageDialog = false },
            title = { Text("Camouflage Notification") },
            text = {
                Column {
                    Text("Enter the text to show in the notification:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = camouflageInput,
                        onValueChange = { camouflageInput = it },
                        label = { Text("Notification Text") },
                        placeholder = { Text("e.g. Check out this car") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showCamouflageDialog = false
                    if (selectedImageUri != null || textInput.isNotBlank()) {
                        viewModel.sendMessage(textInput, selectedImageUri, camouflageInput, replyToId = replyingTo?.id)
                        textInput = ""; selectedImageUri = null; camouflageInput = ""; replyingTo = null
                    }
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { showCamouflageDialog = false }) { Text("Cancel") }
            }
        )
    }

    ChatScreenContent(
        chatName = chatName, messages = messages, isDark = isDark, textInput = textInput,
        onTextInputChange = { textInput = it }, selectedImageUri = selectedImageUri,
        currentMode = currentMode,
        onModeChange = { currentMode = it }, onBack = { navController.popBackStack() },
        onPickImage = { pickImageLauncher.launch("image/*") },
        onCameraClick = {
            try {
                val file = java.io.File(context.getExternalFilesDir(null), "cam_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                toastMessage = "Camera Error: ${e.message}"
            }
        },
        onSend = {
            if (isStealthMode) {
                 if (selectedImageUri != null || textInput.isNotBlank()) {
                    showCamouflageDialog = true
                 } else {
                     toastMessage = "Enter text or select an image!"
                 }
            } else {
                if (selectedImageUri != null || textInput.isNotBlank()) {
                    viewModel.sendMessage(textInput, selectedImageUri, replyToId = replyingTo?.id)
                    textInput = ""; selectedImageUri = null
                    replyingTo = null
                }
            }
        },
        onError = { toastMessage = it },
        onDeleteMessage = { msgs, forAll -> viewModel.deleteMessages(msgs, forAll) },
        onDownloadMessage = { viewModel.downloadMedia(it) },
        onRemoveImage = { selectedImageUri = null },
        isStealthMode = isStealthMode,
        onStealthModeChange = { isStealthMode = it },
        isConnected = isConnected,
        isContactOnline = isContactOnline,
        onImageClick = { uri -> expandedImageUri = uri },
        replyingTo = replyingTo,
        onReplyDismiss = { replyingTo = null },
        onReplyTrigger = { replyingTo = it },
        onReveal = { viewModel.revealMessage(it) },
        uploadProgress = uploadProgress,
        highlightMessageId = highlightMessageId
    )


    // Full Screen Image Overlay
    FullScreenImageOverlay(
        imageUri = expandedImageUri,
        onDismiss = { expandedImageUri = null }
    )
    
    // Back Handler for Reply Mode
    androidx.activity.compose.BackHandler(enabled = replyingTo != null) {
        replyingTo = null
    }
    
    // Handle Back Press for Image Overlay
    androidx.activity.compose.BackHandler(enabled = expandedImageUri != null) {
        expandedImageUri = null
    }
}

@Composable
fun FullScreenImageOverlay(imageUri: Uri?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = imageUri != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (imageUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onDismiss() })
                    },
                contentAlignment = Alignment.Center
            ) {
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale > 1f) offset + pan else Offset.Zero
                            }
                        }
                ) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Full Screen Image",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )
                }
                
                // Close Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .padding(top = 24.dp) // Status bar padding
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreenContent(
    chatName: String, messages: List<Message>, isDark: Boolean, textInput: String,
    onTextInputChange: (String) -> Unit,
    selectedImageUri: Uri?, currentMode: ChatMode, onModeChange: (ChatMode) -> Unit,

    onBack: () -> Unit, onPickImage: () -> Unit, onSend: () -> Unit, onError: (String) -> Unit,
    onDeleteMessage: (List<Message>, Boolean) -> Unit, onDownloadMessage: (Message) -> Unit, onRemoveImage: () -> Unit,
    isStealthMode: Boolean, onStealthModeChange: (Boolean) -> Unit, onCameraClick: () -> Unit, isConnected: Boolean,
    isContactOnline: Boolean, onImageClick: (Uri) -> Unit,
    replyingTo: Message?, onReplyDismiss: () -> Unit, onReplyTrigger: (Message) -> Unit,
    onReveal: (Message) -> Unit, uploadProgress: Map<String, Float> = emptyMap(), highlightMessageId: String? = null
) {
    val listState = rememberLazyListState()
    LaunchedEffect(highlightMessageId, messages) {
        if (highlightMessageId != null && messages.isNotEmpty()) {
            // LazyColumn uses reversed list, so we must search in reversed list relative order
            val index = messages.asReversed().indexOfFirst { it.id == highlightMessageId }
            if (index != -1) {
                listState.scrollToItem(index)
            }
        }
    }
    val context = LocalContext.current
    
    // 🎛️ ONE-LINER SPACING CONTROLS - Adjust these values to move UI up/down
    // listState declared above
    // Bottom Bar Visibility Logic: Show if at bottom OR if idle for 1s
    var showBottomBar by remember { mutableStateOf(true) }
    val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val isScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    
    LaunchedEffect(isAtBottom, isScrolling) {
        if (isAtBottom) {
             showBottomBar = true
        } else if (isScrolling) {
            showBottomBar = false
        } else {
             // Not at bottom, and stopped scrolling. Wait 1s then show.
             kotlinx.coroutines.delay(1000)
             showBottomBar = true
        }
    }
    
    val targetInputAreaMove = if (showBottomBar) 92.dp else 20.dp
    val animatedInputAreaMove by animateDpAsState(targetValue = targetInputAreaMove, label = "input_move", animationSpec = tween(durationMillis = 300))
    
    val bottomBarMove = 20.dp            // 1. Bottom bar (Add, Switch, Eye)
    val inputAreaMove = animatedInputAreaMove // 2. Animated Input Position
    val capacityPillMove = (-25).dp      // 3. Capacity pill indicator (relative to input)
    val replyBannerMove = 13.dp          // 4. Reply banner vertical position (Normal)
    val replyBannerPillMove = 32.dp      // 5. Reply banner vertical position (When capacity pill is active)
    val chatToPillMove = 42.dp           // 6. Latest bubble distance to pill area
    val chatToReplyBannerMove = 81.dp    // 7. Latest bubble distance to Reply Banner 🆕
    
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var inputHeightPx by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope() // For Scrolling

    val selectedMessages = remember { mutableStateListOf<Message>() }
    val isSelectionMode = selectedMessages.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (showDeleteDialog && isSelectionMode) {
             // Dialog Overlay logic handled below
        }


        val capacityPillXOffset = 5.dp
        val chatListHorizontalPadding = 20.dp
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val targetBottomPadding = max(inputAreaMove, imeBottom + 8.dp)
        val animatedBottomPadding by animateDpAsState(targetValue = targetBottomPadding, label = "input_padding")
        
        // 🎞️ Smooth Animation for Reply Banner/Pill Spacing
        val targetExtraPadding = if (selectedImageUri != null && replyingTo != null) chatToReplyBannerMove + 34.dp // Both Active: Banner moves up + Pill height
                                 else if (selectedImageUri != null) chatToPillMove 
                                 else if (replyingTo != null) chatToReplyBannerMove 
                                 else 13.dp
        val animatedExtraPadding by animateDpAsState(targetValue = targetExtraPadding, label = "extra_padding", animationSpec = tween(durationMillis = 300))
            
        // 🌫️ Top Gradient Scrim - Header Background
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(120.dp) // Covers header area + fade
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.9f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    IconButton(onClick = { selectedMessages.clear() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        text = "${selectedMessages.size}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).bounceClick(onClick = onBack), contentAlignment = Alignment.Center) { Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(chatName.take(1), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = chatName, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp), color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = if (isContactOnline) "Online" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isContactOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.offset(y = (-4).dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 📳 Edge Haptics Logic
            val haptic = LocalHapticFeedback.current

            // Revised Logic for Distinct Haptics:
            var wasAtBottom by remember { mutableStateOf(true) } // Start seemingly at bottom
            var wasAtTop by remember { mutableStateOf(false) }

            LaunchedEffect(listState, messages.size) {
               snapshotFlow {
                   val layoutInfo = listState.layoutInfo
                   val totalItems = layoutInfo.totalItemsCount
                   if (totalItems == 0) return@snapshotFlow Pair(false, false)
                   
                   // Bottom is Index 0 (reverseLayout)
                   val isAtBottom = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 10 // tolerance
                   
                   // Top is Last Index
                   val isAtTop = if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                        layoutInfo.visibleItemsInfo.last().index == totalItems - 1 &&
                        layoutInfo.visibleItemsInfo.last().offset + layoutInfo.visibleItemsInfo.last().size <= layoutInfo.viewportEndOffset + 10
                   } else false
                   
                   Pair(isAtBottom, isAtTop)
               }.collect { (isBottom, isTop) ->
                   if (isBottom && !wasAtBottom) {
                       haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                   }
                   if (isTop && !wasAtTop) {
                       haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                   }
                   wasAtBottom = isBottom
                   wasAtTop = isTop
               }
            }

            // Auto-Scroll to Bottom on Send
            LaunchedEffect(messages.size) {
                 if (messages.isNotEmpty() && messages.last().isFromMe) {
                     listState.animateScrollToItem(0)
                 }
            }

            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Say something to $chatName...",
                        style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = chatListHorizontalPadding),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(3.dp), // Added 3dp spacing between bubbles
                contentPadding = PaddingValues(
                    bottom = with(LocalDensity.current) { inputHeightPx.toDp() } + animatedBottomPadding + animatedExtraPadding
                )
            ) {
                items(messages.reversed(), key = { it.id }) { message -> 
                    val isSelected = selectedMessages.contains(message)
                    Box(modifier = Modifier.animateItemPlacement().bubbleEnterAnimation()) {
                        MessageBubble(
                            message = message, 
                            allMessages = messages, 
                            isDark = isDark, 
                            isSelected = isSelected,
                            isHighlighted = message.id == highlightMessageId,
                            onLongClick = { 
                                if (!selectedMessages.contains(message)) selectedMessages.add(message)
                            },
                             onDownload = onDownloadMessage,
                            onClick = { 
                                if (isSelectionMode) {
                                    if (selectedMessages.contains(message)) selectedMessages.remove(message)
                                    else selectedMessages.add(message)
                                } else {
                                    if (currentMode == ChatMode.EXTRACT && message.text == null) {
                                        onReveal(message)
                                    } else if (message.imageUri != null && message.deliveryStatus != 3 && message.status != 3) { // Not downloading
                                         onImageClick(message.imageUri) 
                                    }
                                }
                            }, 
                            onReply = { onReplyTrigger(it) },
                            uploadProgress = uploadProgress[message.id],
                            onReplyClick = { replyId ->
                                val index = messages.asReversed().indexOfFirst { it.id == replyId }
                                if (index != -1) {
                                    scope.launch { listState.animateScrollToItem(index) }
                                }
                            }
                        ) 
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().graphicsLayer(clip = false)) {
            AnimatedVisibility(
                visible = replyingTo != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp) // Aligned with Input Area + Send Button (24dp container + 4dp row padding)
                    .padding(bottom = animatedBottomPadding + with(LocalDensity.current) { inputHeightPx.toDp() } + replyBannerMove + (if (selectedImageUri != null) replyBannerPillMove else 0.dp))
                    .zIndex(3f)
            ) {
                if (replyingTo != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(4.dp).height(36.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(8.dp))
                            if (replyingTo.imageUri != null) {
                                AsyncImage(
                                    model = replyingTo.imageUri, 
                                    contentDescription = null, 
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Replying to", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(text = replyingTo.text ?: "Photo", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = onReplyDismiss) { Icon(Icons.Default.Close, "Cancel", modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
            }
            
            LaunchedEffect(replyingTo) { if (replyingTo != null) { kotlinx.coroutines.delay(100); focusRequester.requestFocus() } }
            
            // 🌫️ "Gradual Blur" Scrim - Fades out content behind the bottom bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { inputHeightPx.toDp() + bottomBarMove + 60.dp }) // Height of input + buffer
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = if (isDark) 0.8f else 0.4f),
                                MaterialTheme.colorScheme.background
                            ),
                            startY = 0f
                        )
                    )
                    .windowInsetsPadding(WindowInsets.ime) // Moves up with keyboard
            )

            AnimatedVisibility(
                visible = currentMode == ChatMode.HIDE,
                // Adjusted scale to 0.8f for more noticeable pop
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), initialScale = 0.8f) + fadeIn(animationSpec = tween(300)),
                exit = scaleOut(animationSpec = tween(200), targetScale = 0.8f) + fadeOut(animationSpec = tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = animatedBottomPadding).padding(horizontal = 24.dp).graphicsLayer(clip = false)
            ) {
                if (selectedImageUri != null) {
                    var maxCapacity by remember(selectedImageUri) { mutableStateOf(0) }
                    val remaining = maxCapacity - textInput.length
                    LaunchedEffect(selectedImageUri) { maxCapacity = ImageUtils.estimateCapacity(context, selectedImageUri) }
                    if (maxCapacity > 0) {
                        Surface(
                            color = if (remaining < 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = if (remaining < 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.BottomStart).offset(x = capacityPillXOffset, y = capacityPillMove).zIndex(1f)
                        ) {
                            Text(text = "Capacity: ${if(remaining > 1000) "~${remaining/1000}k" else "$remaining"} left", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(4.dp).graphicsLayer(clip = false).onSizeChanged { inputHeightPx = it.height }) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp), modifier = Modifier.weight(1f).heightIn(min = 56.dp).wrapContentHeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                            if (selectedImageUri != null) {
                                Box(modifier = Modifier.padding(end = 8.dp)) {
                                    AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.size(42.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.inverseSurface, modifier = Modifier.size(20.dp).align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).shadow(2.dp, CircleShape).clickable { onRemoveImage() }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(3.dp))
                                    }
                                }
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = textInput, onValueChange = onTextInputChange, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = false, maxLines = 5,
                                decorationBox = { inner -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { if (textInput.isEmpty()) Text("Message $chatName...", color = Color.Gray); inner() } },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).focusRequester(focusRequester)
                            )
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                                Box(modifier = Modifier.fillMaxSize().bounceClick(onClick = onCameraClick), contentAlignment = Alignment.Center) { Icon(painter = painterResource(R.drawable.camera), contentDescription = "Camera", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(56.dp)) {
                        Box(modifier = Modifier.fillMaxSize().bounceClick(onClick = { if(textInput.isNotBlank() || selectedImageUri != null) onSend() }), contentAlignment = Alignment.Center) { Icon(painter = painterResource(R.drawable.sendicon), contentDescription = "Send", tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(26.dp)) }
                    }
                }
            }
            
            AnimatedVisibility(
                visible = currentMode == ChatMode.EXTRACT, 
                // Adjusted scale to 0.8f for more noticeable pop
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow), initialScale = 0.8f) + fadeIn(), 
                exit = scaleOut(targetScale = 0.8f) + fadeOut(), 
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = animatedBottomPadding).padding(horizontal = 24.dp)
            ) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Select an image to reveal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomBarMove, start = 24.dp, end = 24.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
                        Box(modifier = Modifier.fillMaxSize().bounceClick(onClick = onPickImage), contentAlignment = Alignment.Center) { Icon(painter = painterResource(R.drawable.addimage), contentDescription = "Add", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp).offset(x = 1.dp, y = 1.dp)) }
                    }
                    Surface(modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            val tabWidth = maxWidth / 2
                            val indicatorOffset by animateDpAsState(if (currentMode == ChatMode.HIDE) 0.dp else tabWidth, label = "indicator")
                            val hideTextColor by animateColorAsState(if (currentMode == ChatMode.HIDE) (if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black) else MaterialTheme.colorScheme.onSurfaceVariant, label = "hide_text")
                            val revealTextColor by animateColorAsState(if (currentMode == ChatMode.EXTRACT) (if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color.Black) else MaterialTheme.colorScheme.onSurfaceVariant, label = "reveal_text")
                            Box(modifier = Modifier.width(tabWidth).fillMaxHeight().offset(x = indicatorOffset).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primaryContainer))
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().bounceClick { onModeChange(ChatMode.HIDE) }, contentAlignment = Alignment.Center) { Text("Hide", fontWeight = FontWeight.SemiBold, color = hideTextColor) }
                                Box(modifier = Modifier.weight(1f).fillMaxHeight().bounceClick { onModeChange(ChatMode.EXTRACT) }, contentAlignment = Alignment.Center) { Text("Reveal", fontWeight = FontWeight.SemiBold, color = revealTextColor) }
                            }
                        }
                    }
                    Surface(shape = CircleShape, color = if (isStealthMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, shadowElevation = if (isStealthMode) 8.dp else 0.dp, modifier = Modifier.size(56.dp)) {
                        Box(modifier = Modifier.fillMaxSize().bounceClick { onStealthModeChange(!isStealthMode) }, contentAlignment = Alignment.Center) { 
                            Crossfade(targetState = isStealthMode, label = "stealth_icon") { isEnabled ->
                                Icon(
                                    painter = painterResource(if (isEnabled) R.drawable.f7_envelope_closed else R.drawable.envelope_open_fill), 
                                    contentDescription = "Stealth", 
                                    tint = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = if (isEnabled) Modifier.size(22.dp).offset(y = 0.5.dp) else Modifier.size(21.dp)
                                ) 
                            }
                        }
                    }
                }
            }
        }
        
        if (showDeleteDialog && isSelectionMode) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete ${selectedMessages.size} messages?") },
                text = { Text("Selected messages will be permanently deleted.") },
                confirmButton = {
                    val allFromMe = selectedMessages.all { it.isFromMe }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                         
                         Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                             if (allFromMe) {
                                  TextButton(onClick = { onDeleteMessage(selectedMessages.toList(), false); selectedMessages.clear(); showDeleteDialog = false }) {
                                     Text("For Me")
                                  }
                                  Button(onClick = { onDeleteMessage(selectedMessages.toList(), true); selectedMessages.clear(); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                     Text("Everyone")
                                  }
                             } else {
                                  Button(onClick = { onDeleteMessage(selectedMessages.toList(), false); selectedMessages.clear(); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                     Text("For Me")
                                  }
                             }
                        }
                    }
                },
                dismissButton = {}
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message, allMessages: List<Message>, isDark: Boolean, isSelected: Boolean, isHighlighted: Boolean = false,
    onClick: () -> Unit, onLongClick: () -> Unit, onReply: (Message) -> Unit, onDownload: (Message) -> Unit,
    uploadProgress: Float? = null, onReplyClick: (String) -> Unit = {}
) {
    // 🎛️ BUBBLE SPACING CONTROLS
    val textTopMargin = 8.dp      // ↕️ Space between Image and Text
    val textBottomMargin = 4.dp   // ↕️ Space between Text and Time


    val isMe = message.isFromMe
    val contentColor = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    
    // Highlight Animation (Flash)
    val highlightAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            // Start visible then fade out
            highlightAlpha.snapTo(0.6f)
            highlightAlpha.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(2000))
        }
    }

    val baseColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.tertiary // Distinct bold color for highlight

    val bubbleColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) 
                      else highlightColor.copy(alpha = highlightAlpha.value).compositeOver(baseColor)
                      
    val showText = message.text != null
    
    val swipeOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
        
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).pointerInput(Unit) {
        detectHorizontalDragGestures(onDragEnd = { scope.launch { if (java.lang.Math.abs(swipeOffset.value) > 100f) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onReply(message) }; swipeOffset.animateTo(0f) } },
            onHorizontalDrag = { _, dragAmount -> scope.launch { swipeOffset.snapTo((swipeOffset.value + dragAmount * 0.5f).coerceIn(-200f, 200f)); if (java.lang.Math.abs(swipeOffset.value) > 100f) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } })
    }, contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart) {
         if (swipeOffset.value != 0f) {
             val iconTint = if (isDark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) // Lighter in dark, darker in light
             val scaleBase = java.lang.Math.min(java.lang.Math.abs(swipeOffset.value) / 100f, 1f)
             // Mirror icon if swiping left-to-right (positive offset) so it points right
             val scaleX = if (swipeOffset.value > 0) -scaleBase else scaleBase
             
             Icon(Icons.AutoMirrored.Filled.Reply, null, tint = iconTint, modifier = Modifier.align(if (swipeOffset.value > 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 16.dp).graphicsLayer(scaleX = scaleX, scaleY = scaleBase))
         }
        Column(modifier = Modifier.offset { IntOffset(swipeOffset.value.toInt(), 0) }.fillMaxWidth(), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Surface(
                color = bubbleColor, 
                contentColor = contentColor, 
                shape = if (isMe) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp), 
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                 if (message.replyToId != null) {
                     val replied = allMessages.find { it.id == message.replyToId }
                     if (replied != null) {
                         Surface(color = contentColor.copy(alpha = 0.05f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { onReplyClick(replied.id) }) {
                             Row(modifier = Modifier.padding(8.dp)) {
                                 Box(modifier = Modifier.width(3.dp).height(36.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                                 Spacer(modifier = Modifier.width(8.dp))
                                 if (replied.imageUri != null) {
                                    AsyncImage(
                                        model = replied.imageUri, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                 Column { Text(if (replied.isFromMe) "You" else "Contact", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold); Text(replied.text ?: "Photo", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                             }
                         }
                     }
                 }
                if (message.imageUri != null) {
                    Box(contentAlignment = Alignment.Center) {
                        AsyncImage(model = message.imageUri, contentDescription = null, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) // Removed clickable from here
                        if (message.status == 1) {
                             val context = LocalContext.current
                             val fileSize = remember(message.imageUri) {
                                 if (message.imageUri != null) ImageUtils.getFileSize(context, message.imageUri) else 0L
                             }
                             
                             // Upload Pill (Bottom Start)
                             Box(modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) { // Matched padding
                                 Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { // Matched spacing
                                      if (uploadProgress != null && uploadProgress > 0f) {
                                          CircularProgressIndicator(progress = uploadProgress, modifier = Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                                          if (fileSize > 0) {
                                               val totalMB = fileSize / 1024f / 1024f
                                               val currentMB = totalMB * uploadProgress
                                               Text(String.format("%.1f/%.1f MB", currentMB, totalMB), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White)
                                          } else {
                                               Text("${(uploadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White)
                                          }
                                      } else {
                                          CircularProgressIndicator(modifier = Modifier.size(12.dp), color = Color.White, strokeWidth = 2.dp)
                                          Text("Uploading...", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White)
                                      }
                                 }
                             }
                        }
                        if (!isMe && message.status == 2) IconButton(onClick = { onDownload(message) }) { Icon(Icons.Default.Download, null, tint = Color.White) }
                        
                        // Time Overlay for Image-Only Messages
                        if (!showText) {
                            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(TimeUtils.formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White)
                                    if (isMe) {
                                         val (icon, tint) = when {
                                             message.status == 1 -> Icons.Rounded.AccessTime to Color.White.copy(alpha = 0.7f)
                                             message.deliveryStatus == 3 -> Icons.Rounded.DoneAll to Color(0xFF34B7F1) // Read = Double Tick Blue
                                             message.deliveryStatus == 2 -> Icons.Rounded.CheckCircle to Color.White
                                             else -> Icons.Outlined.CheckCircle to Color.White
                                         }
                                         Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        
                    }
                }
                if (showText) {
                    if (message.imageUri != null) Spacer(modifier = Modifier.height(textTopMargin))
                    Text(text = message.text!!, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(textBottomMargin))
                }
                if (showText) {
                    Row(modifier = Modifier.align(if (isMe) Alignment.End else Alignment.Start), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(TimeUtils.formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = contentColor.copy(alpha = 0.6f))
                        if (isMe) {
                             val (icon, tint) = when {
                                 message.status == 1 -> Icons.Rounded.AccessTime to contentColor.copy(alpha = 0.6f)
                                 message.deliveryStatus == 3 -> Icons.Rounded.DoneAll to Color(0xFF34B7F1) // Read = Double Tick Blue
                                 message.deliveryStatus == 2 -> Icons.Rounded.CheckCircle to contentColor.copy(alpha = 0.6f)
                                 else -> Icons.Outlined.CheckCircle to contentColor.copy(alpha = 0.6f)
                             }
                             Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                        }
                    }
                }
              }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    StegAppTheme(darkTheme = false) {
        ChatScreenContent(
            chatName = "Sam Xavii", 
            messages = listOf(
                Message(id = "1", chatId = "1", text = "Hello!", isFromMe = true), 
                Message(id = "2", chatId = "2", text = "Image", isFromMe = false)
            ),
            isDark = false, 
            textInput = "Type...", 
            onTextInputChange = {}, 
            selectedImageUri = Uri.parse("https://example.com/mock.jpg"),
            currentMode = ChatMode.HIDE, 
            onModeChange = {}, 
            onBack = {}, 
            onPickImage = {}, 
            onSend = {}, 
            onError = {}, 
            onDeleteMessage = { _, _ -> }, 
            onDownloadMessage = {}, 
            onRemoveImage = {},
            isStealthMode = false, 
            onStealthModeChange = {}, 
            onCameraClick = {}, 
            isConnected = true, 
            isContactOnline = true, 
            onImageClick = {}, 
            replyingTo = null, 
            onReplyDismiss = {}, 
            onReplyTrigger = {},
            onReveal = {}
        )
    }
}

private enum class ButtonState { Pressed, Idle }

@Composable
fun Modifier.bounceClick(
    scaleDown: Float = 0.90f,
    onClick: () -> Unit
): Modifier {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    val scale by animateFloatAsState(if (buttonState == ButtonState.Pressed) scaleDown else 1f, label = "bounce")
    val haptic = LocalHapticFeedback.current

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
        )
        .pointerInput(buttonState) {
            awaitPointerEventScope {
                buttonState = if (buttonState == ButtonState.Pressed) {
                    waitForUpOrCancellation()
                    ButtonState.Idle
                } else {
                    awaitFirstDown(false)
                    ButtonState.Pressed
                }
            }
        }
}

@Composable
fun Modifier.bubbleEnterAnimation(): Modifier = composed {
    // Increased growth range (0.75 -> 1.0) for better visibility
    val scale = remember { androidx.compose.animation.core.Animatable(0.75f) }
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        // MediumBouncy gives it that extra "life" without being too playful
        launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        // Smooth fade in (linear/tween) to avoid abrupt visibility pop
        launch { alpha.animateTo(1f, tween(durationMillis = 400, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) }
    }
    this.graphicsLayer(scaleX = scale.value, scaleY = scale.value, alpha = alpha.value)
}
