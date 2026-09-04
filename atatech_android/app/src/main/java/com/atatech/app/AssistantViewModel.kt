package com.atatech.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantViewModel : ViewModel() {

    private val _state = MutableStateFlow(AssistantState())
    val assistantState: StateFlow<AssistantState> = _state.asStateFlow()

    fun onInputChange(text: String) {
        _state.update { it.copy(currentInput = text) }
    }

    fun sendMessage() {
        val text = _state.value.currentInput.trim()
        if (text.isEmpty()) return

        val userMessage = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = text
        )

        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                currentInput = "",
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            // TODO: brancher la logique de reponse de l'assistant
        }
    }

    fun clearConversation() {
        _state.update { AssistantState() }
    }
}
