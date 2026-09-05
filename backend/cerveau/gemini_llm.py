"""Client Gemini (Google AI Studio) — OPTION, n'affecte PAS le cerveau Groq.

Bibliothèque officielle à jour : `google-genai` (`from google import genai`).
L'ancienne `google-generativeai` est dépréciée — on ne l'utilise pas.

Clé : GEMINI_API_KEY dans cerveau/.env (jamais en clair dans le code).
À créer sur https://aistudio.google.com/apikey (bouton « Create API key »).

API :
    disponible() -> bool
    completion_gemini(systeme, texte, grounding=False, model=...) -> dict
        {"texte": str, "sources": [(titre, url), ...], "requetes": [...],
         "erreur": str|None}

`grounding=True` active l'outil Google Search intégré (recherche web) : Gemini
peut chercher sur le web avant de répondre, et renvoie les sources utilisées.
"""

import json
import os
import sys
import time as _time
from datetime import datetime, timedelta, timezone
from pathlib import Path

from dotenv import load_dotenv

_env_path = Path(__file__).parent / ".env"
load_dotenv(_env_path)

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")

# Le palier GRATUIT limite à ~20 requêtes/JOUR PAR MODÈLE (ex. gemini-3.5-flash).
# Parade : liste de modèles essayés dans l'ordre — chacun a son PROPRE quota
# journalier, donc en cas de 429 (quota) ou 503 (surcharge) on passe au suivant.
# « lite » en tête : quota gratuit généralement plus large, latence plus basse.
MODELES_GEMINI = [
    "gemini-flash-lite-latest",  # quota gratuit le plus large, rapide
    "gemini-flash-latest",       # = gemini-3.5-flash (~20/jour)
    "gemini-3-flash-preview",    # 3e réserve
]
MODELE_DEFAUT = MODELES_GEMINI[0]

# Erreurs qui justifient d'essayer le modèle SUIVANT (quota/surcharge/transitoire).
_ERR_ROTATION = ("429", "resource_exhausted", "503", "unavailable",
                 "overloaded", "high demand", "500", "internal")

_client = None

# ══════════════════════════════════════════════════════════════════════════════
# SUIVI DE CONSOMMATION (quota) — voir QUOTAS_GEMINI.md
# ══════════════════════════════════════════════════════════════════════════════
# Limites du palier GRATUIT. Confirmé par l'erreur 429 : 20 requêtes/JOUR/modèle.
# La minute (RPM) n'est pas garantie par la doc — valeur prudente pour l'alerte.
LIMITE_JOUR_PAR_MODELE = 20
LIMITE_MINUTE_PAR_MODELE = 10
SEUIL_ALERTE = 0.80            # prévenir à 80 % du budget journalier

# Google réinitialise le quota à MINUIT heure du Pacifique. On date donc chaque
# requête par « jour du Pacifique » (approx PDT = UTC-7 ; léger décalage l'hiver).
_PACIFIQUE = timezone(timedelta(hours=-7))
_USAGE_FICHIER = Path(__file__).parent / ".gemini_usage.json"
_usage = None                 # liste [ [timestamp_epoch, modele], ... ]
_alerte_jour_emise = False     # pour ne prévenir qu'une fois par session


def _jour_pac(ts) -> str:
    return datetime.fromtimestamp(ts, _PACIFIQUE).strftime("%Y-%m-%d")


def _charger_usage():
    global _usage
    if _usage is None:
        try:
            _usage = json.loads(_USAGE_FICHIER.read_text(encoding="utf-8"))
        except Exception:
            _usage = []
    # purge des entrées de plus de 2 jours (garde le fichier petit)
    limite = _time.time() - 2 * 24 * 3600
    _usage = [e for e in _usage if e and e[0] >= limite]
    return _usage


def _enregistrer_usage(modele):
    """Compte une requête Gemini réussie ; alerte à 80 % du quota journalier."""
    global _alerte_jour_emise
    u = _charger_usage()
    u.append([_time.time(), modele])
    try:
        _USAGE_FICHIER.write_text(json.dumps(u), encoding="utf-8")
    except Exception:
        pass
    st = usage_stats()
    if (not _alerte_jour_emise
            and st["total_jour"] >= SEUIL_ALERTE * st["budget_jour"]):
        _alerte_jour_emise = True
        print(f"\n⚠️  ALERTE QUOTA GEMINI : {st['total_jour']}/{st['budget_jour']} "
              f"requêtes utilisées aujourd'hui ({st['pourcent_jour']} %). "
              f"Proche de la limite gratuite — au-delà, bascule automatique sur "
              f"Groq. (détails : QUOTAS_GEMINI.md)\n", file=sys.stderr)


