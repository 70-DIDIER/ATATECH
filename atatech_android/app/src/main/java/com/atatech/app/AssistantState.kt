package com.atatech.app

sealed class AssistantState {
    object Idle : AssistantState()
    object Thinking : AssistantState()
    data class ActionInProgress(val action: ActionType) : AssistantState()
    data class Result(val message: String) : AssistantState()
    data class Error(val message: String) : AssistantState()
}
