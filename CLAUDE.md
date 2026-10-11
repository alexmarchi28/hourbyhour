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
GoogleColors.kt       logica pura, testata su JVM: colori del provider → colori mostrati da Google Calendar
WidgetLayout.kt       logica pura, testata su JVM: cosa mostra il widget e dove (frazioni delle colonne)
EventColors.kt        logica pura, testata su JVM: colori di riempimento e testo degli eventi, per tema
CalendarSource.kt     query su CalendarContract.Instances (le ricorrenze sono già espanse)
WidgetViews.kt        traduce un WidgetLayout in RemoteViews, senza prendere decisioni
WidgetUpdater.kt      carica gli eventi, aggiorna i widget, programma il prossimo aggiornamento
ThreeDaysWidget.kt    AppWidgetProvider: aggiunta, ridimensionamento, cambi di ora/fuso/lingua
CalendarChangeJob.kt  JobService con trigger sul content URI del calendario
MainActivity.kt       permesso calendario, pulsante "aggiungi alla Home", anteprima del widget
```

`Timeline.kt`, `GoogleColors.kt`, `WidgetLayout.kt` ed `EventColors.kt` non dipendono da Android: la logica nuova va lì, con i suoi test. Il flusso è `CalendarSource` → `splitIntoDays` → `Snapshot` → `layoutWidget` → `WidgetViews`. Ogni decisione su cosa mostrare e dove (posizioni, righe dei banner, etichette, evento passato, linea dell'ora) sta in `layoutWidget`: `WidgetViews` traduce e basta, così il comportamento resta coperto dai test.

Il numero di giorni non è fissato nella logica (`layoutWidget` e `splitIntoDays` accettano qualsiasi numero, almeno 1): oggi è solo la costante `WidgetUpdater.DAY_COUNT`.

**Non rinominare `ThreeDaysWidget` né il package `it.tregiorni`**: il nome della classe identifica il widget per il launcher, e cambiarlo fa sparire i widget già aggiunti alla Home.

Regole sulla visualizzazione (in `Timeline.kt`):

- Eventi tutto il giorno ed eventi con orario lunghi **24 ore o più** vanno nella riga in alto (banner) di ogni giorno che toccano, come fa Google Calendar. Gli altri vengono tagliati sui giorni che attraversano.
- Gli eventi tutto il giorno sono salvati a mezzanotte **UTC**: le loro date si leggono in UTC, non nel fuso locale.
- La fascia oraria va dall'inizio del primo evento con orario alla fine dell'ultimo, su tutti e tre i giorni, allargata ad almeno 6 ore. Senza eventi: 8–20.
- Gli eventi sovrapposti si dividono la larghezza del giorno in colonne, in base agli **orari reali**: due eventi che non si sovrappongono non stanno mai affiancati (un evento senza durata conta 1 minuto).

Regole sulla lunghezza degli eventi (in `WidgetLayout.kt`):

- Ogni riquadro corrisponde all'inizio e alla fine reali dell'evento. **Non allungare gli eventi per far stare il titolo**: sembrerebbero sovrapposti a quelli vicini o più lunghi del vero.
- Solo gli eventi più sottili di una barretta visibile (3 dp) si allungano, e solo nel tempo libero: prima dopo l'evento, poi prima, mai sopra un altro evento nella stessa colonna.
- È il titolo ad adattarsi al riquadro: testo normale se ci sta (`event_box.xml`), altrimenti una riga in autosize da 8 a 11sp, calcolata sull'altezza vera nel launcher (`event_box_compact.xml`). Nei riquadri più bassi niente titolo. Sotto 8sp il testo diventerebbe illeggibile per i titoli lunghi, perché l'autosize riduce il testo anche per farlo stare in larghezza.
- I riquadri "tutto il giorno" e quelli con orario condividono separatore e margini (`dimens.xml`): ogni giorno della riga "tutto il giorno" è una `banner_cell` identica, così i bordi coincidono con la griglia.

`CalendarSource` scarta i calendari nascosti, gli eventi cancellati e gli inviti rifiutati.

## Colori degli eventi

Il provider salva i colori della **vecchia palette dell'API** di Google Calendar (Grafite = `#E1E1E1`, grigio chiaro), mentre l'app Google Calendar mostra colori diversi. `GoogleColors` riproduce i suoi passaggi, ricavati dal codice di Google Calendar 2026.38:

