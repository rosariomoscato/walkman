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
