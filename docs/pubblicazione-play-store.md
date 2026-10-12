# Pubblicazione su Google Play

Guida passo passo per la prima pubblicazione di HourByHour, con un account sviluppatore **personale creato dopo il 13 novembre 2023**. Per questi account Google chiede un **test chiuso con almeno 12 tester per 14 giorni di fila** prima di poter pubblicare per tutti. Quindi i tempi minimi sono: test chiuso (14 giorni), poi richiesta di accesso alla produzione (revisione di Google, di solito entro 7 giorni), poi pubblicazione.

Cosa è già pronto nel repository:

| Cosa | Dove |
|---|---|
| App bundle firmato da caricare | `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` |
| Titolo, descrizioni, note di versione (inglese e italiano) | `fastlane/metadata/android/<lingua>/*.txt` |
| Icona 512×512, grafica in primo piano 1024×500, 4 screenshot | `fastlane/metadata/android/<lingua>/images/` |
| Informativa sulla privacy | https://alexmarchi28.github.io/hourbyhour-privacy/ (repo pubblico `alexmarchi28/hourbyhour-privacy`) |
| Chiave di caricamento (upload key) | `~/.android-keys/hourbyhour-upload.jks`, password in `keystore.properties` (fuori da git) |

**Prima di tutto: fai un backup della chiave.** Copia `~/.android-keys/hourbyhour-upload.jks` e il contenuto di `keystore.properties` nel gestore di password o in un posto sicuro fuori dal PC. Se la perdi, Google può sostituirla, ma servono una richiesta al supporto e qualche giorno.

## 1. Crea l'app

Play Console → **Crea app**:

