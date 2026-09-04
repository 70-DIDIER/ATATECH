# API Nye Gbe — Démarches administratives

**Pour l'équipe Android et l'équipe chatbot WhatsApp.**
Vous n'avez rien à installer du projet backend : vous appelez cette API en HTTP.

L'assistant accompagne en **éwé** un utilisateur qui ne sait pas lire, dans une
démarche administrative : menu des services → collecte des pièces en photo →
paiement Mixx by Yas → rendez-vous → récapitulatif.

Le scénario est **scripté** : réponses instantanées, aucun appel à un modèle,
voix éwé pré-générée. Vous ne verrez jamais de latence de plusieurs secondes.

---

## 1. Démarrage en 3 minutes

Côté backend, quelqu'un lance :

```
interface_web\lancer_api.bat
```

Le serveur affiche l'adresse à utiliser, par exemple `http://10.79.86.132:5055`.

Vérifiez que vous le joignez — **cette route ne demande pas de clé**, c'est
exprès, c'est votre test réseau :

```bash
curl http://10.79.86.132:5055/api/v1/ping
```
```json
{"ok": true, "version": "1.0", "service": "nye-gbe-demarches", "cle_requise": true}
```

Ensuite, **toutes les autres routes exigent un en-tête** :

```
X-Api-Cle: <la clé que l'équipe backend vous donne>
```

Si `cle_requise` vaut `false`, l'API est ouverte et vous pouvez omettre l'en-tête.

### Réseau — ce qu'il faut savoir

- **C'est du HTTP, pas du HTTPS.** Volontairement : un client natif n'a pas
  besoin de contexte sécurisé, et un certificat auto-signé vous coûterait une
  configuration inutile. Sur Android, autorisez le trafic en clair vers cette IP
  (`android:usesCleartextTraffic="true"`, ou mieux un `network_security_config.xml`
  limité à votre IP de développement).
- **Rendez l'URL de base configurable** dans votre app (un champ dans un écran
  réglages, ou une constante `BuildConfig`). Elle changera : elle dépend du Wi-Fi.
- Si le backend est passé en HTTPS (tunnel public), le certificat de l'autorité
  se télécharge sur `GET /ca`.

---

## 2. Les routes

| Méthode | Chemin | Rôle | Clé |
|---|---|---|---|
| GET | `/api/v1/ping` | test réseau | non |
| GET | `/api/v1/demarches/services` | catalogue des services | oui |
| POST | `/api/v1/demarches/session` | ouvre une conversation, renvoie le menu | oui |
| POST | `/api/v1/demarches/message` | un tour en **texte** (réponse, choix, code) | oui |
| POST | `/api/v1/demarches/photo` | un tour en **photo** (multipart) | oui |
| GET | `/audio/<fichier>` | la voix éwé (WAV) | non |
| GET/POST | `/webhook/whatsapp` | webhook Meta Cloud API | — |
| GET | `/webhook/simulateur` | rejouer le parcours WhatsApp sans compte Meta | non |

### 2.1 Ouvrir une conversation

```bash
curl -X POST http://10.79.86.132:5055/api/v1/demarches/session \
  -H "X-Api-Cle: $CLE" -H "Content-Type: application/json" \
  -d '{"session_id": "22890000000"}'
```

`session_id` est **facultatif** : sans lui, le serveur en tire un au sort et vous
le renvoie. Pour WhatsApp, mettez-y le numéro de l'expéditeur.

Réponse (réelle, non simplifiée) :

