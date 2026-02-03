package com.vamsi.stegapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vamsi.stegapp.viewmodel.ContactViewModel
import com.vamsi.stegapp.viewmodel.ContactViewModelFactory
import com.vamsi.stegapp.data.db.ContactEntity
import com.vamsi.stegapp.viewmodel.HomeUiState
import com.vamsi.stegapp.data.db.MessageEntity
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.filled.Message
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import com.vamsi.stegapp.ui.theme.StegAppTheme
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle

// ... imports added ...

// Data Class for Mock Chats
data class ChatPreview(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0,
    val imageUrl: String? = null
)

val initialMockChats = listOf(
    ChatPreview("1", "Sam Xavii", "Hey, did you decode the image?", "10:30 AM", 2, null),
    ChatPreview("2", "Project Alpha", "Sending the key now...", "Yesterday", 0, null),
    ChatPreview("3", "Design Team", "The new UI looks sick! 🔥", "Yesterday", 0, null),
    ChatPreview("4", "Mom", "Call me when you're free", "Monday", 0, null)
)

@Composable
fun HomeScreen(
    navController: NavController,
    isDark: Boolean,
    viewModel: ContactViewModel = viewModel(factory = ContactViewModelFactory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    
    HomeScreenContent(
        isDark = isDark,
        uiState = uiState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onChatClick = { chatName, imageUri, messageId -> 
            viewModel.clearUnreadCount(chatName)
            val encodedName = java.net.URLEncoder.encode(chatName, "UTF-8")
            val baseRoute = "chat/$encodedName"
            val params = mutableListOf<String>()
            if (imageUri != null) params.add("imageUri=$imageUri")
            if (messageId != null) params.add("messageId=$messageId")
            
            val route = if (params.isNotEmpty()) "$baseRoute?${params.joinToString("&")}" else baseRoute
            navController.navigate(route)
        },
        onAddContact = { name -> viewModel.addContact(name) },
        onDeleteContact = { contact -> viewModel.deleteContact(contact) }
    )
}

@Composable
fun HomeScreenContent(
    isDark: Boolean,
    uiState: HomeUiState,
    onSearchQueryChange: (String) -> Unit,
    onChatClick: (String, String?, String?) -> Unit,
    onAddContact: (String) -> Unit,
    onDeleteContact: (ContactEntity) -> Unit
) {
    // SAI Colors (Dynamic)
    val saiBackground = MaterialTheme.colorScheme.background
    val saiSurface = MaterialTheme.colorScheme.surfaceContainerHigh
    val saiTextPrimary = MaterialTheme.colorScheme.onSurface
    val saiTextSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    // UI States
    var contactToDelete by remember { mutableStateOf<ContactEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    // searchQuery moved to ViewModel
    
    // Camera States
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showShareDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && capturedImageUri != null) {
            showShareDialog = true
        }
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("Share Photo with...") },
            text = {
                Box(modifier = Modifier.height(300.dp)) {
                    LazyColumn {
                        items(uiState.contacts) { contact ->
                             Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showShareDialog = false
                                        // Navigate to chat with imageUri
                                        val encodedUri = java.net.URLEncoder.encode(capturedImageUri.toString(), "UTF-8")
                                        onChatClick(contact.name, encodedUri, null)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                             ) {
                                 Surface(shape = CircleShape, color = saiTextSecondary, modifier = Modifier.size(40.dp)) {
                                     Box(contentAlignment = Alignment.Center) { Text(contact.name.take(1), color = Color.White) }
                                 }
                                 Spacer(modifier = Modifier.width(16.dp))
                                 Text(contact.name, style = MaterialTheme.typography.bodyLarge, color = saiTextPrimary)
                             }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShareDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ... (Delete and Add Dialogs - kept pending, or assumed valid if unrelated)
    if (contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text(text = "Delete Chat?") },
            text = { Text(text = "Are you sure you want to delete the chat with ${contactToDelete?.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        contactToDelete?.let { onDeleteContact(it) }
                        contactToDelete = null
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(text = "New Chat") },
            text = { 
                OutlinedTextField(
                    value = newName, 
                    onValueChange = { newName = it },
                    label = { Text("Contact Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onAddContact(newName)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.Transparent.toArgb() // Or MaterialTheme.colorScheme.background.toArgb() if needed
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    
    // Locked Search Logic
    // val filteredContacts removed

    Surface(
        color = saiBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header (WhatsApp Style but Simple)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 50.dp, start = 20.dp, end = 10.dp, bottom = 20.dp), // Reduced end padding for icons
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chats",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = saiTextPrimary
                            )
                        )
                        
                        // Icons
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Camera
                            IconButton(
                                onClick = {
                                    val uri = createImageUri(context)
                                    capturedImageUri = uri
                                    cameraLauncher.launch(uri)
                                }
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Camera", tint = saiTextPrimary)
                            }
                            
                            // Search
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = if (showSearch) MaterialTheme.colorScheme.primary else saiTextPrimary)
                            }
                            
                            // Menu
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = saiTextPrimary)
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Profile") },
                                        onClick = { showMenu = false; android.widget.Toast.makeText(context, "Profile clicked", android.widget.Toast.LENGTH_SHORT).show() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        onClick = { showMenu = false; android.widget.Toast.makeText(context, "Settings clicked", android.widget.Toast.LENGTH_SHORT).show() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Free Up Space") }, // Clear Cache Logic
                                        onClick = { 
                                            showMenu = false
                                            try {
                                                context.cacheDir.deleteRecursively()
                                                android.widget.Toast.makeText(context, "Cache Cleared! 🧹", android.widget.Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Failed to clear cache", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Logout") },
                                        onClick = { 
                                            showMenu = false
                                            android.widget.Toast.makeText(context, "Logout clicked", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    
                    // Search Bar (Animated Visibility)
                    AnimatedVisibility(visible = showSearch, enter = expandVertically(), exit = shrinkVertically()) {
                        OutlinedTextField(
                            value = uiState.query,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            placeholder = { Text("Search...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = saiSurface,
                                unfocusedContainerColor = saiSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            shape = CircleShape
                        )
                    }
                }

                // Chat List
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    if (uiState.query.isNotBlank() && uiState.contacts.isEmpty() && uiState.messageResults.isEmpty()) {
                        item {
                            EmptySearchState(query = uiState.query)
                        }
                    } else {
                        // Contacts Section
                        if (uiState.contacts.isNotEmpty()) {
                            if (uiState.query.isNotBlank()) item { SectionHeader("Contacts") }
                            items(uiState.contacts) { contact ->
                                ChatListItem(
                                    contact = contact, 
                                    textColor = saiTextPrimary, 
                                    secondaryColor = saiTextSecondary, 
                                    surfaceColor = saiSurface,
                                    searchQuery = uiState.query,
                                    onClick = { onChatClick(contact.name, null, null) },
                                    onLongClick = { contactToDelete = contact }
                                )
                                Divider(
                                    modifier = Modifier.padding(start = 76.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        
                        // Messages Section
                        if (uiState.messageResults.isNotEmpty()) {
                            item { SectionHeader("Messages") }
                            items(uiState.messageResults) { msg -> 
                                MessageResultItem(
                                    message = msg,
                                    searchQuery = uiState.query,
                                    onClick = { onChatClick(msg.chatId, null, msg.id) } // Navigate to chat
                                )
                                Divider(
                                    modifier = Modifier.padding(start = 76.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Floating Action Button (Styled like Send Button)
            val fabColor = MaterialTheme.colorScheme.tertiaryContainer // Distinct color
            val fabContentColor = MaterialTheme.colorScheme.onTertiaryContainer
            
            Surface(
                shape = CircleShape,
                color = fabColor,
                shadowElevation = 0.dp,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = 28.dp) // Changed to 28dp to match Chat Screen (24+4)
                    .size(56.dp)
            ) {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat", tint = fabContentColor)
                }
            }
        }
    }
}

fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
         diff < 60000 -> "Now"
         diff < 3600000 -> "${diff / 60000}m"
         diff < 86400000 -> java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
         else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}

/*
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    StegAppTheme {
         // Preview disabled as it requires ViewModel now
    }
}
*/

fun createImageUri(context: Context): Uri {
    val directory = File(context.getExternalFilesDir("stego_images"), "camera_captures")
    if (!directory.exists()) {
        directory.mkdirs()
    }
    val file = File.createTempFile("captured_", ".jpg", directory)
    // authority must match AndroidManifest provider authority
    val authority = "${context.packageName}.provider"
    return FileProvider.getUriForFile(context, authority, file)
}

// Helper to bold matching query
@Composable
fun highlightText(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    if (query.isBlank()) return androidx.compose.ui.text.AnnotatedString(text)
    
    val startIndex = text.indexOf(query, ignoreCase = true)
    if (startIndex == -1) return androidx.compose.ui.text.AnnotatedString(text)
    
    val endIndex = startIndex + query.length
    
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text.substring(0, startIndex))
        withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(startIndex, endIndex))
        }
        append(text.substring(endIndex))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    contact: ContactEntity,
    textColor: Color,
    secondaryColor: Color,
    surfaceColor: Color,
    searchQuery: String = "",
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Pic with online indicator
        Box {
            Surface(
                shape = CircleShape,
                color = surfaceColor, // Placeholder color
                modifier = Modifier.size(56.dp),
                shadowElevation = 0.dp
            ) {
                 if (contact.profileImageUri != null) {
                    AsyncImage(
                        model = contact.profileImageUri,
                        contentDescription = contact.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                 } else {
                     Box(contentAlignment = Alignment.Center) {
                         Text(
                             text = contact.name.take(1),
                             style = MaterialTheme.typography.titleLarge,
                             fontWeight = FontWeight.Bold,
                             color = textColor
                         )
                     }
                 }
            }
            
            // Online indicator dot
            if (contact.isOnline) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiary, // Dynamic distinct color
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 0.dp, y = 0.dp) // Centered on the stroke (0.dp places corner at corner)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape) // Stroke matches background for cutout effect
                ) {}
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = highlightText(contact.name, searchQuery),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = textColor
                )
                Text(
                    text = formatTime(contact.lastMessageTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (contact.unreadCount > 0) MaterialTheme.colorScheme.primary else secondaryColor, // Blue if unread
                    fontWeight = if (contact.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = highlightText(contact.lastMessage ?: "", searchQuery),
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor,
                    maxLines = 1
                )
                if (contact.unreadCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary, // Monet Color
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = contact.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
fun EmptySearchState(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(64.dp).alpha(0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No results for \"$query\"",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun MessageResultItem(
    message: MessageEntity,
    searchQuery: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
         Surface(
             shape = CircleShape,
             color = MaterialTheme.colorScheme.surfaceContainerHigh,
             modifier = Modifier.size(48.dp)
         ) {
             Box(contentAlignment = Alignment.Center) {
                 Icon(Icons.Default.Message, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
             }
         }
         Spacer(modifier = Modifier.width(16.dp))
         Column(modifier = Modifier.weight(1f)) {
             Text(
                 text = if(message.isFromMe) "You • ${message.chatId}" else message.chatId, 
                 style = MaterialTheme.typography.labelMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant
             )
             Text(
                 text = highlightText(message.text ?: "Photo", searchQuery),
                 style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurface,
                 maxLines = 2
             )
         }
    }
}
