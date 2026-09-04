package com.atatech.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AssistantStatusArea(state: AssistantState) {
    AnimatedContent(targetState = state, label = "status") { s ->
        when (s) {
            is AssistantState.Thinking -> ThinkingIndicator()
            is AssistantState.ActionInProgress -> ActionInProgressIndicator(s.action)
            is AssistantState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            else -> Spacer(modifier = Modifier.height(1.dp))
        }
    }
}
