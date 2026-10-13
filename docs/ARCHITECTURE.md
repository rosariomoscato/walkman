# WalkTape — architettura e risultati della wave 01

Stato: ricerca di fattibilità completata su fonti ufficiali e server reale. Documento vincolante per lo scaffold della wave 02.

> Emendamenti wave 02 (verificati in build reale): AGP 9 usa il **Kotlin built-in** (plugin `kotlin.android` rimosso; versione Kotlin decisa da AGP); `compileSdk = 37` (navigation-compose 2.10.2 richiede API 37; piattaforma `android-37.0` installata) con `targetSdk = 36` invariato; Media3 aggiornato a **1.11.1**; aggiunti KSP 2.3.12, Room 2.8.5, DataStore 1.2.1, Navigation Compose 2.10.2, lifecycle 2.11.0, activity-compose 1.13.0, core-ktx 1.19.1, appcompat 1.8.0, material-icons (via BOM 1.7.8). Dettagli nel WORKLOG della wave 02.

## Stack e versioni scelte

| Componente | Versione | Fonte |
| --- | --- | --- |
| Kotlin | 2.4.20 | kotlinlang.org/releases (stabile corrente) |
| Android Gradle Plugin | 9.4.0 (set. 2026) | developer.android.com/build/releases/gradle-plugin |
| Gradle | 9.6+ via wrapper (minimo richiesto da AGP 9.4) | tabella compatibilità AGP |
| JDK | 25 (JBR di Android Studio 2026.2.1.8) | installazione verificata; AGP richiede ≥17 |
| Compose BOM | 2026.09.00 → Compose 1.12.1, Material3 1.4.0 | developer.android.com BOM mapping |
| Media3 | 1.11.0 stabile | developer.android.com/jetpack/androidx/releases/media3 |
| minSdk | 26 (Android 8, da `SPEC.md`) | decisione W01-T02 |
| targetSdk / compileSdk | 36 / 36 (Android 16, piattaforma dei dispositivi di riferimento; API 37.2 installata per test futuri) | decisione W01-T02 |

Librerie sussidiarie (Room, DataStore, security-crypto, rete HTTP/serializzazione, WorkManager): la scelta puntuale avviene allo scaffold (wave 02/03) con verifica delle versioni stabili su fonti ufficiali. Nessuna dipendenza non verificata verrà aggiunta.

## Ambiente di build (verificato)

- Android Studio 2026.2.1.8 installata via winget in `C:\Program Files\Android\Android Studio` (JBR 25.0.3).
- SDK in `%LOCALAPPDATA%\Android\Sdk`: cmdline-tools (latest, build 15859902), platform-tools 37.0.1 (adb 1.0.41), emulator 37.2.12, build-tools 37.0.0, piattaforme android-36 (rev 2) e android-37.2 (rev 1). Licenze accettate.
- Variabili utente: `JAVA_HOME` → JBR di Android Studio; `ANDROID_HOME` → SDK.
- Non ancora presenti: system image e AVD (creazione in wave 02, con verifica requisiti virtualizzazione Windows). Nessun dispositivo fisico collegato finora.

## Architettura applicativa proposta

### Incremento wave 05 — W05-T01

