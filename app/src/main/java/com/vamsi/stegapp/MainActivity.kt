package com.vamsi.stegapp

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
            route = "chat/{chatName}?imageUri={imageUri}",
            arguments = listOf(
                navArgument("chatName") { type = NavType.StringType },
                navArgument("imageUri") { type = NavType.StringType; nullable = true }
            ),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) { backStackEntry ->
            val chatName = backStackEntry.arguments?.getString("chatName") ?: "Chat"
            val imageUri = backStackEntry.arguments?.getString("imageUri")
            ChatScreen(navController = navController, chatName = chatName, initialImageUri = imageUri)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    chatName: String,
    initialImageUri: String? = null,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(LocalContext.current, chatName))
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    val isConnected by viewModel.isConnected.collectAsState(initial = false)
    val isContactOnline by viewModel.contactOnlineStatus.collectAsState(initial = false)
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var textInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("strongPassword123") }
    // Initialize with passed image if available
    var selectedImageUri by remember { mutableStateOf<Uri?>(initialImageUri?.let { Uri.parse(it) }) }
    var showPasswordSheet by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf(ChatMode.HIDE) }
    
    // Stealth & Camouflage State
    var isStealthMode by remember { mutableStateOf(false) }
    var showCamouflageDialog by remember { mutableStateOf(false) }
    var camouflageInput by remember { mutableStateOf("") }
    // We already have textInput and selectedImageUri hoisted, so no need for pending vars if we don't clear them immediately.
    // Actually, onSend clears them. So we need to NOT clear them if stealth mode triggers dialog.
    // Or just Capture them in the Dialog logic context.
    // But `AlertDialog` is separate.
    // Since `textInput` and `selectedImageUri` are state, they persist until we change them.
    // So if we just set `showCamouflageDialog = true` inside `onSend` and DON'T clear, the state remains.
    // Then in Dialog Confirm, we send and THEN clear. Perfect.

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
            if (currentMode == ChatMode.EXTRACT) viewModel.receiveMessage(uri, passwordInput)
            else showPasswordSheet = true
        }
    }
    
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            selectedImageUri = tempCameraUri
             if (currentMode == ChatMode.EXTRACT) viewModel.receiveMessage(tempCameraUri!!, passwordInput)
            else showPasswordSheet = true
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
                         // selectedImageUri is nullable in viewModel.sendMessage, so this is safe now
                        viewModel.sendMessage(textInput, selectedImageUri, passwordInput, camouflageInput)
                        // Clear Everything
                        textInput = ""
                        selectedImageUri = null
                        camouflageInput = ""
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
        onTextInputChange = { textInput = it }, passwordInput = passwordInput,
        onPasswordChange = { passwordInput = it }, selectedImageUri = selectedImageUri,
        showPasswordSheet = showPasswordSheet, onTogglePasswordSheet = { showPasswordSheet = !showPasswordSheet },
        onDismissPasswordSheet = { showPasswordSheet = false }, currentMode = currentMode,
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
                 // Text-only OR Image Steganography
                 if (selectedImageUri != null || textInput.isNotBlank()) {
                    showCamouflageDialog = true
                 } else {
                     toastMessage = "Enter text or select an image!"
                 }
            } else {
                if (selectedImageUri != null || textInput.isNotBlank()) {
                    viewModel.sendMessage(textInput, selectedImageUri, passwordInput)
                    textInput = ""; selectedImageUri = null
                }
            }
        },
        onError = { toastMessage = it },
        onDeleteMessage = { msg, forAll -> viewModel.deleteMessage(msg, forAll) },
        onDownloadMessage = { viewModel.downloadMedia(it) },
        onRemoveImage = { selectedImageUri = null },
        isStealthMode = isStealthMode,
        onStealthModeChange = { isStealthMode = it },
        isConnected = isConnected,
        isContactOnline = isContactOnline
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
    chatName: String, messages: List<Message>, isDark: Boolean, textInput: String,
    onTextInputChange: (String) -> Unit, passwordInput: String, onPasswordChange: (String) -> Unit,
    selectedImageUri: Uri?, showPasswordSheet: Boolean, onTogglePasswordSheet: () -> Unit,
    onDismissPasswordSheet: () -> Unit, currentMode: ChatMode, onModeChange: (ChatMode) -> Unit,

    onBack: () -> Unit, onPickImage: () -> Unit, onSend: () -> Unit, onError: (String) -> Unit,
    onDeleteMessage: (Message, Boolean) -> Unit, onDownloadMessage: (Message) -> Unit, onRemoveImage: () -> Unit,
    isStealthMode: Boolean, onStealthModeChange: (Boolean) -> Unit, onCameraClick: () -> Unit, isConnected: Boolean,
    isContactOnline: Boolean
) {
    val saiBackground = if (isDark) Color(0xFF000000) else Color(0xFFF2F4F6)
    val saiSurface = if (isDark) Color(0xFF2C2C2E) else Color.White
    val saiTextPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val saiDockBackground = if (isDark) Color(0xFF2C2C2E) else Color.White
    val saiPillActive = if (isDark) Color.Black else Color(0xFFE0E0E0)
    val saiIconTint = if (isDark) Color.White else Color(0xFF1C1C1E)
    val globalShadowColor = if (isDark) Color.Black.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.3f)
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    // Back Handler usage commented out for debugging
    // androidx.activity.compose.BackHandler(enabled = selectedMessage != null) {
    //    selectedMessage = null
    // }

    Box(modifier = Modifier.fillMaxSize().background(saiBackground)) {
        // ... (existing code) ...


        
        // Dim background when menu is open
        if (selectedMessage != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).pointerInput(Unit) { detectTapGestures { selectedMessage = null } })
        }

        val inputAreaPaddingBottom = 86.dp 
        val buttonShadowElevation = 12.dp 
        val messageAreaOffset = 0.dp 
        val extractHintOffset = -8.dp 
        val listVerticalOffset = 0.dp 
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
            
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = saiIconTint) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = Color.Gray, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(chatName.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = chatName, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp), color = saiTextPrimary)
                        Text(
                            text = if (isContactOnline) "Online" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isContactOnline) Color(0xFF4CAF50) else Color(0xFFFF5252)
                        )
                    }
                }

                // Actions (Stealth Switch)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Stealth", style = MaterialTheme.typography.labelSmall, color = saiTextPrimary)
                    Switch(
                        checked = isStealthMode,
                        onCheckedChange = { onStealthModeChange(it) },
                        modifier = Modifier.scale(0.7f),
                         colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 100.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Say something to $chatName...",
                        style = MaterialTheme.typography.titleLarge.copy(color = saiTextPrimary.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().offset(y = listVerticalOffset).padding(horizontal = 16.dp),
                reverseLayout = true, 
                contentPadding = PaddingValues(
                    bottom = max(inputAreaPaddingBottom, imeBottom) + 88.dp // Stabilized padding, removed dynamic offsets to prevent jitter
                )
            ) {
                items(messages.reversed(), key = { it.id }) { message -> 
                    MessageBubble(message, isDark, onLongClick = { selectedMessage = message }, onDownload = onDownloadMessage) 
                }
            }
        }

        
        val targetBottomPadding = max(100.dp, imeBottom + 8.dp)
        val animatedBottomPadding by animateDpAsState(targetValue = targetBottomPadding, label = "input_padding")

        Box(modifier = Modifier.fillMaxSize().graphicsLayer(clip = false)) {
            // ... (Input Field Visibility Logic - Unchanged)
            AnimatedVisibility(
                visible = currentMode == ChatMode.HIDE,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.CenterVertically),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.CenterVertically),
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = messageAreaOffset).padding(bottom = animatedBottomPadding).padding(horizontal = 24.dp).graphicsLayer(clip = false)
            ) {
                // ... (Input Row)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(4.dp).graphicsLayer(clip = false)
                ) {
                    Surface(
                        color = saiSurface, shape = RoundedCornerShape(28.dp), shadowElevation = 0.dp, tonalElevation = 0.dp,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp).wrapContentHeight() 
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp) 
                        ) {
                            
                            if (selectedImageUri != null) {
                                Box(modifier = Modifier.padding(end = 8.dp)) {
                                    AsyncImage(
                                        model = selectedImageUri, 
                                        contentDescription = null, 
                                        modifier = Modifier.size(42.dp).clip(CircleShape), 
                                        contentScale = ContentScale.Crop
                                    )
                                    // Remove Button
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.inverseSurface,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-2).dp)
                                            .shadow(2.dp, CircleShape)
                                            .clickable { onRemoveImage() }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                                            modifier = Modifier.padding(3.dp)
                                        )
                                    }
                                }
                            }

                            androidx.compose.foundation.text.BasicTextField(
                                value = textInput, onValueChange = onTextInputChange,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = saiTextPrimary),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(saiTextPrimary),
                                singleLine = false,
                                maxLines = 5,
                                decorationBox = { inner -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) { if (textInput.isEmpty()) Text("Message $chatName...", color = Color.Gray); inner() } },
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                            )

                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                                IconButton(onClick = onCameraClick, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.PhotoCamera, "Camera", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    val sendBtnColor = MaterialTheme.colorScheme.primaryContainer
                    Surface(
                        shape = CircleShape, color = sendBtnColor, shadowElevation = 0.dp, tonalElevation = 0.dp,
                        modifier = Modifier.size(56.dp).shadow(16.dp, CircleShape, clip = false, ambientColor = sendBtnColor, spotColor = sendBtnColor)
                    ) {
                        IconButton(
                            onClick = { onSend() },
                            enabled = textInput.isNotBlank() || selectedImageUri != null
                        ) {
                            Icon(Icons.Default.Send, "Send", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp).rotate(-45f).offset(x = 2.dp))
                        }
                    }
                }
            }
            
             AnimatedVisibility(
                visible = currentMode == ChatMode.EXTRACT,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.CenterVertically),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.CenterVertically),
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = extractHintOffset).padding(bottom = inputAreaPaddingBottom + 8.dp).padding(horizontal = 24.dp).graphicsLayer(clip = false)
            ) {
                Surface(color = saiSurface, shape = RoundedCornerShape(24.dp), shadowElevation = 0.dp, tonalElevation = 0.dp, modifier = Modifier.padding(4.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Select an image to separate", style = MaterialTheme.typography.bodyMedium, color = saiTextPrimary)
                    }
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp, start = 24.dp, end = 24.dp).graphicsLayer(clip = false)) {
            Row(modifier = Modifier.fillMaxWidth().padding(4.dp).graphicsLayer(clip = false), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // 1. ADD BUTTON (Left)
                val addBtnColor = MaterialTheme.colorScheme.primaryContainer
                Surface(
                    shape = CircleShape, 
                    color = addBtnColor, 
                    shadowElevation = 0.dp, 
                    tonalElevation = 0.dp, 
                    modifier = Modifier.size(56.dp).shadow(16.dp, CircleShape, spotColor = addBtnColor, ambientColor = addBtnColor, clip = false)
                ) {
                    IconButton(onClick = onPickImage) { 
                        Icon(Icons.Default.AddPhotoAlternate, "Add", tint = MaterialTheme.colorScheme.onPrimaryContainer) 
                    }
                }

                // 2. TOGGLE SWITCH (Center)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .shadow(buttonShadowElevation, RoundedCornerShape(32.dp), spotColor = globalShadowColor, ambientColor = globalShadowColor),
                    shape = RoundedCornerShape(32.dp),
                    color = saiDockBackground,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        val tabWidth = maxWidth / 2
                        val indicatorOffset by animateDpAsState(if (currentMode == ChatMode.HIDE) 0.dp else tabWidth, label = "indicator")
                        val hideTextColor by animateColorAsState(if (currentMode == ChatMode.HIDE) (if (isDark) Color.White else Color.Black) else Color.Gray, label = "hide_text")
                        val revealTextColor by animateColorAsState(if (currentMode == ChatMode.EXTRACT) (if (isDark) Color.White else Color.Black) else Color.Gray, label = "reveal_text")
                        Box(modifier = Modifier.width(tabWidth).fillMaxHeight().offset(x = indicatorOffset).clip(RoundedCornerShape(28.dp)).background(saiPillActive))
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { onModeChange(ChatMode.HIDE) }, contentAlignment = Alignment.Center) { Text("Hide", fontWeight = FontWeight.SemiBold, color = hideTextColor) }
                            Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { onModeChange(ChatMode.EXTRACT) }, contentAlignment = Alignment.Center) { Text("Reveal", fontWeight = FontWeight.SemiBold, color = revealTextColor) }
                        }
                    }
                }

                // 3. LOCK BUTTON (Right)
                Surface(shape = CircleShape, color = saiDockBackground, shadowElevation = 0.dp, tonalElevation = 0.dp, modifier = Modifier.size(56.dp).shadow(buttonShadowElevation, CircleShape, spotColor = globalShadowColor, ambientColor = globalShadowColor)) {
                    IconButton(onClick = onTogglePasswordSheet) { Icon(Icons.Default.Lock, "Key", tint = if (passwordInput == "strongPassword123") Color.Gray else saiIconTint) }
                }
            }
        }
        AnimatedVisibility(visible = showPasswordSheet, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            PasswordBottomSheet(passwordInput, onPasswordChange, onDismissPasswordSheet)
        }

        // Message Options Overlay (Simplified Layout for Z-Index)
        if (selectedMessage != null) {
            // Full screen transparent click handler to dismiss menu
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { selectedMessage = null },
                contentAlignment = Alignment.Center
            ) {
                // Delete Dialog
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Delete Message?") },
                        text = { Text(if (selectedMessage!!.isFromMe) "You can delete this message for everyone or just for yourself." else "Delete this message from your chat history?") },
                        confirmButton = {
                            if (selectedMessage!!.isFromMe) {
                                Button(onClick = { 
                                    selectedMessage?.let { onDeleteMessage(it, true) }
                                    selectedMessage = null; showDeleteDialog = false 
                                }) { Text("For Everyone") }
                            } else {
                                Button(onClick = { 
                                    selectedMessage?.let { onDeleteMessage(it, false) }
                                    selectedMessage = null; showDeleteDialog = false 
                                }) { Text("Delete") }
                            }
                        },
                        dismissButton = {
                            Row {
                                if (selectedMessage!!.isFromMe) {
                                    TextButton(onClick = { 
                                        selectedMessage?.let { onDeleteMessage(it, false) }
                                        selectedMessage = null; showDeleteDialog = false 
                                    }) { Text("For Me") }
                                }
                                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                            }
                        }
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(28.dp), // Matching Material 3 Dialog shape
                        color = MaterialTheme.colorScheme.surface, // Fallback for older M3 versions
                        shadowElevation = 6.dp,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .width(280.dp) // Standard Dialog width
                            .clickable(enabled = false) {}
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp) // Standard Dialog padding
                        ) {
                            // Header matching Delete Dialog
                            Text("Message Options", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Choose an action for this message.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Buttons Row aligned to End
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Cancel TextButton
                                TextButton(
                                    onClick = { selectedMessage = null }
                                ) {
                                    Text("Cancel")
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))

                                // Delete Button (Primary)
                                TextButton(
                                    onClick = { showDeleteDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }


    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: Message, isDark: Boolean, onLongClick: (Offset) -> Unit, onDownload: (Message) -> Unit) {
    val isMe = message.isFromMe
    val isRevealed = message.imageUri != null && message.text != null && !isMe
    
    // Bubble Colors
    val bubbleMe = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA) 
    val bubbleOther = if (isDark) Color(0xFF1C1C1E) else Color.White // Changed to White for visibility on Light Gray background

    val contentColor = if (isDark) Color.White else Color.Black
    val shape = if (isMe) RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    
    // Removed BouncyContent to fix potential interaction issues ("highlights some ui element") and simplify layout
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Surface(
                color = if (isMe) bubbleMe else bubbleOther, 
                contentColor = contentColor, 
                shape = shape, 
                shadowElevation = if (!isMe && !isDark) 1.dp else 0.dp, 
                modifier = Modifier
                .widthIn(max = 320.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { offset -> onLongClick(offset) }
                    )
                }
        ) {
            // Check if we have ANYTHING to show. If hidden image + null text + not revealed + not stego info... empty?
            // "showImage" logic below handles image hiding.
            // If showImage is false, and text is null, and NOT revealed... we might render empty padding.
             
            // Calculate if content exists
             val showImage = message.imageUri != null && !(message.isStego && message.status == 4 && message.text != null)
             val showText = message.text != null
             val showRevealed = isRevealed
             val showStegoInfo = message.isStego && message.isFromMe
             
             // Content Render
             Column(modifier = Modifier.padding(10.dp)) {
                
                if (showImage) {
                    Box(contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = message.imageUri, 
                            contentDescription = null, 
                            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))
                                .let { if (message.status == 1 || message.status == 3) it.graphicsLayer { alpha = 0.5f } else it },
                            contentScale = ContentScale.Crop
                        )
                        
                        // 1. Sending Buffer
                        if (message.status == 1) { // SENDING
                             CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                        
                         // 2. Download Button (Receiver)
                        if (!isMe && message.status == 2) { // REMOTE / PENDING
                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.6f)) {
                                IconButton(onClick = { onDownload(message) }) {
                                    Icon(Icons.Default.Download, "Download", tint = Color.White)
                                }
                            }
                        }

                        // 3. Downloading Spinner
                        if (!isMe && message.status == 3) { // DOWNLOADING
                             CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    if (showText) Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (showText) {
                    Text(text = message.text!!, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp))
                }
                
                if (showRevealed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Using LockOpen if available, else Lock with color
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(" Secret Revealed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (showStegoInfo) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(12.dp), tint = contentColor.copy(alpha = 0.6f))
                        Text(if (message.status == 1) " Sending..." else " Hidden", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.6f))
                    }
                }
                

            }
        }
    }
}
