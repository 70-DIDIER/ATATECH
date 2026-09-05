# Backend de l'agent — copie de référence

Ces fichiers viennent du dépôt backend **Nye Gbe / JPOPE**. Ils sont ici pour
que vous puissiez **lire et comprendre** comment l'agent décide et agit, et
proposer des évolutions.

> ⚠️ **CE N'EST PAS LA SOURCE DE VÉRITÉ.**
> C'est une copie figée, prise le 5 septembre 2026. Le serveur qui tourne
> pendant le hackathon lit le dépôt backend, pas celui-ci. Une correction faite
> ici **n'aura aucun effet** tant qu'elle n'est pas reportée là-bas.
> Si vous modifiez quelque chose, prévenez l'équipe backend.

---

## Ce qui vous intéresse en premier

### `cerveau/intentions.py` — la compréhension

C'est le cœur de la boucle *« commande → intention »*. La phrase de
l'utilisateur part chez Gemini avec une consigne stricte, et revient en JSON :

```json
{
  "intention": "conversation" | "appel" | "recherche" | "info" | "ambigu",
  "numero": "90000000",          // si appel
  "requete": "pharmacie",        // si recherche
  "type_recherche": "carte" | "web",
  "ewe": "ce que l'assistant DIT en éwé",
  "fr":  "la même chose en français"
}
```

Deux propriétés à connaître avant d'y toucher :

- **La fonction ne lève jamais.** Erreur réseau, JSON invalide, quota épuisé :
  elle retombe sur `conversation`. C'est délibéré — un assistant qui plante
  devant un jury est pire qu'un assistant qui bavarde.
- **La consigne est volontairement prudente** : « dans le doute →
  conversation ; action UNIQUEMENT sur une demande explicite à l'impératif ».
  Sans cette règle, « j'ai vendu du pain » était compris comme une recherche.

### `interface_web/server.py` → `_traiter_message()` — la décision

C'est là que l'intention devient une **action**. Aujourd'hui, deux seulement
produisent quelque chose d'exécutable :

| Intention | Ce que le serveur renvoie |
|---|---|
| `appel` + numéro | un lien `tel:` |
| `recherche` + requête | une URL Google Maps (lieu) ou Google (info) |
| `info`, `ambigu` | une réponse parlée, sans action |
| `conversation` | le chemin classique : traduction, cerveau, TTS |

### `interface_web/api_assistant.py` — la porte de l'app

Les routes `/api/v1/assistant/*` que votre application appelle. Elles rendent
un `job_id`, puis vous interrogez `/api/v1/job/<id>` jusqu'à `fini: true`.
La réponse est un objet plat, avec un champ `action` :

```json
{ "type": "appel_numero" | "ouvrir_url" | "aucune",
  "numero": "...", "url": "...", "confirmation_requise": true }
```

`api_demarches.py` est joint parce que `api_assistant.py` en dépend (il y
reprend la vérification de la clé d'API).

---

## Le point d'architecture le plus important

**Le serveur ne peut RIEN exécuter sur le téléphone.** Il ne sait ni passer un
appel, ni lire un répertoire, ni ouvrir un composeur USSD, ni connaître une
position GPS. Son rôle s'arrête à produire une **instruction d'action** ; c'est
l'application qui l'exécute, et seulement après un geste de l'utilisateur.

C'est pour ça que le contrat porte `confirmation_requise: true` sur tout ce qui
coûte de l'argent ou passe un appel. L'application **ne déclenche jamais** une
action toute seule — elle affiche un bouton et attend. C'est déjà la règle de
l'interface web.

```
   parole / texte
        │
        ▼
   ASR + intentions.py      ← serveur : comprendre
        │
        ▼
   _traiter_message()       ← serveur : décider
        │
        ▼
   { "action": {...} }      ← le contrat
        │
        ▼
   ContactsContract,        ← APPLICATION : exécuter
   ACTION_CALL, geo:,          (après confirmation de l'utilisateur)
   ACTION_DIAL *145*1%23
```

---

## Ce qui manque, et qui est le vrai travail à faire

Pour les trois exemples de l'énoncé :

| Commande | État aujourd'hui |
|---|---|
| « Appelle papa » | **partiel** — `intentions.py` détecte l'appel, mais seulement avec un **numéro explicite**. Chercher « papa » dans le répertoire n'existe nulle part. Il faut une intention `appel_contact` côté serveur et la recherche `ContactsContract` côté application |
| « Trouve les pharmacies proches » | **partiel** — renvoie une URL Google Maps. Aucune géolocalisation, aucun annuaire local. Le module `annuaire.py` a été proposé mais jamais écrit |
| « Envoie 5 000 F à Kodjo » | **inexistant en tant qu'agent** — ce qui existe est le scénario **scripté** `demo_money.py` (déclenché par le mot `money`), qui ne comprend rien : il déroule des phrases figées. Un vrai agent devrait extraire le montant et le destinataire de la phrase |

---

## La contrainte qui décidera de tout : le quota

**Gemini, palier gratuit : environ 60 requêtes par JOUR**, tous modèles
confondus (3 × 20). Chaque commande en langage naturel en consomme une, parfois
deux — `moteur_llm.py` bascule automatiquement sur Groq quand Gemini est
épuisé, mais Groq a lui aussi ses limites.

Conséquence pratique : **un agent qui comprend vraiment n'est pas démontrable
plus de quelques minutes.** C'est exactement pourquoi les scénarios (démarches
administratives, mobile money) sont *scriptés* : phrases écrites en dur, voix
pré-générée, zéro appel à un modèle, réponse en 2 millisecondes.

Pour une soutenance, le partage raisonnable est :
- **l'agent en langage libre** sur trois ou quatre commandes préparées, pour
  montrer qu'il comprend vraiment ;
- **les scénarios scriptés** pour tout le reste, parce qu'ils ne peuvent pas
  échouer.

---

## Faire tourner ce code

**`api_assistant.py` a besoin des modèles.** Il ne peut pas vivre dans le
serveur léger : l'ASR éwé (MMS-1B) et la synthèse vocale doivent être chargés,
ce qui prend plusieurs minutes et beaucoup de mémoire. Il n'est monté que par
`server.py`, sur le port 5000.

Ce dossier ne contient donc **pas** de quoi lancer le serveur : il y manque le
pipeline (`assemblage/`), le cerveau éwé, les tables de normalisation et les
modèles téléchargés. Pour un serveur qui démarre en 2 secondes et qui suffit aux
scénarios scriptés, c'est `api_seule.py`, côté dépôt backend.

**Les clés** se mettent dans `cerveau/.env` — voir `cerveau/.env.exemple`.
Ce fichier `.env` est ignoré par git. Ne l'y ajoutez jamais : une clé publiée
sur GitHub est repérée par des robots en quelques minutes.
