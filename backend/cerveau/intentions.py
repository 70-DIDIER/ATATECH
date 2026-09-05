"""Détection d'INTENTION — conversation vs action (contrôle vocal léger).

Additif : n'affecte PAS la conversation normale. La classification est faite par
Gemini ; en cas d'échec (erreur, JSON invalide) → « conversation » (repli sûr :
on ne casse jamais le comportement actuel).

Actions gérées (réalisables dans un NAVIGATEUR — pas d'appel auto ni de capture
système, interdits par le navigateur ; l'utilisateur garde le dernier mot) :
  - appel      : « appelle le 117 »        → lien tel: (l'utilisateur valide)
  - recherche  : « montre-moi une pharmacie » → onglet Maps/Recherche
  - info       : heure / petit calcul      → réponse affichée + parlée
  - ambigu     : demande floue             → question de clarification
  - conversation : tout le reste           → pipeline habituel inchangé

API :
    detecter_intention(texte, maintenant="Hh MM") -> dict
        {"intention": ..., "numero"?, "requete"?, "type_recherche"?,
         "ewe": <ce que l'assistant DIT>, "fr": <idem en français>}
"""

import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import gemini_llm

_CONSIGNE = (
    "Tu es le détecteur d'intention d'un assistant vocal en langue éwé/mina "
    "(Togo). L'utilisateur parle en éwé ou en mina. Détermine s'il demande une "
    "ACTION réalisable dans un navigateur web, ou si c'est une CONVERSATION "
    "normale.\n"
    "Actions possibles :\n"
    "- appel : appeler un numéro de téléphone. Extrais le NUMÉRO (chiffres "
    "seulement). Si le numéro manque ou est incomplet → intention \"ambigu\".\n"
    "- recherche : chercher ou voir un lieu / une information. Extrais la "
    "requête. type_recherche=\"carte\" si c'est un LIEU (pharmacie, hôpital, "
    "marché, restaurant, banque…), sinon \"web\".\n"
    "- info : demande l'HEURE ou un CALCUL simple. Donne la réponse toi-même. "
    "Heure actuelle : {maintenant}.\n"
    "- conversation : tout le reste (salutations, discussion, questions "
    "générales, quelqu'un qui raconte sa journée…).\n\n"
    "RÈGLE IMPORTANTE — dans le DOUTE, choisis TOUJOURS \"conversation\". "
    "Ne classe en ACTION QUE si l'utilisateur DEMANDE EXPLICITEMENT l'action "
    "avec un verbe à l'impératif (« appelle… », « cherche… », « montre-moi… », "
    "« trouve… », « où est… », « quelle heure… », « combien font… »). "
    "Si l'utilisateur RACONTE ou CONSTATE quelque chose, ce n'est PAS une action : "
    "« j'ai vendu du pain », « je vais au marché », « j'ai faim », « il fait chaud » "
    "→ conversation. Un simple nom de chose ou de lieu dans une phrase ne suffit "
    "PAS à déclencher une recherche : il faut une vraie DEMANDE de recherche.\n"
    "En cas de doute réel (demande floue, numéro incomplet) → \"ambigu\" et pose "
    "une question de clarification, au lieu de deviner. Mieux vaut demander que "
    "déclencher la mauvaise action.\n\n"
    "Réponds UNIQUEMENT un objet JSON, sans aucun texte autour :\n"
    "{\n"
    '  "intention": "conversation" | "appel" | "recherche" | "info" | "ambigu",\n'
    '  "numero": "<chiffres>",           (si appel)\n'
    '  "requete": "<quoi chercher>",     (si recherche)\n'
    '  "type_recherche": "carte" | "web",(si recherche)\n'
    '  "ewe": "<ce que l\'assistant DIT en éwé : confirmation de l\'action, '
    'réponse de l\'info, ou question si ambigu>",\n'
    '  "fr": "<la même chose en français>"\n'
    "}\n"
    "Pour une ACTION, « ewe »/« fr » ANNONCENT ce que tu vas faire (ex. "
    "« Mele ka ƒoƒo na 117 dzram ɖo » / « Je prépare l'appel vers le 117 »). "
    "Pour « conversation », laisse « ewe » et « fr » VIDES (le message ira au "
    "cerveau normal)."
)


def detecter_intention(texte: str, maintenant: str = "?") -> dict:
    """Classe le message. Ne lève jamais : repli « conversation » si échec."""
    repli = {"intention": "conversation", "ewe": "", "fr": ""}
    try:
        systeme = _CONSIGNE.replace("{maintenant}", str(maintenant))
        r = gemini_llm.completion_gemini(systeme=systeme, texte=texte,
                                         temperature=0)
        if r.get("erreur") or not r.get("texte"):
            return repli
        m = re.search(r"\{.*\}", r["texte"], re.DOTALL)
        if not m:
            return repli
        data = json.loads(m.group(0))
        if isinstance(data, dict) and data.get("intention"):
            data.setdefault("ewe", "")
            data.setdefault("fr", "")
            return data
    except Exception:
        pass
    return repli
