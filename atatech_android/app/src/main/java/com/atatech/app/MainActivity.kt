package com.atatech.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NyeGbeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainAssistantScreen(
                                onOpenHistory = { navController.navigate("history") },
                                onOpenSettings = { navController.navigate("settings") },
                                onOpenAppel = { navController.navigate("appel") }
                            )
                        }
                        composable("history") {
                            HistoryScreen(onBack = { navController.popBackStack() })
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable("appel") {
                            AppelScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
