package com.atatech.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private sealed class ConnectionTestState {
    object Idle : ConnectionTestState()
    object Testing : ConnectionTestState()
    data class Success(val message: String) : ConnectionTestState()
    data class Failure(val message: String) : ConnectionTestState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var language by remember { mutableStateOf(AppPreferences.getLanguage(context)) }
    var baseUrl by remember { mutableStateOf(ApiPreferences.getBaseUrl(context)) }
    var apiKey by remember { mutableStateOf(ApiPreferences.getApiKey(context)) }
    var connectionTestState by remember { mutableStateOf<ConnectionTestState>(ConnectionTestState.Idle) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Langue", style = MaterialTheme.typography.titleMedium)
            AppLanguage.entries.forEach { option ->
                val selected = language == option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = selected, onClick = {
                            language = option
                            AppPreferences.setLanguage(context, option)
                        })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = {
                            language = option
                            AppPreferences.setLanguage(context, option)
                        }
                    )
                    Text(option.label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Connexion au serveur", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    ApiPreferences.setBaseUrl(context, it)
                    connectionTestState = ConnectionTestState.Idle
                },
                label = { Text("URL de base") },
                placeholder = { Text("http://192.168.1.24:5000") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    ApiPreferences.setApiKey(context, it)
                    connectionTestState = ConnectionTestState.Idle
                },
                label = { Text("Clé API (X-Api-Cle)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Button(
                onClick = {
                    connectionTestState = ConnectionTestState.Testing
                    scope.launch {
                        connectionTestState = try {
                            val response = ApiClient.create(context).ping()
                            ConnectionTestState.Success(
                                "Connecté — ${response.service} v${response.version}"
                            )
                        } catch (e: Exception) {
                            ConnectionTestState.Failure(
                                e.message ?: "Échec de connexion"
                            )
                        }
                    }
                },
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text("Tester la connexion")
            }

            when (val state = connectionTestState) {
                is ConnectionTestState.Testing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text("Test en cours…")
                }
                is ConnectionTestState.Success -> Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                is ConnectionTestState.Failure -> Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
                ConnectionTestState.Idle -> {}
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Compte", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Gestion du compte — à venir",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text("À propos", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "PapIA — assistant administratif accessible pour le Togo. Version 1.0",
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
