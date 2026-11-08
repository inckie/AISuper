package com.damn.aisuper.modal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Base sealed interface for modal requests presented over the UI.
 * Allows extending with future modal primitives (Alert, Prompt, BottomSheet, etc.).
 */
sealed interface ModalRequest {
    val id: String
}

/**
 * Confirmation modal primitive.
 * Awaitable and non-cancellable: requires the user to explicitly confirm or decline.
 */
data class ConfirmationRequest(
    override val id: String,
    val title: String,
    val message: String,
    val confirmText: String = "OK",
    val cancelText: String = "Отмена",
    val isDestructive: Boolean = false,
    val onResult: (Boolean) -> Unit
) : ModalRequest

/**
 * Interface for modal context / host state.
 */
interface ModalHostState {
    val activeModal: StateFlow<ModalRequest?>

    suspend fun showConfirmation(
        title: String,
        message: String,
        confirmText: String = "OK",
        cancelText: String = "Отмена",
        isDestructive: Boolean = false
    ): Boolean

    fun respondToActive(confirmed: Boolean): Boolean

    fun dismissCurrent()
}

/**
 * Default implementation of ModalHostState for managing modal dialogs.
 */
class DefaultModalHostState : ModalHostState {
    private val _activeModal = MutableStateFlow<ModalRequest?>(null)
    override val activeModal: StateFlow<ModalRequest?> = _activeModal.asStateFlow()

    override suspend fun showConfirmation(
        title: String,
        message: String,
        confirmText: String,
        cancelText: String,
        isDestructive: Boolean
    ): Boolean {
        // Dismiss any previous modal before showing a new one
        dismissCurrent()

        val deferred = CompletableDeferred<Boolean>()
        val reqId = "modal_confirm_" + (100000..999999).random()
        val request = ConfirmationRequest(
            id = reqId,
            title = title,
            message = message,
            confirmText = confirmText,
            cancelText = cancelText,
            isDestructive = isDestructive,
            onResult = { confirmed ->
                _activeModal.update { current ->
                    if (current?.id == reqId) null else current
                }
                if (!deferred.isCompleted) {
                    deferred.complete(confirmed)
                }
            }
        )
        _activeModal.value = request
        return deferred.await()
    }

    override fun respondToActive(confirmed: Boolean): Boolean {
        val current = _activeModal.value
        return if (current is ConfirmationRequest) {
            current.onResult(confirmed)
            true
        } else {
            false
        }
    }

    override fun dismissCurrent() {
        val current = _activeModal.value
        if (current is ConfirmationRequest) {
            current.onResult(false)
        } else {
            _activeModal.value = null
        }
    }
}