def usage_stats() -> dict:
    """Consommation Gemini : par modèle et total, pour la minute et le jour
    (jour du Pacifique, comme le compte Google)."""
    u = _charger_usage()
    now = _time.time()
    jour_now = _jour_pac(now)
    debut_min = now - 60
    par_modele = {}
    for ts, m in u:
        d = par_modele.setdefault(m, {"jour": 0, "minute": 0})
        if _jour_pac(ts) == jour_now:
            d["jour"] += 1
        if ts >= debut_min:
            d["minute"] += 1
    total_jour = sum(d["jour"] for d in par_modele.values())
    total_min = sum(d["minute"] for d in par_modele.values())
    budget_jour = LIMITE_JOUR_PAR_MODELE * len(MODELES_GEMINI)
    return {
        "par_modele": par_modele,
        "total_jour": total_jour, "total_minute": total_min,
        "budget_jour": budget_jour,
        "limite_jour_modele": LIMITE_JOUR_PAR_MODELE,
        "limite_minute_modele": LIMITE_MINUTE_PAR_MODELE,
        "pourcent_jour": round(100 * total_jour / max(budget_jour, 1)),
    }


def ligne_conso() -> str:
    """Ligne courte prête à afficher (compteur minute + jour)."""
    st = usage_stats()
    return (f"Gemini — aujourd'hui : {st['total_jour']}/{st['budget_jour']} "
            f"requêtes ({st['pourcent_jour']} %) | cette minute : "
            f"{st['total_minute']}/{st['limite_minute_modele']*len(MODELES_GEMINI)}")


def disponible() -> bool:
    """Vrai si une clé Gemini est configurée dans cerveau/.env."""
    return bool(GEMINI_API_KEY and GEMINI_API_KEY != "ta_cle_api_gemini_ici")


def _get_client():
    global _client
    if _client is None:
        if not disponible():
            raise RuntimeError(
                "GEMINI_API_KEY manquante dans cerveau/.env "
                "(créer la clé sur https://aistudio.google.com/apikey)"
            )
        from google import genai
        _client = genai.Client(api_key=GEMINI_API_KEY)
    return _client


def _un_appel(client, model, systeme, texte, grounding, temperature) -> dict:
    """Un appel Gemini sur UN modèle donné. Lève en cas d'erreur (gérée par
    l'appelant qui décide de changer de modèle)."""
    from google.genai import types
    cfg = {"system_instruction": systeme, "temperature": temperature}
    if grounding:
        cfg["tools"] = [types.Tool(google_search=types.GoogleSearch())]
    resp = client.models.generate_content(
        model=model, contents=texte,
        config=types.GenerateContentConfig(**cfg),
    )
    out = {"texte": (resp.text or "").strip(), "sources": [], "requetes": [],
           "modele": model, "erreur": None}
    if grounding:
        try:
            gm = getattr(resp.candidates[0], "grounding_metadata", None)
            if gm:
                for ch in (getattr(gm, "grounding_chunks", None) or []):
                    web = getattr(ch, "web", None)
                    if web:
                        out["sources"].append((getattr(web, "title", "") or "",
                                               getattr(web, "uri", "") or ""))
                out["requetes"] = list(getattr(gm, "web_search_queries", None) or [])
        except Exception:
            pass
    return out


def _messages_to_contents(messages):
    """Convertit un historique OpenAI/Groq ([{role, content}]) au format
    MULTI-TOURS de Gemini : instruction système séparée + liste de tours avec
    rôles (« user »/« model »). CRUCIAL : préserve « qui a dit quoi » et le
    message COURANT (sinon Gemini reçoit un bloc collé et répond génériquement)."""
    from google.genai import types
    systeme = "\n".join(m["content"] for m in messages if m["role"] == "system")
    contents = []
    for m in messages:
        if m["role"] == "system":
            continue
        role = "model" if m["role"] == "assistant" else "user"
        contents.append(types.Content(role=role,
                                      parts=[types.Part(text=m["content"])]))
    return systeme, contents