1. **Palette attuale.** I colori legacy di calendario (24) e di evento (11) diventano uno dei 24 colori attuali (Grafite `#616161`). I colori personalizzati con luminosità HSV > 0,79 hanno saturazione ×1,3 e luminosità ×0,8. L'HSV è calcolato in `float`, come Skia.
2. **Adattamento al tema.** In CIE LCh (come `ColorUtils` di AndroidX) luminosità e croma passano per curve lineari a tratti, diverse per tema chiaro e scuro. La tonalità resta uguale.

I test usano colori campionati da screenshot di Google Calendar sul telefono. Se Google cambia palette o curve, ricampiona e aggiorna i test (vedi "Verifica sul telefono").

Nel widget ogni colore di evento si imposta per **entrambi i temi** (`setColorStateList`/`setColorInt` con le varianti chiaro e scuro): il launcher sceglie quella giusta e il cambio di tema è immediato.

## Come è disegnato il widget

**Non usare bitmap né dimensioni assolute.** I launcher possono dichiarare una dimensione diversa da quella reale: il launcher Motorola dichiara circa 3/4 dell'altezza vera, quindi una bitmap viene stirata. Il widget è fatto di layout posizionati con i **pesi**:

- Un `LinearLayout` con `weightSum = S` e un solo figlio con `layout_weight = 1` dà al figlio 1/S dello spazio; la `gravity` decide da che lato. Due livelli annidati posizionano un intervallo [da, a] (vedi `event.xml` e `setWeightSums` in `WidgetViews`).
- L'altezza dichiarata dal launcher serve solo per stime: densità delle etichette delle ore, altezza minima degli eventi brevi, numero di righe di testo.
- Nei layout dei widget si possono usare solo le classi ammesse da `RemoteViews`: **niente `View` né `Space`**, per spaziatori e linee usa `FrameLayout`.
- Il launcher può riapplicare le nuove `RemoteViews` sopra quelle vecchie invece di ricrearle: i contenitori riempiti a runtime vanno svuotati con `removeAllViews` e devono contenere **solo** view aggiunte a runtime.
- Colori statici in `values/colors.xml` e `values-night/colors.xml`: il widget segue subito il tema di sistema. In codice si impostano solo i colori degli eventi, con una variante per tema (vedi "Colori degli eventi").

Aggiornamenti: alla aggiunta e al ridimensionamento del widget, quando cambia il calendario (job con trigger sul content URI, che si riprogramma dopo ogni esecuzione), e ogni quarto d'ora con un allarme `RTC` che non sveglia il telefono (sposta la linea dell'ora e cambia giorno a mezzanotte). `updatePeriodMillis` (30 min) è solo una rete di sicurezza.

## Verifica sul telefono

I test JVM coprono la logica pura (`Timeline`, `GoogleColors`, `WidgetLayout`, `EventColors`, `nextRefreshTime`), non le RemoteViews. Ogni modifica al widget va provata sul telefono: installa, forza l'aggiornamento e guarda il risultato (`adb exec-out screencap -p > shot.png`). Prova anche dimensioni diverse (ridimensionando il widget) e il tema chiaro (`adb shell cmd uimode night no`, poi ripristina con `yes`). L'anteprima nell'app usa le stesse `RemoteViews`, ma non sostituisce la prova sul launcher.

Per confrontare un colore con Google Calendar, fai uno screenshot di entrambi (stesso tema) e leggi il colore di un pixel al centro di un evento, lontano dal testo.

## Test

- Ogni modifica alla logica arriva con i suoi test. Per i casi limite (mezzanotte, ora legale, eventi che si toccano, evento che finisce adesso) scrivi un test esplicito.
- Per capire se i test proteggono davvero una parte di codice, rompila apposta (cambia un `<=` in `<`, un coefficiente, una condizione) e controlla che almeno un test fallisca. Poi ripristina con `git checkout`.
- I colori attesi nei test di `GoogleColors` vengono da screenshot reali o da un'implementazione indipendente: non rigenerarli dal codice Kotlin, altrimenti il test non verifica più niente.

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
