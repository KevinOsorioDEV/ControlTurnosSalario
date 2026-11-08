package com.kmanu.turnos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kmanu.turnos.ui.AppViewModel
import com.kmanu.turnos.ui.HistoryScreen
import com.kmanu.turnos.ui.LiquidationScreen
import com.kmanu.turnos.ui.SettingsScreen
import com.kmanu.turnos.ui.ShiftScreen
import com.kmanu.turnos.ui.theme.Motion
import com.kmanu.turnos.ui.theme.TurnosTheme

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    SHIFT("Turno", Icons.Outlined.Schedule, Icons.Rounded.Schedule),
    HISTORY("Historial", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    LIQUIDATION("Liquidación", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
    SETTINGS("Ajustes", Icons.Rounded.Tune, Icons.Rounded.Tune),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TurnosTheme {
                TurnosApp()
            }
        }
    }
}

@Composable
private fun TurnosApp(viewModel: AppViewModel = viewModel(factory = AppViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.SHIFT) }
    var editorOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            // El editor ocupa la pantalla: sin barra no se puede salir de él perdiendo los cambios.
            if (!editorOpen) {
                ShortNavigationBar {
                    Tab.entries.forEach { item ->
                        ShortNavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(if (tab == item) item.selectedIcon else item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        val current = state
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (current == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(Motion.standard()) { direction * it / 4 } + fadeIn(Motion.standard()))
                            .togetherWith(
                                slideOutHorizontally(Motion.standard()) { -direction * it / 4 } + fadeOut(Motion.standard(Motion.SHORT)),
                            )
                    },
                    label = "pestañas",
                ) { selected ->
                    when (selected) {
                        Tab.SHIFT -> ShiftScreen(current, viewModel)
                        Tab.HISTORY -> HistoryScreen(current, viewModel, onEditorOpenChange = { editorOpen = it })
                        Tab.LIQUIDATION -> LiquidationScreen(current, viewModel)
                        Tab.SETTINGS -> SettingsScreen(current.settings, viewModel::saveSettings)
                    }
                }
            }
        }
    }
}
