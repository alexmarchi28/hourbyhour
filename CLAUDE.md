# CLAUDE.md

**HourByHour** (prima "Tre Giorni") è un'app Android con un widget per la schermata Home che mostra **da 1 a 7 giorni (da oggi, o la settimana da lunedì), ora per ora**, con gli eventi di Google Calendar nella loro fascia oraria. Toccando un evento lo si apre in Google Calendar; toccando un giorno si apre quel giorno.

Gli eventi si leggono da `CalendarContract`, il calendario del telefono che Google Calendar tiene sincronizzato. **Niente login Google e niente Calendar API**: l'app non ha il permesso INTERNET e i dati non escono dal telefono.

## Comandi

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # test (devono passare prima di ogni commit)
./gradlew lintDebug            # lint: nessun errore ammesso
./gradlew installDebug         # installa sul telefono collegato via adb

# Forza l'aggiornamento del widget sul telefono
adb shell am broadcast -n it.hourbyhour/.HourByHourWidget -a it.hourbyhour.action.REFRESH
# Esegue subito il job che reagisce alle modifiche del calendario
adb shell cmd jobscheduler run -f it.hourbyhour 1
```

L'SDK è in `~/Android/Sdk` (`local.properties`, non versionato). `gradle.properties` forza IPv4: su questa macchina la JVM si blocca sugli indirizzi IPv6.

## Stack e versioni (non cambiarle senza chiedere)

- Kotlin integrato in AGP 9.4.1 (niente plugin `org.jetbrains.kotlin.android`), Gradle 9.8.1, JDK 21
- `minSdk 31` (Android 12), `compileSdk`/`targetSdk 36`. Il telefono di sviluppo è un Motorola Edge 60 con Android 16.
- **Nessuna dipendenza a runtime**: solo API della piattaforma (niente AndroidX, Compose, Glance, WorkManager). JUnit 4 solo per i test.

Prima di aggiungere una dipendenza, chiedi.

## Architettura

```
Timeline.kt           logica pura, testata su JVM: Agenda (eventi letti), eventi → giorni, fascia oraria, colonne per le sovrapposizioni
GoogleColors.kt       logica pura, testata su JVM: colori del provider → colori mostrati da Google Calendar
WidgetLayout.kt       logica pura, testata su JVM: cosa mostra il widget e dove (frazioni delle colonne)
EventColors.kt        logica pura, testata su JVM: colori di riempimento e testo degli eventi, per tema
WidgetSettings.kt     logica pura, testata su JVM: impostazioni di un widget (trasparenza, numero di giorni, partenza da lunedì, calendari)
WidgetSettingsStore.kt  salva le impostazioni per id del widget (SharedPreferences), cancellate in onDeleted
WidgetSettingsActivity.kt  schermata impostazioni di un widget, aperta dal launcher, con anteprima sopra lo sfondo della Home
CalendarSource.kt     query su CalendarContract.Instances (le ricorrenze sono già espanse) e sull'elenco dei calendari
WidgetViews.kt        traduce un WidgetLayout in RemoteViews, senza prendere decisioni
WidgetUpdater.kt      carica gli eventi, aggiorna i widget, programma il prossimo aggiornamento
HourByHourWidget.kt   AppWidgetProvider: aggiunta, ridimensionamento, cambi di ora/fuso/lingua
CalendarChangeJob.kt  JobService con trigger sul content URI del calendario
MainActivity.kt       permesso calendario, pulsante "aggiungi alla Home", anteprima del widget
WidgetPreview.kt      anteprima del widget nelle schermate dell'app (stesse RemoteViews del launcher, senza click)
CalendarIntents.kt    intent che aprono l'app calendario su un evento o un giorno
```

`Timeline.kt`, `GoogleColors.kt`, `WidgetLayout.kt`, `EventColors.kt` e `WidgetSettings.kt` non dipendono da Android: la logica nuova va lì, con i suoi test. Il flusso è `CalendarSource` → `Agenda` (una lettura per tutti i widget) → `Agenda.snapshot(settings)` (i giorni di ogni widget) → `layoutWidget` → `WidgetViews`. Ogni decisione su cosa mostrare e dove (posizioni, righe dei banner, etichette, intestazioni, evento passato, linea dell'ora) sta in `layoutWidget`: `WidgetViews` traduce e basta, così il comportamento resta coperto dai test.

Il calendario si legge **una volta** per due settimane da lunedì (`Agenda.DAYS`), una finestra che copre ogni impostazione possibile (fino a 7 giorni da oggi, o la settimana da lunedì, questa o la prossima); ogni widget e l'anteprima ne prendono i propri giorni senza rileggere il calendario. La fascia oraria si calcola solo sui giorni mostrati.

**Non rinominare `HourByHourWidget` né il package `it.hourbyhour`** (né l'`applicationId`): il nome della classe identifica il widget per il launcher, e cambiarlo fa sparire i widget già aggiunti alla Home; l'`applicationId` non si può cambiare dopo la pubblicazione sul Play Store. Sono stati rinominati una sola volta, da `ThreeDaysWidget`/`it.tregiorni`, prima di qualsiasi pubblicazione.

Regole sulla visualizzazione (in `Timeline.kt`):

- Eventi tutto il giorno ed eventi con orario lunghi **24 ore o più** vanno nella riga in alto (banner) di ogni giorno che toccano, come fa Google Calendar. Gli altri vengono tagliati sui giorni che attraversano.
- Gli eventi tutto il giorno sono salvati a mezzanotte **UTC**: le loro date si leggono in UTC, non nel fuso locale.
- La fascia oraria va dall'inizio del primo evento con orario alla fine dell'ultimo, su tutti i giorni mostrati, allargata ad almeno 6 ore. Senza eventi: 8–20.
- Gli eventi sovrapposti si dividono la larghezza del giorno in colonne, in base agli **orari reali**: due eventi che non si sovrappongono non stanno mai affiancati (un evento senza durata conta 1 minuto).

Regole sulla lunghezza degli eventi (in `WidgetLayout.kt`):

- Ogni riquadro corrisponde all'inizio e alla fine reali dell'evento. **Non allungare gli eventi per far stare il titolo**: sembrerebbero sovrapposti a quelli vicini o più lunghi del vero.
- Solo gli eventi più sottili di una barretta visibile (3 dp) si allungano, e solo nel tempo libero: prima dopo l'evento, poi prima, mai sopra un altro evento nella stessa colonna.
- È il titolo ad adattarsi al riquadro: testo normale se ci sta (`event_box.xml`), altrimenti una riga in autosize da 8 a 11sp, calcolata sull'altezza vera nel launcher (`event_box_compact.xml`). Nei riquadri più bassi niente titolo. Sotto 8sp il testo diventerebbe illeggibile per i titoli lunghi, perché l'autosize riduce il testo anche per farlo stare in larghezza.
- I riquadri "tutto il giorno" e quelli con orario condividono separatore e margini (`dimens.xml`): ogni giorno della riga "tutto il giorno" è una `banner_cell` identica, così i bordi coincidono con la griglia.

`CalendarSource` legge solo i calendari con gli eventi sincronizzati sul telefono (`sync_events = 1`) e scarta gli eventi cancellati e gli inviti rifiutati. Il flag "visibile" del provider **non si usa**: non segue le spunte di Google Calendar ("Famiglia" risultava non visibile pur essendo spuntato). Quali calendari mostrare lo decide ogni widget.

## Impostazioni per widget

Ogni widget ha le sue impostazioni (`WidgetSettings`), salvate per id del widget. Il launcher apre `WidgetSettingsActivity` (`android:configure` in `widget_info.xml`, con `reconfigurable|configuration_optional`): sul launcher Motorola è l'ingranaggio che compare mentre ridimensioni il widget. La schermata non si apre quando aggiungi un widget, perché i valori predefiniti vanno bene.

- **Trasparenza dello sfondo:** da 0 a 100% a passi di 5, predefinita 5% (l'alfa `0xF2` di prima). In `colors.xml` lo sfondo è **opaco**: `WidgetViews` applica l'alfa di ogni widget come tint della forma arrotondata, una variante per tema.
- Il testo segue il tema chiaro/scuro e non ha ombre (scelta dell'utente): sta a lui scegliere uno sfondo della Home adatto, oppure poca trasparenza.
- **Numero di giorni:** da 1 a 7, predefinito 3.
- **Inizia da lunedì:** vale da 5 giorni in su (sotto resta salvato ma non ha effetto, e l'interruttore è disattivato). Quando i giorni scelti della settimana sono finiti (sabato e domenica con 5 giorni, domenica con 6) si mostra la **settimana successiva**; con 7 giorni sempre la settimana in corso. La regola è in `WidgetSettings.firstDay`.
- **Calendari:** `calendarIds` a `null` (predefinito) vuol dire **tutti**, compresi quelli creati in seguito; un insieme vuol dire solo quelli. Spuntarli tutti nella schermata torna a `null` (`WidgetSettings.calendarSelection`). Gli id che non esistono più si ignorano. La lista mostra solo i calendari sincronizzati, il principale per primo, con la casella del colore Google Calendar.
- Un calendario appena creato arriva nel calendario del telefono solo quando Google lo sincronizza (sul telefono di sviluppo una volta al giorno, oppure con "Aggiorna" nel menu di Google Calendar). Prima di allora il widget non può vederlo: la schermata lo spiega sotto la lista.
- Nuove impostazioni vanno in `WidgetSettings`, in `WidgetSettingsStore` (con la cancellazione in `delete`) e in questa schermata.
- `MainActivity` mostra l'anteprima (`WidgetPreview`) con le impostazioni predefinite.

## Tocchi sul widget

Cosa apre ogni tocco lo decide `layoutWidget` (`ClickTarget`, testato); `CalendarIntents` lo traduce in intent e `WidgetViews` li collega.

- **Eventi** (con orario e "tutto il giorno"): aprono l'evento intero, anche dalla parte dopo mezzanotte. Per gli eventi ricorrenti si apre l'occorrenza toccata (`EXTRA_EVENT_BEGIN_TIME`/`END_TIME`).
- **Intestazione, cella "tutto il giorno" (vuota o "+N") e parte vuota della colonna:** aprono il giorno (`content://com.android.calendar/time/<millis>`) alla prima ora mostrata. Google Calendar resta nella vista che l'utente usava: in "Mese" mostra il mese, in "Giorno"/"3 giorni" il giorno.
- Si apre **Google Calendar** se installato (dichiarato in `<queries>` nel manifest), altrimenti l'app calendario predefinita: sul telefono di sviluppo c'è anche Outlook.
- Ogni `PendingIntent` ha un proprio `identifier`: Android distingue i pending intent per indirizzo e non per extra, e le occorrenze di un evento ricorrente hanno lo stesso indirizzo.
- I riquadri degli eventi stanno sopra la colonna dentro livelli trasparenti grandi quanto la colonna: quei livelli non sono cliccabili, quindi il tocco arriva al riquadro sotto il dito o, nel vuoto, alla colonna. Verificato sul launcher Motorola, anche con eventi affiancati.
- Le anteprime nell'app (`WidgetPreview`) non hanno click, così toccarle non fa uscire dalla schermata.
- Per provarli: `adb shell uiautomator dump` dà la posizione di ogni riquadro (`it.hourbyhour:id/event_box`, `day_header`, `banner_more`…), poi `adb shell input tap x y` e controlla cosa si apre.