```json
{
  "session_id": "22890000000",
  "etat": {"parcours": null, "etape": 0, "jour": null,
           "nationalite_faite": false, "relance": false},
  "fini": false,
  "messages": [
    {
      "cle": "menu",
      "carte": "fiche",
      "titre": "Ndi o! Ma kpenɔ le gbaɖeɖewo biabia me egbe. Mɔkplɔkpɔ xé a tya:",
      "titre_fr": "Ndi o ! Je suis là pour t'aider. Quelle démarche administrative souhaites-tu effectuer aujourd'hui ? Voici les services principaux disponibles :",
      "lignes": [
        {"ewe": "Agbalẽwo kple Dukɔmevi nyɔnyɔ: Nationalité, Passeport, NIU.",
         "fr": "Papiers & Citoyenneté : Certificat de Nationalité, Passeport, NIU."},
        {"ewe": "Mɔzɔzɔ kple Xɔnyɔnyɔ: Tsi (TdE) / Zo (CEET), Tasiaɖam kple mɔto ŋkɔŋlɔ.",
         "fr": "Transport & Logement : Raccordement Eau (TdE) / Électricité (CEET), Immatriculation."},
        {"ewe": "Sukununya kple Concours: Fonction publique concours, Bourse, Suku kɔtawo.",
         "fr": "Éducation & Concours : Concours de la Fonction Publique, Bourses, Relevés de notes."},
        {"ewe": "Dɔwɔwɔ kple Asitsatsa: ANPE carte.",
         "fr": "Emploi & Entreprise : Carte de demandeur d'emploi (ANPE)."}
      ],
      "menu": [],
      "ewe": "Nu ka gbaɖeɖe a biɔɔ?",
      "fr": "Dis-moi quel document tu veux faire.",
      "attend": {
        "type": "choix",
        "options": [{"num": 1, "fr": "Certificat de Nationalité", "ewe": "Nationalité"},
                    {"num": 2, "fr": "Passeport", "ewe": "Passeport"}]
      },
      "audio_url": "/audio/demarches_menu.wav"
    }
  ]
}
```

### 2.2 Un tour en texte

```bash
curl -X POST http://10.79.86.132:5055/api/v1/demarches/message \
  -H "X-Api-Cle: $CLE" -H "Content-Type: application/json" \
  -d '{"session_id": "22890000000", "texte": "1"}'
```
```json
{
  "session_id": "22890000000",
  "etat": {"parcours": "nationalite", "etape": 0, "jour": null,
           "nationalite_faite": false, "relance": false},
  "fini": false,
  "messages": [{
    "cle": "nationalite_0",
    "titre": null, "titre_fr": null, "carte": null, "lignes": [], "menu": [],
    "ewe": "Enyo, ɖo wò acte de naissance fɔto ɖa nam.",
    "fr": "Ok, envoie-moi une photo de ton Acte de naissance légalisé.",
    "attend": {"type": "photo", "piece": "acte_naissance"},
    "audio_url": "/audio/demarches_nationalite_0.wav"
  }]
}
```

### 2.3 Un tour en photo

```bash
curl -X POST http://10.79.86.132:5055/api/v1/demarches/photo \
  -H "X-Api-Cle: $CLE" \
  -F "session_id=22890000000" \
  -F "photo=@acte.jpg"
```

Même forme de réponse. **Le contenu de l'image n'est pas analysé** — c'est une
simulation : on vérifie qu'une image est bien arrivée, et on avance.
Limite : **6 Mo** (au-delà : `413`).

### 2.4 Deux façons de garder l'état — choisissez la vôtre

| | Comment | Pour qui |
|---|---|---|
| **Session** | envoyez `session_id` à chaque appel, le serveur retient | **chatbot WhatsApp** — le `session_id` est le numéro de téléphone, vous ne stockez rien |
| **État** | renvoyez le champ `etat` reçu au tour précédent, sans `session_id` | **app mobile** — le serveur ne retient rien, l'app survit à un redémarrage du backend |

En mode photo, l'état se passe en champ de formulaire : `-F 'etat={"parcours":"nationalite",...}'`.

Si vous ne fournissez **ni l'un ni l'autre** : `400` avec un message clair.
Une session inactive est oubliée au bout de **2 h**.

---

## 3. `attend` — la seule chose que vous avez à implémenter

Chaque message vous dit ce qu'il faut demander ensuite. **Pilotez votre écran
là-dessus**, ne devinez rien du texte.

| `attend.type` | Champs utiles | Ce que le client doit faire |
|---|---|---|
| `choix` | `options: [{num, fr, ewe}]` | afficher un bouton par option ; envoyer le numéro ou le libellé |
| `photo` | `piece` (ex. `acte_naissance`) | ouvrir l'appareil photo / la galerie → `POST /photo` |
| `code_secret` | `masquer: true`, `montant`, `devise`, `service` | **champ masqué `••••`**, afficher masqué dans le fil, **ne jamais journaliser** |
| `texte` | — | champ de saisie libre |
| `rien` | — | fin du parcours (voir aussi `fini: true`) |

