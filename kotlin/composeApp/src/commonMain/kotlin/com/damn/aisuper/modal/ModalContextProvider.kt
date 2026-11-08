package com.damn.aisuper.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

val LocalModalHost = compositionLocalOf<ModalHostState> {
    error("No ModalHostState provided. Make sure to wrap your UI in ModalContextProvider.")
}

/**
 * Modal context provider that hosts dialogs, alert boxes, and future modal primitives
 * completely decoupled from widget rendering composables.
 */
@Composable
fun ModalContextProvider(
    modalHost: ModalHostState,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalModalHost provides modalHost) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            val activeModal by modalHost.activeModal.collectAsState()
            when (val modal = activeModal) {
                is ConfirmationRequest -> {
                    ConfirmationModal(request = modal)
                }
                // Future modal primitives (e.g. AlertRequest, PromptRequest) can be added here
                null -> {}
            }
        }
    }
}

/**
 * Confirmation modal primitive.
 * Awaitable and non-cancellable (dismissOnClickOutside = false, dismissOnBackPress = false).
 */
@Composable
private fun ConfirmationModal(request: ConfirmationRequest) {
    val confirmColors = if (request.isDestructive) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        )
    } else {
        ButtonDefaults.buttonColors()
    }

    AlertDialog(
        onDismissRequest = {
            // Non-cancellable: user must explicitly choose Confirm or Cancel
        },
        title = {
            Text(text = request.title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Text(text = request.message, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            Button(
                onClick = { request.onResult(true) },
                colors = confirmColors
            ) {
                Text(text = request.confirmText)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { request.onResult(false) }
            ) {
                Text(text = request.cancelText)
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}
