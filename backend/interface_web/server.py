"""
JPOPE Interface Web — Serveur Flask
Réutilise les fonctions de assemblage/pipeline.py sans réécrire la logique.
"""
import os
import sys
import json
import uuid
import time
import threading
from pathlib import Path

# ── Chargement des modèles SANS RÉSEAU ────────────────────────────────────────
# Doit être posé AVANT le premier import de transformers / huggingface_hub :
# ces bibliothèques lisent la variable au moment de leur import, la changer
# ensuite n'aurait plus aucun effet.
#
# Pourquoi : huggingface_hub 1.x fait ses requêtes avec httpx, et son client
# partagé peut se retrouver fermé — d'où l'erreur « Cannot send a request, as
# the client has been closed » au chargement des modèles. Or ces appels
# réseau ne servent à rien ici : mms-1b-all (avec l'adaptateur éwé) et
# mms-tts-ewe sont déjà complets dans ~/.cache/huggingface/hub, et
# from_pretrained ne les contacte que pour vérifier s'il existe une version
# plus récente.
#
# Effets : plus aucun client HTTP créé (l'erreur devient impossible),
# démarrage plus rapide, et le serveur fonctionne sans connexion — utile le
# jour de la soutenance.
#
# À SAVOIR : si tu ajoutes un jour un modèle qui n'est PAS encore en cache,
# il faudra commenter ces deux lignes le temps de son premier téléchargement.
os.environ.setdefault("HF_HUB_OFFLINE", "1")
os.environ.setdefault("TRANSFORMERS_OFFLINE", "1")

from flask import Flask, render_template, request, jsonify, send_file, Response

# ── Chemins ────────────────────────────────────────────────────────────────────
BASE        = Path(__file__).parent
ASSEMBLAGE  = BASE.parent / "assemblage"
CERVEAU_DIR = BASE.parent / "cerveau"
SORTIES_DIR = ASSEMBLAGE / "sorties"

sys.path.insert(0, str(ASSEMBLAGE))
sys.path.insert(0, str(CERVEAU_DIR))

import numpy as np
import soundfile as sf
from pipeline import (charger_asr, charger_tts, asr_mms, tts_ewe, lire_config_asr,
                      lire_config_tts, transcrire_audio)
from pipeline_commun import sauver_audio   # écrit en 44,1 kHz PCM_16 (lisible partout)
import cerveau
from cerveau import tour
import apprentissage
import gemini_llm   # lecture de texte dans une image (multimodal) + quota
import intentions   # détection d'intention (conversation vs action) — additif
import demo_transfert   # DÉMO scriptée (transfert mobile money) — couche isolée, phrases en dur
import demo_sante       # DÉMO scriptée (santé : soignant / patient) — couche isolée, phrases en dur
import demo_gestion     # DÉMO scriptée (gestion de commerce) — couche isolée, phrases en dur
import demo_demarches   # DÉMO scriptée (démarches administratives) — couche isolée
import demo_voix        # briques communes aux démos scriptées (voix + cache)
import gestion          # ANCIENNE démo gestion « langage libre » — routes conservées
                        # mais PLUS APPELÉES par l'interface (voir demo_gestion.py)

# ── Flask ──────────────────────────────────────────────────────────────────────
app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 50 * 1024 * 1024  # 50 Mo max upload

# ── Comptes utilisateurs ──────────────────────────────────────────────────────
# Tout est dans auth.py : modèle User, formulaires, routes /inscription,
# /connexion, /deconnexion, et la configuration de la base (config.py).
# init_auth() applique la config, branche SQLAlchemy, Flask-Login et la
# protection CSRF, puis crée instance/app.db au premier démarrage.
from auth import init_auth, login_required_page, login_required_api

init_auth(app)

# ── Modèles (chargés en arrière-plan au démarrage) ────────────────────────────
asr_proc      = None
asr_model     = None
tts_tok       = None
tts_model     = None
modeles_prets = False
erreur_init   = None


def initialiser_modeles():
    global asr_proc, asr_model, tts_tok, tts_model, modeles_prets, erreur_init
    try:
        config = lire_config_asr()
        if config["mode"] == "distant":
            url = config.get("url_colab", "").strip()
            if url:
                print(f"\n[JPOPE] Mode ASR : distant → {url}")
                asr_proc = asr_model = None
            else:
                print("[JPOPE] url_colab vide — bascule en ASR local.")
                asr_proc, asr_model = charger_asr()
        else:
            print("\n[JPOPE] Chargement ASR local (MMS-1B-all, float32)...")
            asr_proc, asr_model = charger_asr()
        print("[JPOPE] Chargement TTS (mms-tts-ewe)...")
        tts_tok, tts_model = charger_tts()
        modeles_prets = True
        print("[JPOPE] ✓ Prêt — http://localhost:5000\n")
        # Voix des démos scriptées pré-calculées en tâche de fond (scripts figés
        # → mise en cache définitive). N'empêche rien : le serveur répond déjà.
        threading.Thread(target=_prechauffer_demos, daemon=True).start()
    except Exception as e:
        erreur_init = str(e)
        print(f"[JPOPE] ✗ Erreur chargement modèles : {e}")


