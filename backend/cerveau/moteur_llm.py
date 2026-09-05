"""Dispatcheur de MOTEUR LLM — Groq (défaut) ou Gemini, en OPTION.

But : pouvoir choisir le moteur SANS toucher au cerveau Groq existant. Ce
module est additif ; supprimer ce fichier + gemini_llm.py = retour exact à
l'état d'origine (rollback instantané).

    completion(messages, moteur="groq", grounding=False, ...) -> dict
        {"texte": str, "sources": [...], "moteur": str, "erreur": str|None}

  - moteur="groq"   : passe par normalisation.completion_groq (bascule de
                      modèle automatique) — comportement ACTUEL, inchangé.
  - moteur="gemini" : passe par gemini_llm.completion_gemini, avec grounding
                      (recherche web) activable.

`messages` = format OpenAI/Groq : [{"role": "system"|"user"|"assistant",
"content": ...}]. Pour Gemini, on extrait le message système et on concatène
le reste comme contenu utilisateur (le banc envoie des tours simples)."""

import os
import sys
import time
from pathlib import Path

from dotenv import load_dotenv

sys.path.insert(0, str(Path(__file__).parent))
load_dotenv(Path(__file__).parent / ".env")

from normalisation import completion_groq
import gemini_llm

# ── Réglage GLOBAL du moteur du cerveau ──────────────────────────────────────
# Défaut = Gemini (bien meilleur sur éwé/mina, cf. compare_moteurs.py), avec
# REPLI automatique sur Groq si Gemini échoue (quota, clé absente, réseau).
# ROLLBACK INSTANTANÉ : remettre "groq" ci-dessous = comportement d'origine.
MOTEUR_DEFAUT = "gemini"
GROUNDING_DEFAUT = False   # recherche web désactivée (inutile + hors gratuit)

dernier_moteur = None      # moteur réellement utilisé au dernier appel (observabilité)

_groq_client = None


def _client_groq():
    global _groq_client
    if _groq_client is None:
        from groq import Groq
        cle = os.getenv("GROQ_API_KEY")
        if not cle or cle == "ta_cle_api_groq_ici":
            raise RuntimeError("GROQ_API_KEY manquante dans cerveau/.env")
        _groq_client = Groq(api_key=cle)
    return _groq_client


def _messages_vers_gemini(messages):
    """Sépare (system_instruction, contenu_utilisateur) depuis un format chat."""
    systeme = "\n".join(m["content"] for m in messages if m["role"] == "system")
    corps = "\n".join(m["content"] for m in messages if m["role"] != "system")
    return systeme, corps


def completion(messages, moteur: str = "groq", grounding: bool = False,
               temperature: float = 0.3, max_tokens: int = 512) -> dict:
    """Point d'entrée unique. Défaut moteur='groq' → comportement actuel."""
    if moteur == "groq":
        try:
            texte = completion_groq(_client_groq(), messages,
                                    temperature=temperature, max_tokens=max_tokens)
            if not texte:
                return {"texte": "", "sources": [], "moteur": "groq",
                        "erreur": "quota Groq épuisé sur tous les modèles"}
            return {"texte": texte, "sources": [], "moteur": "groq", "erreur": None}
        except Exception as e:
            return {"texte": "", "sources": [], "moteur": "groq",
                    "erreur": f"{type(e).__name__}: {e}"}

    if moteur == "gemini":
        # Conversation MULTI-TOURS structurée (rôles user/model) — PAS un bloc
        # collé : sinon Gemini perd le fil et répond génériquement.
        r = gemini_llm.completion_gemini(messages=messages, grounding=grounding,
                                         temperature=temperature)
        etiquette = "gemini+web" if grounding else "gemini"
        return {"texte": r["texte"], "sources": r["sources"],
                "requetes": r.get("requetes", []),
                "moteur": etiquette, "erreur": r["erreur"]}

    return {"texte": "", "sources": [], "moteur": moteur,
            "erreur": f"moteur inconnu : {moteur!r} (groq | gemini)"}


# Erreurs Gemini TRANSITOIRES (surcharge serveur) : on réessaie Gemini avant
# de lâcher sur Groq, car un 503 est passager et Gemini est préféré.
_MARQUEURS_TRANSITOIRES = ("503", "unavailable", "overloaded", "high demand",
                           "500", "internal", "deadline", "timeout")
GEMINI_REESSAIS = 2       # essais supplémentaires sur erreur transitoire
GEMINI_ATTENTE = 2.5      # secondes entre deux essais (backoff)


def _est_transitoire(err: str) -> bool:
    e = (err or "").lower()
    return any(m in e for m in _MARQUEURS_TRANSITOIRES)


def completion_texte(messages, moteur: str = None, grounding: bool = None,
                     temperature: float = 0.7, max_tokens: int = 512) -> str:
    """Renvoie le TEXTE de la réponse. Point d'entrée du CERVEAU.

    Défaut = MOTEUR_DEFAUT (gemini). Sur erreur TRANSITOIRE de Gemini (503/
    surcharge), réessaie Gemini quelques fois ; sinon REPLI AUTOMATIQUE sur Groq
    (quota, clé absente, réseau, réponse vide). Met à jour `dernier_moteur`.
    Retourne None seulement si TOUS les moteurs échouent."""
    global dernier_moteur
    moteur = moteur or MOTEUR_DEFAUT
    grounding = GROUNDING_DEFAUT if grounding is None else grounding
    ordre = [moteur] + (["groq"] if moteur != "groq" else [])
    derniere_err = None
    for i, m in enumerate(ordre):
        max_essais = (GEMINI_REESSAIS + 1) if m == "gemini" else 1
        for essai in range(max_essais):
            r = completion(messages, moteur=m,
                           grounding=grounding if m == "gemini" else False,
                           temperature=temperature, max_tokens=max_tokens)
            if r["texte"] and not r["erreur"]:
                dernier_moteur = r["moteur"]
                if i > 0:
                    err = (derniere_err or "").lower()
                    if any(x in err for x in ("429", "resource_exhausted", "quota")):
                        print(f"[moteur] ⚠️ QUOTA GEMINI ÉPUISÉ pour aujourd'hui "
                              f"→ bascule automatique sur {m.upper()}. "
                              f"(Gemini reviendra demain — voir QUOTAS_GEMINI.md)",
                              file=sys.stderr)
                    else:
                        print(f"[moteur] Gemini indisponible ({derniere_err}) "
                              f"→ repli sur {m}", file=sys.stderr)
                return r["texte"]
            derniere_err = r["erreur"]
            # Réessai Gemini seulement si erreur transitoire et essais restants
            if (m == "gemini" and essai < max_essais - 1
                    and _est_transitoire(derniere_err)):
                print(f"[moteur] gemini surchargé (503), nouvel essai "
                      f"{essai + 1}/{GEMINI_REESSAIS} dans {GEMINI_ATTENTE}s…",
                      file=sys.stderr)
                time.sleep(GEMINI_ATTENTE)
                continue
            break
    dernier_moteur = None
    print(f"[moteur] AUCUN moteur disponible (dernière erreur : {derniere_err})",
          file=sys.stderr)
    return None
