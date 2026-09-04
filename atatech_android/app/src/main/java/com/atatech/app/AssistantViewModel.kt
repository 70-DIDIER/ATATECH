package com.atatech.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atatech.app.api.ApiClientProvider
import com.atatech.app.api.MessageRequest
import com.atatech.app.api.MoshiProvider
import com.atatech.app.api.NyeGbeApi
import com.atatech.app.api.OuvrirSessionRequest
import com.atatech.app.api.ReponseDemarches
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
import retrofit2.Response

class AssistantViewModel : ViewModel() {

    private val _state = MutableStateFlow(AssistantState())
    val assistantState: StateFlow<AssistantState> = _state.asStateFlow()

    /** Ouvre la conversation — à appeler une fois au premier affichage de l'écran. */
    fun demarrer(context: Context) {
        if (_state.value.demarre) return
        _state.update { it.copy(demarre = true, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            appliquerReponse(context) { api -> api.ouvrirSession(OuvrirSessionRequest()) }
        }
    }

    /** Réponse libre, choix (numéro envoyé comme texte) ou code secret. */
    fun envoyerTexte(context: Context, texte: String) {
        if (texte.isBlank() || _state.value.isLoading) return

        val etatPrecedent = _state.value.etat
        val estCodeSecret = _state.value.attendActuel?.type == "code_secret"

        _state.update {
            it.copy(
                // Le code n'est jamais gardé en clair dans l'état de l'app — voir §3.
                tours = it.tours + TourConversation.Utilisateur(
                    texte = if (estCodeSecret) "••••" else texte,
                    masque = estCodeSecret
                ),
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            appliquerReponse(context) { api ->
                api.envoyerMessage(MessageRequest(texte = texte, etat = etatPrecedent))
            }
        }
    }

    fun envoyerPhoto(context: Context, fichier: File) {
        if (_state.value.isLoading) return
        val etatPrecedent = _state.value.etat

        _state.update {
            it.copy(
                tours = it.tours + TourConversation.Utilisateur("[photo envoyée]"),
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val api = ApiClientProvider.getApi(context)
                // En mode "état", le champ etat se passe en JSON texte dans le formulaire — voir §2.4.
                val etatPart = etatPrecedent?.let {
                    val json = MoshiProvider.moshi.adapter(com.atatech.app.api.EtatConversation::class.java).toJson(it)
                    json.toRequestBody("application/json".toMediaTypeOrNull())
                }
                val photoBody = fichier.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val photoPart = MultipartBody.Part.createFormData("photo", fichier.name, photoBody)

                val reponse = api.envoyerPhoto(etat = etatPart, photo = photoPart)
                traiterReponse(reponse)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Réseau injoignable") }
            }
        }
    }

    fun recommencer() {
        _state.update { AssistantState() }
    }

    private suspend fun appliquerReponse(
        context: Context,
        appel: suspend (NyeGbeApi) -> Response<ReponseDemarches>
    ) {
        try {
            val api = ApiClientProvider.getApi(context)
            traiterReponse(appel(api))
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, errorMessage = e.message ?: "Réseau injoignable") }
        }
    }

    private fun traiterReponse(reponse: Response<ReponseDemarches>) {
        if (reponse.isSuccessful && reponse.body() != null) {
            val corps = reponse.body()!!
            _state.update {
                it.copy(
                    tours = it.tours + corps.messages.map { m -> TourConversation.Assistant(m) },
                    etat = corps.etat,
                    attendActuel = corps.messages.lastOrNull()?.attend,
                    fini = corps.fini,
                    isLoading = false
                )
            }
        } else {
            _state.update { it.copy(isLoading = false, errorMessage = "Erreur ${reponse.code()}") }
        }
    }
}
