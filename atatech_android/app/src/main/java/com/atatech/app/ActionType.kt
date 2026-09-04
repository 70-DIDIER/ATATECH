package com.atatech.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.graphics.vector.ImageVector

sealed class ActionType(val label: String, val icon: ImageVector) {
    object ScanningDocument : ActionType("Scan de la pièce...", Icons.Default.DocumentScanner)
    object ExtractingInfo : ActionType("Extraction des informations...", Icons.Default.TextFields)
    object VerifyingData : ActionType("Vérification du dossier...", Icons.Default.FactCheck)
    object ProcessingPayment : ActionType("Traitement du paiement...", Icons.Default.Payment)
    object TranslatingPrescription : ActionType("Traduction de l'ordonnance...", Icons.Default.Translate)
    object SendingAlert : ActionType("Envoi de l'alerte d'urgence...", Icons.Default.Emergency)
    data class Custom(val text: String) : ActionType(text, Icons.Default.Autorenew)
}
