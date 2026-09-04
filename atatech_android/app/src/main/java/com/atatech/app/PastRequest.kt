package com.atatech.app

data class PastRequest(
    val id: String,
    val title: String,
    val status: RequestStatus,
    val date: String
)

enum class RequestStatus {
    EN_COURS,
    VALIDEE,
    REJETEE
}