# ── Jobs (file d'événements SSE) ───────────────────────────────────────────────
jobs: dict = {}
jobs_lock = threading.Lock()


def creer_job() -> str:
    job_id = str(uuid.uuid4())
    with jobs_lock:
        jobs[job_id] = {"events": [], "done": False}
    return job_id


def push_event(job_id: str, event: dict):
    with jobs_lock:
        if job_id in jobs:
            jobs[job_id]["events"].append(event)


def finir_job(job_id: str):
    with jobs_lock:
        if job_id in jobs:
            jobs[job_id]["done"] = True


# ── Routes ────────────────────────────────────────────────────────────────────
@app.route("/")
def accueil():
    """Site vitrine — page d'accueil publique (templates/accueil.html)."""
    return render_template("accueil.html")


@app.route("/app")
@login_required_page
def chatbot():
    """Boîte de conversation — PROTÉGÉE : sans compte, Flask-Login renvoie
    vers /connexion?next=/app, et l'utilisateur revient ici une fois connecté.

    Le nom de la fonction est `chatbot` (et non plus `index`) parce que auth.py
    y renvoie par `url_for("chatbot")` après une inscription ou une connexion.

    Le contenu, lui, est INCHANGÉ. Le front n'appelle que des URL absolues
    (/api/…, /audio/…) : rien à modifier de son côté."""
    return render_template("index.html")


@app.route("/api/statut")
def statut():
    return jsonify({
        "pret": modeles_prets,
        "erreur": erreur_init,
    })


@app.route("/api/quota")
@login_required_api
def quota():
    """Suivi discret du quota Gemini pour l'interface (jauge)."""
    try:
        if not gemini_llm.disponible():
            return jsonify({"disponible": False})
        st = gemini_llm.usage_stats()
        return jsonify({
            "disponible": True,
            "texte": f"G · {st['total_jour']}/{st['budget_jour']} "
                     f"requêtes aujourd'hui ({st['pourcent_jour']} %)",
        })
    except Exception:
        return jsonify({"disponible": False})


@app.route("/api/traiter_texte", methods=["POST"])
@login_required_api
def traiter_texte():
    if not modeles_prets:
        return jsonify({"erreur": "Modèles en cours de chargement — patientez."}), 503
    data  = request.get_json() or {}
    texte = data.get("texte", "").strip()
    if not texte:
        return jsonify({"erreur": "Texte vide"}), 400

    job_id = creer_job()
    threading.Thread(target=_bg_texte, args=(job_id, texte), daemon=True).start()
    return jsonify({"job_id": job_id})


@app.route("/api/traiter_audio", methods=["POST"])
@login_required_api
def traiter_audio():
    if not modeles_prets:
        return jsonify({"erreur": "Modèles en cours de chargement — patientez."}), 503
    if "audio" not in request.files:
        return jsonify({"erreur": "Fichier audio manquant"}), 400

    job_id     = creer_job()
    audio_path = str(SORTIES_DIR / f"input_{job_id[:8]}.wav")
    request.files["audio"].save(audio_path)
    threading.Thread(target=_bg_audio, args=(job_id, audio_path), daemon=True).start()
    return jsonify({"job_id": job_id})


# Taille max d'une image acceptée (garde-fou : on reste sur de petites photos).
# Le navigateur compresse déjà avant l'envoi ; ceci refuse les envois énormes.
IMAGE_MAX_OCTETS = 6 * 1024 * 1024   # 6 Mo


@app.route("/api/traiter_image", methods=["POST"])
@login_required_api
def traiter_image():
    if not modeles_prets:
        return jsonify({"erreur": "Modèles en cours de chargement — patientez."}), 503
    if "image" not in request.files:
        return jsonify({"erreur": "Image manquante"}), 400
    if not gemini_llm.disponible():
        return jsonify({"erreur": "La lecture d'image n'est pas disponible "
                        "(service en ligne non configuré)."}), 503

    fichier = request.files["image"]
    data = fichier.read()
    if not data:
        return jsonify({"erreur": "Image vide"}), 400
    if len(data) > IMAGE_MAX_OCTETS:
        return jsonify({"erreur": "Image trop lourde — prends une photo plus "
                        "petite (une phrase courte suffit)."}), 400
    mime = fichier.mimetype or "image/jpeg"

    job_id = creer_job()
    threading.Thread(target=_bg_image, args=(job_id, data, mime),
                     daemon=True).start()
    return jsonify({"job_id": job_id})