- `playback/PlaybackService` è un `MediaSessionService` con ExoPlayer, notifica Media3 predefinita e PendingIntent per riaprire l'app. Il manifest dichiara `mediaPlayback`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` e `WAKE_LOCK`.
- Attivati gli artefatti `media3-exoplayer`, `media3-session` e `media3-datasource-okhttp`, tutti **1.11.1**. Il DataSource ufficiale OkHttp conserva richieste Range e gestione degli errori HTTP; compatibilità con OkHttp 5.5.0 verificata dai test Android.
- Gli elementi media contengono solo URI logiche `wtstream://song/<profileId>/<songId codificato>`: niente URL autenticati nella coda/sessione. `ResolvingDataSource` genera un token+salt nuovo a ogni `open`, anche per richieste Range, riutilizzando le credenziali incapsulate dal client OpenSubsonic. La sostituzione con `DataSpec.withUri` conserva posizione, lunghezza, header e flag.
- Primo percorso audio: `stream.view` con **`format=raw`**, originale senza transcode; qualità per rete/profilo in W09-T01. Verifica server 2026-10-13: MP3 `audio/mpeg`, FLAC `audio/flac`, 200 su richiesta completa e 206 con Content-Range su richiesta parziale. Anche le richieste senza formato, nel campione e con client `probe`, hanno restituito originali: non assumere un transcode predefinito per qualsiasi client.
- Client HTTP streaming separato da quello JSON: connect timeout 10 s/read timeout 30 s, **nessun call timeout totale**, nessun redirect/cookie/logger/cache condivisa. Non applicare il limite totale di 30 s delle API JSON a un'intera traccia.
- Il servizio osserva il profilo attivo: azzera il collegamento, pausa/stop/svuota la coda prima di caricare le nuove credenziali. I media di un profilo diverso vengono rifiutati prima della rete. Il callback di sessione attende il caricamento iniziale Room/Keystore prima di accettare i media. Coda persistita e ripristino: W05-T04.
- Connessioni alla sessione ammesse per UID dell'app e controller considerati trusted da Android; il servizio resta exported per i controlli di sistema. AudioAttributes MUSIC/MEDIA, audio focus automatico, becoming-noisy e wake lock locale configurati secondo Media3; verifiche complete interruzioni/Bluetooth/hardware rimangono W05-T05/T07.
- `PlaybackConnection` usa MediaController sul main executor, connessione onStart/rilascio onStop dell'activity. Dettaglio album: riproduzione completa o da una traccia selezionata. Copertina nella notifica, player TPS-L2 e coda persistita non sono parte di questo incremento.

