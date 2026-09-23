package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    initialGeminiKey: String,
    initialModel: String,
    initialSupabaseUrl: String,
    initialSupabaseKey: String,
    onDismiss: () -> Unit,
    onTestDatabase: ((url: String, key: String, onResult: (Boolean, String) -> Unit) -> Unit)? = null,
    onSave: (geminiKey: String, model: String, supabaseUrl: String, supabaseKey: String) -> Unit
) {
    var geminiKey by remember { mutableStateOf(initialGeminiKey) }
    var selectedModel by remember { mutableStateOf(initialModel) }
    var supabaseUrl by remember { mutableStateOf(initialSupabaseUrl) }
    var supabaseKey by remember { mutableStateOf(initialSupabaseKey) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val models = listOf("gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-pro")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Studio & Database Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "AI Intelligence Engine",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Gemini API Key
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("Enter your Gemini API key") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = "Key") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_gemini_key_input")
                )

                // Model Selection
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("AI Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        models.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    selectedModel = model
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Supabase Cloud Database",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = supabaseUrl,
                    onValueChange = { 
                        supabaseUrl = it
                        testResultStatus = null
                    },
                    label = { Text("Supabase Project URL") },
                    placeholder = { Text("https://xyzcompany.supabase.co") },
                    leadingIcon = { Icon(Icons.Default.Storage, contentDescription = "DB") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_supabase_url_input")
                )

                OutlinedTextField(
                    value = supabaseKey,
                    onValueChange = { 
                        supabaseKey = it
                        testResultStatus = null
                    },
                    label = { Text("Supabase Anon Key") },
                    placeholder = { Text("eyJhbGciOi...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_supabase_key_input")
                )

                if (onTestDatabase != null) {
                    OutlinedButton(
                        onClick = {
                            isTestingConnection = true
                            testResultStatus = null
                            onTestDatabase(supabaseUrl, supabaseKey) { success, msg ->
                                isTestingConnection = false
                                testResultStatus = Pair(success, msg)
                            }
                        },
                        enabled = !isTestingConnection && supabaseUrl.isNotBlank() && supabaseKey.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_database_connection_btn")
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Connection...")
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Database Connection")
                        }
                    }

                    testResultStatus?.let { (success, msg) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (success) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (success) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    fontSize = 12.sp,
                                    color = if (success) Color(0xFF10B981) else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(geminiKey, selectedModel, supabaseUrl, supabaseKey)
                },
                modifier = Modifier.testTag("settings_save_btn")
            ) {
                Text("Save Configuration")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