# ── DÉMO scriptée : transfert mobile money (couche ISOLÉE) ────────────────────
# Renvoie la phrase EN DUR de l'étape n + son audio TTS. AUCUN appel LLM/Gemini :
# les phrases viennent de demo_transfert.py → zéro quota, identiques à chaque fois.
# Le pipeline normal (texte/voix/image) n'est jamais sollicité ici.
@app.route("/api/demo/etape/<int:n>")
@login_required_api
def demo_etape(n: int):
    if not modeles_prets:
        return jsonify({"erreur": "Modèles en cours de chargement — patientez."}), 503
    data = demo_transfert.etape(n)
    if data is None:
        return jsonify({"fin": True})
    # Synthèse vocale de la phrase éwé scriptée (texte fixe) → 44,1 kHz PCM_16.
    wav, sr = tts_ewe(data["ewe"], tts_tok, tts_model)
    filename = f"demo_{n}.wav"
    sauver_audio(wav, sr, str(SORTIES_DIR / filename))
    return jsonify({
        "index":     n,
        "ewe":       data["ewe"],
        "fr":        data["fr"],
        "audio_url": f"/audio/{filename}",
        "dernier":   n == demo_transfert.nombre_etapes() - 1,
    })


# ── DÉMOS SCRIPTÉES (couches ISOLÉES : santé, gestion de commerce) ───────────
# Mêmes principes que la démo transfert : les phrases viennent EN DUR de
# demo_sante.py / demo_gestion.py. AUCUN appel LLM/Gemini → l'assistant ne peut
# rien produire hors script, et les démonstrations ne consomment aucun quota.
# Seule la voix est calculée (TTS local), et une seule fois par message : le
# .wav est mis en cache sur disque sous un nom stable.

# Débit VOLONTAIREMENT plus posé que la conversation normale (0,68) : santé et
# argent, chaque phrase doit être suivie sans effort.
VITESSE_SCRIPT = 0.62
# Blanc inséré entre deux segments d'un même message (titre, lignes de fiche…).
PAUSE_SCRIPT_MS = 450

# Un seul TTS à la fois, toutes démos confondues (le modèle n'est pas réentrant).
script_lock = threading.Lock()

# Alias conservés : d'anciens scripts et tests s'y réfèrent.
VITESSE_SANTE, PAUSE_SANTE_MS, sante_lock = (VITESSE_SCRIPT, PAUSE_SCRIPT_MS,
                                             script_lock)


def _audio_script(bloc: dict, demo) -> str:
    """Voix éwé du bloc, en cache disque. Chaque segment (titre, ligne de fiche,
    phrase, option de menu) est synthétisé à part puis recollé avec un blanc :
    le TTS articule mieux les phrases courtes et la lecture reste posée.

    Le cache est indexé par EMPREINTE du texte prononcé (manifeste par démo) :
    corriger une phrase ou insérer une étape refait automatiquement la bonne
    voix, sans jamais rejouer l'ancienne."""
    nom    = demo.nom_audio(bloc)
    chemin = SORTIES_DIR / nom
    url    = f"/audio/{nom}"
    if not demo.voix_a_refaire(bloc, SORTIES_DIR):
        return url

    with script_lock:
        if not demo.voix_a_refaire(bloc, SORTIES_DIR):
            return url               # refaite pendant l'attente du verrou
        params   = {**lire_config_tts(), "vitesse": VITESSE_SCRIPT}
        morceaux = []
        sr       = None
        for segment in demo.segments_voix(bloc):
            wav, sr = tts_ewe(segment, tts_tok, tts_model, params)
            if morceaux:
                morceaux.append(np.zeros(int(sr * PAUSE_SCRIPT_MS / 1000),
                                         dtype=np.float32))
            morceaux.append(wav.astype(np.float32))
        if not morceaux:
            return ""
        sauver_audio(np.concatenate(morceaux), sr, str(chemin))
        demo.marquer_voix(bloc, SORTIES_DIR)
    return url


def _audio_sante(bloc: dict) -> str:          # alias historique
    return _audio_script(bloc, demo_sante)


