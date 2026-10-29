package com.example.somnus.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.somnus.data.preferences.ThemeMode
import com.example.somnus.ui.components.SomnusConfirmDialog
import com.example.somnus.ui.components.SomnusTopBar

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.deletionSuccess) {
        if (state.deletionSuccess) {
            snackbarHostState.showSnackbar("All sleep records have been deleted from this device.")
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            SomnusTopBar(
                title = "Settings",
                subtitle = "Privacy & Diagnostics",
                onBackClick = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Privacy & Local-Only Architecture Policy
            PrivacyPolicyCard()

            // Diagnostics & App State
            DiagnosticsCard(state = state)

            // Appearance / Theme Mode Preference
            ThemePreferenceCard(
                currentTheme = state.userPreferences.themeMode,
                onSelectTheme = { viewModel.setThemeMode(it) }
            )

            // Online Food Lookup Preference (DataStore, default OFF)
            OnlineFoodLookupCard(
                enabled = state.userPreferences.onlineFoodLookupEnabled,
                onToggle = { viewModel.setOnlineFoodLookupEnabled(it) }
            )

            // AI Assistant Groq Cloud API Preference & Connection Test
            GroqAssistantApiKeyCard(
                currentApiKey = state.userPreferences.groqApiKey,
                consentGranted = state.userPreferences.groqPrivacyConsentGranted,
                isTesting = state.isTestingGroq,
                testResult = state.groqTestResult,
                testSuccess = state.groqTestSuccess,
                onSaveKey = { viewModel.setGroqApiKey(it) },
                onTestConnection = { viewModel.testGroqConnection(it) },
                onConsentGranted = { viewModel.setGroqPrivacyConsent(true) }
            )

            // Dangerous Data Management (Delete All Data)
            DataManagementCard(
                isDeleting = state.isDeleting,
                onDeleteAllClick = { showDeleteAllConfirm = true }
            )

            // About Section (Plain text Open Food Facts & ODbL attribution)
            AboutCard()

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Delete All Confirmation Dialog
        if (showDeleteAllConfirm) {
            SomnusConfirmDialog(
                title = "Delete all sleep records?",
                message = "This will permanently remove ${state.sessionCount} sleep sessions, ${state.solRecordCount} SOL records, and ${state.psqiAssessmentCount} PSQI assessments from your local database. There is no cloud backup to restore from. Are you sure?",
                confirmText = "Delete Everything",
                dismissText = "Cancel",
                isDestructive = true,
                onConfirm = {
                    showDeleteAllConfirm = false
                    viewModel.deleteAllData()
                },
                onDismiss = { showDeleteAllConfirm = false }
            )
        }
    }
}

@Composable
private fun PrivacyPolicyCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Local-Only Data Architecture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "• 100% on-device: Somnus has no server backend, accounts, or telemetry.\n" +
                        "• Zero ad or analytics SDKs: No tracking code of any kind is included.\n" +
                        "• No network transmission: Your bedtime, SOL records, and PSQI answers never leave this physical hardware.\n" +
                        "• Sourced separation: Wearable estimates are stored alongside, never overwriting, self-reported metrics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(state: SettingsUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "System Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            DiagnosticRow("Application Version", state.appVersion)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            DiagnosticRow("Database Engine", "SQLite via Android Room")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            DiagnosticRow("Database Schema", "Version ${state.databaseVersion} (Explicit Migrations)")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            DiagnosticRow("Sleep Sessions Stored", "${state.sessionCount} records")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            DiagnosticRow("SOL Records Stored", "${state.solRecordCount} records")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            DiagnosticRow("PSQI Assessments Stored", "${state.psqiAssessmentCount} assessments")
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ThemePreferenceCard(
    currentTheme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DarkMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentTheme == ThemeMode.DARK,
                    onClick = { onSelectTheme(ThemeMode.DARK) },
                    label = { Text("Dark (Default)") }
                )
                FilterChip(
                    selected = currentTheme == ThemeMode.SYSTEM,
                    onClick = { onSelectTheme(ThemeMode.SYSTEM) },
                    label = { Text("System") }
                )
                FilterChip(
                    selected = currentTheme == ThemeMode.LIGHT,
                    onClick = { onSelectTheme(ThemeMode.LIGHT) },
                    label = { Text("Light") }
                )
            }
        }
    }
}

@Composable
private fun DataManagementCard(
    isDeleting: Boolean,
    onDeleteAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Data Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permanently remove all local sleep logs, SOL records, and PSQI assessments from this device. Once deleted, this data cannot be recovered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onDeleteAllClick,
                enabled = !isDeleting,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isDeleting) "Deleting..." else "Delete All Sleep Data")
            }
        }
    }
}

@Composable
private fun OnlineFoodLookupCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Online food lookup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "When enabled, unrecognized barcodes are queried against Open Food Facts (openfoodfacts.org). Only the barcode number is sent over the network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Product data from Open Food Facts (openfoodfacts.org), made available under the Open Database License (ODbL).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}

@Composable
private fun GroqAssistantApiKeyCard(
    currentApiKey: String,
    consentGranted: Boolean,
    isTesting: Boolean,
    testResult: String?,
    testSuccess: Boolean?,
    onSaveKey: (String) -> Unit,
    onTestConnection: (String) -> Unit,
    onConsentGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    var keyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    if (showConsentDialog) {
        AlertDialog(
            onDismissRequest = {
                showConsentDialog = false
                pendingAction = null
                keyInput = ""
            },
            title = {
                Text(text = "Enable Cloud AI?")
            },
            text = {
                Text(
                    text = "By adding a Groq API key, you allow Somnus to send your health summaries and questions to Groq's servers for processing. Somnus normally keeps all data on your device. Do not proceed if you do not want your data leaving this device."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConsentDialog = false
                        onConsentGranted()
                        pendingAction?.invoke()
                        pendingAction = null
                    }
                ) {
                    Text("I Understand, Enable Groq")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConsentDialog = false
                        pendingAction = null
                        keyInput = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI Assistant (Groq Cloud API)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Provide a Groq API key for high-speed cloud neural inference with automatic model selection and rate-limit fallback. If no key is set or offline, Somnus falls back to deterministic on-device answers. Keys are stored locally on-device and never committed to source.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("Groq API Key (gsk_...)") },
                placeholder = { Text("gsk_...") },
                singleLine = true,
                visualTransformation = if (isKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                        Icon(
                            imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isKeyVisible) "Hide key" else "Show key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val trimmed = keyInput.trim()
                        if (consentGranted) {
                            onSaveKey(trimmed)
                        } else {
                            pendingAction = { onSaveKey(trimmed) }
                            showConsentDialog = true
                        }
                    },
                    enabled = keyInput.trim() != currentApiKey,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save Key")
                }

                OutlinedButton(
                    onClick = {
                        val targetKey = keyInput.trim().ifBlank { currentApiKey }
                        if (consentGranted) {
                            onTestConnection(targetKey)
                        } else {
                            pendingAction = { onTestConnection(targetKey) }
                            showConsentDialog = true
                        }
                    },
                    enabled = !isTesting && (keyInput.isNotBlank() || currentApiKey.isNotBlank()),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Testing...")
                    } else {
                        Text("Test Connection")
                    }
                }
            }

            if (testResult != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val isSuccess = testSuccess == true
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSuccess) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = testResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSuccess) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
