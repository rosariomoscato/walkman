# WalkTape — memoria di progetto

Ultimo aggiornamento: 2026-10-12 (fine wave 02).

## Punto di ripresa

- Fase: wave 02 completata e verificata su emulatore; shell nativa WalkTape avviabile (home/impostazioni/diagnostica, temi, IT/EN, layout adattivo).
- Prossima wave: **03 — Profili e connessione reale**; primo task **W03-T01** (client Navidrome/OpenSubsonic con autenticazione token+salt e gestione errori).
- Prima di continuare: leggere `docs/ARCHITECTURE.md` (con emendamenti wave 02) e `DESIGN.md` (token vincolanti).
- Modifiche wave 02 NON committate: commit/push solo su richiesta esplicita dell'utente.
- Per le build usare `.\gradlew.bat` con `JAVA_HOME` impostato al JBR nel comando; `local.properties` fornisce l'SDK path.

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
- D17 — Client OpenSubsonic: autenticazione token+salt MD5 (`p=` rifiutato dal server); transcoding attivo lato server (Opus di default per client sconosciuti); download sempre originale; Range onorato su pass-through/download, ignorato su transcode.
- D18 — App mono-modulo `:app` (package `com.walktape.app`), contenitore manuale `Graph` senza framework DI; Room con schema esportato in `app/schemas/` e `MIGRATIONS` da estendere a ogni cambio schema; preferenze in DataStore; per-app locale via AppCompat (it/en); backup cloud disattivato (`allowBackup=false` + `dataExtractionRules` che esclude tutto).
- D19 — Emulatore di riferimento per le verifiche: AVD **`walktape_aosp`** (system image AOSP `android-36/default/x86_64`, 3 GB RAM/4 core, avvio headless con swiftshader). L'immagine google_apis va sistematicamente in ANR su questo host (carico kDrive/Docker): usare sempre l'AOSP per l'automazione.
- D20 — Verifica UI su emulatore senza capacità visive del modello: `uiautomator dump` per contenuti/coordinate e colore medio degli screenshot (System.Drawing) per i temi; procedure collaudate in WORKLOG wave 02.

## Ambiente noto e dati di riferimento

- Workspace: `C:\Users\rosar\kDrive\Lavoro\Progetti\walkman`, repository Git con remote `origin` = https://github.com/rosariomoscato/walkman, branch `main` tracciato. Identità per-commit: `Rosario Moscato <53149348+rosariomoscato@users.noreply.github.com>` (config non persistita).
- Shell: Windows PowerShell 5.1. Usare `npx.cmd`; `Range` header via `[System.Net.HttpWebRequest]::AddRange`; redirect `>` di binari corrompe i file (usare `adb pull`).
- Node 22.23.1 disponibile; `rg` non nel PATH: usare gli strumenti dedicati di ricerca.
- Playwright MCP attivo su **Edge**; usarlo per verifiche web/browser, mai come prova Android.
- Server: Navidrome 0.62.0 (`1b46b977`) su `http://localhost:4533`, API Subsonic 1.16.1 + OpenSubsonic (songLyrics, transcoding, formPost, playbackReport, transcodeOffset, indexBasedQueue). Libreria: 196 artisti, 460 cartelle.
- Dal telefono usare indirizzo LAN/Tailscale del computer (non ancora verificato dal telefono).
- Hardware: Motorola G55 e successivi; Android 15/16; utente disponibile per test su telefono e in auto.
- Host: kDrive sync della cartella progetto consuma CPU durante le build (~0,7 core); Docker Desktop presente. Segnalato all'utente il suggerimento di escludere `build/`/`.gradle/` dal sync kDrive.
- Default approvati: cache audio 1 GB, copertine 250 MB, download solo Wi-Fi, originali nei download, avvio su “La mia musica”.

## Da verificare / informazioni mancanti

- Raggiungibilità LAN/Tailscale dal telefono (wave 03).
- Testi con contenuti reali via `getLyricsBySongId` (wave 07); funzioni mutative su account di test (wave 07+).
- Audio combinato su hardware reale (wave 09); Android Auto in auto (wave 10).
- TalkBack, caratteri ingranditi, contrasti misurati (wave 06/12).
- Repository GitHub per release, identità distributore/contatto privacy, chiave di firma (wave 12).
- Aggiornamenti patch disponibili (AGP 9.4.1, plugin compose 2.4.21): valutare al prossimo incremento.

## Stato dei file e del lavoro

- Progetto Android completo nella radice: `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `gradlew(.bat)`, `app/` (sorgenti, risorse, schema Room, test unit). APK debug verificato su emulatore (v0.1.0).
- `local/` e `.playwright-mcp/` esclusi da Git. `local.properties` escluso da Git. `NEXT_PROMPT.md` è un documento locale, escluso da Git su richiesta dell'utente (non pubblicato).
- Modifiche wave 02 **non committate**; nessun push senza richiesta esplicita.
- Consultare `git status` alla ripresa: non assumere che file non tracciati siano eliminabili.

## Protocollo di handoff

Alla fine di un incremento: aggiornare checkbox verificate, aggiungere log con ID task e prove, poi aggiornare questa memoria con stato, blocchi e **prossima azione precisa**. Registrare qui nuove decisioni con ID, motivazione e riferimento al log; non accumulare output grezzi o segreti.
