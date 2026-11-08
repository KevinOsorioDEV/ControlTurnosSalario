package com.kmanu.turnos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmanu.turnos.ui.AppViewModel
import com.kmanu.turnos.ui.HistoryScreen
import com.kmanu.turnos.ui.LiquidationScreen
import com.kmanu.turnos.ui.SettingsScreen
import com.kmanu.turnos.ui.ShiftScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    SHIFT("Turno", Icons.Filled.AccessTime),
    HISTORY("Historial", Icons.Filled.History),
    LIQUIDATION("Liquidación", Icons.Filled.Payments),
    SETTINGS("Ajustes", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                TurnosApp()
            }
        }
    }
}

private val LightColors = lightColorScheme(primary = Color(0xFF1F5FA8))
private val DarkColors = darkColorScheme(primary = Color(0xFFA6C8FF))

@Composable
private fun TurnosApp(viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.SHIFT) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        val current = state
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (current == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                when (tab) {
                    Tab.SHIFT -> ShiftScreen(current, viewModel)
                    Tab.HISTORY -> HistoryScreen(current, viewModel)
                    Tab.LIQUIDATION -> LiquidationScreen(current, viewModel)
                    Tab.SETTINGS -> SettingsScreen(current.settings, viewModel::saveSettings)
                }
            }
        }
    }
}