## Colori degli eventi

Il provider salva i colori della **vecchia palette dell'API** di Google Calendar (Grafite = `#E1E1E1`, grigio chiaro), mentre l'app Google Calendar mostra colori diversi. `GoogleColors` riproduce i suoi passaggi, ricavati dal codice di Google Calendar 2026.38:

1. **Palette attuale.** I colori legacy di calendario (24) e di evento (11) diventano uno dei 24 colori attuali (Grafite `#616161`). I colori personalizzati con luminosità HSV > 0,79 hanno saturazione ×1,3 e luminosità ×0,8. L'HSV è calcolato in `float`, come Skia.
2. **Adattamento al tema.** In CIE LCh (come `ColorUtils` di AndroidX) luminosità e croma passano per curve lineari a tratti, diverse per tema chiaro e scuro. La tonalità resta uguale.

I test usano colori campionati da screenshot di Google Calendar sul telefono. Se Google cambia palette o curve, ricampiona e aggiorna i test (vedi "Verifica sul telefono").

Nel widget ogni colore di evento si imposta per **entrambi i temi** (`setColorStateList`/`setColorInt` con le varianti chiaro e scuro): il launcher sceglie quella giusta e il cambio di tema è immediato.

## Come è disegnato il widget

**Non usare bitmap né dimensioni assolute.** I launcher possono dichiarare una dimensione diversa da quella reale: il launcher Motorola dichiara circa 3/4 dell'altezza vera, quindi una bitmap viene stirata. Il widget è fatto di layout posizionati con i **pesi**:

- Un `LinearLayout` con `weightSum = S` e un solo figlio con `layout_weight = 1` dà al figlio 1/S dello spazio; la `gravity` decide da che lato. Due livelli annidati posizionano un intervallo [da, a] (vedi `event.xml` e `setWeightSums` in `WidgetViews`).
- Le dimensioni dichiarate dal launcher (`WidgetSize`) servono solo per stime. L'altezza (sbagliata sul Motorola) decide densità delle etichette delle ore, barretta minima, livello del titolo e numero di righe. La larghezza (affidabile) decide le intestazioni ("Lun 12", poi "L 12", poi "12") e se un titolo può andare a capo: se la parola più lunga non sta nel riquadro, una riga sola con "…", perché Android spezzerebbe la parola a caso. La larghezza del testo è stimata con una media per carattere (`CHAR_WIDTH_EM`).
- Nei layout dei widget si possono usare solo le classi ammesse da `RemoteViews`: **niente `View` né `Space`**, per spaziatori e linee usa `FrameLayout`.
- Il launcher può riapplicare le nuove `RemoteViews` sopra quelle vecchie invece di ricrearle: i contenitori riempiti a runtime vanno svuotati con `removeAllViews` e devono contenere **solo** view aggiunte a runtime.
- Colori statici in `values/colors.xml` e `values-night/colors.xml`: il widget segue subito il tema di sistema. In codice si impostano solo i colori degli eventi, con una variante per tema (vedi "Colori degli eventi").

