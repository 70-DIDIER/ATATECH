"""API publique des DÉMARCHES ADMINISTRATIVES — consommée par l'app Android et
par le chatbot WhatsApp.

POURQUOI CE MODULE EXISTE À PART
    Le scénario est SCRIPTÉ (demo_demarches.py) : au moment où un client
    appelle, il n'y a ni ASR, ni TTS, ni LLM à faire tourner — seulement du JSON
    et des .wav déjà écrits sur le disque. Ce blueprint n'importe donc NI torch,
    NI transformers, NI le pipeline. Il peut être monté :
      - par api_seule.py  → serveur léger, démarre en ~2 s (usage normal) ;
      - par server.py     → l'interface web complète, qui a le TTS chargé et
                            peut synthétiser à la volée une voix manquante.
    C'est le rôle du paramètre `deps` : une seule implémentation, deux hôtes.

DEUX FAÇONS DE GARDER L'ÉTAT, AU CHOIX DU CLIENT
    - `session_id` : le serveur retient l'état (pratique pour WhatsApp, qui n'a
      que le numéro de l'expéditeur à fournir) ;
    - `etat` : l'état fait l'aller-retour et le serveur ne retient rien
      (pratique pour une app mobile ; c'est déjà la convention de
      /api/sante/message).
    Les deux peuvent être mélangés : si les deux sont fournis, `etat` gagne.

CE QUI N'EST JAMAIS CONSERVÉ
    Le code de paiement saisi par l'utilisateur : ni en session, ni en journal,
    ni dans la réponse. Voir demo_demarches.repondre().
"""
import functools
import os
import threading
import time
from pathlib import Path

from flask import Blueprint, jsonify, request

import demo_demarches
import demo_money

# ══════════════════════════════════════════════════════════════════════════════
# Les scenarios disponibles
# ══════════════════════════════════════════════════════════════════════════════
# Chaque module expose le MEME contrat : etat_neuf, repondre, fini, nom_audio,
# tous_les_blocs... On peut donc les traiter indifferemment. L'etat porte le nom
# du scenario en cours ; par defaut, les demarches administratives.
SCENARIOS = {"demarches": demo_demarches, "money": demo_money}
SCENARIO_DEFAUT = "demarches"


def module_du(etat) -> object:
    """Le module de scenario que cet etat designe."""
    nom = (etat or {}).get("scenario") or SCENARIO_DEFAUT
    return SCENARIOS.get(nom, demo_demarches)

VERSION = "1.0"

# Les .wav des démos sont écrits là par preparer_voix_sante.py / server.py.
SORTIES_DIR = Path(__file__).parent.parent / "assemblage" / "sorties"

# Au-delà, une conversation abandonnée est oubliée (mémoire du serveur).
SESSION_TTL_S = 2 * 3600

# Garde-fou de taille sur les photos, aligné sur IMAGE_MAX_OCTETS de server.py.
PHOTO_MAX_OCTETS = 6 * 1024 * 1024

bp = Blueprint("api_demarches", __name__)

_sessions = {}
_sessions_lock = threading.Lock()

# Injecté par l'hôte (voir init_api). Par défaut : on se contente du cache.
_audio_url = None


# ══════════════════════════════════════════════════════════════════════════════
# Clé d'API
# ══════════════════════════════════════════════════════════════════════════════
def cle_attendue():
    return (os.environ.get("API_CLE") or "").strip()


def cle_api_requise(vue):
    """Exige l'en-tête X-Api-Cle — SAUF si API_CLE n'est pas défini.

    En hackathon, on démarre souvent sans clé : le serveur l'annonce alors en
    clair au démarrage (voir api_seule.py) plutôt que de refuser tout le monde
    ou, pire, de laisser croire qu'il protège quelque chose."""
    @functools.wraps(vue)
    def _verifier(*a, **kw):
        attendue = cle_attendue()
        if attendue and request.headers.get("X-Api-Cle", "") != attendue:
            return jsonify({"erreur": "Clé d'API absente ou invalide "
                                      "(en-tête X-Api-Cle)."}), 401
        return vue(*a, **kw)
    return _verifier


# ══════════════════════════════════════════════════════════════════════════════
# Sessions (mode « session_id »)
# ══════════════════════════════════════════════════════════════════════════════
def _purger():
    limite = time.time() - SESSION_TTL_S
    for sid in [s for s, v in _sessions.items() if v["vu"] < limite]:
        _sessions.pop(sid, None)


def lire_session(session_id: str) -> dict:
    with _sessions_lock:
        _purger()
        entree = _sessions.get(session_id)
        return dict(entree["etat"]) if entree else demo_demarches.etat_neuf()


