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
import coil.compose.AsyncImage
import com.vamsi.stegapp.model.Message
import com.vamsi.stegapp.ui.components.*
import com.vamsi.stegapp.ui.screens.HomeScreen
import com.vamsi.stegapp.ui.screens.LoginScreen
import com.vamsi.stegapp.utils.UserPrefs
import com.vamsi.stegapp.ui.theme.StegAppTheme
import com.vamsi.stegapp.viewmodel.ChatViewModel
import com.vamsi.stegapp.viewmodel.ChatViewModelFactory

enum class ChatMode { HIDE, EXTRACT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            "chat/{chatName}",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) { backStackEntry ->
            val chatName = backStackEntry.arguments?.getString("chatName") ?: "Chat"
            ChatScreen(navController = navController, chatName = chatName)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    chatName: String,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(LocalContext.current, chatName))
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isDark = isSystemInDarkTheme()
    val messages by viewModel.messages.collectAsState(initial = emptyList())
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var textInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("strongPassword123") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showPasswordSheet by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf(ChatMode.HIDE) }

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

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
        if (uri != null) {
            if (currentMode == ChatMode.EXTRACT) viewModel.receiveMessage(uri, passwordInput)
            else showPasswordSheet = true
        }
    }

    ChatScreenContent(
        chatName = chatName, messages = messages, isDark = isDark, textInput = textInput,
        onTextInputChange = { textInput = it }, passwordInput = passwordInput,
        onPasswordChange = { passwordInput = it }, selectedImageUri = selectedImageUri,
        showPasswordSheet = showPasswordSheet, onTogglePasswordSheet = { showPasswordSheet = !showPasswordSheet },
        onDismissPasswordSheet = { showPasswordSheet = false }, currentMode = currentMode,
        onModeChange = { currentMode = it }, onBack = { navController.popBackStack() },
        onPickImage = { pickImageLauncher.launch("image/*") },
        onSend = {
            viewModel.sendMessage(textInput, selectedImageUri!!, passwordInput)
            textInput = ""; selectedImageUri = null
        },
        onError = { toastMessage = it },
        onDeleteMessage = { viewModel.deleteMessage(it) },
        onDownloadMessage = { viewModel.downloadMedia(it) }
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
    onDeleteMessage: (Message) -> Unit, onDownloadMessage: (Message) -> Unit
) {
    val saiBackground = if (isDark) Color(0xFF000000) else Color(0xFFF2F4F6)
    val saiSurface = if (isDark) Color(0xFF2C2C2E) else Color.White
    val saiTextPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val saiDockBackground = if (isDark) Color(0xFF2C2C2E) else Color.White
    val saiPillActive = if (isDark) Color.Black else Color(0xFFE0E0E0)
    val saiIconTint = if (isDark) Color.White else Color(0xFF1C1C1E)
    val globalShadowColor = if (isDark) Color.Black.copy(alpha = 0.7f) else Color.Gray.copy(alpha = 0.3f)
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
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
                    Text(text = chatName, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp), color = saiTextPrimary)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(4.dp).graphicsLayer(clip = false)
                ) {
                    Surface(
                        color = saiSurface, shape = RoundedCornerShape(32.dp), shadowElevation = 0.dp, tonalElevation = 0.dp,
                        modifier = Modifier.weight(1f).height(56.dp) 
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
                            if (selectedImageUri != null) {
                                AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = textInput, onValueChange = onTextInputChange,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = saiTextPrimary),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(saiTextPrimary),
                                singleLine = true, 
                                decorationBox = { inner -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { if (textInput.isEmpty()) Text("Message $chatName...", color = Color.Gray); inner() } },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                                IconButton(onClick = { onError("Voice recording coming soon!") }, modifier = Modifier.fillMaxSize()) {
                                    Icon(Icons.Default.Mic, "Voice", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                    val sendBtnColor = MaterialTheme.colorScheme.primaryContainer
                    Surface(
                        shape = CircleShape, color = sendBtnColor, shadowElevation = 0.dp, tonalElevation = 0.dp,
                        modifier = Modifier.size(56.dp).shadow(16.dp, CircleShape, clip = false, ambientColor = sendBtnColor, spotColor = sendBtnColor)
                    ) {
                        IconButton(onClick = { if (selectedImageUri != null) onSend() else onError("Select image first!") }) {
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
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = saiSurface,
                    shadowElevation = 8.dp,
                    tonalElevation = 8.dp,
                    modifier = Modifier.border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                        .clickable(enabled = false) {} // Consume clicks inside the card
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Message Options", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = saiTextPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Delete Button
                            Button(
                                onClick = { 
                                    selectedMessage?.let { onDeleteMessage(it) }
                                    selectedMessage = null 
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Delete")
                            }
                            
                            // Cancel Button
                            OutlinedButton(
                                onClick = { selectedMessage = null },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = saiTextPrimary)
                            ) {
                                Text("Cancel")
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
            Column(modifier = Modifier.padding(10.dp)) {
                // Logic: Show Image ONLY if NOT revealed (and text is null or it's from me)
                // If revealed, we hide the image.
                val showImage = message.imageUri != null && !isRevealed
                
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
                    if (message.text != null) Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (message.text != null) {
                    Text(text = message.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp))
                }
                
                if (isRevealed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Using LockOpen if available, else Lock with color
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(" Secret Revealed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (message.isStego && message.isFromMe) {
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
