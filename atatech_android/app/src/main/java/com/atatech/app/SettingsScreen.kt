package com.atatech.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
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
import com.atatech.app.api.ApiClientProvider
import com.atatech.app.api.ApiConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var language by remember { mutableStateOf(AppPreferences.getLanguage(context)) }
    var baseUrl by remember { mutableStateOf(ApiConfig.getBaseUrl(context)) }
    var apiKey by remember { mutableStateOf(ApiConfig.getApiKey(context)) }
    var testEnCours by remember { mutableStateOf(false) }
    var resultatTest by remember { mutableStateOf<String?>(null) }
    var testReussi by remember { mutableStateOf(false) }

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
                .verticalScroll(rememberScrollState())
        ) {
            Text("Connexion API", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    ApiConfig.setBaseUrl(context, it)
                    resultatTest = null
                },
                label = { Text("URL de base (ex: http://192.168.1.24:5000)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    ApiConfig.setApiKey(context, it)
                    resultatTest = null
                },
                label = { Text("Clé API (X-Api-Cle)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    enabled = !testEnCours,
                    onClick = {
                        testEnCours = true
                        resultatTest = null
                        scope.launch {
                            try {
                                val reponse = ApiClientProvider.getApi(context).ping()
                                if (reponse.isSuccessful && reponse.body() != null) {
                                    val corps = reponse.body()!!
                                    testReussi = true
                                    resultatTest = "Connecté — ${corps.service} v${corps.version}" +
                                        if (corps.cleRequise) " (clé requise)" else " (ouverte)"
                                } else {
                                    testReussi = false
                                    resultatTest = "Erreur ${reponse.code()}"
                                }
                            } catch (e: Exception) {
                                testReussi = false
                                resultatTest = "Échec — ${e.message ?: "réseau injoignable"}"
                            } finally {
                                testEnCours = false
                            }
                        }
                    }
                ) {
                    Text(if (testEnCours) "Test en cours…" else "Tester la connexion")
                }

                if (testEnCours) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(20.dp)
                    )
                }
            }

            resultatTest?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 8.dp),
                    color = if (testReussi) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

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