def _bloc_json(bloc: dict, demo) -> dict:
    """Bloc du script + son audio, prêt pour l'interface."""
    return {
        "cle":       bloc["cle"],
        "role":      bloc.get("role"),
        "tag":       bloc.get("tag"),
        "titre":     bloc.get("titre"),
        "titre_fr":  bloc.get("titre_fr"),
        "carte":     bloc.get("carte"),
        "lignes":    bloc.get("lignes", []),
        "menu":      bloc.get("menu", []),
        "ewe":       bloc.get("ewe", ""),
        "fr":        bloc.get("fr", ""),
        "audio_url": _audio_script(bloc, demo),
    }


@app.route("/api/sante/message", methods=["POST"])
@login_required_api
def sante_message():
    """Avance le script santé. L'état (rôle / scénario / étape) est renvoyé au
    navigateur et re-posté à chaque tour : le serveur reste sans mémoire, et
    changer de rôle ne fait donc jamais perdre la conversation affichée."""
    if not modeles_prets:
        return jsonify({"erreur": "Modèles en cours de chargement — patientez."}), 503
    data = request.get_json() or {}
    etat = data.get("etat") or demo_sante.etat_neuf()

    if data.get("action") == "role":
        role = data.get("role")
        if role not in demo_sante.SCENARIOS:
            return jsonify({"erreur": "Rôle inconnu"}), 400
        etat, blocs = demo_sante.demarrer_role(role)
    else:
        etat, blocs = demo_sante.repondre(etat, data.get("texte", ""))

    return jsonify({"etat": etat,
                    "messages": [_bloc_json(b, demo_sante) for b in blocs]})


@app.route("/api/gestion/message", methods=["POST"])
@login_required_api
def gestion_message():
    """Avance le script GESTION DE COMMERCE. Même principe que la santé : le
    scénario est donné par le chiffre en tête du message d'ouverture, puis
    chaque message fait avancer d'un tour sans que rien ne soit analysé."""
    if not modeles_prets:
        return jsonify({"erreur": "Modèles en cours de chargement — patientez."}), 503
    data = request.get_json() or {}
    etat = data.get("etat") or demo_gestion.etat_neuf()
    etat, blocs = demo_gestion.repondre(etat, data.get("texte", ""))
    return jsonify({"etat": etat,
                    "messages": [_bloc_json(b, demo_gestion) for b in blocs]})


def _prechauffer_script(demo, libelle: str):
    """Pré-calcule les voix de TOUS les messages d'un script, une fois les
    modèles chargés. Le script est figé : les .wav restent en cache sur disque,
    donc ce travail n'a lieu qu'UNE seule fois (les démarrages suivants le
    sautent). Ensuite, la démonstration répond instantanément.

    Sur un PC chargé, la synthèse peut tourner à ~7× le temps réel : compter
    plusieurs minutes au tout premier démarrage. Le serveur, lui, répond déjà —
    seules les démos attendent leurs voix. Silencieux en cas de souci : les
    audios manquants sont alors faits à la volée, à la demande."""
    blocs     = demo.tous_les_blocs()
    manifeste = demo.lire_manifeste(SORTIES_DIR)
    a_faire   = [b for b in blocs if demo.voix_a_refaire(b, SORTIES_DIR, manifeste)]
    for orphelin in demo.voix_orphelines(SORTIES_DIR):
        try:
            (SORTIES_DIR / orphelin).unlink()
            print(f"[JPOPE]   {libelle} : voix périmée retirée ({orphelin})")
        except OSError:
            pass
    if not a_faire:
        print(f"[JPOPE] ✓ {libelle} : voix déjà en cache.")
        return
    print(f"[JPOPE] {libelle} : préparation de {len(a_faire)} voix "
          f"(une seule fois, en tâche de fond)...")
    for i, bloc in enumerate(a_faire, 1):
        try:
            _audio_script(bloc, demo)
            print(f"[JPOPE]   {libelle} {i}/{len(a_faire)} — {bloc['cle']}")
        except Exception as e:
            print(f"[JPOPE] ({libelle}) voix « {bloc['cle']} » différée : {e}")
        time.sleep(0.2)   # laisse passer une demande en cours (verrou partagé)
    print(f"[JPOPE] ✓ {libelle} : voix prêtes.")


def _prechauffer_demos():
    _prechauffer_script(demo_sante, "Démo santé")
    _prechauffer_script(demo_gestion, "Démo gestion")
    _prechauffer_script(demo_demarches, "Démo démarches")


