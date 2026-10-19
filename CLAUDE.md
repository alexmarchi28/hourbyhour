# CLAUDE.md

**HourByHour** (prima "Tre Giorni") è un'app Android con un widget per la schermata Home che mostra **da 1 a 7 giorni (da oggi, o la settimana da lunedì), ora per ora**, con gli eventi di Google Calendar nella loro fascia oraria. Toccando un evento lo si apre in Google Calendar; toccando un giorno si apre quel giorno.

Gli eventi si leggono da `CalendarContract`, il calendario del telefono che Google Calendar tiene sincronizzato. **Niente login Google e niente Calendar API**: l'app non ha il permesso INTERNET e i dati non escono dal telefono.

## Comandi

```bash
./gradlew assembleDebug        # build
./gradlew testDebugUnitTest    # test sulla JVM (devono passare prima di ogni commit)
./gradlew connectedDebugAndroidTest   # test sul telefono collegato (vedi "Test")
./gradlew lintDebug            # lint: nessun errore ammesso
./gradlew installDebug         # installa sul telefono collegato via adb

# Copertura dei test JVM: app/build/reports/coverage/test/debug/index.html
./gradlew createDebugUnitTestCoverageReport
# Copertura dei test sul telefono: app/build/reports/coverage/androidTest/debug/connected/index.html
# -Pcoverage mette JaCoCo nell'APK di debug: dopo, reinstalla quello normale con installDebug.
./gradlew -Pcoverage createDebugAndroidTestCoverageReport installDebug

# Forza l'aggiornamento del widget sul telefono
adb shell am broadcast -n it.hourbyhour/.HourByHourWidget -a it.hourbyhour.action.REFRESH
# Esegue subito il job che reagisce alle modifiche del calendario
adb shell cmd jobscheduler run -f it.hourbyhour 1
```

L'SDK è in `~/Android/Sdk` (`local.properties`, non versionato). `gradle.properties` forza IPv4: su questa macchina la JVM si blocca sugli indirizzi IPv6.

## Stack e versioni (non cambiarle senza chiedere)

- Kotlin integrato in AGP 9.4.1 (niente plugin `org.jetbrains.kotlin.android`), Gradle 9.8.1, JDK 21
- `minSdk 31` (Android 12), `compileSdk`/`targetSdk 36`. Il telefono di sviluppo è un Motorola Edge 60 con Android 16.
- **Nessuna dipendenza a runtime**: solo API della piattaforma (niente AndroidX, Compose, Glance, WorkManager). Solo per i test: JUnit 4 e androidx.test (`runner` e `core`), che non finiscono nell'APK dell'app.

Prima di aggiungere una dipendenza, chiedi.

## Architettura

```
CalendarEvent.kt      modelli letti dal calendario: CalendarEvent, CalendarInfo
Agenda.kt             logica pura, testata su JVM: Agenda (eventi letti e finestra di lettura), Snapshot (cosa mostra un widget), mondayOf
Timeline.kt           logica pura, testata su JVM: eventi → giorni, fascia oraria, colonne per le sovrapposizioni
GoogleColors.kt       logica pura, testata su JVM: colori del provider → colori mostrati da Google Calendar
WidgetLayout.kt       logica pura, testata su JVM: cosa mostra il widget e dove (frazioni delle colonne)
EventColors.kt        logica pura, testata su JVM: colori di riempimento e testo degli eventi, per tema; withAlpha
WidgetSettings.kt     logica pura, testata su JVM: impostazioni di un widget (trasparenza, colori di sistema, numero di giorni, partenza da lunedì, calendari, fascia oraria)
SampleAgenda.kt       logica pura, testata su JVM: una settimana di eventi inventati (selettore dei widget e scheda dello store)
WidgetSummary.kt      logica pura, testata su JVM: descrizione breve di ogni widget e numerazione, per l'elenco nell'app
WidgetRefresh.kt      logica pura, testata su JVM: cosa mostra ogni widget (una lettura del calendario per tutti), interfacce EventSource e SettingsRepository
AppServices.kt        il punto unico che costruisce le implementazioni vere (PhoneCalendar, WidgetSettingsStore, orologio di sistema)
WidgetSettingsStore.kt  salva le impostazioni per id del widget (SharedPreferences), cancellate in onDeleted
WidgetSettingsEditor.kt  logica pura, testata su JVM: la schermata impostazioni (posizione dei controlli, effetto di ogni modifica)
WidgetSettingsActivity.kt  schermata impostazioni di un widget, aperta dal launcher, con anteprima sopra lo sfondo della Home: traduce WidgetSettingsEditor
CalendarSource.kt     query su CalendarContract.Instances (le ricorrenze sono già espanse) e sull'elenco dei calendari; PhoneCalendar è l'EventSource vero
WidgetViews.kt        traduce un WidgetLayout in RemoteViews, senza prendere decisioni
WidgetUpdater.kt      guscio Android di WidgetRefresh: misura i widget e li disegna
RefreshScheduler.kt   dopo ogni ridisegno: sveglia del prossimo quarto d'ora, job sul calendario, anteprima del selettore
HourByHourWidget.kt   AppWidgetProvider: aggiunta, ridimensionamento, cambi di ora/fuso/lingua
CalendarChangeJob.kt  JobService con trigger sul content URI del calendario (programmato da RefreshScheduler)
MainActivity.kt       permesso calendario, pulsante "aggiungi alla Home", anteprima del widget, elenco dei widget sulla Home
WidgetList.kt         elenco dei widget sulla Home in MainActivity: toccandone uno si aprono le sue impostazioni
WidgetPreview.kt      anteprima del widget nelle schermate dell'app (stesse RemoteViews del launcher, senza click)
WidgetPickerPreview.kt  anteprima nel selettore dei widget del launcher, con gli eventi inventati
CalendarIntents.kt    intent che aprono l'app calendario su un evento o un giorno
```

