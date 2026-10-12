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

## 2026-10-12 — Wave 02: fondamenta native e design system

Task: W02-T01, W02-T02, W02-T03, W02-T04, W02-T05, W02-T06.

Attività:

- Verificate versioni stabili su Google Maven (`dl.google.com` maven-metadata) e canali ufficiali: Room 2.8.5, DataStore 1.2.1, Navigation Compose 2.10.2, lifecycle 2.11.0, activity-compose 1.13.0, core-ktx 1.19.1, appcompat 1.8.0, media3 1.11.1 (patch successiva a 1.11.0), Gradle 9.8.1, KSP 2.3.12 (versioning indipendente da Kotlin).
- Scaffold mono-modulo `:app` nella radice (package `com.walktape.app`): settings/build Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml`, wrapper Gradle 9.8.1 generato da distribuzione ufficiale, `local.properties` con sdk.dir (escluso da Git).
- Scoperta e migrazione **AGP 9 built-in Kotlin** (guida ufficiale developer.android.com/build/migrate-to-built-in-kotlin): rimosso plugin `kotlin.android` (module/root/catalog); jvmTarget segue `compileOptions` (17).
- `compileSdk` alzato a 37 (AAR metadata: navigation-compose 2.10.2 richiede ≥37); installata piattaforma `android-37.0`; `targetSdk` resta 36 (D14 emendato in PROJECT_MEMORY).
- Architettura (W02-T02): contenitore `Graph` (niente framework DI), Room `WalkTapeDatabase` v1 con `ProfileEntity`/`ProfileDao` e schema esportato in `app/schemas/`, array `MIGRATIONS` pronto per le migrazioni future, DataStore preferenze (`ThemeMode`), `DiagnosticsCollector`+`Redactor` con test unitari.
- `DESIGN.md` (W02-T03): principi, token TPS-L2 (blu/acciaio/arancio/crema/notte), tipografia, materiali, spaziature, stati, accessibilità, localizzazione; implementati i token in `ui/theme`.
- Componenti condivisi (W02-T04): `EmptyState`, `MechanicalKey` (tasto meccanico con pressione animata), `SectionHeader`; navigazione Compose con `NavigationBar`/`NavigationRail` in base a `WindowSizeClass`; schermate Home/Impostazioni/Diagnostica.
- i18n e diagnostica (W02-T05): stringhe IT (default) + EN, `locales_config` + per-app locale via AppCompat (`setApplicationLocales`, autoStore), diagnostica con rapporto reale e oscuramento credenziali/token; `allowBackup=false` + `dataExtractionRules` che esclude tutto (nessun cloud backup, coerente con SPEC).
- Icona launcher provvisoria: adaptive (blu + bobine vettoriali) con `monochrome`; descrizione in `DESIGN.md`.

File principali: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/**`, `DESIGN.md`, `docs/ARCHITECTURE.md` (emendato).

Verifiche:

- `gradlew assembleDebug`: BUILD SUCCESSFUL (dopo correzioni: alias catalog non riservato, built-in Kotlin, calculateWindowSizeClass dentro setContent, material-icons-extended, Alignment orizzontale in Column).
- `gradlew test`: 5/5 test Redactor superati.
- `gradlew lintDebug`: nessun errore (warning residui accettati e documentati: targetSdk 36 intenzionale, patch AGP/Kotlin disponibili, localeConfig API 33+).
- Emulatore: system image AOSP `android-36/default/x86_64` (AVD `walktape_aosp`, 3 GB/4 core; l'immagine google_apis va sistematicamente in ANR su questo host — registrato). Con AOSP: build/install/avvio OK, `mFocusedApp=MainActivity`; UI verificata con `uiautomator dump`: home con stato vuoto onesto e navigation bar; impostazioni complete (tema/lingua/diagnostica); tema scuro verificato oggettivamente tramite colore medio screenshot (chiaro RGB medio 241,238,233 ≈ WtCream; scuro 31,40,60 ≈ WtBlueNight) e **persistenza dopo force-stop** (DataStore `theme_mode=DARK`, colore medio a regime 32,40,60); lingua per-app IT applicata a caldo (dump interamente in italiano) e persistita dopo reinstall; diagnostica con dati reali (versione 0.1.0, modello, API 36, storage, `profiles.configured: 0`); landscape con `user_rotation=1`: navigation rail a sinistra (bounds verificati) al posto della bottom bar.
- Non eseguiti: prove su telefono fisico (nessun dispositivo collegato), TalkBack manuale, caratteri ingranditi al 200% (rimandati a wave 06/12), screenshot non ispezionabili visivamente dal modello (verifiche fatte con dump UI e colori medi).

Vincoli registrati:

- L'host soffre di carico da kDrive (sync della cartella progetto durante le build: ~0,7 core costanti; CPU cumulata altissima) e Docker Desktop; gli ANR dell'emulatore google_apis dipendono da ciò. Suggerito all'utente di escludere `build/`, `.gradle/` dal sync kDrive.
- `local.properties` necessario (le variabili utente JAVA_HOME/ANDROID_HOME non si propagano alle shell dei tool): contiene solo sdk.dir.

Stato: wave 02 completata; criterio di uscita soddisfatto (shell nativa avviabile su emulatore, design documentato, nessun dato dimostrativo). Modifiche non committate (commit solo su richiesta).

Prossimo passo: **W03-T01** — client Navidrome/OpenSubsonic, autenticazione e gestione errori.

## 2026-10-12 — Commit wave 02 e esclusione di NEXT_PROMPT.md dal repository

Task: operazione git richiesta esplicitamente dall'utente (fuori dalle wave).

- Richiesta dell'utente: commit/push della wave 02 e **NEXT_PROMPT.md non deve stare su GitHub**; il file era già nei due commit precedenti.
- Aggiunto `NEXT_PROMPT.md` a `.gitignore`; rimosso dal tracking (`git rm --cached`); aggiornata la memoria con la nota.
- Commit della wave 02; riscrittura della cronologia con `git filter-branch --index-filter "git rm --cached --ignore-unmatch NEXT_PROMPT.md"` per eliminarlo da tutti i commit e push forzato sul repository personale (nessun altro clone).
- Verifica: `git log --stat` senza NEXT_PROMPT.md in nessun commit; file ancora presente localmente per le sessioni future.

Stato: wave 02 pubblicata senza il prompt di ripresa. Prossimo passo: invariato (**W03-T01**).

## 2026-10-12 — Handoff dopo push e anteprima della shell

- Push della cronologia ripulita completato con lease esplicita: `origin/main` e HEAD allineati a `62b56303af6c64f2a320f73639acfcbdcbe1c048`. Working tree pulita prima di questo handoff; `NEXT_PROMPT.md` presente sul computer, ignorato e assente dalla cronologia di main.
- Aperto l'emulatore AOSP in finestra visibile, installato APK debug e avviata MainActivity per anteprima dell'utente; riscontro positivo. Emulatore poi chiuso su richiesta tramite ADB; verificata assenza di dispositivi/processi emulatore.
- Aggiornati PROJECT_MEMORY.md e NEXT_PROMPT.md per ripresa esatta da W03-T01, senza ripetere scaffold o discovery. Questi aggiornamenti documentali non sono committati.
- Precisazione sulla voce precedente: carico kDrive e ANR di sistema sono stati osservati, ma il rapporto causale non è stato dimostrato. Non considerare la causa isolata.

Prossima azione: W03-T01. Nessuna implementazione della wave 03 eseguita.

## 2026-10-12 — W03-T01: client OpenSubsonic e login reale

Task: W03-T01.

- Implementato `core/net`: indirizzi HTTPS con opt-in HTTP locale/VPN, token MD5 UTF-8 con salt SecureRandom nuovo per richiesta, ping ed estensioni, envelope JSON rigoroso e dimensione massima 8 MiB; parametri ripetuti e prefissi URL preservati.
- Errori tipizzati: protocollo (anche HTTP 200 failed), autenticazione/permessi/versioni, HTTP, timeout, TLS, server irraggiungibile e risposte malformate. Testi e cause remoti non escono dal layer rete. Redirect e retry impliciti disabilitati; cancellazione coroutine cancella la chiamata.
- Dipendenze stabili verificate nei metadata ufficiali Maven Central: OkHttp/MockWebServer 5.5.0, Gson 2.14.0 (JSON tree, senza reflection su modelli Kotlin); permission INTERNET e factory in Graph. Fonti API: OpenSubsonic `docs/api-reference` (sorgente ufficiale GitHub).
- Verifica: `:app:testDebugUnitTest -PrealServerTest=true --offline` BUILD SUCCESSFUL, 16 test (5 diagnostica, 10 client, 1 integrazione), senza skip nel test reale. Server reale dal computer: ping Navidrome, estensioni e rifiuto password errata superati. Credenziali lette dal test, nessun output sensibile.
- `:app:assembleDebug` riuscito; `:app:lintDebug --offline` BUILD SUCCESSFUL. Prime esecuzioni interrotte per timeout dei tool; correzione del fixture MockWebServer 5 (start esplicito). Risultati finali verificati nei report XML.
- Non ancora verificati: client su emulatore tramite 10.0.2.2, telefono/LAN/Tailscale, profili, QR e biometria.

Stato: W03-T01 completato; wave 03 ancora aperta. Prossima azione: W03-T02/T03, storage protetto e configurazione profili.

## 2026-10-12 — Wave 03: profili, credenziali protette, QR e blocco (T01–T05)

Task: W03-T01, W03-T02, W03-T03, W03-T04 (parziale), W03-T05.

Attività:

- **Profili e storage protetto (T02)**: schema Room v2 con `credentialId`/`encryptedPassword`/`allowLocalHttp` e migrazione 1→2 (i profili shell wave-02 restano metadati ma vengono disattivati: richiedono nuovo login). `CredentialVault`: chiave AES-GCM per profilo non esportabile in Android Keystore, IV randomizzato, AAA bindato all'id; cancellazione chiave alla rimozione. `ProfileRepository`: aggiunta solo dopo verifica live, transazione atomica di attivazione (un solo attivo), directory dati `files/profiles/<id>`, rimozione con conteggio byte e retryabilità; nessuna mutazione server.
- **Configurazione e verifica (T03)**: schermata Profili con form (nome/URL/utente/password), opt-in esplicito HTTP locale/VPN (default rifiutato, `usesCleartextTraffic`), tipo connessione mostrato, verifica connessione salvata, errori localizzati per categoria (rete/timeout/TLS/protocollo/autenticazione/permessi/versione). `MainActivity`/navigazione aggiornate; home con stato reale del profilo attivo.
- **QR senza password (T04, parziale)**: `QrCodec` JSON v1 `{v,n,u,un}` (mai password; fallback URL nudo; nome vuoto ammesso per solo-URL), generazione bitmap zxing-core locale e dialog di condivisione; scansione via zxing-android-embedded 4.3.0 che precompila il form. Test JVM: 6/6.
- **Blocco biometrico opzionale (T04, parziale)**: preferenza `app_lock_enabled` (default off), interruttore in impostazioni solo se `canAuthenticate(BIOMETRIC_WEAK|DEVICE_CREDENTIAL)` riesce, gate `LockGate` con BiometricPrompt all'avvio (attende il primo emissione DataStore: nessun gate su default).
- **Cambio/rimozione (T05)**: attiva/disattiva transatomico, rimozione con conferma (nome profilo + byte locali) e pulizia chiave+dati+coda DB. Ripristino coda/posizione per profilo demandato a W05-T04 per dipendenza dal motore di riproduzione (già previsto dal piano).
- Dipendenze aggiunte dopo verifica metadata ufficiali: zxing core 3.5.4, zxing-android-embedded 4.3.0, androidx.biometric 1.1.0, androidx.test runner 1.7.0/ext-junit 1.3.0. Room-testing scartato su androidTest (conflitto kotlinx-serialization in AGP 9): migrazione validata aprendo Room v2 su DB v1 ricostruito dallo schema esportato.

Verifiche:

- JVM (`:app:testDebugUnitTest -PrealServerTest=true`): 22/22 (5 Redactor, 10 client, 1 server reale dal computer, 6 QrCodec). Build APK, androidTest APK e `lintDebug` riusciti.
- Emulatore AOSP `walktape_aosp`: 4/4 test di strumentazione — migrazione 1→2 con validazione Room; round-trip/tamper/cross-profile/eliminazione Keystore; due profili con autenticazione separata verso MockWebServer, cambio attivo concorrente (sempre 1 attivo), file isolati per profilo; **server reale da emulatore via 10.0.2.2:4533** con credenziali da `/data/local/tmp` (rimosso dopo l'uso): password errata rifiutata senza creare profilo, credenziali cifrate riconnettono, ciphertext non contiene la password.
- UI emulatore (uiautomator dump): home con stato vuoto onesto + pulsante Profili; form completo con "Verifica e salva" disabilitato a campi vuoti e checkbox HTTP; impostazioni con "Blocco app" correttamente non disponibile (nessuna biometria/PIN sull'emulatore AOSP) e senza switch; schermata profili con "Aggiungi profilo" e "Scansiona QR".
- Non eseguiti: scansione QR via camera (scena virtuale emulatore non affidabile), dialog di condivisione QR su profilo reale (richiede profilo configurato su UI), gate biometrico interattivo (nessuna biometria sull'emulatore), HTTPS contro server TLS reale, telefono fisico LAN/Tailscale/VPN, seconda verifica profilo su telefono.
- Ambiente: l'emulatore sotto carico host va in ANR di sistema; workaround collaudato: `pm compile -m speed -f` sugli APK prima di `am instrument` (test poi OK in 6-13 s). Build con processo detached + log su file e `-Dkotlin.compiler.execution.strategy=in-process` per evitare kill da timeout strumenti (host: kDrive + Docker + qemu). Emulatore spento al termine.

File: `app/src/main/java/com/walktape/app/core/profiles/**`, `core/db/**` (v2+migrazione), `core/net/**`, `core/settings/**`, `ui/screens/profiles/**`, `ui/screens/settings/**`, `MainActivity.kt`, `res/values*/profiles.xml`, stringhe aggiornate, `gradle/libs.versions.toml`, `app/build.gradle.kts`, test in `src/test` e `src/androidTest`.

Stato: T01, T02, T03, T05 completati e verificati; T04 implementato con verifiche parziali (codec e UI), resta verifica camera/biometria su telefono; T06 aperto (login reale e isolamento già verificati su computer+emulatore; VPN assente e doppio profilo richiedono telefono). Wave 03 non completata.

Prossimo passo: verifica T04/T06 su telefono (LAN/Tailscale, scansione QR, blocco biometria, due profili) con la collaborazione dell'utente; parallelamente avviabile wave 04 (indicizzazione libreria).