# ── API PUBLIQUE /api/v1/* + webhook WhatsApp (mêmes blueprints qu'api_seule) ──
# Le serveur d'API autonome (api_seule.py) est le point d'entrée NORMAL pour
# l'app Android et le chatbot : il démarre en 2 s, sans modèle. On monte ici les
# MÊMES blueprints pour qu'un seul serveur suffise quand l'interface web tourne
# déjà — une seule implémentation, deux hôtes.
#
# La seule différence : ici le TTS est chargé, donc une voix absente du cache
# peut être fabriquée à la demande au lieu de renvoyer audio_url: null.
def _audio_v1(bloc: dict, demo=None):
    """Voix du bloc : synthèse à la volée si les modèles sont prêts, sinon on
    se rabat sur le cache disque. Ne lève jamais — un souci de TTS ne doit pas
    faire échouer une réponse de l'API."""
    demo = demo or demo_demarches
    if not modeles_prets:
        return api_demarches.audio_du_cache(bloc, demo)
    try:
        return _audio_script(bloc, demo)
    except Exception as e:
        print(f"[JPOPE] voix API différée ({bloc.get('cle')}) : {e}")
        return api_demarches.audio_du_cache(bloc, demo)


import api_demarches
import api_assistant
import webhook_whatsapp

api_demarches.init_api(app, {"audio_url": _audio_v1})
webhook_whatsapp.init_webhook(app)


# ── API mobile de l'ASSISTANT COMPLET (voix, cerveau, image) ──────────────────
# Ces routes-là ne peuvent PAS vivre dans api_seule.py : elles ont besoin de
# l'ASR et du TTS, donc des modèles chargés ici. api_assistant.py n'importe
# rien du pipeline — il reçoit ce dont il a besoin, et rien de plus.
def _chemin_temporaire(prefixe: str, extension: str) -> str:
    return str(SORTIES_DIR / f"{prefixe}_{uuid.uuid4().hex[:8]}{extension}")


api_assistant.init_assistant(app, {
    "creer_job":     creer_job,
    "jobs":          jobs,
    "jobs_lock":     jobs_lock,
    "lancer_thread": lambda cible, *args: threading.Thread(
        target=cible, args=args, daemon=True).start(),
    "bg_texte":      lambda job_id, texte: _bg_texte(job_id, texte),
    "bg_audio":      lambda job_id, chemin: _bg_audio(job_id, chemin),
    "bg_image":      lambda job_id, octets, mime: _bg_image(job_id, octets, mime),
    "modeles_prets": lambda: modeles_prets,
    "chemin_temporaire": _chemin_temporaire,
})


# ── ANCIENNE démo gestion « langage libre » (PLUS APPELÉE PAR L'INTERFACE) ────
# La démonstration de gestion est désormais SCRIPTÉE (demo_gestion.py, route
# /api/gestion/message ci-dessus). Ces deux routes détectaient l'intention avec
# un LLM puis reformulaient la réponse en éwé — d'où une formulation variable et
# une consommation de quota. Elles sont conservées telles quelles pour pouvoir
# revenir en arrière, mais static/gestion.js ne les appelle plus.
@app.route("/api/gestion/texte", methods=["POST"])
@login_required_api
def gestion_texte():
    if not modeles_prets:
        return jsonify({"erreur": "Modèles en cours de chargement — patientez."}), 503
    data = request.get_json() or {}
    texte = data.get("texte", "").strip()
    if not texte:
        return jsonify({"erreur": "Texte vide"}), 400
    job_id = creer_job()
    threading.Thread(target=_bg_gestion_texte, args=(job_id, texte),
                     daemon=True).start()
    return jsonify({"job_id": job_id})


@app.route("/api/gestion/audio", methods=["POST"])
@login_required_api
def gestion_audio():
    if not modeles_prets:
        return jsonify({"erreur": "Modèles en cours de chargement — patientez."}), 503
    if "audio" not in request.files:
        return jsonify({"erreur": "Fichier audio manquant"}), 400
    job_id     = creer_job()
    audio_path = str(SORTIES_DIR / f"gestion_{job_id[:8]}.wav")
    request.files["audio"].save(audio_path)
    threading.Thread(target=_bg_gestion_audio, args=(job_id, audio_path),
                     daemon=True).start()
    return jsonify({"job_id": job_id})


