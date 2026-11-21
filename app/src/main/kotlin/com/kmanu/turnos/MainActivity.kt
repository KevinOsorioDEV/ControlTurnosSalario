package com.kmanu.turnos

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmanu.turnos.ui.AppViewModel
import com.kmanu.turnos.ui.HistoryScreen
import com.kmanu.turnos.ui.LiquidationScreen
import com.kmanu.turnos.ui.LockScreen
import com.kmanu.turnos.ui.OnboardingScreen
import com.kmanu.turnos.ui.SettingsScreen
import com.kmanu.turnos.ui.ShiftScreen
import com.kmanu.turnos.ui.canUseDeviceLock
import com.kmanu.turnos.ui.requestDeviceAuth
import com.kmanu.turnos.ui.theme.Motion
import com.kmanu.turnos.ui.theme.TurnosTheme

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    SHIFT("Turno", Icons.Outlined.Schedule, Icons.Rounded.Schedule),
    HISTORY("Historial", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    LIQUIDATION("Liquidación", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
    SETTINGS("Ajustes", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

class MainActivity : FragmentActivity() {
    private val viewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TurnosTheme {
                TurnosApp(viewModel, this)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.onAppLeft()
    }
}

@Composable
private fun TurnosApp(viewModel: AppViewModel, activity: FragmentActivity) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val current = access
    if (current == null) {
        // Sin datos todavía: no se muestra nada que el bloqueo debería tapar.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        return
    }
    Crossfade(current.configured, animationSpec = Motion.standard(), label = "primer uso") { configured ->
        if (configured) {
            LockedApp(viewModel, activity, current.lockEnabled)
        } else {
            OnboardingScreen(viewModel::completeSetup)
        }
    }
}

@Composable
private fun LockedApp(viewModel: AppViewModel, activity: FragmentActivity, lockEnabled: Boolean) {
    val locked = lockEnabled && viewModel.locked

    LaunchedEffect(lockEnabled) {
        // Con el bloqueo activo, la vista previa de apps recientes no muestra tus datos.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) activity.setRecentsScreenshotEnabled(!lockEnabled)
    }
    BackHandler(enabled = locked) { activity.moveTaskToBack(true) }

    // La app sigue compuesta debajo para no perder lo que estabas escribiendo al bloquearse.
    Box(Modifier.fillMaxSize()) {
        MainShell(
            viewModel = viewModel,
            activity = activity,
            lockEnabled = lockEnabled,
            modifier = if (locked) Modifier.clearAndSetSemantics {} else Modifier,
        )
        AnimatedVisibility(visible = locked, enter = EnterTransition.None, exit = fadeOut(Motion.standard(Motion.SHORT))) {
            LockScreen(viewModel, activity)
        }
    }
}

@Composable
private fun MainShell(
    viewModel: AppViewModel,
    activity: FragmentActivity,
    lockEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.SHIFT) }
    var editorOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    val onLockChange: (Boolean) -> Unit = { enabled ->
        when {
            activity.canUseDeviceLock() ->
                activity.requestDeviceAuth(viewModel, "Confirma que eres tú") { success ->
                    if (success) viewModel.setLockEnabled(enabled)
                }
            enabled -> viewModel.showMessage("Primero configura una huella, PIN o patrón en tu teléfono.")
            else -> viewModel.setLockEnabled(false)
        }
    }

    Scaffold(
        modifier = modifier,
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
                        Tab.SHIFT -> ShiftScreen(current, viewModel, onOpenLiquidation = { tab = Tab.LIQUIDATION })
                        Tab.HISTORY -> HistoryScreen(current, viewModel, onEditorOpenChange = { editorOpen = it })
                        Tab.LIQUIDATION -> LiquidationScreen(current, viewModel)
                        Tab.SETTINGS -> SettingsScreen(current.settings, viewModel, lockEnabled, onLockChange)
                    }
                }
            }
        }
    }
}
