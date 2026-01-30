package com.vamsi.stegapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
    val contacts by viewModel.contacts.collectAsState()
    
    HomeScreenContent(
        isDark = isDark,
        contacts = contacts,
        onChatClick = { chatName -> 
            viewModel.clearUnreadCount(chatName)
            navController.navigate("chat/$chatName") 
        },
        onAddContact = { name -> viewModel.addContact(name) },
        onDeleteContact = { contact -> viewModel.deleteContact(contact) }
    )
}

@Composable
fun HomeScreenContent(
    isDark: Boolean,
    contacts: List<ContactEntity>,
    onChatClick: (String) -> Unit,
    onAddContact: (String) -> Unit,
    onDeleteContact: (ContactEntity) -> Unit
) {
    // SAI Colors (Matching MainActivity)
    val saiBackground = if (isDark) Color(0xFF000000) else Color(0xFFF2F4F6)
    val saiSurface = if (isDark) Color(0xFF2C2C2E) else Color.White
    val saiTextPrimary = if (isDark) Color.White else Color(0xFF1C1C1E)
    val saiTextSecondary = if (isDark) Color.Gray else Color.Gray

    // UI States
    var contactToDelete by remember { mutableStateOf<ContactEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    
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
                        items(contacts) { contact ->
                             Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showShareDialog = false
                                        // Navigate to chat with imageUri
                                        val encodedUri = java.net.URLEncoder.encode(capturedImageUri.toString(), "UTF-8")
                                        onChatClick("${contact.name}?imageUri=$encodedUri")
                                        /* 
                                           Note: onChatClick in HomeScreen expects just 'chatName'. 
                                           But our updated HomeScreen lambda (lines 66-69) does:
                                           navController.navigate("chat/$chatName")
                                           So we need to pass a string that results in "chat/Name?imageUri=..."
                                           If we pass "Name?imageUri=...", then "chat/Name?imageUri=..." works!
                                        */
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
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }
    
    // Filtered Contacts Logic
    val filteredContacts = if (searchQuery.isBlank()) contacts else contacts.filter { 
        it.name.contains(searchQuery, ignoreCase = true) || 
        it.lastMessage?.contains(searchQuery, ignoreCase = true) == true
    }

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
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
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
                    items(filteredContacts) { contact -> // Use filtered list
                        ChatListItem(
                            contact = contact, 
                            textColor = saiTextPrimary, 
                            secondaryColor = saiTextSecondary, 
                            surfaceColor = saiSurface,
                            searchQuery = searchQuery,
                            onClick = { onChatClick(contact.name) },
                            onLongClick = { contactToDelete = contact }
                        )
                        Divider(
                            modifier = Modifier.padding(start = 76.dp),
                            thickness = 0.5.dp,
                            color = if (isDark) Color.DarkGray else Color.LightGray
                        )
                    }
                }
            }
            
            // Floating Action Button (Styled like Send Button)
            val fabColor = MaterialTheme.colorScheme.primaryContainer
            val fabContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            
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
                    .shadow(16.dp, CircleShape, spotColor = fabColor, ambientColor = fabColor, clip = false)
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
        // Profile Pic
        Surface(
            shape = CircleShape,
            color = surfaceColor, // Placeholder color
            modifier = Modifier.size(56.dp),
            shadowElevation = 1.dp
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

        Spacer(modifier = Modifier.width(16.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = highlightText(contact.name, searchQuery),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = textColor
                    )
                    if (contact.isOnline) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                }
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