Fonti ufficiali consultate: [MediaSessionService (sorgente AndroidX)](https://github.com/androidx/media/blob/release/libraries/session/src/main/java/androidx/media3/session/MediaSessionService.java), [OkHttpDataSource versione 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/datasource_okhttp/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java); firme della versione effettiva controllate anche tramite `javap` sugli artefatti scaricati. La guida developer.android.com non era raggiungibile dal tool web nella sessione.

### Controlli wave 05 — W05-T02

- `PlaybackConnection` espone `PlaybackSnapshot` immutabile: metadati/stato, posizione/durata effettiva, disponibilità dei comandi, ripetizione e volume Android. Listener `Player.onEvents` per snapshot coerenti; polling 500 ms solo durante la connessione foreground e con media, perché Media3 non emette eventi per il normale avanzamento. Listener/job/comandi pendenti rimossi onStop e riallineati alla sessione onStart, senza play automatico.
- Comandi eseguiti sul main/application looper, previa verifica `isCommandAvailable`. Precedente usa `seekToPrevious` (restart dopo la soglia Media3, altrimenti brano precedente), successivo `seekToNext`; play a fine coda riparte dal primo media. Retry da errore/idle chiama prepare. Ripetizione OFF→ONE→ALL→OFF; reset al cambio profilo, persistenza rinviata a T04.
- Seek in millisecondi, limitato a [0,durata], solo su media seekable con durata nota. Chiave del media+indice catturata durante il gesto: un gesto precedente non sposta la traccia/profilo successivo. Coda richiesta dalla libreria validata contro il profilo attivo; richieste pendenti annullate scollegando l'activity.
- Volume reale `AudioManager.STREAM_MUSIC`, mai `Player.setVolume`: slider e tasti fisici lavorano sullo stesso volume Android. Dichiarato il permesso normale MODIFY_AUDIO_SETTINGS; MainActivity instrada i tasti volume su STREAM_MUSIC. Valori riletti da sistema e route a volume fisso rispettate.
- `PlaybackControls` nella shell: barra di trasporto e ModalBottomSheet per seek/ripetizione/volume, con layout a due righe per caratteri ingranditi e pannello scrollabile. Errori UI come enum generici, nessuna esposizione di exception/server message/token.

Fonte ufficiale per semantica/threading/eventi/comandi: [Player Media3 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/Player.java). Dipendenze/versioni invariate.

Limitazione verificata su emulatore AOSP 36 (2026-10-13): un `MediaController` locale non espone i comandi di scrittura volume dispositivo (`COMMAND_SET/ADJUST_DEVICE_VOLUME*` = false), quindi il volume resta governato via `AudioManager`/tasti fisici e non dal controller; l'iniezione di `KEYCODE_VOLUME_UP` tramite `input keyevent`/`sendKeyDownUpSync` su questo AVD non modifica il volume (le API audio sì): i tasti fisici restano da verificare sul Motorola. Dettagli in WORKLOG W05-T02 e test diagnostico `VolumeKeyDiagnosticTest` (opt-in).

### Coda wave 05 — W05-T03

- Shuffle **nativo** ExoPlayer, non ricostruzione dei MediaItems dal controller. `ServiceShuffle` prepara una permutazione completa con il corrente per primo, tramite `DefaultShuffleOrder`; il MediaItem corrente, la posizione e il buffer non vengono sostituiti. Disabilitando shuffle la timeline canonica originale resta disponibile. Stato e permutazione appartengono al servizio e sopravvivono al rilascio/rebind del controller.
- `Timeline.getFirstWindowIndex/getNextWindowIndex` espongono l'ordine anche al MediaController: il sorgente ufficiale **1.11.1** include gli indici shuffle nel Bundle della timeline. La UI percorre quell'ordine con repeat OFF, senza duplicare la logica di avanzamento. Cache delle righe invalidata da timeline/indice corrente/shuffle, non da ogni tick di posizione.
- `onAddMediaItems` assegna a ogni occorrenza un `mediaId` UUID distinto, preservando il songId negli extras (`QueueIdentity.SONG`); URI di streaming rimane logica/vincolata al profilo. Rimozione, riproduzione e riordino identificano l'occorrenza, non il titolo o il songId. Non possono colpire una copia diversa o un indice ormai cambiato.
- Inserimenti serializzati da Mutex; attendono identità normalizzate prima di ancorare azioni successive. NEXT/NOW inseriscono dopo il corrente; APPEND in fondo. In shuffle, il servizio conserva la permutazione dei sopravvissuti e posiziona i nuovi gruppi dopo l'ancora richiesta oppure in fondo. Non si resuscitano elementi rimossi dopo un toggle; cambio profilo svuota e resetta anche shuffle.
- Le selezioni Album/Brani vengono risolte dall'indice Room per profilo, con bind IN in blocchi di 500 e ordine dei record visualizzati (disco/traccia per album); nessun brano selezionato perso se la pagina non è più caricata. Richieste annullate da cambio selezione/profilo non producono mutazioni; elementi mancanti mantengono la selezione con errore. Solo NOW avvia; NEXT/APPEND rispettano la pausa e restano in pausa se la coda è vuota.
- Riordino manuale applicato con `moveMediaItem`, preservando il corrente; se shuffle è attivo ripristina prima la traversata lineare. UI operativa tramite menu e azioni accessibili, senza handle drag non verificati. Nessun cambio schema Room o nuova dipendenza. Persistenza coda/posizione: **W05-T04**.

Fonti: [Timeline 1.11.1](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/Timeline.java), in particolare serializzazione `FIELD_SHUFFLED_WINDOW_INDICES`; Player/Media3 del medesimo tag. Verifiche: vedi WORKLOG W05-T03.

### Incremento wave 04 (in verifica)

Aggiornamento W04-T02/T03 (in verifica): Room v4 conserva l'indice v3 e aggiunge playlist/posizioni, metadati `played` degli album e `starred` degli artisti, catalogo di disponibilità audio locale. Le playlist sono lette tramite `getPlaylists`/`getPlaylist` senza parametro amministrativo username, salvate per profilo in transazione con chiave posizione (brani duplicati preservati), rimosse dal cache solo dopo una lettura riuscita della lista. Dettaglio e liste sono paginati e consultabili offline. Ricerca unificata e filtri usano query SQL con parametri bindati e ordinamenti in whitelist. Il catalogo audio viene registrato solo per file non vuoti all'interno della directory privata del profilo; la popolazione da download/cache audio sarà integrata in wave 08. Nessun download reale dichiarato implementato in questa fase.

- Room v3 aggiunge `artist`, `album`, `song`, `genre`, `library_state`, tutti con `profileId` nelle chiavi. Migrazione 2→3 esplicita; schema esportato insieme ai precedenti.
- L'indice viene popolato progressivamente da `getArtists`, `getGenres`, pagine `getAlbumList2` (500 album) e `getAlbum`. Quattro richieste dettaglio simultanee al massimo; sostituzione atomica dei brani di ciascun album. L'aggiornamento completo resta manuale.
- Liste locali tramite Room Paging e Paging 3.5.3 (runtime + Compose); anche il dettaglio genere usa Paging. Consultazione e ricerca dell'indice non dipendono da richieste al server.
- Copertine in directory privata del profilo, LRU su disco 250 MiB e memoria bitmap 16 MiB per store. Nessun servizio immagini esterno. Risposte binarie limitate a 8 MiB; errori JSON non sono salvati come immagini.
- Verifica reale iniziale su AOSP: 459 album, 6.403 brani, 196 artisti in circa 52 secondi. Le prove successive e i task aperti sono riportati in WORKLOG e PLAN.

- App mono-modulo nella radice del repository, pacchetti separati per responsabilità: `core/net` (client OpenSubsonic), `core/db` (Room + migrazioni), `core/files` (cache/download), `playback` (servizio Media3), `ui` (Compose, feature per schermata), `sync` (code offline e risoluzione conflitti). Modularizzazione Gradle rinviata a necessità concrete di build time.
- Riproduzione: `MediaSessionService` Media3 con ExoPlayer; coda persistita localmente per profilo.
- Isolamento profili: database Room con chiave/nome per profilo o tabelle filtrate per `profileId`; credenziali in storage cifrato. Dettagli allo scaffold (wave 02/03).

## Server Navidrome — verifiche reali (2026-10-12, account `walktape-test`)

Server: Navidrome 0.62.0 (`1b46b977`), API Subsonic v1.16.1, `openSubsonic: true`. URL `http://localhost:4533` raggiungibile; REST `/rest/*.view?...&f=json` operativo.

| Verifica | Esito |
| --- | --- |
| `ping` con token+salt (MD5) | ok. Nota: `p=hex:` rifiutato (richiesto token+salt). |
| `getOpenSubsonicExtensions` | transcodeOffset, formPost, songLyrics, indexBasedQueue, transcoding, playbackReport |
| `getArtists` | 196 artisti |
| `getAlbumList2 type=newest` | metadati completi (anno, numero brani, durata) |
| `getRandomSongs` | metadati brani completi (formato, bitrate, durata, percorso, coverArt) |
| `getPlaylists` | 1 playlist visibile |
| `getScanStatus` | scanning=false, 460 cartelle, lastScan 2026-10-04 |
| `stream` pass-through | 206 Partial Content, `Content-Range` onorato, originale `audio/mpeg` |
| `stream` transcodificato (`maxBitRate=192` su brano 320 kbps, o `format=opus`) | 200, `audio/ogg` (Opus): **transcoding server attivo**; Range ignorato su flusso transcodificato |
| `download` | 206, sempre file originale, `Content-Range` onorato |
| `getLyricsBySongId` | endpoint attivo ma nessun brano campione (12) con testi embedded: verifica con contenuti reali rinviata a wave 07 |

Vincoli registrati:

- Suggerimenti/artisti simili (`getSimilarSongs2`, `getTopSongs`, `getArtistInfo2`) richiedono integrazioni esterne sul server (Last.fm/AudioMuse): la funzione “suggerimenti opzionali” dipende dalla disponibilità server, come previsto da `SPEC.md`.
- Autenticazione solo `u`+`t`+`s` (MD5 di password+salt); API key OpenSubsonic non elencata tra le estensioni attive.
- Credenziali di test in `local/test-server.env` (escluso da Git); account `walktape-test` non amministratore con accesso alla libreria musicale.
- Raggiungibilità LAN/Tailscale dal telefono: **non verificata** (richiede il telefono; prevista in wave 03).

## Percorso audio (W01-T04)

- **Gapless**: supporto nativo ExoPlayer per formati con metadati gapless (MP3 LAME, FLAC). Riferimento: note di rilascio Media3 (correzioni “gapless transitions”). Verifica pratica sulle combinazioni in wave 09.
- **Crossfade**: non incluso in Media3; implementazione custom con doppio player e mix in transizione. Compatibile col perimetro; interagisce con gapless (i due brani in crossfade non sono anche gapless tra loro): comportamento da documentare in wave 09.
- **ReplayGain**: Media3 estrae i metadati ReplayGain LAME dagli MP3 (#2840); i Vorbis comments FLAC sono accessibili dai metadati. Applicazione del guadagno tramite `androidx.media3.common.audio` — esistono `BaseAudioProcessor`, `GainProcessor`, `SonicAudioProcessor` e `AudioProcessor.StreamMetadata` (reference ufficiale verificata). Percorso: lettura tag → calcolo gain album/brano → processor in catena.
- **Equalizzatore e rinforzo bassi**: `android.media.audiofx.Equalizer`/`BassBoost` via `audioSessionId` di ExoPlayer (API 19+, compatibile con minSdk 26). Alternativa DSP custom in AudioProcessor se audiofx mostrasse limiti sui dispositivi di riferimento (verifica in wave 09).
- **Effetto cassetta**: `AudioProcessor` custom (fruscio a basso livello + wow/flutter d'ampiezza/velocità, appoggiandosi a `SonicAudioProcessor` per la modulazione di velocità). Fattibile; consumo e qualità da misurare in wave 09.
- **Loudness metadata CTA-2075**: configurata automaticamente da Media3 quando presente (nota di rilascio) — complementare, non sostitutiva di ReplayGain.

## Android Auto (W01-T05)

- Modello richiesto: `MediaLibraryService` + `MediaSession` Media3; manifest con `<meta-data android:name="com.google.android.gms.car.application">` (+ tema opzionale `...car.application.theme`). Nessuna UI custom: libreria/playlist/download esposti come albero browsable; vincoli visivi della piattaforma, come da `SPEC.md`.
- APK fuori dal Play Store su Android Auto reale: ufficialmente supportato via opzioni sviluppatore dell'app Android Auto — “Allow unknown sources” (vale per app multimediali) dopo aver attivato la developer mode dell'app (tap su Version 10 volte). Procedura da seguire nelle prove in auto (wave 10).
- Test desktop: Desktop Head Unit (DHU) incluso negli strumenti SDK;_scenario di test media ufficiali disponibile. Utilizzo previsto in wave 10.
- Requisiti Play (driver distraction) non vincolanti per la distribuzione GitHub, ma le linee guida di design Auto vanno rispettate.

## Decisioni tecniche (nuove, registrate in `PROJECT_MEMORY.md`)

- D14 — Versioni bloccate: Kotlin 2.4.20, AGP 9.4.0, Gradle wrapper 9.6+, Compose BOM 2026.09.00, Media3 1.11.0; minSdk 26, target/compileSdk 36.
- D15 — Ambiente: Android Studio + SDK come da tabella; variabili utente `JAVA_HOME`/`ANDROID_HOME`.
- D16 — Account di test `walktape-test` sul server reale; credenziali in `local/test-server.env`, mai in chat/documenti/Git.
- D17 — Client OpenSubsonic: autenticazione token+salt MD5; transcoding server attivo (Opus di default per client sconosciuti) con `format`/`maxBitRate` selezionabili dal client; download sempre originale.

## Controlli non eseguiti

- Prove su emulatore e telefono (nessun AVD/dispositivo collegato in wave 01).
- LAN/Tailscale dal telefono verso il server.
- Testi reali via `getLyricsBySongId` (nessun brano con testi nel campione).
- Funzioni mutative (playlist, preferiti, scrobble) su account di test: previste nelle wave 07+.
- Comportamento gapless/crossfade/DSP combinati su hardware reale: wave 09.
