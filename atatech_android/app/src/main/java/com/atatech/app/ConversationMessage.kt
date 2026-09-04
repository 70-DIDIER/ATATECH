package com.atatech.app

data class ConversationMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val contentType: MessageContentType = MessageContentType.TEXT,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole {
    USER,
    ASSISTANT
}

enum class MessageContentType {
    TEXT,
    AUDIO
}
