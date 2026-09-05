"""API mobile de l'ASSISTANT COMPLET — voix, cerveau, image.

CE QUE CE MODULE AJOUTE, ET POURQUOI IL EST SÉPARÉ DES DÉMARCHES
    api_demarches.py sert le scénario SCRIPTÉ : ni ASR, ni LLM, ni TTS, donc
    il tourne dans api_seule.py qui démarre en 2 s sans PyTorch.
    Ici, c'est l'inverse : parler au micro et se faire répondre en éwé demande
    les trois briques du projet. Ce blueprint ne peut donc être monté QUE par
    server.py, qui a les modèles chargés. D'où l'injection de dépendances : ce
    fichier ne sait rien faire tout seul, il se contente d'exposer proprement
    ce que server.py sait déjà faire.

POURQUOI PAS LES ROUTES EXISTANTES /api/traiter_* ?
    Elles sont protégées par @login_required_api, c'est-à-dire par un COOKIE de
    session Flask. Un navigateur en a un ; une application Android n'en a pas.
    Ces routes-ci sont protégées par la clé d'API (en-tête X-Api-Cle), comme le
    reste de l'API v1.

POURQUOI DU POLLING ET PAS DU SSE ?
    L'interface web reçoit les étapes en direct par Server-Sent Events
    (/api/stream/<job_id>). En Retrofit/OkHttp, consommer du SSE demande du
    travail inutile. Ici : on poste, on reçoit un job_id, on interroge
    GET /api/v1/job/<job_id> toutes les secondes. Les événements accumulés par
    server.py sont APLATIS en un seul objet, facile à décoder.

    Attention aux durées réelles sur ce PC : l'ASR met 5 à 20 s, le TTS 30 à
    60 s. Prévoir un timeout de lecture d'au moins 180 s côté client, et
    afficher `etape` pour que l'utilisateur ne croie pas à un blocage.
"""
import functools
import os

from flask import Blueprint, jsonify, request

import api_demarches      # pour réutiliser exactement la même clé d'API

bp = Blueprint("api_assistant", __name__)

# Injecté par server.py (voir init_assistant). Ce module n'importe jamais
# torch ni le pipeline : il ne fait qu'appeler ce qu'on lui donne.
_deps = {}

# Garde-fou de taille sur les envois, aligné sur server.py.
AUDIO_MAX_OCTETS = 25 * 1024 * 1024
IMAGE_MAX_OCTETS = 6 * 1024 * 1024


def cle_api_requise(vue):
    """Même règle que le reste de l'API v1 : X-Api-Cle, ou tout ouvert si
    API_CLE n'est pas défini."""
    @functools.wraps(vue)
    def _verifier(*a, **kw):
        attendue = api_demarches.cle_attendue()
        if attendue and request.headers.get("X-Api-Cle", "") != attendue:
            return jsonify({"erreur": "Clé d'API absente ou invalide "
                                      "(en-tête X-Api-Cle)."}), 401
        return vue(*a, **kw)
    return _verifier


def _pret():
    """Les modèles sont-ils chargés ? Au démarrage de server.py, l'ASR et le
    TTS mettent plusieurs minutes à arriver : mieux vaut un 503 explicite
    qu'une requête qui pend."""
    verif = _deps.get("modeles_prets")
    return bool(verif and verif())


# ══════════════════════════════════════════════════════════════════════════════
# Envoi d'un travail
# ══════════════════════════════════════════════════════════════════════════════
def _lancer(cible, *args):
    """Crée un job et lance le traitement en tâche de fond, comme le fait
    l'interface web. Renvoie la réponse JSON à donner au client."""
    job_id = _deps["creer_job"]()
    _deps["lancer_thread"](cible, job_id, *args)
    return jsonify({"job_id": job_id, "fini": False})


@bp.route("/api/v1/assistant/texte", methods=["POST"])
@cle_api_requise
def assistant_texte():
    """Message écrit → réponse éwé + voix. Même chemin que l'interface web."""
    if not _pret():
        return jsonify({"erreur": "Modèles en cours de chargement — "
                                  "réessaie dans un instant.",
                        "code": "modeles_non_prets"}), 503
    data = request.get_json(silent=True) or {}
    texte = (data.get("texte") or "").strip()
    if not texte:
        return jsonify({"erreur": "Texte vide."}), 400
    return _lancer(_deps["bg_texte"], texte)


@bp.route("/api/v1/assistant/audio", methods=["POST"])
@cle_api_requise
def assistant_audio():
    """Note vocale → transcription éwé → réponse éwé + voix.

    FORMAT : WAV PCM 16 bits mono 16 kHz (ou FLAC / OGG-Vorbis). L'ASR lit le
    fichier avec libsndfile : le M4A/AAC produit PAR DÉFAUT par MediaRecorder
    sur Android n'est PAS lisible. Enregistrer avec AudioRecord et écrire
    l'en-tête WAV soi-même."""
    if not _pret():
        return jsonify({"erreur": "Modèles en cours de chargement — "
                                  "réessaie dans un instant.",
                        "code": "modeles_non_prets"}), 503
    if "audio" not in request.files:
        return jsonify({"erreur": "Fichier audio manquant (champ « audio »)."}), 400

    donnees = request.files["audio"].read()
    if not donnees:
        return jsonify({"erreur": "Fichier audio vide."}), 400
    if len(donnees) > AUDIO_MAX_OCTETS:
        return jsonify({"erreur": "Enregistrement trop lourd (25 Mo maximum)."}), 413

    # On refuse tout de suite un conteneur illisible plutôt que de laisser
    # l'ASR échouer 20 s plus tard sur un message incompréhensible.
    if not _entete_audio_connu(donnees):
        return jsonify({
            "erreur": "Format audio non lisible. Envoyer du WAV PCM 16 bits "
                      "mono 16 kHz (ou FLAC / OGG). Le M4A/AAC de "
                      "MediaRecorder n'est pas accepté : utiliser AudioRecord.",
            "code": "format_audio",
        }), 415

    chemin = _deps["chemin_temporaire"]("input", ".wav")
    with open(chemin, "wb") as f:
        f.write(donnees)
    return _lancer(_deps["bg_audio"], chemin)


