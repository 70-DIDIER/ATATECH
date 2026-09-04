# Prompt à donner à l'équipe frontend mobile

> Copiez tout le bloc ci-dessous dans votre assistant de code (Claude Code,
> Cursor, Copilot…), à la racine du projet Android. Il est autosuffisant : il
> contient le diagnostic, le contrat d'API complet et les critères de recette.
> Adaptez seulement l'adresse IP en tête si le réseau a changé.

---

```text
Tu répares l'application Android « Nye Gbe », un assistant vocal en langue éwé
(Togo) pour des personnes qui ne savent pas lire. Hackathon : sois direct,
pas de refonte inutile, on garde ce qui marche.

════════════════════════════════════════════════════════════════════════════
LE BUG À CORRIGER — lis ceci avant de toucher au code
════════════════════════════════════════════════════════════════════════════
L'application actuelle enregistre une note vocale, la fait transcrire par le
SpeechRecognizer d'Android, puis la traduit en français sur le téléphone.
Elle N'APPELLE JAMAIS le backend : le serveur ne reçoit aucune requête (c'est
vérifié dans ses journaux, seuls des GET de navigateur y apparaissent).

C'est l'erreur de conception à défaire. L'intelligence (reconnaissance vocale
éwé, compréhension, synthèse vocale éwé) est ENTIÈREMENT côté serveur. Le
téléphone n'est qu'une façade : il enregistre, il envoie, il affiche, il joue
le son qu'on lui renvoie. Il ne comprend rien et ne traduit rien.

À SUPPRIMER :
  - toute utilisation de android.speech.SpeechRecognizer / RecognizerIntent
  - toute traduction embarquée (ML Kit Translate, Google Translate, etc.)
  - toute logique de conversation écrite en Kotlin (le scénario vient du serveur)

À METTRE À LA PLACE : des appels HTTP au backend, décrits ci-dessous.

════════════════════════════════════════════════════════════════════════════
0. CE QUI BLOQUE PEUT-ÊTRE AUSSI LES REQUÊTES — à faire en premier
════════════════════════════════════════════════════════════════════════════
Le backend est en HTTP clair. Depuis Android 9, une app ne peut pas appeler une
URL http:// sans autorisation explicite : l'exception est levée AVANT que le
paquet ne parte, donc le serveur ne voit rien. C'est cohérent avec les
symptômes observés.

1) AndroidManifest.xml :
     <uses-permission android:name="android.permission.INTERNET" />
     <application
         android:networkSecurityConfig="@xml/network_security_config"
         ... >

2) res/xml/network_security_config.xml :
     <?xml version="1.0" encoding="utf-8"?>
     <network-security-config>
         <domain-config cleartextTrafficPermitted="true">
             <domain includeSubdomains="false">10.79.86.132</domain>
         </domain-config>
     </network-security-config>

3) Vérifie dans Logcat, au premier appel, qu'il n'y a NI
   CleartextNotPermittedException, NI UnknownHostException, NI
   NetworkOnMainThreadException.

Si l'IP a changé, il existe une porte de sortie sans reconfiguration : le
backend est aussi accessible en HTTPS valide sur
https://transcript-dsc-rooms-limits.trycloudflare.com
(plus lent : ~800 ms par appel, mais aucune config Android nécessaire.)

════════════════════════════════════════════════════════════════════════════
1. LE BACKEND
════════════════════════════════════════════════════════════════════════════
URL de base : http://10.79.86.132:5055
  → RENDS-LA CONFIGURABLE (écran Réglages + persistance DataStore/SharedPrefs).
    Elle change à chaque changement de réseau. Prévois un bouton
    « Tester la connexion » qui appelle GET /api/v1/ping.

Test réseau, sans authentification :
  GET /api/v1/ping   →   {"ok": true, "version": "1.0",
                          "service": "nye-gbe-demarches", "cle_requise": false}

Si "cle_requise" vaut true, ajoute l'en-tête X-Api-Cle: <clé> sur toutes les
autres requêtes. Aujourd'hui c'est false.

Réseau : Retrofit + OkHttp + Moshi, appels en coroutine (jamais sur le thread
principal). Timeout de lecture : 180 s (voir § 5, l'ASR et la synthèse vocale
sont lents sur ce PC).

════════════════════════════════════════════════════════════════════════════
2. LE SCÉNARIO DES DÉMARCHES — c'est la démo à faire marcher EN PRIORITÉ
════════════════════════════════════════════════════════════════════════════
L'assistant accompagne l'utilisateur dans une démarche administrative :
menu des services → photos des pièces → paiement → rendez-vous → récapitulatif.

Le scénario est SCRIPTÉ côté serveur. Réponses instantanées (2 à 4 ms), aucun
modèle, aucune intelligence à écrire côté mobile.

--- Ouvrir la conversation ---
POST /api/v1/demarches/session
     { "session_id": "<un identifiant stable, ex. l'ID d'installation>" }

--- Un tour de conversation ---
POST /api/v1/demarches/message
     { "session_id": "...", "texte": "1" }                    // texte / bouton
     { "session_id": "...", "type": "voix" }                  // note vocale
POST /api/v1/demarches/photo     (multipart)
     session_id=...  +  photo=@fichier.jpg                    // max 6 Mo

--- La réponse, toujours la même forme ---
{
  "session_id": "...",
  "etat": { ... },          // à ignorer si tu utilises session_id
  "fini": false,
  "messages": [
    {
      "cle": "nationalite_0",
      "titre": null,               // en-tête de carte, en éwé (peut être null)
      "titre_fr": null,
      "carte": null,               // "fiche" = encadré ; null = bulle simple
      "lignes": [],                // [{ "ewe": "...", "fr": "..." }]
      "menu": [],
      "ewe": "Enyo, ɖo wò acte de naissance fɔto ɖa nam.",
      "fr":  "Ok, envoie-moi une photo de ton Acte de naissance légalisé.",
      "attend": { "type": "photo", "piece": "acte_naissance" },
      "audio_url": "/audio/demarches_nationalite_0.wav"
    }
  ]
}

════════════════════════════════════════════════════════════════════════════
3. « attend » PILOTE L'ÉCRAN — c'est le cœur du travail
════════════════════════════════════════════════════════════════════════════
Ne devine JAMAIS l'étape à partir du texte. Lis attend.type :

  "choix"        → un bouton par entrée de attend.options [{num, fr, ewe}]
                   au clic : POST /message { "texte": "<num>" }
  "photo"        → ouvre l'appareil photo (attend.piece dit quelle pièce)
                   puis POST /photo
  "code_secret"  → champ MASQUÉ (••••). attend.montant et attend.devise
                   donnent le montant à afficher. Le code ne doit être ni
                   journalisé, ni stocké, ni réaffiché en clair. Le serveur ne
                   le conserve pas non plus.
  "texte"        → champ de saisie libre
  "rien"         → parcours terminé (voir aussi "fini": true)

Un type inconnu : affiche le message et propose une saisie libre. N'échoue pas.

════════════════════════════════════════════════════════════════════════════
4. LA NOTE VOCALE — n'utilise AUCUNE reconnaissance vocale
════════════════════════════════════════════════════════════════════════════
Le scénario avance quoi que dise l'utilisateur : le contenu de la note vocale
n'est jamais lu. Tu n'as donc même pas besoin d'envoyer l'audio.

  L'utilisateur enregistre une note vocale
      → POST /api/v1/demarches/message  { "session_id": "...", "type": "voix" }

Si tu veux garder un flux « enregistrer puis envoyer » (avec la forme d'onde,
le bouton annuler, etc.), envoie le fichier — il sera lu puis abandonné :
      → POST /api/v1/demarches/voix   (multipart : session_id + audio)

CE QUE LA VOIX NE PEUT PAS FAIRE : choisir. Sans reconnaissance vocale, le
serveur ignore si l'utilisateur a dit « nationalité » ou « passeport ». Donc :
  - note vocale AVANT tout parcours  → le serveur répond LE MENU
  - 2e note vocale sans choix        → une relance qui redemande un numéro
  - les choix passent par les BOUTONS de attend.options

PARCOURS COMPLET À REPRODUIRE (aucune reconnaissance vocale) :
  ouverture de l'écran        → POST /session          → menu
  note vocale                 → {"type":"voix"}        → menu
  bouton [1]                  → {"texte":"1"}          → nationalite_0
  photo ×4                    → POST /photo            → nationalite_1…4
  note vocale (son code)      → {"type":"voix"}        → nationalite_5
  note vocale                 → {"type":"voix"}        → passeport_0
  photo ×2                    → POST /photo            → passeport_1…2
  note vocale (2e code)       → {"type":"voix"}        → pass_jour
  bouton [2] Mercredi         → {"texte":"2"}          → pass_recap_mercredi
                                                          "fini": true

════════════════════════════════════════════════════════════════════════════
5. LA CONVERSATION LIBRE (hors scénario) — à faire APRÈS
════════════════════════════════════════════════════════════════════════════
Ces routes-là font tourner la vraie reconnaissance vocale éwé, le cerveau et la
synthèse vocale. Elles sont sur le serveur COMPLET, port 5000 (pas 5055).

  POST /api/v1/assistant/texte   { "texte": "..." }          → { "job_id" }
  POST /api/v1/assistant/audio   multipart : audio=@note.wav → { "job_id" }
  POST /api/v1/assistant/image   multipart : image=@photo.jpg→ { "job_id" }
  GET  /api/v1/job/<job_id>      → interroger 1×/seconde jusqu'à "fini": true

La réponse du job est un seul objet plat :
{ "fini": true, "etape": "...", "transcription": "...", "texte_image": null,
  "langue": "ewe", "rep_ewe": "...", "rep_fr": "...",
  "audio_url": "/audio/....wav",
  "action": { "type": "aucune" | "appel_numero" | "ouvrir_url", ... },
  "erreur": null }

Affiche "etape" pendant l'attente : la reconnaissance vocale prend 5 à 20 s et
la synthèse 30 à 60 s. Sans indicateur, l'utilisateur croira à un plantage.
Un 503 avec "code": "modeles_non_prets" = les modèles chargent encore, réessaie.

FORMAT AUDIO OBLIGATOIRE pour /assistant/audio :
    WAV PCM 16 bits, MONO, 16 000 Hz.
    N'UTILISE PAS MediaRecorder : sa sortie par défaut est du M4A/AAC, que le
    serveur ne sait pas lire (il répondra 415). Enregistre avec AudioRecord
    (PCM brut) et écris toi-même l'en-tête WAV de 44 octets.
    Le serveur vérifie l'en-tête et répond 415 avec un message clair si ce
    n'est pas du WAV/FLAC/OGG.

Pour le SCÉNARIO des démarches (§ 2-4), rien de tout ça n'est nécessaire.

════════════════════════════════════════════════════════════════════════════
6. AFFICHAGE ET DESIGN — l'app doit ressembler à l'interface web
════════════════════════════════════════════════════════════════════════════
Palette (identique au web) :
    violet principal  #662483      violet foncé   #4A1A5F
    violet clair      #8F4FAE      violet pâle    #F4EEF8
    anthracite texte  #343535      texte doux     #5C5D64
    texte discret     #6E6F78      safran (déco)  #F9B233
    fond              #F6F6F8      surface        #FFFFFF
    bordure           #E6E6EC      rayon des bulles 18dp

Structure d'une bulle de réponse, de haut en bas — trois niveaux, comme le web :
  1. LE TEXTE ÉWÉ SANS SIGNES SPÉCIAUX, en 15,5sp, couleur #343535
     C'est la ligne principale : la plus lisible pour l'utilisateur.
     Transformation à appliquer sur le champ "ewe" :
        ŋ→ng  Ŋ→Ng  ɖ→d  Ɖ→D  ɔ→o  Ɔ→O  ɛ→e  Ɛ→E
        ƒ→f   Ƒ→F   ʋ→v  Ʋ→V  ɣ→h  Ɣ→H
     puis retirer les accents : normaliser en NFD, supprimer U+0300–U+036F,
     recomposer en NFC. (En Kotlin : java.text.Normalizer.)
  2. LE MÊME TEXTE AVEC LES SIGNES (le champ "ewe" brut), 13sp, #6E6F78,
     juste en dessous, plus discret : c'est la graphie correcte.
  3. LA TRADUCTION FRANÇAISE ("fr") dans un encadré : fond #FFFFFF, bordure
     #E6E6EC, rayon 12dp, précédée d'un petit label « TRADUCTION » en
     majuscules, 10,5sp, gras, lettres espacées, couleur #6E6F78.

Si "carte" vaut "fiche" : présente titre / titre_fr en en-tête, puis les
"lignes" en puces (éwé au-dessus, français en dessous), dans un encadré.

NE METS JAMAIS un mot éwé en majuscules automatiques : « Woezɔ » deviendrait
« WOEZƆ ». Pas de textAllCaps sur les textes éwé.

AUDIO : audio_url est un chemin relatif, préfixe-le de l'URL de base. C'est un
WAV 44,1 kHz que MediaPlayer lit sans transcodage. Joue-le AUTOMATIQUEMENT à
l'arrivée de chaque message de l'assistant (l'utilisateur ne sait pas lire),
et laisse un bouton pour le réécouter. audio_url peut être null : affiche le
texte et continue, ce n'est pas une erreur.

════════════════════════════════════════════════════════════════════════════
7. CRITÈRES DE RECETTE
════════════════════════════════════════════════════════════════════════════
1. Le bouton « Tester la connexion » affiche « ok » (GET /api/v1/ping).
2. Dans Logcat, chaque appel HTTP apparaît avec son URL et son code (ajoute un
   HttpLoggingInterceptor de niveau BASIC). Aucune exception réseau.
3. Le parcours complet du § 4 se déroule jusqu'à "fini": true, avec le code
   masqué à l'écran, sans une seule ligne de reconnaissance vocale.
4. Chaque message de l'assistant se lit à voix haute tout seul.
5. Plus aucune référence à SpeechRecognizer ni à une bibliothèque de
   traduction dans le projet.

════════════════════════════════════════════════════════════════════════════
8. ORDRE DE TRAVAIL — ne fais pas tout d'un coup
════════════════════════════════════════════════════════════════════════════
Étape 1 : la configuration réseau du § 0 + l'écran Réglages + le test de
          connexion. Montre-moi Logcat prouvant que /api/v1/ping répond 200.
Étape 2 : le client Retrofit et les data class du contrat du § 2.
Étape 3 : l'écran de conversation piloté par attend (§ 3), boutons et photos.
Étape 4 : la note vocale en mode "voix" (§ 4), puis la lecture audio.
Étape 5 : le design du § 6.
La conversation libre (§ 5) vient en dernier, seulement si le § 4 marche.

ARRÊTE-TOI après l'étape 1 et montre-moi le résultat avant de continuer.
```

---

## Note pour l'équipe backend

Le serveur enregistre chaque requête avec sa durée et l'adresse de l'appelant
(fenêtre « NYE GBE - API (journal en direct) », copie dans
`interface_web/journal_api.log`). Dès que l'app émet réellement, ça s'y voit :

```
[    2.6 ms] POST /api/v1/demarches/message → 200  1 Ko
10.79.86.51 - - [04/Sep/2026 19:52:10] "POST ... HTTP/1.1" 200 -
```

L'adresse source dit par où l'appel est passé : `10.79.86.x` = Wi-Fi direct
(~25 ms), `127.0.0.1` = par le tunnel Cloudflare (~800 ms).

Si rien n'apparaît, l'app n'émet toujours pas : le problème reste dans
l'application, pas dans le réseau ni dans le backend.
