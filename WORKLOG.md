# WalkTape — registro del lavoro

Registro append-only. Aggiungere nuove voci in fondo; correggere eventuali errori con una nuova voce che richiami quella precedente.

Non inserire credenziali, token, chiavi di firma o output che li contengano. Distinguere attività svolta, risultato verificato e controllo ancora da eseguire.

## Modello per le prossime voci

```text
## AAAA-MM-GG — Titolo dell'incremento
Task: WXX-TYY (anche più ID)
Attività: cosa è stato effettivamente fatto e perché.
File/decisioni: percorsi rilevanti e ID delle decisioni nuove o modificate.
Verifiche: comando o procedura, esito e ambiente; indicare quelle non eseguite.
Stato: completato / parziale / bloccato, con motivazione.
Prossimo passo: azione precisa; aggiornare anche PROJECT_MEMORY.md.
```

## 2026-10-12 — Contesto iniziale e specifica approvata

Task: prerequisiti di W00-T01.

- Installate a livello progetto le quattro skill `leonvanzyl/skills`: start-an-app, review-an-app, deploy-an-app e create-brand-kit.
- Configurato Playwright MCP di progetto in `opencode.json`.
- Verifiche già riportate nella conversazione: elenco skill con scope project; `npx.cmd -y opencode-ai mcp list` ha indicato Playwright connesso.
- Intervista svolta con `start-an-app`, adattandone lo stack alla scelta Android nativa dell'utente.
- Approvata la specifica completa di WalkTape, inclusi Android Auto, offline, effetti audio opzionali e identità TPS-L2.
- Ricevuti dati del server e dispositivi: Navidrome 0.62.0, circa 10.000 brani MP3/FLAC, URL locale `http://localhost:4533`, Motorola G55 e successivi su Android 15/16.

Stato: discovery completata. Nessuna verifica del server o implementazione Android ancora eseguita.

## 2026-10-12 — Piano per wave e continuità tra sessioni

Task: W00-T01, W00-T02, W00-T03, W00-T04.

- Trascritto il perimetro approvato in `SPEC.md`.
- Creato `PLAN.md` con wave 00–12, task stabili, dipendenze, criteri di uscita e backlog.
- Creati `WORKLOG.md` e `PROJECT_MEMORY.md`, distinguendo cronologia da sintesi corrente.
- Creato `AGENTS.md` con protocollo di lettura, verifica, aggiornamento e handoff.
- Decisione D13: mantenere piano, log e memoria come documenti operativi per la continuità del progetto.

Verifiche preliminari: lettura della radice e ricerca di `AGENTS.md`; nessun documento preesistente sovrascritto. `git status --short` prima della modifica mostrava solo skill e configurazione non tracciate.

Stato: documentazione creata; controllo finale di coerenza da registrare nella voce successiva.

Prossimo passo: verifica documentale, poi W01-T01 per l'ambiente Android.

## 2026-10-12 — Verifica della documentazione e handoff

Task: W00-T01, W00-T02, W00-T03, W00-T04.

- Verificata copertura della specifica approvata e separazione del backlog.
- Controllati ID e checkbox tramite ricerca dedicata: 78 task nelle wave 00–12, quattro completati relativi esclusivamente alla documentazione; ogni wave ha un criterio di uscita.
- Esplicitata l'integrazione delle azioni di selezione multipla nelle wave dei relativi servizi, per non creare dipendenze circolari.
- Riletta la memoria: contiene decisioni, prerequisiti e punto di ripresa W01-T01, senza dichiarare verifiche Android/server non eseguite.
- `git diff --no-index --check -- NUL <file>` su tutti e cinque i documenti: nessun errore di whitespace; Git segnala soltanto la normalizzazione LF/CRLF prevista nell'ambiente Windows.
- `git status --short`: nuovi documenti e file di configurazione/skill non tracciati; nessun commit eseguito.
- Il tentativo di conteggio con `rg` non è stato eseguito perché il comando non è nel PATH; verifica effettuata con lo strumento dedicato di ricerca.

Stato: wave 00 completata e verificata. Nessun test applicativo richiesto per questo incremento documentale.

Prossimo passo: **W01-T01**, verificare toolchain Android/JDK/ADB e registrare l'ambiente disponibile, poi proseguire con la fattibilità tecnica della wave 01.

## 2026-10-12 — Prompt per la prossima sessione

Task: manutenzione dell'handoff di W00-T03 e W00-T04, su richiesta dell'utente.

- Creato `NEXT_PROMPT.md` con testo copiabile, ordine di lettura dei documenti, vincoli approvati e azione iniziale W01-T01.
- Esplicitato che il punto di ripresa aggiornato nella memoria prevale sul prompt se il progetto avanza.
- Aggiunto il riferimento al prompt in `PROJECT_MEMORY.md`.
- Verifica documentale: riferimenti e task di ripresa coerenti con piano e memoria; nessuna modifica al perimetro funzionale.

Stato: prompt di handoff disponibile. Prossima azione invariata: W01-T01 nella prossima sessione.