def _entete_audio_connu(donnees: bytes) -> bool:
    """Reconnaît les conteneurs que libsndfile sait ouvrir, à l'octet près."""
    return (donnees[:4] == b"RIFF" and donnees[8:12] == b"WAVE"   # WAV
            or donnees[:4] == b"fLaC"                              # FLAC
            or donnees[:4] == b"OggS")                             # OGG


@bp.route("/api/v1/assistant/image", methods=["POST"])
@cle_api_requise
def assistant_image():
    """Photo d'un texte court (pancarte, étiquette) → lecture puis explication
    parlée en éwé."""
    if not _pret():
        return jsonify({"erreur": "Modèles en cours de chargement.",
                        "code": "modeles_non_prets"}), 503
    if "image" not in request.files:
        return jsonify({"erreur": "Image manquante (champ « image »)."}), 400
    fichier = request.files["image"]
    donnees = fichier.read()
    if not donnees:
        return jsonify({"erreur": "Image vide."}), 400
    if len(donnees) > IMAGE_MAX_OCTETS:
        return jsonify({"erreur": "Image trop lourde (6 Mo maximum)."}), 413
    return _lancer(_deps["bg_image"], donnees,
                   fichier.mimetype or "image/jpeg")


# ══════════════════════════════════════════════════════════════════════════════
# Lecture du résultat
# ══════════════════════════════════════════════════════════════════════════════
# server.py empile des événements pensés pour le SSE. On les aplatit ici en un
# seul objet : le client n'a qu'un JSON à décoder, toujours de la même forme.
def _aplatir(evenements, fini: bool) -> dict:
    sortie = {
        "fini":          fini,
        "etape":         None,     # où en est le traitement (à afficher)
        "transcription": None,     # ce que l'ASR a compris
        "texte_image":   None,     # ce que l'OCR a lu
        "langue":        None,
        "normalisation": None,
        "rep_ewe":       None,
        "rep_fr":        None,
        "audio_url":     None,
        "action":        {"type": "aucune"},
        "erreur":        None,
    }
    for e in evenements:
        type_ = e.get("type")
        if type_ == "etape":
            sortie["etape"] = e.get("message")
        elif type_ == "transcription":
            sortie["transcription"] = e.get("texte")
        elif type_ == "texte_image":
            sortie["texte_image"] = e.get("texte")
        elif type_ == "analyse":
            sortie["langue"] = e.get("langue")
            sortie["normalisation"] = e.get("normalisation")
        elif type_ == "erreur":
            sortie["erreur"] = e.get("message")
        elif type_ in ("termine", "action"):
            for champ in ("rep_ewe", "rep_fr", "audio_url", "langue",
                          "normalisation", "transcription", "texte_image"):
                if e.get(champ) is not None:
                    sortie[champ] = e[champ]
            if type_ == "action":
                sortie["action"] = _action(e)
    return sortie


def _action(e: dict) -> dict:
    """Traduit l'événement d'action de l'interface web en instruction pour
    l'application mobile.

    L'app NE DÉCLENCHE JAMAIS l'action toute seule : elle affiche un bouton et
    attend le geste de l'utilisateur. C'est déjà la règle du web."""
    quoi = e.get("action")
    if quoi == "appel":
        return {"type": "appel_numero", "numero": e.get("numero"),
                "confirmation_requise": True}
    if quoi == "recherche":
        return {"type": "ouvrir_url", "url": e.get("url"),
                "requete": e.get("requete"), "confirmation_requise": True}
    return {"type": "aucune"}


@bp.route("/api/v1/job/<job_id>")
@cle_api_requise
def lire_job(job_id: str):
    """État d'un traitement. À interroger environ une fois par seconde jusqu'à
    « fini »: true. Un job inconnu (serveur redémarré) renvoie 404 — le client
    doit alors abandonner l'attente au lieu de boucler indéfiniment."""
    jobs, verrou = _deps["jobs"], _deps["jobs_lock"]
    with verrou:
        job = jobs.get(job_id)
        if job is None:
            return jsonify({"erreur": "Travail inconnu ou expiré "
                                      "(le serveur a peut-être redémarré).",
                            "code": "job_inconnu"}), 404
        evenements = list(job["events"])
        fini = job["done"]
    reponse = _aplatir(evenements, fini)
    reponse["job_id"] = job_id
    return jsonify(reponse)


# ══════════════════════════════════════════════════════════════════════════════
# Installation
# ══════════════════════════════════════════════════════════════════════════════
def init_assistant(app, deps: dict):
    """Monte l'API sur server.py, qui fournit tout le nécessaire :

        creer_job, jobs, jobs_lock, lancer_thread,
        bg_texte, bg_audio, bg_image,
        modeles_prets (fonction), chemin_temporaire (fonction)
    """
    manquantes = [c for c in ("creer_job", "jobs", "jobs_lock", "lancer_thread",
                              "bg_texte", "bg_audio", "bg_image",
                              "modeles_prets", "chemin_temporaire")
                  if c not in deps]
    if manquantes:
        raise RuntimeError("init_assistant : dépendances manquantes "
                           + ", ".join(manquantes))
    _deps.update(deps)
    app.register_blueprint(bp)
    return bp