Les valeurs de `piece` dans l'ordre du scénario :
`acte_naissance`, `nationalite_parent`, `certificat_residence`, `photo_identite`,
puis `cni`, `attestation_profession`.

> **Le code de paiement n'est ni stocké, ni journalisé, ni renvoyé** par le
> backend : il vérifie seulement qu'il n'est pas vide. Faites pareil de votre
> côté — c'est vérifié par un test automatique chez nous.

### Tolérance intégrée

Si vous envoyez du texte là où une photo est attendue, l'assistant **relance une
fois** (`cle: "relance_photo"`), puis **avance quand même** au tour suivant. Une
démonstration ne peut donc pas se bloquer devant un jury.

---

## 4. Afficher un message

```
titre      →  en-tête de carte, en éwé          (peut être null)
titre_fr   →  sa traduction
carte      →  "fiche" = présentez en encadré ; null = bulle simple
lignes[]   →  {ewe, fr} : les puces de la carte
ewe        →  la phrase prononcée, en éwé       (c'est le texte principal)
fr         →  sa traduction
audio_url  →  la voix éwé, ou null
```

Mettez **l'éwé en premier et en gros** : c'est la langue de l'utilisateur. Le
français est là pour l'agent ou le proche qui l'accompagne.

**Audio** : `audio_url` est un chemin relatif — préfixez-le de votre URL de base.
Le fichier est un **WAV 44,1 kHz PCM 16 bits**, que `MediaPlayer` lit sans
transcodage. **`audio_url` peut valoir `null`** : la voix de ce message n'a pas
encore été fabriquée. Ce n'est pas une erreur — affichez le texte et continuez.

---

## 5. Le scénario complet, tour par tour

À rejouer tel quel pour valider votre intégration.

| # | Ce que le client envoie | `cle` reçue | `attend` |
|---|---|---|---|
| 1 | `Ndi o, me ɖi be ma wɔ gbaɖeɖe biabia aɖe.` | `menu` | `choix` |
| 2 | `1` (ou « nationalité ») | `nationalite_0` | `photo` · acte_naissance |
| 3 | photo | `nationalite_1` | `photo` · nationalite_parent |
| 4 | photo | `nationalite_2` | `photo` · certificat_residence |
| 5 | photo | `nationalite_3` | `photo` · photo_identite |
| 6 | photo | `nationalite_4` | `code_secret` · 5 000 FCFA |
| 7 | code | `nationalite_5` | `texte` |
| 8 | n'importe quoi | `passeport_0` | `photo` · cni |
| 9 | photo | `passeport_1` | `photo` · attestation_profession |
| 10 | photo | `passeport_2` | `code_secret` · 30 000 FCFA |
| 11 | code | `pass_jour` | `choix` · Mardi/Mercredi/Jeudi |
| 12 | `Mercredi` | `pass_recap_mercredi` | `rien` — `fini: true` |

Détails utiles :

- Au **tour 2**, le chiffre du menu **et** le nom du service marchent tous les deux
  (`1`, `nationalité`, `Me ɖi be ma wɔ nationalité gbaɖeɖe biabia.`).
- Au **tour 8**, l'assistant enchaîne **tout seul** vers le passeport : c'est ce
  que le message 7 annonce (« Mǐ le yi passeport ɔ ŋu gbeè »). N'importe quelle
  réponse y conduit.
- Au **tour 11**, le jour accepte le numéro (`1`/`2`/`3`), le nom français
  (`Mercredi`) ou éwé (`Aɖaŋugbe`). Chaque jour a **son propre** récapitulatif :
  `pass_recap_mardi`, `pass_recap_mercredi`, `pass_recap_jeudi`.
- Les catégories 3 et 4 du menu ne sont **pas encore scriptées** : elles
  renvoient le message `pas_encore`, qui repropose les deux parcours prêts.

---

## 6. Techno recommandée pour l'app Android