## 2026-10-12 — Wave 01: fattibilità e ambiente Android

Task: W01-T01, W01-T02, W01-T03, W01-T04, W01-T05, W01-T06, W01-T07.

Attività:

- Inventario ambiente iniziale: nessun JDK/SDK/ADB/Gradle/Android Studio presente; winget e Node 22.23.1 disponibili; residuo `adb_usb.ini` in `~/.android` lasciato intatto.
- Con autorizzazione dell'utente: installata Android Studio 2026.2.1.8 via winget (JBR 25.0.3); scaricati cmdline-tools ufficiali (build 15859902, URL ricavato da developer.android.com/studio) in `%LOCALAPPDATA%\Android\Sdk`; accettate le licenze; installati platform-tools 37.0.1, emulator 37.2.12, build-tools 37.0.0, piattaforme android-36 rev 2 e android-37.2 rev 1; impostate variabili utente `JAVA_HOME` e `ANDROID_HOME`.
- Versioni stabilite su fonti ufficiali (Playwright/Edge su developer.android.com e kotlinlang.org): Kotlin 2.4.20, AGP 9.4.0 (Gradle min 9.6, JDK min 17, build-tools min 36), Compose BOM 2026.09.00 (Compose 1.12.1, Material3 1.4.0), Media3 1.11.0. Decisione: minSdk 26, target/compileSdk 36.
- API Navidrome/OpenSubsonic verificate su documentazione ufficiale (navidrome.org, opensubsonic.netlify.app) e sul server reale.
- Percorso audio verificato su release note e reference Media3; Android Auto verificato su guide ufficiali (manifest, unknown sources, DHU).
- Creato `docs/ARCHITECTURE.md` con stack, architettura proposta, esiti server, percorso audio, Android Auto, decisioni D14–D17 e controlli non eseguiti.
- Creati `.gitignore` (esclude `local/`, `.playwright-mcp/`, chiavi, build) e `local/README.md`.

File/decisioni: `docs/ARCHITECTURE.md`; D14, D15, D16, D17 in `PROJECT_MEMORY.md`; `local/test-server.env` (fuori da Git).

Verifiche (tutte su ambiente reale, non emulatore):

- `adb version` 37.0.1, `emulator -version` 37.2.12, `sdkmanager --list_installed` conforme; `java -version` JBR 25.0.3.
- Server `http://localhost:4533`: login web raggiungibile (browser Edge via Playwright MCP); `/rest/ping` con token+salt MD5 → ok (server 0.62.0/1b46b977, openSubsonic true); `p=hex:` rifiutato (registro per il client).
- Account di test `walktape-test` creato dall'UI admin (Playwright, su autorizzazione dell'utente; password generata a runtime e salvata direttamente in `local/test-server.env` tramite listener TCP locale senza esposizione in chat/output; verifica file: sole chiavi presenti).
- API letture: getOpenSubsonicExtensions (songLyrics, transcoding, formPost, playbackReport, transcodeOffset, indexBasedQueue), getArtists (196), getAlbumList2, getRandomSongs, getPlaylists (1), getScanStatus (460 cartelle, scan 2026-10-04).
- Media: stream pass-through 206 con `Content-Range` e originale audio/mpeg; stream con maxBitRate/format → 200 audio/ogg (Opus): transcoding server attivo, Range ignorato su transcode; download 206 sempre originale.
- Non eseguiti: AVD/system image (nessuna verifica emulatore), LAN/Tailscale dal telefono, testi con contenuti reali (nessun brano campione li aveva), funzioni mutative su server, audio combinato su hardware.

Stato: wave 01 completata; criterio di uscita soddisfatto (percorso tecnico documentato, ambiente di build disponibile, vincoli espliciti). Nessuna incompatibilità col perimetro approvata emersa; unica deriva potenziale: suggerimenti/artisti simili dipendono da integrazioni esterne del server, già prevista da SPEC come “secondo disponibilità server”.

Prossimo passo: **W02-T01**, creare il progetto Android nativo nella radice preservando documentazione, skill e configurazioni.

## 2026-10-12 — Commit e push iniziali su richiesta dell'utente

Task: operazione git richiesta esplicitamente dall'utente (fuori dalle wave).

- Verificati `git status`, assenza di remote e contenuto dei file in stage; `local/` e `.playwright-mcp/` esclusi da `.gitignore`, nessun segreto incluso (controllato anche `opencode.json`).
- Identità commit senza modificare la config Git: `user.name`/`user.email` forniti solo per il commit (`-c`), con email noreply GitHub ricavata dall'ID pubblico dell'account (53149348+rosariomoscato@users.noreply.github.com).
- Commit `47ef5c0` (57 file), branch rinominato `master` → `main`, remote `origin` = https://github.com/rosariomoscato/walkman, push con tracking `main` → `origin/main` riuscito.
- Verifica: `git status` pulito dopo il primo commit; push confermato da Git (`* [new branch] main -> main`).

Stato: repository pubblicato con la sola documentazione (nessun codice applicativo). Nessun segreto nel repository.

Prossimo passo: invariato (**W02-T01**).
