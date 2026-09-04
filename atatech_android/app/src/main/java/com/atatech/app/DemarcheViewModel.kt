package com.atatech.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.squareup.moshi.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class DemarcheViewModel : ViewModel() {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val etatAdapter = moshi.adapter(EtatDemarche::class.java)

    private var etat: EtatDemarche? = null

    private val _messages = MutableStateFlow<List<DemarcheMessage>>(emptyList())
    val messages: StateFlow<List<DemarcheMessage>> = _messages.asStateFlow()

    private val _attend = MutableStateFlow<Attend?>(null)
    val attend: StateFlow<Attend?> = _attend.asStateFlow()

    private val _isFini = MutableStateFlow(false)
    val isFini: StateFlow<Boolean> = _isFini.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private fun applyResponse(response: DemarchesResponse) {
        etat = response.etat
        _messages.update { it + response.messages }
        _attend.value = response.messages.lastOrNull()?.attend
        _isFini.value = response.fini
    }

    fun startSession(context: Context) {
        if (_messages.value.isNotEmpty() || _isLoading.value) return
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val response = ApiClient.create(context).session(SessionRequest())
                applyResponse(response)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Impossible de joindre le serveur"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendText(context: Context, texte: String) {
        if (texte.isBlank() || _isLoading.value) return
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val response = ApiClient.create(context).message(
                    MessageRequest(texte = texte, etat = etat)
                )
                applyResponse(response)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Impossible de joindre le serveur"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendPhoto(context: Context, photoFile: File) {
        if (_isLoading.value) return
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val etatJson = etat?.let { etatAdapter.toJson(it) }
                val etatPart = etatJson?.toRequestBody("text/plain".toMediaTypeOrNull())
                val photoPart = MultipartBody.Part.createFormData(
                    "photo",
                    photoFile.name,
                    photoFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                )
                val response = ApiClient.create(context).photo(
                    sessionId = null,
                    etat = etatPart,
                    photo = photoPart
                )
                applyResponse(response)
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Impossible de joindre le serveur"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
