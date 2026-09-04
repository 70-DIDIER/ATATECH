package com.atatech.app

import android.graphics.Bitmap

interface Orchestrator {
    suspend fun runOcr(image: Bitmap): String
    suspend fun extractFields(ocrResult: String, userSpeech: String): String
    suspend fun verify(extractedFields: String): Boolean
}

class StubOrchestrator : Orchestrator {
    override suspend fun runOcr(image: Bitmap): String {
        // TODO: brancher le vrai moteur OCR
        return ""
    }

    override suspend fun extractFields(ocrResult: String, userSpeech: String): String {
        // TODO: brancher l'extraction reelle
        return ""
    }

    override suspend fun verify(extractedFields: String): Boolean {
        // TODO: brancher la verification reelle
        return true
    }
}