**Kotlin + Jetpack Compose, natif.** Quatre raisons, dans l'ordre d'importance :

1. **L'USSD Mixx by Yas tranche le débat.** Ouvrir le composeur pré-rempli sur
   `*145*1#` demande un `Intent.ACTION_DIAL` avec l'URI `tel:*145*1%23` (le `#`
   doit être encodé). C'est natif et fiable. En WebView / PWA / Capacitor, c'est
   **impossible** — le navigateur filtre le `#`. En Flutter / React Native, cela
   dépend d'un plugin communautaire régulièrement cassé selon la version d'Android.
2. **Les autres capacités sont dans le SDK**, sans aucune dépendance tierce :
   `ContactsContract` (chercher « grand-maman »), `Intent.ACTION_CALL` (appeler),
   `FusedLocationProviderClient` (pharmacies proches).
3. **L'audio est maîtrisé de bout en bout** : `MediaPlayer` lit nos WAV
   44,1 kHz sans transcodage, et si vous branchez le micro plus tard,
   `AudioRecord` produit le PCM brut dont notre ASR a besoin (voir § 8).
4. **Compose** écrit une UI conversationnelle en très peu de code, et
   Retrofit + Moshi consomment ce JSON tel quel.

**Repli acceptable : Flutter**, *uniquement si l'équipe est déjà Flutter* — le
temps gagné sur l'UI compense les plugins. Dans ce cas : `http`, `just_audio`,
`image_picker`, `flutter_contacts`, `url_launcher`, `geolocator`, et **USSD
dégradé** en simple ouverture du composeur.

**À écarter : WebView / PWA / Capacitor** (USSD impossible, contacts non fiables).

Permissions à déclarer : `INTERNET`, `CAMERA`, `READ_CONTACTS`, `CALL_PHONE`,
`ACCESS_FINE_LOCATION` (et `RECORD_AUDIO` seulement si vous branchez le micro).

---

## 7. Le volet WhatsApp — deux branchements possibles

**(a) Vous ne codez rien.** Pointez le webhook Meta sur notre serveur :

```
URL de rappel   : https://<tunnel>/webhook/whatsapp
Jeton de vérif. : la valeur de WHATSAPP_VERIFY_TOKEN (défaut : nyegbe)
```

Nous gérons la réception, l'état par numéro, la mise en forme et l'envoi. Nous
traitons `text`, `image`, `interactive` (boutons) et `audio`. Nous répondons
`200` immédiatement puis traitons à côté — Meta ne renverra pas le message en
boucle.

**(b) Vous gardez votre chatbot** et appelez `/api/v1/demarches/message` en
passant **le numéro de l'expéditeur comme `session_id`**. L'état suit la
conversation tout seul, vous ne stockez rien.

Dans les deux cas, la correspondance à respecter :

| `attend.type` | Message WhatsApp |
|---|---|
| `choix` | boutons interactifs, ou « réponds 1 / 2 » |
| `photo` | demander une image ; le message entrant `image` fait avancer |
| `code_secret` | demander le code, **ne jamais le journaliser ni le réafficher** |
| `rien` | fin du parcours |

Et `audio_url` s'envoie en **note vocale** (`type: "audio"`, `audio.link`) — le
serveur doit alors être joignable publiquement (variable `PUBLIC_URL`).

**Démontrer sans compte Meta** : ouvrez `/webhook/simulateur`. C'est une page qui
rejoue le parcours WhatsApp en passant par **exactement le même code** que le
vrai webhook — rien n'est court-circuité.

---

## 8. Si vous branchez le micro plus tard

Ce scénario se pilote au texte, aux photos et aux choix : **le micro n'est pas
nécessaire**. Mais le jour où vous l'ajouterez, un piège coûteux vous attend.

Notre ASR lit les fichiers avec `libsndfile` :

- ✅ **WAV PCM 16 bits, mono, 16 kHz** ← le format à envoyer
- ✅ FLAC, OGG/Vorbis
- ❌ **M4A / AAC — la sortie PAR DÉFAUT de `MediaRecorder` sur Android**
- ❌ AMR (`THREE_GPP`)