- Nome dell'app: `HourByHour - Calendar widget`
- Lingua predefinita: **Inglese (Stati Uniti) – en-US**
- App o gioco: **App**. Gratuita o a pagamento: **Gratuita** (dopo non si può più rendere a pagamento)
- Accetta le dichiarazioni (norme per gli sviluppatori, leggi sull'esportazione degli Stati Uniti)

## 2. Configura l'app (Dashboard → "Configura la tua app")

Ogni voce è un modulo da compilare una volta sola. Le risposte per HourByHour:

- **Norme sulla privacy**: `https://alexmarchi28.github.io/hourbyhour-privacy/`
- **Accesso all'app**: tutte le funzionalità sono disponibili senza restrizioni di accesso (non c'è login).
- **Annunci**: l'app **non contiene annunci**.
- **Classificazione dei contenuti**: email di contatto, categoria **Tutti gli altri tipi di app** (utility, produttività). Rispondi **No** a tutte le domande (violenza, sesso, linguaggio, sostanze, gioco d'azzardo, interazione tra utenti, condivisione della posizione, acquisti digitali). Risultato atteso: PEGI 3 / Everyone.
- **Pubblico di destinazione**: scegli solo fasce **dai 13 anni in su** (ad esempio 18 e oltre). Se includi età sotto i 13 anni, l'app ricade nelle norme per le famiglie, con requisiti in più.
- **App di notizie**: No. **App per la salute**, **funzionalità finanziarie**, **app governative**: No / nessuna.
- **ID pubblicità**: l'app **non** usa l'ID pubblicità.
- **Sicurezza dei dati**:
  - "La tua app raccoglie o condivide i tipi di dati utente richiesti?" → **No**. Per Google "raccogliere" vuol dire inviare dati fuori dal telefono; i dati usati solo sul telefono non vanno dichiarati. HourByHour non ha nemmeno il permesso internet.
  - Il riepilogo mostrerà "Nessun dato raccolto" e "Nessun dato condiviso".

## 3. Scheda dello store (Crescita → Presenza nello store → Scheda principale dello store)

Copia i testi dai file in `fastlane/metadata/android/en-US/`:

- Nome dell'app: `title.txt`
- Breve descrizione: `short_description.txt`
- Descrizione completa: `full_description.txt`
- Icona: `images/icon.png`. Grafica in primo piano: `images/featureGraphic.png`. Screenshot del telefono: i 4 file in `images/phoneScreenshots/`

Poi **Aggiungi traduzioni → Italiano – it-IT** e ripeti con i file di `fastlane/metadata/android/it-IT/` (anche le immagini, che sono in italiano).

Nelle **Impostazioni dello store**: categoria **Produttività**; email di contatto (obbligatoria e **pubblica**: puoi usare un indirizzo dedicato); sito web facoltativo.

## 4. Test chiuso (Test e rilascio → Test → Test chiuso)

1. Apri il canale di test chiuso (o creane uno) → **Crea nuova release**.
2. **Firma dell'app di Google Play**: accetta l'opzione proposta, cioè la chiave di firma generata e custodita da Google. Tu firmi solo con la chiave di caricamento.
3. Carica `app-release.aab` (prima esegui `./gradlew bundleRelease`).
4. Nome della release: `0.1.0`. Note di versione: da `changelogs/1.txt`, in inglese e italiano.
5. **Paesi**: tutti, o quelli che vuoi.
6. **Tester**: crea un **elenco email** con gli indirizzi Google (Gmail) dei tester, almeno **12**: meglio 14-15, per avere margine se qualcuno esce. In alternativa puoi usare un Google Group. Aggiungi un indirizzo email o un URL per il feedback.
7. **Avvia l'implementazione**. Google la esamina: la prima volta può volerci qualche giorno.
8. Quando lo stato è "Pubblicata", copia il **link di partecipazione** e mandalo ai tester. Ognuno deve aprirlo con l'account Google che hai inserito, accettare ("Diventa un tester") e installare l'app dal Play Store.

I 14 giorni contano dal momento in cui ci sono almeno 12 tester iscritti, e devono restare iscritti senza interruzioni. Chiedi ai tester di aggiungere davvero il widget e usarlo: Google valuta anche quanto l'app è stata provata, e nella richiesta di produzione chiede che feedback hai ricevuto.

Per una nuova versione durante il test: aumenta `versionCode` (e `versionName`) in `app/build.gradle.kts`, esegui `./gradlew bundleRelease` e crea una nuova release nello stesso canale.

## 5. Richiesta di accesso alla produzione

Dopo i 14 giorni: Dashboard → **Richiedi l'accesso alla produzione**. Il modulo ha tre parti:

- **Il test chiuso**: come hai trovato i tester (amici, parenti, colleghi), come hanno usato l'app, che feedback hai ricevuto e cosa hai cambiato.
- **L'app**: a chi è rivolta (chi vuole vedere il calendario ora per ora sulla Home) e cosa offre di diverso (vista oraria su più giorni, gratis, senza login né accesso a internet).
- **Prontezza per la produzione**: come sai che l'app è pronta (test automatici, test chiuso, aggiornamenti fatti in base al feedback).

La revisione di solito richiede fino a 7 giorni. Se Google chiede altro test, continua il test chiuso e riprova.

## 6. Pubblicazione

Con l'accesso approvato: **Produzione → Crea nuova release**. Puoi riusare l'app bundle del test chiuso ("Aggiungi dalla raccolta"). Puoi scegliere un'implementazione graduale (ad esempio il 20% degli utenti), poi aumentarla. Google esamina ogni release prima di pubblicarla.

## Da ricordare

- Ogni app bundle caricato deve avere un `versionCode` più alto del precedente.
- Dal 31 agosto di ogni anno Google alza il livello API minimo richiesto (`targetSdk`): nel 2026 è 36, quello attuale dell'app.
- Se l'app un giorno inviasse dati fuori dal telefono (ad esempio statistiche o segnalazioni di crash), vanno aggiornati l'informativa sulla privacy, il modulo sulla sicurezza dei dati e i testi dell'app che promettono il contrario.
