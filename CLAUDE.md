# CLAUDE.md

App Android con un widget per la schermata Home che mostra **i prossimi tre giorni, ora per ora**, con gli eventi di Google Calendar nella loro fascia oraria. Il widget è di sola lettura: nessuna azione al tocco.

Gli eventi si leggono da `CalendarContract`, il calendario del telefono che Google Calendar tiene sincronizzato. **Niente login Google e niente Calendar API**: l'app non ha il permesso INTERNET e i dati non escono dal telefono.

## Comandi

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # test (devono passare prima di ogni commit)
./gradlew lintDebug            # lint: nessun errore ammesso
./gradlew installDebug         # installa sul telefono collegato via adb

# Forza l'aggiornamento del widget sul telefono
adb shell am broadcast -n it.tregiorni/.ThreeDaysWidget -a it.tregiorni.action.REFRESH
# Esegue subito il job che reagisce alle modifiche del calendario
adb shell cmd jobscheduler run -f it.tregiorni 1
```

L'SDK è in `~/Android/Sdk` (`local.properties`, non versionato). `gradle.properties` forza IPv4: su questa macchina la JVM si blocca sugli indirizzi IPv6.

## Stack e versioni (non cambiarle senza chiedere)

- Kotlin integrato in AGP 9.4.1 (niente plugin `org.jetbrains.kotlin.android`), Gradle 9.8.1, JDK 21
- `minSdk 31` (Android 12), `compileSdk`/`targetSdk 36`. Il telefono di sviluppo è un Motorola Edge 60 con Android 16.
- **Nessuna dipendenza a runtime**: solo API della piattaforma (niente AndroidX, Compose, Glance, WorkManager). JUnit 4 solo per i test.

Prima di aggiungere una dipendenza, chiedi.

## Architettura

```
Timeline.kt           logica pura, testata su JVM: eventi → giorni, fascia oraria, colonne per le sovrapposizioni
CalendarSource.kt     query su CalendarContract.Instances (le ricorrenze sono già espanse)
WidgetViews.kt        costruisce le RemoteViews del widget a partire da uno Snapshot
WidgetUpdater.kt      carica gli eventi, aggiorna i widget, programma il prossimo aggiornamento
ThreeDaysWidget.kt    AppWidgetProvider: aggiunta, ridimensionamento, cambi di ora/fuso/lingua
CalendarChangeJob.kt  JobService con trigger sul content URI del calendario
MainActivity.kt       permesso calendario, pulsante "aggiungi alla Home", anteprima del widget
```

`Timeline.kt` non dipende da Android: la logica nuova va lì, con i suoi test.

Regole sulla visualizzazione (in `Timeline.kt`):

- Eventi tutto il giorno ed eventi con orario lunghi **24 ore o più** vanno nella riga in alto (banner) di ogni giorno che toccano, come fa Google Calendar. Gli altri vengono tagliati sui giorni che attraversano.
- Gli eventi tutto il giorno sono salvati a mezzanotte **UTC**: le loro date si leggono in UTC, non nel fuso locale.
- La fascia oraria va dall'inizio del primo evento con orario alla fine dell'ultimo, su tutti e tre i giorni, allargata ad almeno 6 ore. Senza eventi: 8–20.
- Gli eventi sovrapposti si dividono la larghezza del giorno in colonne.

`CalendarSource` scarta i calendari nascosti, gli eventi cancellati e gli inviti rifiutati.

## Come è disegnato il widget

**Non usare bitmap né dimensioni assolute.** I launcher possono dichiarare una dimensione diversa da quella reale: il launcher Motorola dichiara circa 3/4 dell'altezza vera, quindi una bitmap viene stirata. Il widget è fatto di layout posizionati con i **pesi**:

- Un `LinearLayout` con `weightSum = S` e un solo figlio con `layout_weight = 1` dà al figlio 1/S dello spazio; la `gravity` decide da che lato. Due livelli annidati posizionano un intervallo [da, a] (vedi `event.xml` e `setWeightSums` in `WidgetViews`).
- L'altezza dichiarata dal launcher serve solo per stime: densità delle etichette delle ore, altezza minima degli eventi brevi, numero di righe di testo.
- Nei layout dei widget si possono usare solo le classi ammesse da `RemoteViews`: **niente `View` né `Space`**, per spaziatori e linee usa `FrameLayout`.
- Il launcher può riapplicare le nuove `RemoteViews` sopra quelle vecchie invece di ricrearle: i contenitori riempiti a runtime vanno svuotati con `removeAllViews` e devono contenere **solo** view aggiunte a runtime.
- Colori statici in `values/colors.xml` e `values-night/colors.xml`: il widget segue subito il tema di sistema. In codice si impostano solo i colori degli eventi.

Aggiornamenti: alla aggiunta e al ridimensionamento del widget, quando cambia il calendario (job con trigger sul content URI, che si riprogramma dopo ogni esecuzione), e ogni quarto d'ora con un allarme `RTC` che non sveglia il telefono (sposta la linea dell'ora e cambia giorno a mezzanotte). `updatePeriodMillis` (30 min) è solo una rete di sicurezza.

## Verifica sul telefono

I test JVM coprono solo `Timeline.kt`. Ogni modifica al widget va provata sul telefono: installa, forza l'aggiornamento e guarda il risultato (`adb exec-out screencap -p > shot.png`). Prova anche dimensioni diverse (ridimensionando il widget) e il tema chiaro (`adb shell cmd uimode night no`, poi ripristina con `yes`). L'anteprima nell'app usa le stesse `RemoteViews`, ma non sostituisce la prova sul launcher.

## Convenzioni

- Identificatori, commenti e messaggi di commit in **inglese**. Testi mostrati all'utente in **italiano** (`strings.xml`).
- Niente stato globale: gli `object` (`WidgetUpdater`, `CalendarSource`) non hanno stato.
- Lettura del calendario e costruzione del widget fuori dal main thread (`goAsync` nel provider, thread nel job e nell'activity).

## Git

- Lavora su un **branch per funzionalità** (es. `feat/eventi-cliccabili`), non direttamente su `main`.
- **Commit piccoli e atomici**, con build, test e lint che passano.
- Messaggi in inglese, all'imperativo: titolo ≤ 72 caratteri, riga vuota, poi il *perché* se non è ovvio.

## Fuori scope per ora (non implementare senza richiesta esplicita)

Eventi cliccabili o qualunque azione al tocco, Material You e colori dinamici, configurazione del widget (numero di giorni, fascia oraria), login Google e Calendar API.