def completion_gemini(systeme: str = None, texte: str = None, messages=None,
                      grounding: bool = False, model: str = None,
                      temperature: float = 0.3) -> dict:
    """Appel Gemini avec BASCULE DE MODÈLE : essaie MODELES_GEMINI dans l'ordre
    (chacun a son propre quota gratuit de ~20/jour) ; sur 429/503 on passe au
    suivant. `model` explicite = ce modèle seul. grounding=True active la
    recherche web Google. Jamais d'exception : erreurs dans "erreur".

    Deux usages :
      - completion_gemini(systeme, texte)     : un seul tour (banc de test) ;
      - completion_gemini(messages=[...])     : CONVERSATION multi-tours (cerveau)
        → format multi-tours correct (rôles user/model), pas un bloc collé."""
    modeles = [model] if model else list(MODELES_GEMINI)
    resultat = {"texte": "", "sources": [], "requetes": [], "modele": None,
                "erreur": None}
    try:
        client = _get_client()
    except Exception as e:
        resultat["erreur"] = f"{type(e).__name__}: {e}"
        return resultat

    if messages is not None:
        systeme, contents = _messages_to_contents(messages)
    else:
        contents = texte

    derniere_err = None
    for i, mod in enumerate(modeles):
        try:
            out = _un_appel(client, mod, systeme, contents, grounding, temperature)
            if out["texte"]:
                _enregistrer_usage(mod)          # compteur de quota
                if i > 0:
                    print(f"[gemini] quota/surcharge du modèle précédent "
                          f"→ bascule sur {mod}", file=sys.stderr)
                return out
            derniere_err = f"réponse vide ({mod})"
        except Exception as e:
            derniere_err = f"{type(e).__name__}: {e}"
            # 429/503 → essayer le modèle suivant ; autre erreur → arrêter.
            if not any(m in derniere_err.lower() for m in _ERR_ROTATION):
                break
    resultat["erreur"] = derniere_err
    return resultat


# ── Lecture de texte dans une IMAGE (multimodal) ─────────────────────────────
# Longueur max du texte extrait : au-delà, c'est un document, pas une phrase
# courte → on refuse poliment (on reste « petit » comme demandé).
LONGUEUR_TEXTE_IMAGE_MAX = 500


def lire_texte_image(image_bytes: bytes, mime_type: str = "image/jpeg",
                     model: str = None) -> dict:
    """Envoie l'IMAGE à Gemini (multimodal) pour EXTRAIRE un texte COURT.
    Réutilise le même client + la bascule de modèles + le compteur de quota.
    Retourne {"texte": str, "lisible": bool, "modele": str|None, "erreur": str|None}.
    lisible=False si l'image n'a pas de texte lisible, est floue, ou trop longue
    (document) — l'appelant affiche alors un message poli sans rien inventer."""
    from google.genai import types
    consigne = (
        "Tu lis le texte présent dans cette image. C'est censé être une PHRASE "
        "COURTE (une pancarte, une étiquette, un petit mot, une phrase). "
        "Extrais le texte EXACTEMENT tel qu'il apparaît, sans rien ajouter, "
        "corriger ni traduire. Réponds UNIQUEMENT le texte lu.\n"
        "Si l'image ne contient AUCUN texte lisible, si le texte est trop FLOU "
        "pour être lu de façon fiable, ou si c'est un DOCUMENT ENTIER (trop "
        "long), réponds EXACTEMENT le seul mot : ILLISIBLE"
    )
    resultat = {"texte": "", "lisible": False, "modele": None, "erreur": None}
    try:
        client = _get_client()
    except Exception as e:
        resultat["erreur"] = f"{type(e).__name__}: {e}"
        return resultat

    contents = [types.Part.from_bytes(data=image_bytes, mime_type=mime_type),
                consigne]
    modeles = [model] if model else list(MODELES_GEMINI)
    derniere_err = None
    for i, mod in enumerate(modeles):
        try:
            resp = client.models.generate_content(
                model=mod, contents=contents,
                config=types.GenerateContentConfig(temperature=0),
            )
            txt = (resp.text or "").strip()
            if txt:
                _enregistrer_usage(mod)          # une image compte dans le quota
                if i > 0:
                    print(f"[gemini] image : bascule sur {mod}", file=sys.stderr)
                resultat["modele"] = mod
                nettoye = txt.strip().strip(".").upper()
                if nettoye == "ILLISIBLE" or len(txt) > LONGUEUR_TEXTE_IMAGE_MAX:
                    resultat["lisible"] = False
                else:
                    resultat["texte"] = txt
                    resultat["lisible"] = True
                return resultat
            derniere_err = f"réponse vide ({mod})"
        except Exception as e:
            derniere_err = f"{type(e).__name__}: {e}"
            if not any(m in derniere_err.lower() for m in _ERR_ROTATION):
                break
    resultat["erreur"] = derniere_err
    return resultat


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    print("Gemini disponible :", disponible())
    if disponible():
        r = completion_gemini("Tu réponds en français, court.",
                              "Dis bonjour.", grounding=False)
        print(r)