@app.route("/api/stream/<job_id>")
@login_required_api
def stream(job_id: str):
    def generer():
        idx      = 0
        deadline = time.time() + 900  # 15 min max

        while time.time() < deadline:
            with jobs_lock:
                job = jobs.get(job_id)

            if job is None:
                yield f"data: {json.dumps({'type': 'erreur', 'message': 'Job introuvable'})}\n\n"
                return

            with jobs_lock:
                new_events = job["events"][idx:]
                is_done    = job["done"]

            for ev in new_events:
                yield f"data: {json.dumps(ev)}\n\n"
                idx += 1

            if is_done and not new_events:
                return

            # Heartbeat pour maintenir la connexion ouverte
            yield ": ping\n\n"
            time.sleep(0.5)

    return Response(
        generer(),
        mimetype="text/event-stream",
        headers={
            "Cache-Control":    "no-cache",
            "X-Accel-Buffering": "no",
            "Connection":       "keep-alive",
        },
    )


@app.route("/audio/<path:filename>")
def servir_audio(filename: str):
    # Sécurité : chemin strict dans sorties/
    safe = Path(filename).name
    path = SORTIES_DIR / safe
    if not path.exists():
        return "Fichier introuvable", 404
    return send_file(str(path), mimetype="audio/wav")


@app.route("/ca")
def telecharger_ca():
    """Sert le certificat de l'AUTORITÉ LOCALE (rootCA) à installer sur le
    téléphone pour supprimer l'avertissement « connexion non privée »."""
    ca = BASE / "rootCA.crt"
    if not ca.exists():
        return "Aucun certificat CA (mode HTTP ?).", 404
    return send_file(str(ca), mimetype="application/x-x509-ca-cert",
                     as_attachment=True, download_name="JPOPE-CA.crt")


# ── Traitements en arrière-plan ───────────────────────────────────────────────
def _infos_analyse(texte_entree: str) -> dict:
    """Langue détectée par tour() + texte normalisé (si différent de l'entrée),
    pour affichage dans l'interface."""
    infos = {"langue": cerveau.langue_detectee}
    norm = cerveau.derniere_normalisation
    if norm and norm.get("texte") and norm["texte"] != texte_entree.strip():
        infos["normalisation"] = norm["texte"]
    return infos


def _tts_url(rep_ewe: str, job_id: str) -> str:
    """Synthétise la réponse et renvoie son URL /audio/…
    Sortie en 44,1 kHz PCM 16 bits (via sauver_audio) : lue correctement par
    tous les navigateurs, y compris iOS Safari (le 16 kHz brut affichait 00:00)."""
    wav, sr = tts_ewe(rep_ewe, tts_tok, tts_model)
    filename = f"web_{job_id[:8]}.wav"
    sauver_audio(wav, sr, str(SORTIES_DIR / filename))
    return f"/audio/{filename}"


def _repondre_conversation(job_id: str, texte: str, extras: dict = None,
                           montrer_meta: bool = True):
    """Réponse de CONVERSATION (comportement historique, INCHANGÉ) :
    tour() → TTS → événement « termine ». `montrer_meta=False` masque la ligne
    langue/normalisé (utile pour l'image : `texte` y est une consigne interne,
    pas le contenu à montrer)."""
    push_event(job_id, {"type": "etape", "message": "Génération de la réponse (LLM)..."})
    rep_ewe, rep_fr = tour(texte, langue="ewe")   # interface = ÉWÉ uniquement
    analyse = _infos_analyse(texte) if montrer_meta else {}
    if analyse:
        push_event(job_id, {"type": "analyse", **analyse})
    push_event(job_id, {"type": "etape", "message": "Synthèse vocale (TTS ~1 min)..."})
    audio_url = _tts_url(rep_ewe, job_id)
    push_event(job_id, {"type": "termine", "rep_ewe": rep_ewe, "rep_fr": rep_fr,
                        "audio_url": audio_url, **analyse, **(extras or {})})


def _traiter_message(job_id: str, texte: str, extras: dict = None):
    """Étape ADDITIVE : détecte l'intention (action vs conversation), puis
    dispatche. Une action = confirmation parlée en éwé + élément cliquable ;
    sinon on retombe sur la conversation normale (inchangée)."""
    from datetime import datetime
    extras = extras or {}
    push_event(job_id, {"type": "etape", "message": "Analyse de la demande..."})
    intent = intentions.detecter_intention(
        texte, maintenant=datetime.now().strftime("%Hh%M"))
    typ = (intent.get("intention") or "conversation").lower()

    # Conversation (ou classification vide) → pipeline habituel, INCHANGÉ.
    if typ == "conversation" or not (intent.get("ewe") or "").strip():
        _repondre_conversation(job_id, texte, extras)
        return

    rep_ewe = intent.get("ewe", "").strip()
    rep_fr = intent.get("fr", "").strip()
    push_event(job_id, {"type": "etape", "message": "Confirmation vocale (TTS)..."})
    audio_url = _tts_url(rep_ewe, job_id)
    ev = {"rep_ewe": rep_ewe, "rep_fr": rep_fr, "audio_url": audio_url, **extras}

    if typ == "appel" and (intent.get("numero") or "").strip():
        ev.update(type="action", action="appel", numero=str(intent["numero"]).strip())
    elif typ == "recherche" and (intent.get("requete") or "").strip():
        from urllib.parse import quote
        req = intent["requete"].strip()
        if (intent.get("type_recherche") or "web") == "carte":
            url = f"https://www.google.com/maps/search/?api=1&query={quote(req)}"
        else:
            url = f"https://www.google.com/search?q={quote(req)}"
        ev.update(type="action", action="recherche", requete=req, url=url)
    else:
        # info / ambigu → simple réponse parlée+affichée (pas d'élément cliquable)
        ev.update(type="termine")
    push_event(job_id, ev)


