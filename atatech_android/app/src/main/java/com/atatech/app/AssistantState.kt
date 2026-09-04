package com.atatech.app

data class AssistantState(
    val messages: List<ConversationMessage> = emptyList(),
    val currentInput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
