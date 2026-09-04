package com.atatech.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atatech.app.api.ApiClientProvider
import com.atatech.app.api.EtatConversation
import com.atatech.app.api.MessageRequest
import com.atatech.app.api.MoshiProvider
import com.atatech.app.api.NyeGbeApi
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

    /**
     * L'ecran s'ouvre SANS RIEN DEMANDER au serveur.
     *
     * C'est a l'utilisateur de formuler son intention en premier — une note
     * vocale, ou une phrase ecrite. Le menu des demarches n'arrive qu'en
     * reponse a cette premiere prise de parole. Auparavant, /session etait
     * appele des l'affichage et le menu tombait avant que l'utilisateur ait
     * ouvert la bouche.
     *
     * L'etat de depart est construit localement (mode « etat », §2.4) : aucun
     * aller-retour reseau n'est necessaire pour commencer.
     */
    fun demarrer(context: Context) {
        if (_state.value.demarre) return
        _state.update {
            it.copy(demarre = true, etat = EtatConversation.neuf(), errorMessage = null)
        }
    }

    /**
     * Réponse libre, choix (numéro envoyé comme texte) ou code secret.
     * [libelle] : ce qu'on AFFICHE si ce n'est pas le texte envoyé — un choix
     * envoie « 2 » mais doit montrer « Mercredi ».
     */
    fun envoyerTexte(context: Context, texte: String, libelle: String? = null) {
        if (texte.isBlank() || _state.value.isLoading) return

        val etatPrecedent = _state.value.etat
        val estCodeSecret = _state.value.attendActuel?.type == "code_secret"

        _state.update {
            it.copy(
                // Le code n'est jamais gardé en clair dans l'état de l'app — voir §3.
                tours = it.tours + TourConversation.Utilisateur(
                    texte = if (estCodeSecret) "••••" else texte,
                    masque = estCodeSecret,
                    libelle = if (estCodeSecret) null else libelle
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

    /**
     * NOTE VOCALE — la fonction qui manquait, et sans laquelle aucune bulle
     * audio ne pouvait apparaître.
     *
     * Le fichier N'EST PAS ENVOYÉ : le scénario est scripté, le serveur ne
     * transcrit rien (API_DEMARCHES.md §3). On lui dit seulement « l'utilisateur
     * a répondu à la voix » avec type = "voix". Le fichier reste dans le cache
     * de l'application pour rester réécoutable dans le fil.
     *
     * La bulle est ajoutée AVANT l'appel réseau et n'est jamais retirée : la
     * réponse du serveur ne fait qu'AJOUTER des tours (voir traiterReponse),
     * elle ne remplace jamais la liste.
     */
    fun envoyerVoix(context: Context, note: NoteVocale) {
        if (_state.value.isLoading) return
        val etatPrecedent = _state.value.etat

        _state.update {
            it.copy(
                tours = it.tours + TourConversation.Vocal(note.fichier, note.dureeMs),
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            appliquerReponse(context) { api ->
                api.envoyerMessage(
                    MessageRequest(texte = "", type = "voix", etat = etatPrecedent)
                )
            }
        }
    }

    fun envoyerPhoto(context: Context, fichier: File) {
        if (_state.value.isLoading) return
        val etatPrecedent = _state.value.etat

        _state.update {
            it.copy(
                tours = it.tours + TourConversation.Photo(fichier),
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
        _state.update { AssistantState(demarre = true, etat = EtatConversation.neuf()) }
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