def _bg_texte(job_id: str, texte: str):
    try:
        # Commande d'apprentissage « 123 ... » : inchangé, sans intention.
        if apprentissage.est_commande(texte):
            push_event(job_id, {"type": "etape",
                                "message": "Apprentissage en cours..."})
            confirmation = apprentissage.traiter_commande(texte)
            push_event(job_id, {
                "type":    "termine",
                "rep_ewe": confirmation,
                "rep_fr":  "Enregistré dans mots_appris.csv / "
                           "phrases_apprises.csv — utilisable dès le "
                           "prochain message.",
                "langue":  "apprentissage",
            })
            return
        _traiter_message(job_id, texte)
    except Exception as e:
        push_event(job_id, {"type": "erreur", "message": str(e)})
    finally:
        finir_job(job_id)


def _bg_audio(job_id: str, audio_path: str):
    global asr_proc, asr_model
    try:
        push_event(job_id, {"type": "etape", "message": "Transcription ASR en cours (~2 min)..."})
        config = lire_config_asr()
        if config["mode"] == "local" and (asr_proc is None or asr_model is None):
            push_event(job_id, {"type": "etape", "message": "Chargement ASR local (~30s)..."})
            asr_proc, asr_model = charger_asr()
        texte_ewe = transcrire_audio(audio_path, asr_proc, asr_model)

        if not texte_ewe:
            push_event(job_id, {"type": "erreur", "message": "ASR vide — audio trop court ou silencieux ?"})
            return

        push_event(job_id, {"type": "transcription", "texte": texte_ewe})
        try:
            os.remove(audio_path)
        except OSError:
            pass
        # Voix → détection d'intention (action ou conversation), transcription
        # gardée pour l'affichage.
        _traiter_message(job_id, texte_ewe, extras={"transcription": texte_ewe})
    except Exception as e:
        push_event(job_id, {"type": "erreur", "message": str(e)})
    finally:
        finir_job(job_id)


def _bg_image(job_id: str, image_bytes: bytes, mime: str):
    """Photo de texte : Gemini LIT le texte de l'image, puis le texte extrait
    passe dans le pipeline habituel (compréhension → réponse éwé → TTS)."""
    try:
        push_event(job_id, {"type": "etape",
                            "message": "Lecture du texte dans l'image..."})
        res = gemini_llm.lire_texte_image(image_bytes, mime_type=mime)

        if res["erreur"]:
            push_event(job_id, {"type": "erreur",
                                "message": f"Lecture de l'image impossible : {res['erreur']}"})
            return

        # Pas de texte lisible / trop long / flou → message POLI parlé en éwé.
        if not res["lisible"] or not res["texte"].strip():
            rep_ewe = ("Nyemate ŋu axlẽ nuŋɔŋlɔ sia nyuie o. Taflatse, tsɔ foto "
                       "bubu si me kɔ nyuie na nyagbe kpui aɖe.")
            rep_fr = ("Je n'arrive pas à bien lire ce texte. Essaie avec une "
                      "photo plus nette d'une phrase courte.")
            push_event(job_id, {"type": "etape", "message": "Synthèse vocale (TTS)..."})
            audio_url = _tts_url(rep_ewe, job_id)
            push_event(job_id, {
                "type":        "termine",
                "texte_image": "(texte illisible)",
                "rep_ewe":     rep_ewe,
                "rep_fr":      rep_fr,
                "audio_url":   audio_url,
            })
            return

        texte_extrait = res["texte"].strip()
        push_event(job_id, {"type": "texte_image", "texte": texte_extrait})
        # Consigne INTERNE (jamais affichée) : lecture FIDÈLE, sans rien inventer.
        # montrer_meta=False → la ligne « normalisé » n'affiche pas cette consigne.
        entree = ("Voici un texte lu dans une photo. Donne-le fidèlement dans la "
                  "langue de l'utilisateur, sans ajouter d'information, de détail "
                  f"ni d'explication : « {texte_extrait} »")
        _repondre_conversation(job_id, entree, extras={"texte_image": texte_extrait},
                               montrer_meta=False)
    except Exception as e:
        push_event(job_id, {"type": "erreur", "message": str(e)})
    finally:
        finir_job(job_id)