def ecrire_session(session_id: str, etat: dict) -> None:
    with _sessions_lock:
        _sessions[session_id] = {"etat": dict(etat), "vu": time.time()}


def oublier_session(session_id: str) -> None:
    with _sessions_lock:
        _sessions.pop(session_id, None)


# ══════════════════════════════════════════════════════════════════════════════
# Sérialisation d'un message
# ══════════════════════════════════════════════════════════════════════════════
def audio_du_cache(bloc: dict, demo=demo_demarches):
    """URL du .wav pré-généré, ou None s'il n'a pas encore été fabriqué.

    On ne synthétise RIEN ici : ce module doit pouvoir tourner sans PyTorch.
    Un `audio_url: null` n'est pas une erreur — le client affiche le texte et
    continue. Lancer `preparer_voix_sante.py demarches` remplit le cache."""
    nom = demo.nom_audio(bloc)
    return f"/audio/{nom}" if (SORTIES_DIR / nom).exists() else None


def bloc_json(bloc: dict, demo=demo_demarches) -> dict:
    """Un message du script, prêt pour l'app mobile ou le chatbot."""
    return {
        "cle":       bloc["cle"],
        "titre":     bloc.get("titre"),
        "titre_fr":  bloc.get("titre_fr"),
        "carte":     bloc.get("carte"),
        "lignes":    bloc.get("lignes", []),
        "menu":      bloc.get("menu", []),
        "ewe":       bloc.get("ewe", ""),
        "fr":        bloc.get("fr", ""),
        "attend":    bloc.get("attend", {"type": "texte"}),
        "audio_url": (_audio_url or audio_du_cache)(bloc, demo),
    }


def _reponse(session_id, etat, blocs, demo=demo_demarches):
    return {
        "session_id": session_id,
        "etat":       etat,
        "scenario":   (etat or {}).get("scenario") or SCENARIO_DEFAUT,
        "fini":       demo.fini(etat),
        "messages":   [bloc_json(b, demo) for b in blocs],
    }


# ══════════════════════════════════════════════════════════════════════════════
# Le tour de conversation, commun au texte et à la photo
# ══════════════════════════════════════════════════════════════════════════════
def jouer_tour(session_id: str, etat, texte: str = "",
               type_message: str = "texte") -> dict:
    """Fait avancer le script d'un tour et renvoie la réponse JSON.

    Utilisée par les routes ET par webhook_whatsapp.py — c'est le seul point
    d'entrée du scénario, pour que les trois portes se comportent pareil."""
    if etat is None and session_id:
        etat = lire_session(session_id)

    # DECLENCHEUR : le mot « money » bascule vers le scenario mobile money,
    # meme en plein milieu d'une demarche administrative. On repart d'un etat
    # neuf pour ce scenario : reprendre l'ancien melangerait les deux.
    if type_message == "texte" and demo_money.est_declencheur(texte):
        etat = demo_money.etat_neuf()

    demo = module_du(etat)
    etat, blocs = demo.repondre(etat, texte, type_message)
    if session_id:
        ecrire_session(session_id, etat)
    return _reponse(session_id, etat, blocs, demo)


def _etat_de_la_requete(data: dict):
    """L'état posté par le client, ou None s'il s'en remet à sa session."""
    etat = data.get("etat")
    return etat if isinstance(etat, dict) else None


# ══════════════════════════════════════════════════════════════════════════════
# Routes
# ══════════════════════════════════════════════════════════════════════════════
@bp.route("/api/v1/ping")
def ping():
    """Sans clé, exprès : c'est le test « est-ce que je joins le serveur ? »
    que l'équipe mobile lance en premier depuis le téléphone."""
    return jsonify({"ok": True, "version": VERSION,
                    "service": "nye-gbe-demarches",
                    "cle_requise": bool(cle_attendue())})


@bp.route("/api/v1/demarches/services")
@cle_api_requise
def services():
    """Le catalogue : les 4 catégories du menu et les parcours jouables."""
    return jsonify(demo_demarches.services())


@bp.route("/api/v1/demarches/session", methods=["POST"])
@cle_api_requise
def ouvrir_session():
    """Ouvre une conversation et renvoie tout de suite le menu.

    `session_id` peut être imposé par le client (le numéro de téléphone, pour
    WhatsApp) ; sinon il est tiré au sort."""
    data = request.get_json(silent=True) or {}
    session_id = str(data.get("session_id") or os.urandom(8).hex())
    oublier_session(session_id)
    etat = demo_demarches.etat_neuf()
    ecrire_session(session_id, etat)
    return jsonify(_reponse(session_id, etat, [demo_demarches.MENU]))