Enregistrez donc avec **`AudioRecord`** (PCM brut) et écrivez vous-même l'en-tête
WAV de 44 octets. `MediaRecorder` vous donnera un fichier que le serveur refusera.

---

## 9. Erreurs

Toujours du JSON : `{"erreur": "message lisible en français"}`.

| Code | Quand |
|---|---|
| `400` | ni `session_id` ni `etat` ; photo absente ou vide ; `etat` mal formé |
| `401` | en-tête `X-Api-Cle` absent ou faux |
| `403` | webhook : jeton de vérification ou signature invalide |
| `413` | photo au-delà de 6 Mo |
| `404` | fichier audio inconnu |

---

# Annexe — prompts prêts à coller

## Prompt A — équipe Android

```text
Tu construis l'application Android d'un assistant vocal en langue éwé (Togo),
pour un hackathon de 48 h. Le backend existe déjà et est documenté ci-dessous :
tu ne l'écris pas, tu le consommes.

TECHNO IMPOSÉE : Kotlin + Jetpack Compose, Android natif (minSdk 24).
Réseau : Retrofit + OkHttp + Moshi. Audio : MediaPlayer. Photos : ActivityResult
+ FileProvider. Ne propose ni Flutter, ni React Native, ni WebView : l'app devra
plus tard ouvrir un code USSD (*145*1#) via Intent.ACTION_DIAL avec l'URI
"tel:*145*1%23", ce qui n'est fiable qu'en natif.

CE QUE L'APP DOIT FAIRE
Un écran unique de conversation. L'utilisateur ne sait pas lire : l'éwé est en
gros, la traduction française en dessous en petit, et chaque message de
l'assistant se lit à voix haute automatiquement.

L'API : <coller ici les sections 1 à 5 de docs/API_DEMARCHES.md>

LE POINT CENTRAL — le champ `attend` de chaque message pilote l'écran :
  - "choix"       → un bouton par option ; envoyer le numéro choisi
  - "photo"       → ouvrir l'appareil photo, puis POST multipart /photo
  - "code_secret" → champ masqué, affiché "••••" dans le fil,
                    JAMAIS journalisé ni conservé
  - "texte"       → saisie libre
  - "rien"        → parcours terminé
Ne devine jamais l'étape à partir du texte : lis `attend`.

MODE D'ÉTAT : utilise le mode « etat » (sans session_id). Renvoie le champ
`etat` reçu au tour précédent. L'app survit ainsi à un redémarrage du backend.

RÉSEAU : URL de base CONFIGURABLE dans un écran réglages (défaut
http://192.168.1.24:5000). C'est du HTTP en clair : ajoute un
network_security_config.xml qui l'autorise pour cette IP seulement. En-tête
X-Api-Cle sur toutes les requêtes sauf /api/v1/ping. Écran de réglages avec un
bouton « Tester la connexion » qui appelle /api/v1/ping.

AUDIO : audio_url est un chemin relatif à préfixer de l'URL de base. WAV
44,1 kHz, lisible par MediaPlayer sans transcodage. audio_url peut être null :
affiche le texte et continue, ce n'est pas une erreur.

CRITÈRE D'ACCEPTATION : dérouler les 12 tours du § 5 de la doc de bout en bout,
avec le code masqué à l'écran et la voix qui part toute seule à chaque message.

Commence par : le client Retrofit + les data class du contrat + l'écran de
réglages avec le test de connexion. Montre-moi ça avant d'écrire l'UI de chat.
```

## Prompt B — équipe chatbot WhatsApp