# ── DÉMO gestion : traitement en arrière-plan (couche isolée) ─────────────────
def _bg_gestion(job_id: str, texte: str, extras: dict = None):
    """Interroge / met à jour les données de gestion à partir d'une demande
    (voix transcrite ou texte), puis renvoie la réponse parlée en éwé + FR."""
    from datetime import datetime
    push_event(job_id, {"type": "etape", "message": "Analyse de la demande..."})
    res = gestion.traiter(texte, maintenant=datetime.now().strftime("%Hh%M"))
    push_event(job_id, {"type": "etape", "message": "Synthèse vocale (TTS)..."})
    audio_url = _tts_url(res["rep_ewe"], job_id)
    ev = {"type": "termine", "rep_ewe": res["rep_ewe"], "rep_fr": res["rep_fr"],
          "audio_url": audio_url}
    if extras:
        ev.update(extras)
    push_event(job_id, ev)


def _bg_gestion_texte(job_id: str, texte: str):
    try:
        _bg_gestion(job_id, texte)
    except Exception as e:
        push_event(job_id, {"type": "erreur", "message": str(e)})
    finally:
        finir_job(job_id)


def _bg_gestion_audio(job_id: str, audio_path: str):
    global asr_proc, asr_model
    try:
        push_event(job_id, {"type": "etape", "message": "Transcription ASR en cours (~2 min)..."})
        config = lire_config_asr()
        if config["mode"] == "local" and (asr_proc is None or asr_model is None):
            push_event(job_id, {"type": "etape", "message": "Chargement ASR local (~30s)..."})
            asr_proc, asr_model = charger_asr()
        texte_ewe = transcrire_audio(audio_path, asr_proc, asr_model)
        if not texte_ewe:
            push_event(job_id, {"type": "erreur", "message": "ASR vide — audio trop court ou silencieux ?"})
            return
        push_event(job_id, {"type": "transcription", "texte": texte_ewe})
        try:
            os.remove(audio_path)
        except OSError:
            pass
        _bg_gestion(job_id, texte_ewe, extras={"transcription": texte_ewe})
    except Exception as e:
        push_event(job_id, {"type": "erreur", "message": str(e)})
    finally:
        finir_job(job_id)


# ── Lancement ─────────────────────────────────────────────────────────────────
def _ip_locale():
    """IP du PC sur le réseau local (pour l'accès depuis le téléphone)."""
    import socket
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))   # pas de trafic réel : juste pour lire l'IP
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return None


if __name__ == "__main__":
    os.makedirs(SORTIES_DIR, exist_ok=True)

    ip = _ip_locale()

    # HTTPS si le certificat auto-signé est présent (interface_web/ssl_cert.*).
    # Indispensable pour que le MICRO marche dans le navigateur du TÉLÉPHONE :
    # getUserMedia exige un contexte sécurisé (HTTPS) hors du PC lui-même.
    cert = BASE / "ssl_cert.crt"
    key  = BASE / "ssl_cert.key"
    ssl_ctx = (str(cert), str(key)) if cert.exists() and key.exists() else None
    proto = "https" if ssl_ctx else "http"

    print("=" * 60)
    print("  JPOPE — Interface Web (ÉWÉ uniquement)")
    print(f"  Sur ce PC       : {proto}://localhost:5000")
    if ip:
        print(f"  Sur TÉLÉPHONE   : {proto}://{ip}:5000")
        print("     (même Wi-Fi / partage de connexion)")
    if ssl_ctx:
        if ip:
            print(f"  Certificat 1x : ouvre https://{ip}:5000/ca sur le téléphone")
            print("         pour installer l'autorité → plus AUCUNE alerte ensuite.")
        print("  (sinon : accepte l'avertissement « continuer », le micro marche aussi.)")
    else:
        print("  [!] Pas de certificat → HTTP : le micro ne marchera QUE sur le PC.")
    print("=" * 60)

    # Chargement des modèles en arrière-plan (Flask répond immédiatement)
    threading.Thread(target=initialiser_modeles, daemon=True).start()

    app.run(host="0.0.0.0", port=5000, debug=False, threaded=True, ssl_context=ssl_ctx)