@bp.route("/api/v1/demarches/message", methods=["POST"])
@cle_api_requise
def message():
    """Un tour de conversation en TEXTE ou en VOIX.

    `type` vaut « texte » (défaut) ou « voix ».

    « voix » est le mode à utiliser quand l'utilisateur répond en enregistrant
    une note vocale : **aucune reconnaissance vocale n'est nécessaire** et le
    champ `texte` peut rester vide. Le scénario est scripté, il avance quoi que
    dise l'utilisateur — exactement comme l'interface web, qui ne transcrit pas
    les réponses aux démonstrations. Envoyer le fichier audio est inutile ici ;
    si vous préférez le faire quand même, voir /api/v1/demarches/voix."""
    data = request.get_json(silent=True) or {}
    session_id = data.get("session_id")
    etat = _etat_de_la_requete(data)
    if etat is None and not session_id:
        return jsonify({"erreur": "Fournir « session_id » ou « etat »."}), 400

    type_message = (data.get("type") or "texte").lower()
    if type_message not in ("texte", "voix"):
        return jsonify({"erreur": "« type » doit valoir « texte » ou « voix » "
                                  "(pour une photo : /api/v1/demarches/photo)."}), 400
    return jsonify(jouer_tour(session_id, etat, data.get("texte", ""),
                              type_message))


@bp.route("/api/v1/demarches/voix", methods=["POST"])
@cle_api_requise
def voix():
    """Un tour de conversation en NOTE VOCALE (multipart, champ « audio »).

    Le fichier n'est ni transcrit ni conservé : dans une démonstration
    scriptée, seule compte la présence d'une réponse. Cette route existe pour
    les clients qui veulent garder un flux « enregistrer puis envoyer »
    cohérent ; pour tout le reste, /message avec `type: "voix"` suffit et
    n'envoie rien sur le réseau."""
    session_id = request.form.get("session_id")
    etat = None
    if request.form.get("etat"):
        import json
        try:
            charge = json.loads(request.form["etat"])
            etat = charge if isinstance(charge, dict) else None
        except ValueError:
            return jsonify({"erreur": "Le champ « etat » n'est pas du JSON."}), 400
    if etat is None and not session_id:
        return jsonify({"erreur": "Fournir « session_id » ou « etat »."}), 400

    if "audio" in request.files:
        request.files["audio"].read()      # lu puis abandonné, jamais écrit
    return jsonify(jouer_tour(session_id, etat, "", "voix"))


@bp.route("/api/v1/demarches/photo", methods=["POST"])
@cle_api_requise
def photo():
    """Un tour de conversation en PHOTO (multipart, champ « photo »).

    Le contenu de l'image n'est PAS analysé : c'est une simulation. On vérifie
    seulement qu'une image plausible est bien arrivée, puis on avance."""
    if "photo" not in request.files:
        return jsonify({"erreur": "Image manquante (champ « photo »)."}), 400
    fichier = request.files["photo"]
    donnees = fichier.read()
    if not donnees:
        return jsonify({"erreur": "Image vide."}), 400
    if len(donnees) > PHOTO_MAX_OCTETS:
        return jsonify({"erreur": "Image trop lourde (6 Mo maximum)."}), 413

    session_id = request.form.get("session_id")
    etat = None
    if request.form.get("etat"):
        import json
        try:
            charge = json.loads(request.form["etat"])
            etat = charge if isinstance(charge, dict) else None
        except ValueError:
            return jsonify({"erreur": "Le champ « etat » n'est pas du JSON."}), 400
    if etat is None and not session_id:
        return jsonify({"erreur": "Fournir « session_id » ou « etat »."}), 400

    return jsonify(jouer_tour(session_id, etat, "", "photo"))


# ══════════════════════════════════════════════════════════════════════════════
# Installation
# ══════════════════════════════════════════════════════════════════════════════
def init_api(app, deps: dict = None):
    """Monte l'API sur une application Flask.

    `deps` permet à un hôte plus riche de fournir sa propre fabrique d'URL
    audio — server.py, qui a le TTS chargé, y met sa synthèse à la volée :

        init_api(app, {"audio_url": lambda bloc: _audio_script(bloc, demo)})
    """
    global _audio_url
    if deps and deps.get("audio_url"):
        _audio_url = deps["audio_url"]
    app.register_blueprint(bp)
    return bp


def voix_manquantes() -> list:
    """Messages du script dont le .wav n'est pas encore en cache.

    Sert à l'avertissement de démarrage : mieux vaut le savoir tout de suite
    que le découvrir devant un jury."""
    manquantes = []
    for nom, demo in SCENARIOS.items():
        manquantes += [f"{nom}/{b['cle']}" for b in demo.tous_les_blocs()
                       if not (SORTIES_DIR / demo.nom_audio(b)).exists()]
    return manquantes