Aggiornamenti: alla aggiunta e al ridimensionamento del widget, quando cambia il calendario (job con trigger sul content URI, che si riprogramma dopo ogni esecuzione), e ogni quarto d'ora con un allarme `RTC` che non sveglia il telefono (sposta la linea dell'ora e cambia giorno a mezzanotte). `updatePeriodMillis` (30 min) è solo una rete di sicurezza.

## Verifica sul telefono

I test JVM coprono la logica pura (vedi "Test"), non le RemoteViews. Ogni modifica al widget va provata sul telefono: installa, forza l'aggiornamento e guarda il risultato (`adb exec-out screencap -p > shot.png`). Prova anche dimensioni diverse (ridimensionando il widget) e il tema chiaro (`adb shell cmd uimode night no`, poi ripristina con `yes`). L'anteprima nell'app usa le stesse `RemoteViews`, ma non sostituisce la prova sul launcher.

Per confrontare un colore con Google Calendar, fai uno screenshot di entrambi (stesso tema) e leggi il colore di un pixel al centro di un evento, lontano dal testo.

## Test

- Ogni modifica alla logica arriva con i suoi test. Per i casi limite (mezzanotte, ora legale, eventi che si toccano, evento che finisce adesso) scrivi un test esplicito.
- Per capire se i test proteggono davvero una parte di codice, rompila apposta (cambia un `<=` in `<`, un coefficiente, una condizione) e controlla che almeno un test fallisca. Poi ripristina con `git checkout`.
- I colori attesi nei test di `GoogleColors` vengono da screenshot reali o da un'implementazione indipendente: non rigenerarli dal codice Kotlin, altrimenti il test non verifica più niente.
- I test girano sulla JVM, dove le classi Android esistono solo come stub che lanciano eccezioni. Per testare codice che tocca Android:
  - le decisioni stanno in funzioni senza tipi Android, anche dentro i file Android: `nextRefreshTime` ed `estimatedSize` (`WidgetUpdater.kt`), `calendarUri` (`CalendarIntents.kt`), `spanWeightSums` (`WidgetViews.kt`);
  - le interfacce Android si sostituiscono con finti in memoria: `FakeSharedPreferences` per `WidgetSettingsStore`, `fakeCursor` per le righe lette da `CalendarSource` (ha solo le colonne della query, come il provider).
- Non sono coperti da test automatici, e vanno provati sul telefono: le RemoteViews (`WidgetViews`), le query sul provider (filtri `SELECTION`), le activity, l'anteprima, gli aggiornamenti (`HourByHourWidget`, `CalendarChangeJob`, sveglia).

## Convenzioni

- Identificatori, commenti e messaggi di commit in **inglese**. Testi mostrati all'utente in **italiano** (`strings.xml`).
- Niente stato globale: gli `object` (`WidgetUpdater`, `CalendarSource`) non hanno stato.
- Lettura del calendario e costruzione del widget fuori dal main thread (`goAsync` nel provider, thread nel job e in `WidgetPreview`).

## Git

- Lavora su un **branch per funzionalità** (es. `feat/eventi-cliccabili`), non direttamente su `main`.
- **Commit piccoli e atomici**, con build, test e lint che passano.
- Messaggi in inglese, all'imperativo: titolo ≤ 72 caratteri, riga vuota, poi il *perché* se non è ovvio.

## Fuori scope per ora (non implementare senza richiesta esplicita)

Altre azioni al tocco (ad esempio creare un evento), Material You e colori dinamici, altre impostazioni del widget (ad esempio la fascia oraria fissa), ombre o colore del testo adattati allo sfondo, login Google e Calendar API.
