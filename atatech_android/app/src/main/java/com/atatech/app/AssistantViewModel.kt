package com.atatech.app

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantViewModel : ViewModel() {

    private val orchestrator: Orchestrator = StubOrchestrator()

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _messages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val messages: StateFlow<List<ConversationMessage>> = _messages.asStateFlow()

    private val _currentInput = MutableStateFlow("")
    val currentInput: StateFlow<String> = _currentInput.asStateFlow()

    private val _backgroundListeningEnabled = MutableStateFlow(false)
    val backgroundListeningEnabled: StateFlow<Boolean> = _backgroundListeningEnabled.asStateFlow()

    fun toggleBackgroundListening(enabled: Boolean) {
        // TODO: brancher un vrai service d'ecoute en arriere-plan (mot d'activation)
        _backgroundListeningEnabled.value = enabled
    }

    fun onInputChange(text: String) {
        _currentInput.value = text
    }

    fun sendMessage() {
        val text = _currentInput.value.trim()
        if (text.isEmpty()) return

        val userMessage = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text
        )

        _messages.update { it + userMessage }
        _currentInput.value = ""
        _assistantState.value = AssistantState.Thinking

        viewModelScope.launch {
            // TODO: brancher la logique de reponse de l'assistant
        }
    }

    fun sendAudioMessage(audioFilePath: String) {
        val userMessage = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = audioFilePath,
            contentType = MessageContentType.AUDIO
        )

        _messages.update { it + userMessage }
        _assistantState.value = AssistantState.Thinking

        viewModelScope.launch {
            // TODO: brancher le traitement reel de la note vocale (transcription optionnelle, envoi au pipeline)
        }
    }

    fun startDocumentScan() {
        // TODO: ouvrir la camera et lancer processNationalityRequest avec l'image capturee
        _assistantState.value = AssistantState.ActionInProgress(ActionType.ScanningDocument)
    }

    fun sendSosAlert() {
        // TODO: recuperer la position et declencher l'alerte reelle
        _assistantState.value = AssistantState.ActionInProgress(ActionType.SendingAlert)
    }

    fun processNationalityRequest(scannedImage: Bitmap, userSpeech: String) {
        viewModelScope.launch {
            _assistantState.value = AssistantState.ActionInProgress(ActionType.ScanningDocument)
            delay(1500)
            val ocrResult = orchestrator.runOcr(scannedImage)

            _assistantState.value = AssistantState.ActionInProgress(ActionType.ExtractingInfo)
            delay(1500)
            val extractedFields = orchestrator.extractFields(ocrResult, userSpeech)

            _assistantState.value = AssistantState.ActionInProgress(ActionType.VerifyingData)
            delay(1500)
            orchestrator.verify(extractedFields)

            _assistantState.value = AssistantState.Result("Dossier vérifié, prêt à soumettre.")
        }
    }

    fun clearConversation() {
        _messages.value = emptyList()
        _currentInput.value = ""
        _assistantState.value = AssistantState.Idle
    }
}
