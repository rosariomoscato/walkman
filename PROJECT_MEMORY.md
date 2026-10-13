# WalkTape — memoria di progetto

Ultimo aggiornamento: 2026-10-13 (W05-T03 completato).

## Punto di ripresa

- Fase: **W05-T03 completato**, codice/test/documenti locali non committati. T01+T02 nel commit `74be2d0`, non pushato. Prossima azione precisa: **W05-T04** — Room con coda canonica, permutazione shuffle, occorrenza corrente e posizione per profilo, migrazione esplicita e ripristino sempre in pausa; test riavvio/migrazione/cambio profilo. Non salvare URL autenticati o credenziali.
- Verifiche T03: build APK/app-test riuscita; **53/53 JVM** con login reale, **9/9 Android** (7 motore + 2 selezione con server/indice reale), lint 0 errori/22 warning. Shuffle nativo e UUID per occorrenza: duplicati, ordine ripristinato, posizione conservata e rebind senza perdita di shuffle verificati. Riordino tramite menu/azioni accessibili, non drag (prototipo non superato). Selezione Album/Brani con NOW/NEXT/APPEND: completa, isolata, nessun autoplay da coda vuota per aggiunte. APK aggiornato nel percorso standard. Emulatore spento, file env e marker temporanei rimossi.

