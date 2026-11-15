package com.kmanu.turnos.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kmanu.turnos.ui.theme.Sizes
import com.kmanu.turnos.ui.theme.Spacing
import com.kmanu.turnos.ui.theme.TurnosTheme

/** Huella, rostro, PIN, patrón o contraseña: lo que el teléfono tenga configurado. */
private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

fun FragmentActivity.canUseDeviceLock(): Boolean =
    BiometricManager.from(this).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/** Pide autenticación con el bloqueo del teléfono. La app nunca ve ni guarda la huella o el PIN. */
fun FragmentActivity.requestDeviceAuth(viewModel: AppViewModel, title: String, onResult: (Boolean) -> Unit) {
    if (viewModel.authInProgress) return
    viewModel.authInProgress = true
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.authInProgress = false
                onResult(true)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                viewModel.authInProgress = false
                onResult(false)
            }
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build(),
    )
}

@Composable
fun LockScreen(viewModel: AppViewModel, activity: FragmentActivity) {
    val authenticate = {
        // Si la persona quitó el bloqueo del teléfono, no hay cómo autenticar: no la dejamos por fuera.
        if (activity.canUseDeviceLock()) {
            activity.requestDeviceAuth(viewModel, "Desbloquea Turnos") { success -> if (success) viewModel.unlock() }
        } else {
            viewModel.unlock()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!viewModel.lockPrompted) {
                viewModel.lockPrompted = true
                authenticate()
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(Spacing.md)) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.ms, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val style = TurnosTheme.colors.active
                Surface(shape = CircleShape, color = style.container, contentColor = style.content) {
                    Box(Modifier.size(Sizes.lockBadge), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, Modifier.size(Sizes.lockIcon))
                    }
                }
                Text("Turnos está bloqueada", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Usa tu huella o el PIN de tu teléfono para entrar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            PrimaryAction("Desbloquear", onClick = authenticate)
        }
    }
}
