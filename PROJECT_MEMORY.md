# WalkTape — memoria di progetto

Ultimo aggiornamento: 2026-10-12 (fine wave 01).

## Punto di ripresa

- Fase: wave 01 completata e verificata; ambiente di build Android operativo; server reale raggiunto e verificato.
- Prossima wave: **02 — Fondamenta native e design system**; primo task **W02-T01** (creare il progetto Android nella radice preservando documenti/skill/config).
- Prima dello scaffold: leggere `docs/ARCHITECTURE.md` (stack e decisioni vincolanti della wave 01).
- Da creare in wave 02: system image + AVD per la verifica emulatore (W02-T06); valutare requisiti di virtualizzazione Windows.
- Nessun codice applicativo esistente: la build del primo APK avverrà in wave 02.

## Documenti di riferimento

| File | Responsabilità |
| --- | --- |
| `SPEC.md` | Perimetro funzionale approvato; fonte di verità su cosa costruire. |
| `PLAN.md` | Wave, task stabili, dipendenze, criteri di uscita e backlog. |
| `docs/ARCHITECTURE.md` | Stack/versioni bloccati, architettura proposta, esiti server e percorso audio della wave 01. |
| `WORKLOG.md` | Registro cronologico append-only di attività, verifiche e blocchi. |
| `PROJECT_MEMORY.md` | Sintesi corrente di decisioni, stato e passaggio di consegne. |
| `AGENTS.md` | Regole operative per gli agenti che lavorano nel repository. |
| `NEXT_PROMPT.md` | Prompt copiabile per una nuova sessione; la memoria aggiornata prevale sul punto di ripresa riportato nel prompt. |
| `DESIGN.md` | Da creare in wave 02; sistema visivo vincolante. |

## Decisioni da conservare

- D01 — App Android **nativa**, non PWA né wrapper web. Kotlin/Compose/Media3 proposti; dettagli da verificare.
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
- D14 — Versioni bloccate (verificate su fonti ufficiali): Kotlin 2.4.20, AGP 9.4.0, Gradle wrapper 9.6+, Compose BOM 2026.09.00, Media3 1.11.0; minSdk 26, target/compileSdk 36. Motivazione e riferimenti in `docs/ARCHITECTURE.md` e WORKLOG wave 01.
- D15 — Ambiente build: Android Studio 2026.2.1.8 + JBR 25 (`JAVA_HOME` utente), SDK in `%LOCALAPPDATA%\Android\Sdk` (`ANDROID_HOME` utente) con platform-tools 37.0.1, emulator 37.2.12, build-tools 37.0.0, piattaforme 36 e 37.2; licenze accettate.
- D16 — Account di test `walktape-test` (non admin) sul server reale; credenziali solo in `local/test-server.env`, escluso da Git tramite `.gitignore`; generazione/salvataggio eseguiti senza esposizione in chat/documenti/log.
- D17 — Client OpenSubsonic: autenticazione token+salt MD5 (`p=` rifiutato dal server); transcoding attivo lato server (Opus di default per client sconosciuti, `format`/`maxBitRate` selezionabili); download sempre originale; Range onorato su pass-through/download, ignorato su transcode.

## Ambiente noto e dati di riferimento

- Workspace: `C:\Users\rosar\kDrive\Lavoro\Progetti\walkman`, repository Git con remote `origin` = https://github.com/rosariomoscato/walkman, branch `main` tracciato. Commit iniziale `47ef5c0` (documentazione wave 00-01) pubblicato su richiesta esplicita dell'utente. Identità commit non persistita nella config: fornita per-commit (`-c`) con email noreply GitHub (`53149348+rosariomoscato@users.noreply.github.com`); ai prossimi commit richiesti usare la stessa identità o concordare la configurazione persistente.
- Shell: Windows PowerShell 5.1. Usare `npx.cmd`: `npx.ps1` è bloccato dalla execution policy. In PS 5.1 l'header `Range` con `Invoke-WebRequest` non è impostabile: usare `[System.Net.HttpWebRequest]::AddRange`.
- Node 22.23.1 disponibile; `rg` non nel PATH: usare gli strumenti dedicati di ricerca.
- Playwright MCP attivo su **Edge** (user agent Edg/155 verificato); usarlo per verifiche web/browser, mai come prova Android.
- Server: Navidrome 0.62.0 (`1b46b977`) su `http://localhost:4533`, API Subsonic 1.16.1 + OpenSubsonic (estensioni attive: songLyrics, transcoding, formPost, playbackReport, transcodeOffset, indexBasedQueue). Libreria: 196 artisti, 460 cartelle; brani MP3/FLAC.
- Dal telefono usare indirizzo LAN/Tailscale del computer (non ancora verificato dal telefono).
- Hardware: Motorola G55 e successivi; Android 15/16; utente disponibile per test su telefono e in auto.
- Default approvati: cache audio 1 GB, copertine 250 MB, download solo Wi-Fi, originali nei download, avvio su “La mia musica”.

## Da verificare / informazioni mancanti

- AVD + system image e requisiti di virtualizzazione Windows (wave 02); nessun dispositivo fisico ancora collegato via ADB.
- Raggiungibilità LAN/Tailscale dal telefono (wave 03).
- Testi con contenuti reali via `getLyricsBySongId` (nessun brano campione li aveva; wave 07).
- Capacità server per suggerimenti (richiedono integrazioni esterne tipo Last.fm/AudioMuse).
- Funzioni mutative (playlist/preferiti/scrobble) su account di test (wave 07+).
- Audio combinato su hardware reale (wave 09).
- Repository GitHub di destinazione, identità distributore/contatto privacy, chiave di firma e processo di custodia.
- Grafica definitiva, font e token da decidere in `DESIGN.md` (wave 02) rispettando l'identità approvata.

## Stato dei file e del lavoro

- Radice: `.gitignore`, `local/` (escluso da Git), documentazione di piano, `docs/ARCHITECTURE.md`, skill e configurazioni OpenCode. Nessun progetto Android ancora: lo scaffold arriva con W02-T01.
- Cartella `.playwright-mcp/` (snapshot/log browser) generata dagli strumenti: esclusa da Git.
- Repository remoto pubblicato (solo documentazione, nessun codice applicativo); commit/push successivi solo su richiesta esplicita.
- Consultare `git status` alla ripresa: non assumere che file non tracciati siano eliminabili.

## Protocollo di handoff

Alla fine di un incremento: aggiornare checkbox verificate, aggiungere log con ID task e prove, poi aggiornare questa memoria con stato, blocchi e **prossima azione precisa**. Registrare qui nuove decisioni con ID, motivazione e riferimento al log; non accumulare output grezzi o segreti.