`CalendarEvent.kt`, `Agenda.kt`, `Timeline.kt`, `GoogleColors.kt`, `WidgetLayout.kt`, `EventColors.kt`, `WidgetSettings.kt`, `SampleAgenda.kt`, `WidgetSummary.kt`, `WidgetRefresh.kt` e `WidgetSettingsEditor.kt` non dipendono da Android: la logica nuova va lì, con i suoi test. Il flusso è `EventSource` (`PhoneCalendar` → `CalendarSource`) → `Agenda` (una lettura per tutti i widget) → `Agenda.snapshot(settings)` (i giorni e la fascia oraria di ogni widget) → `layoutWidget` → `WidgetContent` (`WidgetRefresh`) → `WidgetViews`. Ogni decisione su cosa mostrare e dove (posizioni, righe dei banner, etichette, intestazioni, evento passato, linea dell'ora) sta in `layoutWidget`: `WidgetViews` traduce e basta, così il comportamento resta coperto dai test.

Il calendario si legge **una volta** per due settimane da lunedì (`Agenda.DAYS`, `Agenda.window`), una finestra che copre ogni impostazione possibile (fino a 7 giorni da oggi, o la settimana da lunedì, questa o la prossima); ogni widget e l'anteprima ne prendono i propri giorni senza rileggere il calendario. La fascia oraria si calcola solo sui giorni mostrati.

**Non rinominare `HourByHourWidget` né il package `it.hourbyhour`** (né l'`applicationId`): il nome della classe identifica il widget per il launcher, e cambiarlo fa sparire i widget già aggiunti alla Home; l'`applicationId` non si può cambiare dopo la pubblicazione sul Play Store. Sono stati rinominati una sola volta, da `ThreeDaysWidget`/`it.tregiorni`, prima di qualsiasi pubblicazione.

Regole sulla visualizzazione (in `Timeline.kt`):

- Eventi tutto il giorno ed eventi con orario lunghi **24 ore o più** vanno nella riga in alto (banner) di ogni giorno che toccano, come fa Google Calendar. Gli altri vengono tagliati sui giorni che attraversano.
- Gli eventi tutto il giorno sono salvati a mezzanotte **UTC**: le loro date si leggono in UTC, non nel fuso locale.
- La fascia oraria va dall'inizio del primo evento con orario alla fine dell'ultimo, su tutti i giorni mostrati, allargata ad almeno 6 ore. Senza eventi: 8–20.
- Con la fascia oraria personalizzata (vedi "Impostazioni per widget") si mostrano **almeno** le ore scelte, senza il minimo di 6 ore: la griglia si allarga solo per gli eventi fuori fascia, così nessun evento resta nascosto.
- Gli eventi sovrapposti si dividono la larghezza del giorno in colonne, in base agli **orari reali**: due eventi che non si sovrappongono non stanno mai affiancati (un evento senza durata conta 1 minuto).

Regole sulla lunghezza degli eventi (in `WidgetLayout.kt`):

- Ogni riquadro corrisponde all'inizio e alla fine reali dell'evento. **Non allungare gli eventi per far stare il titolo**: sembrerebbero sovrapposti a quelli vicini o più lunghi del vero.
- Solo gli eventi più sottili di una barretta visibile (3 dp) si allungano, e solo nel tempo libero: prima dopo l'evento, poi prima, mai sopra un altro evento nella stessa colonna.
- È il titolo ad adattarsi al riquadro: testo normale se ci sta (`event_box.xml`), altrimenti una riga in autosize da 8 a 11sp, calcolata sull'altezza vera nel launcher (`event_box_compact.xml`). Nei riquadri più bassi niente titolo. Sotto 8sp il testo diventerebbe illeggibile per i titoli lunghi, perché l'autosize riduce il testo anche per farlo stare in larghezza.
- I riquadri "tutto il giorno" e quelli con orario condividono separatore e margini (`dimens.xml`): ogni giorno della riga "tutto il giorno" è una `banner_cell` identica, così i bordi coincidono con la griglia.

`CalendarSource` legge solo i calendari con gli eventi sincronizzati sul telefono (`sync_events = 1`) e scarta gli eventi cancellati e gli inviti rifiutati. Il flag "visibile" del provider **non si usa**: non segue le spunte di Google Calendar ("Famiglia" risultava non visibile pur essendo spuntato). Quali calendari mostrare lo decide ogni widget.

## Impostazioni per widget

Ogni widget ha le sue impostazioni (`WidgetSettings`), salvate per id del widget. Il launcher apre `WidgetSettingsActivity` (`android:configure` in `widget_info.xml`, con `reconfigurable|configuration_optional`): sul launcher Motorola è l'ingranaggio che compare mentre ridimensioni il widget. La schermata non si apre quando aggiungi un widget, perché i valori predefiniti vanno bene.

- **Trasparenza dello sfondo:** da 0 a 100% a passi di 5, predefinita 5% (l'alfa `0xF2` di prima). In `colors.xml` lo sfondo è **opaco**: è un livello a parte (`ImageView` con la forma arrotondata, `@android:id/background`), di cui `WidgetViews` imposta la risorsa del colore (`setImageTintList`) e l'alfa del widget (`setImageAlpha`).
- **Colori di sistema:** spenti di default (`systemColors`, il widget ha i colori di Google Calendar). Accesi, sfondo, testi, griglia e giorno di oggi prendono la palette del telefono (vedi "Colori di sistema").
- Il testo segue il tema chiaro/scuro e non ha ombre (scelta dell'utente): sta a lui scegliere uno sfondo della Home adatto, oppure poca trasparenza.
- **Numero di giorni:** da 1 a 7, predefinito 3.
- **Inizia da lunedì:** vale da 5 giorni in su (sotto resta salvato ma non ha effetto, e l'interruttore è disattivato). Quando i giorni scelti della settimana sono finiti (sabato e domenica con 5 giorni, domenica con 6) si mostra la **settimana successiva**; con 7 giorni sempre la settimana in corso. La regola è in `WidgetSettings.firstDay`.
- **Calendari:** `calendarIds` a `null` (predefinito) vuol dire **tutti**, compresi quelli creati in seguito; un insieme vuol dire solo quelli. Spuntarli tutti nella schermata torna a `null` (`WidgetSettings.calendarSelection`). Gli id che non esistono più si ignorano. La lista mostra solo i calendari sincronizzati, il principale per primo, con la casella del colore Google Calendar.
- **Compleanni:** i compleanni dei contatti Google stanno nel calendario principale, ma Google Calendar li mostra come un calendario a sé, verde. `CalendarSource` li riconosce dalla proprietà estesa `shared:calendarProviderEventType = BIRTHDAY` (non documentata: se Google la cambia, tornano eventi normali del calendario principale), dà loro `GoogleColors.BIRTHDAYS` (salvia, il colore predefinito di Google Calendar: quello scelto dall'utente sta nelle impostazioni di Google Calendar, fuori dalla portata delle altre app) e li segna con `CalendarEvent.birthday`. Ogni widget li mostra o nasconde con `showBirthdays`, indipendentemente dal calendario che li contiene; la casella "Compleanni" viene dopo i calendari ed è spuntata di default, anche per i widget salvati prima che esistesse.
- **Fascia oraria personalizzata:** spenta di default (le ore si adattano agli eventi, come prima). Accesa, mostra almeno le ore scelte, da 0 a 24 a passi di un'ora e almeno un'ora (predefinite 8–20, come senza eventi); un evento fuori fascia allarga la griglia (`hourRange` con `minimum`). Le due barre si spingono a vicenda (`HourRange.startingAt`/`endingAt`). Spenta, le ore scelte restano salvate ma non hanno effetto, e le barre sono disattivate.
- Un calendario appena creato arriva nel calendario del telefono solo quando Google lo sincronizza (sul telefono di sviluppo una volta al giorno, oppure con "Aggiorna" nel menu di Google Calendar). Prima di allora il widget non può vederlo: la schermata lo spiega sotto la lista.
- Nuove impostazioni vanno in `WidgetSettings` (con i loro limiti, come `TRANSPARENCY_RANGE`, usati anche da `WidgetSettingsStore.load`), in `WidgetSettingsStore`, in `WidgetSettingsEditor` e in questa schermata. Ogni chiave salvata finisce con `_<id del widget>`: `delete` cancella così tutte le chiavi del widget, anche quelle nuove.
- Le modifiche non ancora salvate sopravvivono alla ricreazione della schermata (rotazione, tema scuro): `WidgetSettings` è `Serializable` e va nello stato salvato (`onSaveInstanceState`).
- `MainActivity` mostra l'anteprima (`WidgetPreview`) con le impostazioni predefinite.
- **Dall'app:** sul launcher Motorola l'ingranaggio compare solo mentre ridimensioni il widget, e alcuni launcher non permettono di riaprire le impostazioni. Per questo `MainActivity` elenca i widget sulla Home ("I tuoi widget", nascosto se non ce ne sono, aggiornato in `onResume`): "Widget N" in ordine di aggiunta (gli id crescono), con una descrizione breve (`WidgetSettings.summary`: i giorni, poi solo quello che differisce dai valori predefiniti, ad esempio "7 giorni da lunedì · ore 7–19 · 2 calendari · senza compleanni"). Toccandone uno si apre `WidgetSettingsActivity` con il suo `EXTRA_APPWIDGET_ID`. Una nuova impostazione che si vuole riconoscere nell'elenco va anche in `summary` e in `describe` (`WidgetList.kt`).

## Anteprima nel selettore dei widget

Il selettore dei widget del launcher mostra il widget prima che venga aggiunto. **Mai con il calendario vero**: solo eventi inventati (`SampleAgenda`, con i titoli `sample_*` in `strings.xml`), gli stessi delle immagini dello store.

- `previewLayout` in `widget_info.xml` (`widget_preview.xml`): un'immagine statica del widget, tre giorni dalle 9 alle 16, fatta come il widget vero (pesi, solo classi ammesse da `RemoteViews`, colori statici che seguono il tema). I colori degli eventi (`preview_*` in `colors.xml`) sono quelli che `eventColors` dà ai colori di `SampleAgenda` nei due temi: `WidgetPickerPreviewDeviceTest` lo controlla, quindi se cambiano i colori aggiornali lì.
- Da Android 15 l'app passa al selettore anche le `RemoteViews` vere del widget (`AppWidgetManager.setWidgetPreview`, "generated preview"), con le impostazioni predefinite e gli eventi inventati di oggi, senza click. Android accetta poche chiamate all'ora: `WidgetPickerPreview.publishIfNeeded` la ripete solo quando cambia qualcosa che si vede (aggiornamento dell'app, lingua, giorno: l'anteprima mostra le date), ricordando l'ultima in `PickerPreviewStore`. La chiamano l'avvio dell'app (anche senza widget sulla Home), `LOCALE_CHANGED`, `MY_PACKAGE_REPLACED` e ogni aggiornamento dei widget (una lettura delle preferenze se non serve).
- Il launcher Motorola (Android 16) mostra `previewLayout` anche quando l'anteprima generata è pubblicata (verificato il 18/10/2026, tema chiaro e scuro): per chi usa quel launcher conta solo `widget_preview.xml`, con le sue date fisse ("lun 12", "mar 13", "mer 14"). Per vederlo: tieni premuto sulla Home → Widget → cerca HourByHour.

## Tocchi sul widget

Cosa apre ogni tocco lo decide `layoutWidget` (`ClickTarget`, testato); `CalendarIntents` lo traduce in intent e `WidgetViews` li collega.

- **Eventi** (con orario e "tutto il giorno"): aprono l'evento intero, anche dalla parte dopo mezzanotte. Per gli eventi ricorrenti si apre l'occorrenza toccata (`EXTRA_EVENT_BEGIN_TIME`/`END_TIME`).
- **Intestazione, cella "tutto il giorno" (vuota o "+N") e parte vuota della colonna:** aprono il giorno (`content://com.android.calendar/time/<millis>`) alla prima ora mostrata. Google Calendar resta nella vista che l'utente usava: in "Mese" mostra il mese, in "Giorno"/"3 giorni" il giorno.
- Si apre **Google Calendar** se installato (dichiarato in `<queries>` nel manifest), altrimenti l'app calendario predefinita: sul telefono di sviluppo c'è anche Outlook.
- Ogni `PendingIntent` ha un proprio `identifier` (`ClickTarget.key`, testato): Android distingue i pending intent per indirizzo e non per extra, e le occorrenze di un evento ricorrente hanno lo stesso indirizzo.
- I riquadri degli eventi stanno sopra la colonna dentro livelli trasparenti grandi quanto la colonna: quei livelli non sono cliccabili, quindi il tocco arriva al riquadro sotto il dito o, nel vuoto, alla colonna. Verificato sul launcher Motorola, anche con eventi affiancati.
- Le anteprime nell'app (`WidgetPreview`) non hanno click, così toccarle non fa uscire dalla schermata.
- Per provarli: `adb shell uiautomator dump` dà la posizione di ogni riquadro (`it.hourbyhour:id/event_box`, `day_header`, `banner_more`…), poi `adb shell input tap x y` e controlla cosa si apre.

## Colori degli eventi

Il provider salva i colori della **vecchia palette dell'API** di Google Calendar (Grafite = `#E1E1E1`, grigio chiaro), mentre l'app Google Calendar mostra colori diversi. `GoogleColors` riproduce i suoi passaggi, ricavati dal codice di Google Calendar 2026.38:

1. **Palette attuale.** I colori legacy di calendario (24) e di evento (11) diventano uno dei 24 colori attuali (Grafite `#616161`). I colori personalizzati con luminosità HSV > 0,79 hanno saturazione ×1,3 e luminosità ×0,8. L'HSV è calcolato in `float`, come Skia.
2. **Adattamento al tema.** In CIE LCh (come `ColorUtils` di AndroidX) luminosità e croma passano per curve lineari a tratti, diverse per tema chiaro e scuro. La tonalità resta uguale.

I test usano colori campionati da screenshot di Google Calendar sul telefono. Se Google cambia palette o curve, ricampiona e aggiorna i test (vedi "Verifica sul telefono").

Nel widget ogni colore di evento si imposta per **entrambi i temi** (`setColorStateList`/`setColorInt` con le varianti chiaro e scuro): il launcher sceglie quella giusta e il cambio di tema è immediato.

## Colori di sistema (Material You)

Le schermate dell'app prendono sempre i colori dalla **palette di sistema** del telefono (quella che Android ricava dallo sfondo o dal colore scelto in "Sfondo e stile"), con i ruoli di Material 3. Il widget solo se il suo interruttore "Colori di sistema" è acceso (`WidgetSettings.systemColors`, spento di default): di default ha i colori di Google Calendar.

- I ruoli (`palette_*` in `values/colors.xml`, toni scuri in `values-night/colors.xml`) sono **l'unico posto** che dice quale tono della palette (`@android:color/system_accent1_600`, `system_neutral1_900`…) prende ogni ruolo. Il widget con i colori di sistema (`widget_system_*`) e il tema delle schermate (`Theme.HourByHour` in `themes.xml`) usano solo i ruoli, e `values-night` non li ridefinisce. I colori Google del widget (`widget_*`) sono fissi, con la variante scura in `values-night`; per ogni `widget_X` c'è un `widget_system_X`. `SystemPaletteResourcesTest` (JVM, quindi anche nella CI) lo controlla; `PaletteRole` (test sul telefono) ripete i toni attesi.
- `WidgetViews` sceglie la `WidgetPalette` (`GOOGLE` o `SYSTEM`) dalle impostazioni e imposta ogni colore **come risorsa** (`setColorStateList`/`setColor` con l'id del colore): li risolve il launcher quando applica le viste, nel suo tema. Nessun colore del widget è calcolato nell'app, quindi tutto segue un cambio di palette o di tema appena il launcher ridisegna, senza aspettare il ridisegno del quarto d'ora. Per questo lo sfondo è un livello a parte con l'alfa del widget, e gli **eventi passati** hanno il colore dell'evento al 50% di opacità (`eventColors`) invece di essere sfumati verso lo sfondo: su sfondo opaco l'effetto è lo stesso.
- Restano fissi apposta: la linea dell'ora (rossa, come in Google Calendar), i colori degli eventi (quelli di Google Calendar, vedi "Colori degli eventi"), i colori di `widget_preview.xml` (il selettore mostra un widget con le impostazioni predefinite) e l'icona.
- Le schermate usano `Theme.HourByHour` (da `Theme.DeviceDefault.DayNight`): sul Motorola `DeviceDefault` non segue la palette (barre grigie e il verde Motorola). I pulsanti usano `primary_container` e non `secondary_container`: con le palette "espressive" il secondario ha un'altra tonalità e stona con i controlli. I testi del tema sono selettori in `res/color`, che li sbiadiscono quando sono disattivati.
- `StoreGraphics` disegna il widget con le impostazioni predefinite, quindi con i colori di Google: le immagini dello store non dipendono dalla palette del telefono.
- Da verificare a mano sul telefono: che il launcher ridisegni il widget subito dopo un cambio di palette in "Sfondo e stile".

## Come è disegnato il widget

**Non usare bitmap né dimensioni assolute.** I launcher possono dichiarare una dimensione diversa da quella reale: il launcher Motorola dichiara circa 3/4 dell'altezza vera, quindi una bitmap viene stirata. Il widget è fatto di layout posizionati con i **pesi**:

- Un `LinearLayout` con `weightSum = S` e un solo figlio con `layout_weight = 1` dà al figlio 1/S dello spazio; la `gravity` decide da che lato. Due livelli annidati posizionano un intervallo [da, a] (vedi `event.xml` e `setWeightSums` in `WidgetViews`).
- Le dimensioni dichiarate dal launcher (`WidgetSize`) servono solo per stime. L'altezza (sbagliata sul Motorola) decide densità delle etichette delle ore, barretta minima, livello del titolo e numero di righe. La larghezza (affidabile) decide le intestazioni ("Lun 12", poi "L 12", poi "12") e se un titolo può andare a capo: se la parola più lunga non sta nel riquadro, una riga sola con "…", perché Android spezzerebbe la parola a caso. La larghezza del testo è stimata con una media per carattere (`CHAR_WIDTH_EM`).
- Le misure usate per le stime stanno in `LayoutEstimates` (`WidgetLayout.kt`). Quelle esatte (padding, margini, testi) sono anche in `dimens.xml`, usate dai layout: `LayoutEstimatesDeviceTest` controlla che coincidano, e che le stime (altezza di intestazioni, righe "tutto il giorno", riga di titolo) non siano più piccole delle viste disegnate. Cambiando un layout, aggiorna le due cose insieme.
- Nei layout dei widget si possono usare solo le classi ammesse da `RemoteViews`: **niente `View` né `Space`**, per spaziatori e linee usa `FrameLayout`.
- Il launcher può riapplicare le nuove `RemoteViews` sopra quelle vecchie invece di ricrearle: i contenitori riempiti a runtime vanno svuotati con `removeAllViews` e devono contenere **solo** view aggiunte a runtime.
- Colori statici in `values/colors.xml` e `values-night/colors.xml`, impostati da `WidgetViews` come risorse secondo la palette del widget (vedi "Colori di sistema"): il widget segue subito il tema di sistema. Solo i colori degli eventi sono valori calcolati, con una variante per tema (vedi "Colori degli eventi").

Aggiornamenti: alla aggiunta e al ridimensionamento del widget, quando cambia il calendario (job con trigger sul content URI, che si riprogramma dopo ogni esecuzione), e ogni quarto d'ora con un allarme `RTC` che non sveglia il telefono (sposta la linea dell'ora e cambia giorno a mezzanotte). `updatePeriodMillis` (30 min) è solo una rete di sicurezza. Disegnare (`WidgetUpdater`) e programmare (`RefreshScheduler`) sono separati: dopo ogni ridisegno il provider, e il job alla fine della sua esecuzione, chiamano `RefreshScheduler.afterRedraw`; con l'ultimo widget tolto, `cancel`. Un widget che fallisce tiene il contenuto di prima e non ferma gli altri (`drawEach`). Le schermate chiedono il ridisegno al provider (`HourByHourWidget.redraw`), che lavora con `goAsync`: un thread avviato da un'attività che si chiude può morire con il processo.

## Verifica sul telefono

I test automatici (vedi "Test") non vedono il launcher vero né l'aspetto del widget. Ogni modifica al widget va provata anche a mano sul telefono: installa, forza l'aggiornamento e guarda il risultato (`adb exec-out screencap -p > shot.png`). Prova anche dimensioni diverse (ridimensionando il widget) e il tema chiaro (`adb shell cmd uimode night no`, poi ripristina con `yes`). L'anteprima nell'app usa le stesse `RemoteViews`, ma non sostituisce la prova sul launcher.

Per confrontare un colore con Google Calendar, fai uno screenshot di entrambi (stesso tema) e leggi il colore di un pixel al centro di un evento, lontano dal testo.

## Test

- Ogni modifica alla logica arriva con i suoi test. Per i casi limite (mezzanotte, ora legale, eventi che si toccano, evento che finisce adesso) scrivi un test esplicito.
- Per capire se i test proteggono davvero una parte di codice, rompila apposta (cambia un `<=` in `<`, un coefficiente, una condizione) e controlla che almeno un test fallisca. Poi ripristina con `git checkout`.
- I colori attesi nei test di `GoogleColors` vengono da screenshot reali o da un'implementazione indipendente: non rigenerarli dal codice Kotlin, altrimenti il test non verifica più niente.
- I test girano sulla JVM, dove le classi Android esistono solo come stub che lanciano eccezioni. Per testare codice che tocca Android:
  - le decisioni stanno in funzioni senza tipi Android, anche dentro i file Android: `nextRefreshTime` (`RefreshScheduler.kt`), `estimatedSize` (`WidgetUpdater.kt`), `calendarUri` (`CalendarIntents.kt`), `spanWeightSums` (`WidgetViews.kt`);
  - l'ora arriva da un `java.time.Clock` (`Agenda.read`, `WidgetRefresh`, `RefreshScheduler`, `WidgetPickerPreview.publishIfNeeded`), predefinito quello di sistema (`AppServices.clock`): nei test si usa `Clock.fixed`;
  - le interfacce Android si sostituiscono con finti in memoria: `FakeSharedPreferences` per `WidgetSettingsStore`, `fakeCursor` per le righe lette da `CalendarSource` (ha solo le colonne della query, come il provider), `EventSource` e `SettingsRepository` finti per `WidgetRefresh` (`WidgetRefreshTest`: permesso negato, una lettura per tutti i widget, un widget che fallisce).
- Il resto si prova sul telefono, con i test in `app/src/androidTest` (girano nel processo dell'app):
  - `WidgetViewsDeviceTest`: le RemoteViews applicate, misurate e disposte come in un launcher (posizioni, titoli, intestazioni, tocchi). I tocchi mandano broadcast di prova invece di aprire il calendario.
  - `CalendarSourceDeviceTest`: le query sul provider (`SELECTION`, ordine, finestra, compleanni) su calendari locali creati e cancellati da `TestCalendars`. Il permesso di scrittura lo prende in prestito dalla shell adb (`adoptShellPermissionIdentity`): l'app resta con il solo `READ_CALENDAR`.
  - `WidgetDeviceTest` e `WidgetSettingsActivityDeviceTest`: il test ospita un proprio widget (`TestWidget`), fuori dalla Home, e lo aggiorna, ridimensiona, configura e rimuove. Non toccano i widget veri né le loro impostazioni. `WidgetDeviceTest` prova anche un compleanno da capo a fondo: evento in un calendario locale → widget verde → nascosto dalle impostazioni.
  - `CalendarIntentsDeviceTest` (Google Calendar accetta gli intent dei tocchi) e `MainActivityDeviceTest`, che trova nell'elenco dei widget il `TestWidget` (stesso provider dei widget veri, ospitato dal test) e ne apre le impostazioni.
  - `LayoutEstimatesDeviceTest`: le misure di `LayoutEstimates` contro `dimens.xml` e contro le viste disegnate.
  - `WidgetPickerPreviewDeviceTest`: `widget_preview.xml` gonfiato come `RemoteViews` (posizioni, titoli, colori nei due temi) e l'anteprima generata (eventi inventati, nessun click, pubblicata).
  - `WidgetSystemColorsDeviceTest` e `ThemeSystemColorsDeviceTest`: widget (con i colori di sistema e con quelli predefiniti), messaggio, anteprima del selettore e temi delle schermate con i colori attesi (`PaletteRole`), nei due temi. `WidgetDeviceTest` controlla anche il widget vero, `WidgetSettingsActivityDeviceTest` l'interruttore.
- `gradle.properties` contiene `android.injected.androidTest.leaveApksInstalledAfterRun=true`: senza, AGP disinstalla l'app dopo i test sul telefono, e i widget spariscono dalla Home. Non toglierlo.
- Restano da provare a mano: il launcher vero (dimensioni riportate, viste riapplicate), l'aspetto (colori confrontati con Google Calendar, tema chiaro), i tempi della sveglia e del job sul calendario.

## Convenzioni

- Identificatori, commenti e messaggi di commit in **inglese**. Testi mostrati all'utente in **inglese** (`values/strings.xml`, la lingua predefinita) e **italiano** (`values-it/strings.xml`): ogni testo nuovo va in entrambi.
- Niente stato globale: gli `object` (`WidgetUpdater`, `CalendarSource`) non hanno stato. Le dipendenze Android (calendario, impostazioni, orologio) arrivano da `AppServices`, costruito dove serve (provider, job, schermate), mai da singleton; niente framework di dependency injection.
- Lettura del calendario e costruzione del widget fuori dal main thread (`goAsync` nel provider, thread nel job e in `WidgetPreview`).

## Pubblicazione su Google Play

La guida passo passo per la Play Console è in `docs/pubblicazione-play-store.md`.

- `./gradlew bundleRelease` crea `app/build/outputs/bundle/release/app-release.aab`, da caricare nella Play Console. Prima di ogni caricamento aumenta `versionCode` (e `versionName`) in `app/build.gradle.kts`.
- La build di release passa da R8 (codice e risorse inutilizzati rimossi). Prima di pubblicarla provala sul telefono: firma l'APK di release con la chiave di debug (`apksigner sign --ks ~/.android/debug.keystore`, password `android`) e installalo sopra la build di debug, così il widget sulla Home resta. Poi reinstalla la build di debug.
- La chiave di caricamento è `~/.android-keys/hourbyhour-upload.jks`; percorso e password sono in `keystore.properties`, fuori da git. Non metterli mai nel repository. Senza quel file la build di release esce non firmata.
- L'informativa sulla privacy è nel repository **pubblico** `alexmarchi28/hourbyhour-privacy` (GitHub Pages: https://alexmarchi28.github.io/hourbyhour-privacy/), linkata dall'app (`MainActivity`) e dalla scheda dello store. Dice che i dati non escono dal telefono: niente permesso `INTERNET`, impostazioni escluse dai backup (`data_extraction_rules.xml`). Se cambia il modo in cui l'app tratta i dati, aggiorna l'informativa, il modulo "Sicurezza dei dati" e i testi della scheda.

### Scheda dello store

Testi e immagini sono in `fastlane/metadata/android/<lingua>/` (`en-US`, `it-IT`), nella struttura usata da fastlane. Icona, grafica in primo piano e screenshot li disegna `StoreGraphics`, un test sul telefono, con gli eventi inventati di `SampleAgenda` (mai con il calendario vero) e nelle dimensioni chieste da Google Play. Le immagini restano tra i file dell'app; per copiarle nel repository:

```bash
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=it.hourbyhour.StoreGraphics
adb exec-out run-as it.hourbyhour tar c -C files/store . | tar x -C fastlane/metadata/android
```

Rigenerale quando cambia l'aspetto del widget, e guardale prima di committarle.

## Git

- Lavora su un **branch per funzionalità** (es. `feat/eventi-cliccabili`), non direttamente su `main`.
- **Commit piccoli e atomici**, con build, test e lint che passano.
- Ogni PR e ogni push su `main` passano dalla **CI** (`.github/workflows/ci.yml`, GitHub Actions): build, test sulla JVM e lint, con i report scaricabili dalla pagina della run. I test sul telefono restano fuori dalla CI e vanno eseguiti a mano. Una PR entra solo con la CI verde.
- Messaggi in inglese, all'imperativo: titolo ≤ 72 caratteri, riga vuota, poi il *perché* se non è ovvio.

## Fuori scope per ora (non implementare senza richiesta esplicita)

Altre azioni al tocco (ad esempio creare un evento), altre impostazioni del widget (ad esempio una fascia oraria fissa che nasconde gli eventi fuori), ombre o colore del testo adattati allo sfondo, login Google e Calendar API.