```text
Tu branches un chatbot WhatsApp sur un assistant en langue éwé (Togo) dont le
backend existe déjà. Hackathon 48 h.

DEUX OPTIONS — évalue-les et dis-moi laquelle tu prends :

(a) NE RIEN CODER. Pointer le webhook Meta Cloud API directement sur le backend :
      URL de rappel   : https://<tunnel>/webhook/whatsapp
      Jeton de vérif. : WHATSAPP_VERIFY_TOKEN (défaut « nyegbe »)
    Le backend gère déjà la vérification GET hub.challenge, la signature
    X-Hub-Signature-256, les messages text/image/interactive/audio, l'état par
    numéro d'expéditeur, la mise en forme éwé + français et l'envoi via la
    Graph API. Il répond 200 immédiatement et traite en tâche de fond.

(b) GARDER TON CHATBOT et appeler l'API métier :
      POST /api/v1/demarches/message   {"session_id": "<numéro>", "texte": "..."}
      POST /api/v1/demarches/photo     multipart : session_id + photo
    En passant le numéro de l'expéditeur comme session_id, l'état suit la
    conversation tout seul : tu ne stockes rien.

L'API : <coller ici les sections 1 à 5 et 7 de docs/API_DEMARCHES.md>

RÈGLES À RESPECTER, quelle que soit l'option :
  - `attend.type` == "choix"       → boutons interactifs, ou « réponds 1 / 2 »
  - `attend.type` == "photo"       → demander une image
  - `attend.type` == "code_secret" → demander le code et NE JAMAIS le
                                     journaliser, l'afficher ni le stocker
  - `attend.type` == "rien"        → parcours terminé (voir aussi `fini`)
  - audio_url → envoyer en note vocale (type "audio", audio.link), le serveur
    devant être joignable publiquement
  - l'éwé passe AVANT le français dans chaque message

DÉMONTRER SANS COMPTE META : la page /webhook/simulateur rejoue le parcours en
passant par exactement le même code que le vrai webhook.

CRITÈRE D'ACCEPTATION : dérouler les 12 tours du § 5 de la doc dans WhatsApp
(ou dans le simulateur), photos comprises.
```

## Prompt C — dev backend n° 2

```text
Tu complètes le backend d'un assistant vocal éwé (Togo), hackathon 48 h.
Python, sans framework imposé. TU NE TOUCHES À AUCUN de ces fichiers, qui sont
tenus par quelqu'un d'autre : interface_web/demo_demarches.py, api_demarches.py,
api_seule.py, webhook_whatsapp.py, docs/API_DEMARCHES.md.

TÂCHE 1 — cerveau/demarches_data.py (NOUVEAU)
Le menu de l'assistant annonce 4 catégories, mais seules « Certificat de
nationalité » et « Passeport » sont scriptées. Un juré qui tape « 3 » tombe
dans le vide. Rédige les 4 services manquants :
   - NIU (numéro d'identification unique)
   - Raccordement Eau (TdE) / Électricité (CEET)
   - Carte de demandeur d'emploi (ANPE)
   - Concours de la Fonction Publique
Format EXACT d'un bloc, identique à celui du script existant :
   {"cle": "...", "carte": "fiche",
    "titre": "<éwé>", "titre_fr": "<français>",
    "lignes": [{"ewe": "...", "fr": "..."}, ...],
    "ewe": "<la phrase prononcée>", "fr": "<sa traduction>",
    "attend": {"type": "texte"}}
Contenu de chaque fiche : pièces à fournir, coût, délai, lieu de dépôt.
Source : https://service-public.gouv.tg/ — et marque chaque fiche « a_valider:
True » tant qu'un humain ne l'a pas vérifiée.
CONTRAINTE DE VOIX : le moteur vocal ne prononce pas les chiffres, et le texte
entre parenthèses n'est PAS prononcé mais reste affiché. Écris donc les nombres
en toutes lettres en éwé, en laissant le chiffre entre parenthèses :
« ... fɛ nyi atɔ̃ akpe (5.000 F) ».

TÂCHE 2 — interface_web/annuaire.py (NOUVEAU)
Pharmacies et hôpitaux de Lomé, en dur, hors ligne :
   {"nom", "categorie": "pharmacie"|"hopital", "adresse", "telephone",
    "lat", "lon", "garde_24h": bool}
Une douzaine d'entrées réelles suffit. Expose :
   proches(categorie, lat, lon, limite=5) -> list
trié par distance haversine, chaque résultat portant sa "distance_km" arrondie.
Aucun appel réseau : ça doit marcher sans Internet en pleine démo.

POUR CHAQUE TÂCHE, écris aussi un test autonome (pas de pytest, un simple
script avec des assertions affichées ✓/✗, comme interface_web/test_demo_gestion.py)
qui tourne sans réseau et sans modèle.
```
