package com.vamsi.stegapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.vamsi.stegapp.utils.UserPrefs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController, isDark: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    var username by remember { mutableStateOf("") }
    
    // Permission Launcher
    val permissionsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        UserPrefs.setPermissionsRequested(context, true)
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            android.widget.Toast.makeText(context, "Some permissions were denied. App features may be limited.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    fun requestPermissions() {
        if (!UserPrefs.arePermissionsRequested(context)) {
            val permissionsToRequest = buildList {
                add(android.Manifest.permission.CAMERA)
                add(android.Manifest.permission.READ_MEDIA_IMAGES)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    add(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
        }
    }
    
    val backgroundColor = if (isDark) Color(0xFF000000) else Color(0xFFF2F4F6)
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color.White
    val textColor = if (isDark) Color.White else Color(0xFF1C1C1E)


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = cardColor),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    "Welcome to StegApp",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = textColor
                )
                
                Text(
                    "Choose a unique username to start chatting securely.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.LightGray,
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    // ... (rest of UI)

                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(
                        onClick = {
                            if (username.isNotBlank()) {
                                isLoading = true
                                scope.launch {
                                    try {
                                        val response = com.vamsi.stegapp.network.NetworkModule.api.register(com.vamsi.stegapp.network.UserRequest(username.trim()))
                                        if (response.isSuccessful) {
                                            UserPrefs.saveUsername(context, username.trim())
                                            // Start Background Service
                                            val serviceIntent = android.content.Intent(context, com.vamsi.stegapp.service.SocketService::class.java)
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                context.startForegroundService(serviceIntent)
                                            } else {
                                                context.startService(serviceIntent)
                                            }
                                            requestPermissions()
                                            navController.navigate("home") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        } else {
                                            val errorBody = response.errorBody()?.string() ?: ""
                                            if (errorBody.contains("Username already exists")) {
                                                // User exists, log them in!
                                                UserPrefs.saveUsername(context, username.trim())
                                                // Start Background Service
                                                val serviceIntent = android.content.Intent(context, com.vamsi.stegapp.service.SocketService::class.java)
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                    context.startForegroundService(serviceIntent)
                                                } else {
                                                    context.startService(serviceIntent)
                                                }
                                                requestPermissions()
                                                navController.navigate("home") {
                                                    popUpTo("login") { inclusive = true }
                                                }
                                            } else {
                                                isLoading = false
                                                android.widget.Toast.makeText(context, "Error: $errorBody", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isLoading = false
                                        android.widget.Toast.makeText(context, "Connection Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Enter App", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