- Wave 05 ancora aperta: **T04–T07**. Le verifiche T01/T02 e i riscontri telefono sotto restano validi per quegli incrementi, non attestano T03 sul Motorola.
- Verifiche T02: build/lint ok (0 errori/18 warning); 45/45 JVM (login reale); 6/6 Android sul server reale in emulatore con comandi UI esercitati da test (play/pausa, seek accessibile+touch, precedente/successivo, ripetizioni con wrap reale, fine coda e ripartenza, volume Android con gain player invariato, riconnessione senza autoplay); UI reale verificata via dump (barra sopra la navigazione, pannello con stato/tempi/ripetizione/volume). APK debug aggiornato nel percorso standard.
- Limitazioni T02 da telefonare: tasti volume fisici (l'iniezione tasti sull'AVD non è efficace e il controller locale non espone i comandi volume dispositivo — dettagli in WORKLOG e `VolumeKeyDiagnosticTest`); TalkBack manuale e font scale reale sui nuovi controlli.
- **Riscontro hardware dell'utente (T01)**: Motorola G55 5G / Android 16, Tailscale → MP3 OK, FLAC OK, pausa/ripresa dalla notifica OK, schermo spento 10 minuti OK. **Riscontro T02**: l'utente conferma che i comandi in-app funzionano sul telefono (dettagli per punto non forniti; checklist locale resta con voci non attestate esplicitamente). Chiamate/cuffie/Bluetooth/lockscreen completo e cambio profilo hardware non ancora riferiti; T05/T07 aperti.
- Wave 03: T01–T03/T05 pubblicati (`c97fe7d`); wave 04 pubblicata per intero: primo incremento `fa17b0e`, completamento **`26584bc`** (push su richiesta dell'utente; HEAD e origin/main verificati allineati). Working tree pulita al push; note successive locali. Commit/push successivi solo su richiesta esplicita.
- W03-T04/T06 restano aperti sulle prove telefono (checklist in `docs/PHONE_TESTS.md`).
- Verifiche wave 04 (secondo incremento): 39/39 test JVM con login reale; Android 12/12 + 4/4 + regressione UI 1/1 su libreria reale (206 s, tutte le tab, dettagli, ricerca unificata, selezione, ordinamenti, playlist, filtri genere/anno/offline). Prestazioni fixture 10.000 brani: pagina filtrata 80 → 487 ms freddo / 205 ms caldo. Server reale: 459 album, 6.403 brani, 196 artisti; **l'account di test non ha playlist** (pipeline verificata con fixture + API reali di lista; dettaglio reale da ripetere in wave 07).
- Prima di continuare: leggere `docs/ARCHITECTURE.md` e `DESIGN.md`. Per le build `.\gradlew.bat` con `JAVA_HOME` al JBR; emulatore `walktape_aosp` spento al termine dell'incremento.

## Documenti di riferimento

| File | Responsabilità |
| --- | --- |
| `SPEC.md` | Perimetro funzionale approvato; fonte di verità su cosa costruire. |
| `PLAN.md` | Wave, task stabili, dipendenze, criteri di uscita e backlog. |
| `docs/ARCHITECTURE.md` | Stack/versioni bloccati (con emendamenti wave 02), architettura, esiti server, percorso audio. |
| `DESIGN.md` | Sistema visivo vincolante: token TPS-L2, tipografia, materiali, stati, accessibilità. |
| `WORKLOG.md` | Registro cronologico append-only di attività, verifiche e blocchi. |
| `PROJECT_MEMORY.md` | Sintesi corrente di decisioni, stato e passaggio di consegne. |
| `AGENTS.md` | Regole operative per gli agenti che lavorano nel repository. |
| `NEXT_PROMPT.md` | Prompt copiabile per una nuova sessione; la memoria aggiornata prevale sul punto di ripresa riportato nel prompt. |

## Decisioni da conservare

- D01 — App Android **nativa**, non PWA né wrapper web.
- D02 — La skill `start-an-app` guida discovery e verifica; il suo stack web è sostituito dalla scelta nativa esplicitamente approvata.
- D03 — Nome **WalkTape**; TPS-L2 blu/argento/arancione, marchio proprio, un modello nella prima versione.
- D04 — Uso iniziale per proprietario/amici/familiari; prospettiva pubblica. Open source MIT, GitHub Releases e APK firmati.
- D05 — Navidrome come fonte di verità; niente account/backend WalkTape aggiuntivo. OpenSubsonic dove disponibile.
- D06 — Profili multipli, uno attivo, dati/download separati; Tailscale e HTTPS, HTTP nei contesti locale/VPN concordati.
- D07 — Offline completo già nella prima versione, comprese modifiche e conflitti; download interni e cache limitata.
- D08 — Android Auto già nella prima versione, nel linguaggio UI della piattaforma.
- D09 — Suono pulito predefinito; crossfade, ReplayGain, equalizzatore, rinforzo bassi, effetto cassetta e clic opzionali inizialmente spenti.
- D10 — Italiano/inglese, chiaro/scuro, accessibilità, tablet e orientamento orizzontale inclusi.
- D11 — Nessuna telemetria automatica; backup manuale senza credenziali/audio; niente cloud WalkTape.
- D12 — Tutte le wave funzionali concorrono alla prima versione approvata; non tagliare funzionalità senza accordo.
- D13 — Conservare piano, log e memoria per passaggio di conoscenza tra sessioni.
- D14 — **Emendata in wave 02**: AGP 9.4.0 con **Kotlin built-in** (versione Kotlin decisa da AGP; plugin `kotlin.android` non applicato), Gradle wrapper 9.8.1, Compose BOM 2026.09.00, Media3 1.11.1, compileSdk 37 (piattaforma android-37.0; richiesto da navigation-compose 2.10.2), **targetSdk 36** invariato, minSdk 26, KSP 2.3.12, Room 2.8.5, DataStore 1.2.1, Navigation 2.10.2, lifecycle 2.11.0, activity 1.13.0, core 1.19.1, appcompat 1.8.0. Riferimenti: WORKLOG wave 01/02, `gradle/libs.versions.toml`.
- D15 — Ambiente build: Android Studio 2026.2.1.8 + JBR 25, SDK in `%LOCALAPPDATA%\Android\Sdk` con platform-tools 37.0.1, emulator 37.2.12, build-tools 37.0.0, piattaforme 36/37.0/37.2; `local.properties` nel progetto (sdk.dir) perché le variabili utente non si propagano alle shell dei tool.
- D16 — Account di test `walktape-test` (non admin) sul server reale; credenziali solo in `local/test-server.env`, escluso da Git.
- D17 — Client OpenSubsonic: autenticazione token+salt MD5 (`p=` rifiutato dal server); transcoding disponibile lato server (Opus verificato su richieste esplicite). Non assumere un transcode predefinito per qualsiasi client: in W05-T01 anche `stream` senza formato con client `probe` restituisce originali nel campione. Download sempre originale; Range onorato su pass-through/download, ignorato su transcode.
- D18 — App mono-modulo `:app` (package `com.walktape.app`), contenitore manuale `Graph` senza framework DI; Room con schema esportato in `app/schemas/` e `MIGRATIONS` da estendere a ogni cambio schema; preferenze in DataStore; per-app locale via AppCompat (it/en); backup cloud disattivato (`allowBackup=false` + `dataExtractionRules` che esclude tutto).
- D19 — Emulatore di riferimento per le verifiche: AVD **`walktape_aosp`** (system image AOSP `android-36/default/x86_64`, 3 GB RAM/4 core, avvio headless con swiftshader). L'immagine google_apis va sistematicamente in ANR su questo host (carico kDrive/Docker): usare sempre l'AOSP per l'automazione.
- D20 — Verifica UI su emulatore senza capacità visive del modello: `uiautomator dump` per contenuti/coordinate e colore medio degli screenshot (System.Drawing) per i temi; procedure collaudate in WORKLOG wave 02.
- D21 — Credenziali profili: cipher-text AES-GCM in Room (`credentialId`, `encryptedPassword`), chiavi non esportabili per profilo in Android Keystore con AAA bindato all'id; la password in chiaro esiste solo in memoria durante login/uso. Migrazione v2 disattiva i profili della shell wave-02 (metadati conservati, richiesto nuovo login).
- D22 — Isolamento profili: dati per profilo in `filesDir/profiles/<id>`; attivazione transatomica (un solo profilo attivo); rimozione = conferma con byte locali → directory + chiave Keystore + riga DB; mai mutazioni server.
- D23 — QR configurazione: JSON v1 `{v,n,u,un}` senza password (fallback URL nudo; nome vuoto ammesso); zxing core 3.5.4 per generazione locale, zxing-android-embedded 4.3.0 per scansione; la password si inserisce sempre a mano sul dispositivo che scansiona.
- D24 — Blocco app opzionale (default off): interruttore visibile solo se `canAuthenticate(BIOMETRIC_WEAK|DEVICE_CREDENTIAL)`; gate BiometricPrompt all'avvio dopo la prima emissione DataStore (mai su default).
- D25 — Verifica su emulatore sotto carico host: precompilare gli APK con `pm compile -m speed -f` prima di `am instrument` (evita ANR "failed to complete startup"); build Gradle lunghi via processo detached con log su file + `-Dkotlin.compiler.execution.strategy=in-process` (il daemon Kotlin separato fallisce sotto pressione memoria).
- D26 — Room v3: indice per profilo, Paging 3.5.3, refresh progressivo con quattro dettagli album simultanei e sostituzione atomica dei brani. Cache copertine privata 250 MiB disco/16 MiB bitmap per store. Il cambio tab richiede chiavi dipendenti dal tipo effettivo dello snapshot, oltre che dal profilo (regressione verificata).
- D27 — Room v4: playlist/posizioni per profilo con CASCADE, `local_audio` come catalogo disponibilità offline, `played`/`starred` per le sezioni home. Liste via `RawQuery` parametrizzate (`BrowseQueries`): ordinamenti in whitelist, LIKE con escape, filtro offline album/playlist = tutti i brani presenti nel catalogo. `LocalAudioCatalog` accetta solo path relativi canonici dentro la directory profilo e file non vuoti; popolamento reale in wave 08. Playlist lette senza username admin; cache non svuotata da errori di rete.
- D28 — Verifica UI automatica su emulatore: la striscia tab scrollabile richiede ricerca per testTag (`library-tabs`, `testTagsAsResourceId`) + swipe alternati; i dialog ANR di sistema vanno chiusi via accessibilità ("Wait") e possono richiedere il riavvio dell'emulatore se il launcher resta bloccato. Registrare gli ANR come ambiente, non come difetti dell'app, salvo prova contraria.
- D29 — W05-T01: Media3 1.11.1 (ExoPlayer/session/datasource-okhttp) con URI `wtstream://song/<profileId>/<id>` senza segreti, risolte a `stream.view?format=raw` con token+salt nuovo a ogni apertura. Client streaming senza call timeout totale (read 30 s/connect 10 s; redirect disabilitati). Cambio profilo = pausa/stop/clear prima di caricare credenziali; media di altri profili rifiutati. Callback attende il caricamento iniziale Room/Keystore. Connessioni sessione per UID app/controller trusted. Coda per ora in memoria, copertina notifica da integrare; persistenza T04, qualità W09-T01.
- D30 — W05-T02: stato UI come `PlaybackSnapshot` immutabile (listener `onEvents` + polling 500 ms solo con media; nessun URL/segreto). Comandi sempre previa `isCommandAvailable` sul main looper; seek con chiave traccia+indice così un gesto in corso non si applica a traccia/profilo cambiati; play dopo fine coda riparte dal primo media; riconnessione bridge senza autoplay. Volume = volume Android `STREAM_MUSIC` via AudioManager (mai `Player.setVolume`), `volumeControlStream` su activity e finestra pannello, `setDeviceVolumeControlEnabled(true)` sul player per pubblicare l'intervallo reale. Su AVD l'iniezione tasti volume non è affidabile e il controller locale non espone comandi volume dispositivo: tasti fisici da verificare su telefono. Slider Compose con nodo accessibile unico (`clearAndSetSemantics` + `ProgressBarRangeInfo` + `setProgress`). Reset `repeatMode` al cambio profilo.
- D31 — W05-T03: ExoPlayer shuffle nativo, permutazione nel servizio (`ServiceShuffle`) con corrente per primo; timeline canonica preservata, nessun setMediaItems per il toggle. MediaController espone il vero ordine via Timeline (bundle contiene indici shuffle, verificato su sorgenti 1.11.1 e test). MediaId UUID per ogni occorrenza, songId separato negli extras; azioni UI per ID, mai per titolo/songId/indice stale. Aggiunte serializzate e ancorate: NEXT dopo corrente anche in shuffle, APPEND in fondo. Manual reorder torna lineare con avviso, menu e azioni accessibili; drag non esposto. Solo NOW avvia coda vuota. Persistenza resta T04, niente cambio schema per T03.

## Ambiente noto e dati di riferimento

- Workspace: `C:\Users\rosar\kDrive\Lavoro\Progetti\walkman` (nome cartella locale invariato); repository GitHub rinominato dall'utente in `rosariomoscato/walktape`, remote `origin` = https://github.com/rosariomoscato/walktape.git, branch `main` tracciato. Remote aggiornato e accesso verificato il 2026-10-13. Identità per-commit: `Rosario Moscato <53149348+rosariomoscato@users.noreply.github.com>` (config non persistita).
- Shell: Windows PowerShell 5.1. Usare `npx.cmd`; `Range` header via `[System.Net.HttpWebRequest]::AddRange`; redirect `>` di binari corrompe i file (usare `adb pull`).
- Node 22.23.1 disponibile; `rg` non nel PATH: usare gli strumenti dedicati di ricerca.
- Playwright MCP attivo su **Edge**; usarlo per verifiche web/browser, mai come prova Android.
- Server: Navidrome 0.62.0 (`1b46b977`) su `http://localhost:4533`, API Subsonic 1.16.1 + OpenSubsonic (songLyrics, transcoding, formPost, playbackReport, transcodeOffset, indexBasedQueue). Libreria: 196 artisti, 460 cartelle.
- Dal telefono usare indirizzo LAN/Tailscale del computer. Percorso Tailscale verificato dall'utente sul Motorola G55 5G / Android 16 con streaming MP3/FLAC; LAN non ancora verificata.
- Hardware: Motorola G55 e successivi; Android 15/16; utente disponibile per test su telefono e in auto.
- Host: kDrive sync della cartella progetto consuma CPU durante le build (~0,7 core); Docker Desktop presente. Segnalato all'utente il suggerimento di escludere `build/`/`.gradle/` dal sync kDrive.
- Default approvati: cache audio 1 GB, copertine 250 MB, download solo Wi-Fi, originali nei download, avvio su “La mia musica”.

## Da verificare / informazioni mancanti

- Raggiungibilità LAN dal telefono ancora da verificare; Tailscale confermato dall'utente. W03-T06 resta aperto per le altre prove pendenti (VPN assente/isolamento e casi di errore sul telefono).
- Verifiche telefono pendenti: scansione QR via camera, dialog condivisione QR su profilo reale, gate biometrico interattivo, VPN assente, due profili reali (wave 03, T04/T06).
- HTTPS contro un server TLS reale non ancora esercitato (percorsi HTTPS unit-testati; server locale solo HTTP).
- Testi con contenuti reali via `getLyricsBySongId` (wave 07); funzioni mutative su account di test (wave 07+).
- Audio combinato su hardware reale (wave 09); Android Auto in auto (wave 10).
- TalkBack, caratteri ingranditi, contrasti misurati (wave 06/12).
- Checklist telefono wave 05 privata in `local/PHONE_TESTS_W05.md`: comandi in-app T02 (barra+pannello, seek, ripetizione, **tasti volume fisici**), MP3/FLAC, notifica, cambio profilo e schermo spento prolungato; interruzioni/cuffie/Bluetooth in T05/T07. **Discrepanza verificata**: `docs/PHONE_TESTS.md` è già tracciato da Git, non ignorato come indicato nel prompt; nessuna nuova prova telefono aggiunta a quel file. `NEXT_PROMPT.md` e la nuova checklist in `local/` sono ignorati.
- Repository GitHub per release, identità distributore/contatto privacy, chiave di firma (wave 12).
- Aggiornamenti patch disponibili (AGP 9.4.1, plugin compose 2.4.21): valutare al prossimo incremento.

## Stato dei file e del lavoro

- Progetto Android nella radice: `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `gradlew(.bat)`, `app/` (sorgenti, risorse, schemi Room 1/2/3/4, test JVM e androidTest). APK debug verificato su emulatore (v0.1.0).
- Pubblicati: wave 03 in `c97fe7d`, wave 04 completa in `fa17b0e` + `26584bc`. Le eventuali note documentali successive al push restano locali; altri commit/push solo su nuova richiesta esplicita.
- Working tree: W05-T01+T02 committati in **74be2d0** su richiesta, senza push. T03 e note post-commit precedenti sono locali; preservarli. APK aggiornato, non pubblicato. Nessun cambio schema per T03. Emulatore spento; file env temporaneo e marker rimossi. Restano da confermare dal telefono le funzioni coda T03, checklist privata in `local/PHONE_TESTS_W05.md`.
- `local/` e `.playwright-mcp/` esclusi da Git. `local.properties` escluso da Git. `NEXT_PROMPT.md` è un documento locale, escluso da Git su richiesta dell'utente (non pubblicato).
- Consultare `git status` alla ripresa: non assumere che file non tracciati siano eliminabili.

## Protocollo di handoff

Alla fine di un incremento: aggiornare checkbox verificate, aggiungere log con ID task e prove, poi aggiornare questa memoria con stato, blocchi e **prossima azione precisa**. Registrare qui nuove decisioni con ID, motivazione e riferimento al log; non accumulare output grezzi o segreti.
