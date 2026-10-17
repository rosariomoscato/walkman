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

## 2026-10-12 — Commit e push wave 03 su richiesta dell'utente

Task: operazione git richiesta esplicitamente (fuori dalle wave).

- Verificati `git status`, file in stage (35) e assenza di `local/`, `NEXT_PROMPT.md`, file `.env`/keystore nello stage.
- Commit `c97fe7d` "Wave 03: profili e connessione reale (T01-T03, T05)" con identità per-commit Rosario Moscato <53149348+rosariomoscato@users.noreply.github.com>; push riuscito `62b5630..c97fe7d main -> main`.
- Working tree pulita dopo il push; questa voce resta locale (non committata).

Stato: wave 03 pubblicata. Prossimo passo invariato: verifiche telefoniche T04/T06 e/o avvio wave 04.

## 2026-10-13 — Wave 04: indice reale, navigazione e consultazione locale

Task: W04-T01, W04-T04, W04-T05 completati; W04-T02, W04-T03, W04-T06 parziali.

- Schema Room v3 con artisti, album, brani, generi e stato dell'indice, chiavi composte per profilo e indici SQL; migrazione 2→3 e schema esportato. Rimozione profilo estesa alla pulizia dell'indice relativo.
- Client libreria: `getArtists`, `getGenres`, `getAlbumList2`, `getAlbum`, `getCoverArt`. Parser senza reflection; mancati envelope e dettagli inconsistenti non vengono interpretati come libreria vuota. Copertine binarie limitate a 8 MiB, errori JSON tipizzati, niente log di autenticazione.
- Indicizzatore: pagine album di 500, quattro dettagli simultanei al massimo, cancellazione coroutine/rete effettiva, refresh dei brani di ogni album in transazione (rimuove quelli obsoleti). Eliminazione degli album assenti soltanto dopo lettura riuscita; indice parziale consultabile durante il caricamento.
- Room Paging + Paging runtime/Compose 3.5.3, Room Paging 2.8.5: liste e dettaglio genere a caricamento progressivo; ricerca testuale locale e ordinamenti persistenti. UI album/artisti/brani/generi, dettaglio album/artista/genere, copertine e selezione multipla con conteggio/annullamento; azioni audio/playlist/download da collegare nelle wave previste.
- Cache copertine per profilo: 250 MiB disco LRU, 16 MiB bitmap per store, scrittura temporanea e rename; validazione immagini prima del salvataggio. Test di limite, isolamento e riapertura offline su disco.
- Artista traccia distinto dall'artista album, `displayAlbumArtist` quando disponibile con fallback ai metadati album; flag compilation; ordine disco/numero traccia verificato in Room.

Verifiche finali:

- Build app e androidTest, `lintDebug`: BUILD SUCCESSFUL (log locale wt-wave04-navigation.log); `testDebugUnitTest -PrealServerTest=true`: **36/36**, nessuno skip. Comprende 7 test sync (aggiornamento, album scomparsi, brani obsoleti, cancellazione e risposta malformata), 6 API libreria, regressione chiavi tra tab/profili, login reale dal computer e suite wave 02/03.
- AOSP `walktape_aosp`: **7/7** test core (migrazioni, profili/Keystore, indice reale e isolamento, cache copertine), poi **2/2** regressioni UI e ordine multidisco. UI: passaggio fra tutte le tab, dettaglio album, ricerca, cancellazione ricerca, selezione/annullamento e ordinamento. Cleanup del profilo di ispezione: **1/1**.
- Server reale via 10.0.2.2:4533: **196 artisti, 459 album, 6.403 brani**, prima indicizzazione misurata **52,062 s**. Sono dati letti dal server; la precedente cifra di circa 10.000 era una stima iniziale. Copertina reale decodificata. Nessuna mutazione sul server.
- Offline emulatore: disattivati temporaneamente Wi-Fi/dati e modalità aereo; `dumpsys connectivity` confermava **Active default network: none**. Regressione UI **1/1** superata in tale stato; rete ripristinata nel finally. Non è una prova di ascolto/download offline, servizi ancora da implementare.
- File segreto di test trasferito solo nel percorso temporaneo dell'emulatore e rimosso dopo le prove. Profilo UI temporaneo e marker rimossi; nessun segreto in app/test APK o documenti.

Correzioni e limiti:

- Rettifica delle precedenti ipotesi nella chat: il ritorno al launcher durante il cambio tab aveva anche una **ClassCastException applicativa** (album ancora presenti nel vecchio snapshot Paging, chiavi già convertite ad artista), identificata da logcat. Corretto con chiavi tipizzate per tipo/profilo; non attribuire quel crash ai soli ANR di sistema. Restano distinti gli ANR di System UI/launcher osservati sull'emulatore.
- Corretto il dettaglio creato prematuramente con profileId=0 e il pager che manteneva l'id del profilo precedente. Corretto anche il ripristino della posizione quando cambia il risultato di ricerca/ordinamento; pulsante accessibile Cancella ricerca.
- Fixture MockWebServer resa indipendente dall'ordine delle richieste concorrenti: ciascun dettaglio è risposto per id. Test di navigazione fallito inizialmente, poi corretto e ripetuto fino all'esito positivo completo (compresa la prova offline).
- W04-T02 resta aperto: sezioni home approvate e playlist in lettura. W04-T03 resta aperto: filtri anno/genere/offline e ricerca unificata completa. W04-T06 resta aperto per misure mirate e verifica delle parti ancora mancanti. Nessuna prova telefono fisico eseguita.
- Aggiunta `docs/PHONE_TESTS.md` con checklist W03-T04/T06 e controlli libreria; aggiornati architettura e design con l'incremento effettivo. Nessun commit/push della wave 04.

Prossima azione precisa: W04-T02 — completare le sezioni home con metadati reali e playlist in lettura, poi filtri W04-T03 e misure W04-T06. La wave 04 resta aperta.

Chiusura incremento: `git diff --check` senza errori (solo avvisi LF/CRLF); `NEXT_PROMPT.md` e `local/test-server.env` confermati ignorati. Emulatore chiuso e ADB senza dispositivi. Modifiche wave 04 e documenti preservati localmente; nessun commit/push.

## 2026-10-13 — Commit e push dell'incremento wave 04

Task: operazione Git richiesta e confermata esplicitamente dall'utente.

- Ispezionati status, diff, ultimi commit, remote e tracking; controllati i 38 file in stage e il diff dei nuovi test. Nessun file locale riservato, credenziale reale, APK o artefatto di build nello stage; NEXT_PROMPT.md resta ignorato.
- Commit `fa17b0e` — "Wave 04: indice reale, navigazione e ricerca locale" (38 file, 2.988 inserimenti/77 rimozioni). Il messaggio dichiara l'incremento parziale e i task ancora aperti.
- Push ordinario riuscito: `c97fe7d..fa17b0e main -> main`, senza riscritture della cronologia.
- Verifica: HEAD e origin/main entrambi `fa17b0e330f42a3eb0353129df1fd974e2a422b4`; working tree pulita immediatamente dopo il push. Queste note successive e l'aggiornamento della memoria restano locali, non committati.

Prossima azione invariata: W04-T02. W04-T03/T06 e le prove telefono W03-T04/T06 restano aperti.

## 2026-10-13 — Anteprima visibile dell'app su richiesta dell'utente

- Avviato `walktape_aosp` in finestra visibile; atteso completamento del boot Android.
- Installati APK debug app/test già costruiti, precompilati con filtro speed. Helper opt-in `UiSeedTest#seedRealLibraryForManualInspection`: 1/1 superato con server reale via 10.0.2.2, credenziali lette solo dalla configurazione temporanea esclusa da Git.
- Preparato il profilo di test `UI check` con libreria reale (459 album, 6.403 brani, 196 artisti) per consentire all'utente l'esplorazione. Profilo cifrato e indice conservati nell'app dell'emulatore; file segreto temporaneo e marker rimossi nel finally.
- Avvio MainActivity riuscito: `am start -W` Status ok; `mCurrentFocus` e `mFocusedApp` riferiti a WalkTape. Emulatore lasciato aperto per l'utente.
- Nessuna nuova funzione dichiarata completata, nessun commit/push; punto di ripresa sempre W04-T02. Non è una verifica sul telefono fisico e la riproduzione audio resta da implementare.

## 2026-10-13 — Fine anteprima e chiusura emulatore

- L'utente ha visto l'app e dato riscontro positivo («per adesso mi sembra ok»), poi richiesto la chiusura dell'emulatore.
- Eseguito `adb -s emulator-5554 emu kill`; dopo l'arresto verificato `adb devices` senza dispositivi.
- Profilo di test e indice locale conservati. Aggiornati memoria e prompt locale; punto di ripresa W04-T02 invariato. Nessun commit/push.

## 2026-10-13 — Aggiornamento remote dopo rinomina GitHub

- L'utente ha rinominato il repository GitHub in `rosariomoscato/walktape` e chiesto l'aggiornamento locale.
- Eseguito `git remote set-url origin https://github.com/rosariomoscato/walktape.git`; nome e percorso della cartella locale `walkman` invariati.
- Verificati URL fetch/push con `git remote -v` e accesso con `git ls-remote --heads origin main`: branch remoto `main` al commit `fa17b0e330f42a3eb0353129df1fd974e2a422b4`.
- Preservate le modifiche documentali preesistenti. Aggiornati memoria e prompt locale; nessun commit/push. I riferimenti al vecchio URL nel log storico restano come registrazione delle operazioni precedenti.

## 2026-10-13 — Wave 04 completata: home, playlist in lettura, filtri e ricerca unificata

Task: W04-T02, W04-T03, W04-T06 (completano la wave 04).

Attività:

- Room v4 (migrazione 3→4 esplicita, indice conservato): tabelle `playlist`/`playlist_entry` per profilo con chiave posizione (brani ripetuti preservati) e foreign key CASCADE, catalogo `local_audio` per la disponibilità offline, metadati `played` (album) e `starred` (artisti) per le sezioni home. Pulizia estesa alla rimozione profilo.
- API playlist: `getPlaylists`/`getPlaylist` senza parametro amministrativo username; il dettaglio rifiuta id o conteggio incoerenti e gli errori di protocollo non svuotano la cache. `PlaylistIndex` sostituisce ogni playlist in transazione e rimuove solo dopo una lettura riuscita della lista.
- Home "Panoramica": sezioni Continua ascolto (stato vuoto onesto fino alla wave 05), Aggiunti/Ascoltati di recente, Preferiti, Download (stato vuoto fino alla wave 08) e Playlist, tutte da Room osservata per profilo con schermatura anti-profilo-stallo durante i cambi profilo.
- Liste riscritte su `BrowseQueries`/`RawQuery`: parametri bindati (LIKE con escape, tentativi SQL injection neutralizzati), ordinamenti in whitelist, filtri genere/anno/offline per brani, album (offline = intero album scaricato), artisti, generi e playlist; tab Playlist (nome/aggiornamento) e tab "Tutti" con ricerca unificata UNION su tutti i tipi; chip "Disponibili offline" e stato "Nessun risultato". `LocalAudioCatalog` registra solo file non vuoti dentro la directory privata del profilo (path traversal rifiutato); popolamento reale previsto in wave 08.
- UI: navigazione playlist con dettaglio paginato nell'ordine server (badge "Playlist in lettura"), testTag `library-tabs` per l'automazione, righe filtrate per profilo attivo.

Verifiche:

- JVM `testDebugUnitTest -PrealServerTest=true`: **39/39** (nuovi: 3 PlaylistApi con ripetizioni/ownership/404-formali). Il RealServerTest era fallito alle 09:09 per indisponibilità temporanea del server (ping HTTP fuori test confermato lento), poi superato a regime: nessun cambiamento di codice tra i due esiti.
- Android `walktape_aosp`: suite core **12/12** (migrazioni, profili, indice reale e isolamento, cache copertine, ordine multidisco), **BrowseInstrumentedTest 4/4** (migrazione da schema v3 esportato con indice e credenziali conservati; 10.000 brani di fixture con paging/filtri/escape/offline/isolamento; playlist con ordine, ripetuti e cache non cancellata da errori; lista playlist reale → 0 playlist per l'account di test), **LibraryNavigationTest 1/1** in 206 s su libreria reale: tutte le tab con scorrimento della striscia, dettaglio album, ricerca "adele", cancellazione, selezione multipla, ordinamento persistito (attesa scrittura DataStore), Panoramica, playlist fixture (brani ripetuti visibili) con cleanup, filtro offline (vuoto onesto), ricerca unificata, filtri genere+anno validati contro SQL diretto.
- Prestazioni registrate (emulatore AOSP, host sotto carico): fixture 10.000 brani, pagina filtrata di 80 → **487 ms a freddo, 205 ms in media a caldo**; indicizzazione reale 6.403 brani ~52-76 s a seconda del carico.
- Ambiente: durante le prove l'emulatore ha attraversato ANR ripetuti del launcher (Quickstep) fino a bloccare il dialog; risolto riavviando l'emulatore da zero. Il test ora gestisce i dialog "Wait" e alterna le direzioni di scorrimento della striscia tab (il secondo `clickTab("Album")` falliva perché la tab era fuori schermo a sinistra dopo Generi: correzione nel test, non nell'app). Registrazione onesta: ANR di sistema osservati anche su emulatore appena riavviato sotto carico host.
- Cleanup: marker ed env temporanei rimossi da `/data/local/tmp`; emulatore spento; nessun segreto in output, APK o documenti.

File principali: `core/db/Browse*`, `core/library/{BrowseQueries,PlaylistIndex,LocalAudioCatalog}.kt`, `LibrarySync` (+pipeline playlist), `core/net` (Dtos/OpenSubsonicClient playlist), `ui/screens/library/*` (Panoramica, filtri, playlist, ricerca unificata), `ui/nav`, stringhe it/en, schema 4.json, test JVM/Android aggiunti.

Stato: **wave 04 completata** (T01–T06 verificati; criterio di uscita soddisfatto). Modifiche non committate. Prossima azione precisa: **W05-T01** — servizio di riproduzione Media3 (`MediaSessionService` + ExoPlayer) con streaming MP3/FLAC e DataSource autenticato dal client OpenSubsonic.

Nota: dettaglio playlist reale sul server da ripetere in wave 07 quando esisterà una playlist di prova (l'account di test non ne ha); il percorso è coperto da fixture e API reali di lista.

## 2026-10-13 — Commit e push del completamento wave 04

Task: operazione Git richiesta esplicitamente dall'utente.

- Ispezionati status, diff e stage (32 file); nessun file riservato, credenziale, APK o artefatto di build incluso; `NEXT_PROMPT.md` resta ignorato.
- Commit `26584bc` — "Wave 04: Panoramica, playlist in lettura, filtri e ricerca unificata" (32 file, 1.872 inserimenti/57 rimozioni), messaggio con verifiche allegate.
- Push ordinario riuscito su `https://github.com/rosariomoscato/walktape.git`: `fa17b0e..26584bc main -> main`.
- Verifica: HEAD e origin/main entrambi `26584bcd2b6c1c1bcb7660c556b6adf37341a4bb`; working tree pulita subito dopo il push. Note successive restano locali.

Prossima azione invariata: **W05-T01** (servizio di riproduzione Media3).

## 2026-10-13 — W05-T01: servizio Media3 e streaming originale reale

Task: **W05-T01 completato**. Configurazione base dei servizi T05 e breve prova preliminare T07; quei task restano aperti.

- Letti documenti di progetto e controllati status/diff: HEAD `26584bc`, origin/main allineato; modifiche preesistenti solo alle note post-push in memoria/log, preservate.
- Attivati Media3 ExoPlayer/session e aggiunto datasource-okhttp **1.11.1**. `playback/PlaybackService` ospita ExoPlayer e MediaSession con notifica predefinita, PendingIntent per l'activity, attributi music/media, focus automatico, becoming-noisy e wake lock locale. Manifest con permessi e foreground type mediaPlayback.
- `SubsonicStreamDataSourceFactory`: URI logiche con profilo+brano, niente credenziali nei media; risoluzione ad ogni apertura via client OpenSubsonic, token+salt fresco, `format=raw`, Range e DataSpec preservati. JSON rifiutato come contenuto audio. HTTP streaming senza timeout totale, timeout connessione/lettura e redirect disabilitati. Media di altro profilo rifiutati prima della rete; cambio profilo arresta/svuota prima di caricare nuove credenziali. Callback async attende il collegamento iniziale Room/Keystore. Sessione disponibile a UID app/controller trusted.
- `PlaybackConnection`/Graph/MainActivity: MediaController sul main executor, bind onStart/release onStop. Dettaglio album: pulsante Riproduci album e tap su traccia, metadati titolo/artista/album. Touch target minimo 48dp e stringhe IT/EN. Continua ascolto indica l'assenza di coda salvata (persistenza T04).
- File principali: `playback/{SubsonicStream,SubsonicStreamDataSource,PlaybackService,PlaybackConnection}.kt`, `core/net/{OpenSubsonicClient,Connection}.kt`, Graph/MainActivity, manifest/build/catalogo versioni, DetailScreens/stringhe, test JVM e Android in `playback/`. Nessuna migrazione DB.

Verifiche:

- **Server reale host** (credenziali lette localmente senza stampa): campioni MP3 e FLAC via `stream.view?format=raw` → 200 `audio/mpeg`/`audio/flac`; Range 0–1023 → 206, Content-Range corretto e 1024 byte. Anche senza `format` i campioni con client `probe` sono originali; corretta l'assunzione di transcode predefinito per tutti i client. Prima richiesta di sonda con URL malformato → 404, corretta e ripetuta con successo.
- **Build finale**: `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug -PrealServerTest=true`, JBR Android Studio, compiler in-process, log in temp fuori dal repo → BUILD SUCCESSFUL (6m32s). Lint **0 errori/18 warning**: versioni/target fissati, attributi per API nuove, plurali/risorsa non usata/KTX; ExportedService previsto per la sessione, accesso filtrato dal servizio. Nessun warning ignorato tramite nuova baseline.
- **JVM: 45/45**, nessun fallimento/skipped (39 regressione incluso login reale + 6 URI/isolamento/token/parametri streaming).
- **Android AOSP: 6/6**, esecuzione finale 59,49 s: 5 DataSource con MockWebServer (Range chiuso 1000–1007 e lettura byte, salt diverso a ogni apertura, JSON rifiutato, credenziali assenti, profilo errato senza rete); 1 end-to-end sul **server reale via 10.0.2.2**, campioni MP3+FLAC con decoder emulatore, posizione >1 s, pausa stabile, cambio/rimozione profilo ferma e svuota la sessione. MediaController esercitato sul main thread; nessuna attesa fissa per caricamento credenziali nella versione finale.
- **UI reale emulatore**: indice conservato 6.403 brani/196 artisti; aperto Greatest Hits di Björk, pulsante Riproduci album → sessione con coda di 15, PLAYING e metadati corretti, posizione ~33 s. Home Android + KEYCODE_SLEEP → posizione ~87 s ancora PLAYING; dumpsys power `Asleep`, wake lock ExoPlayer, servizio foreground tipo mediaPlayback/notifica transport confermati. Prova breve, non ascolto qualitativo né hardware.
- Risolti durante compilazione: firme Media3 verificate con `javap` e sorgenti ufficiali, `HttpUrl` non accetta scheme custom (codec logico JVM-puro), URI Android distinto da java.net.URI, nomi androidTest senza spazi per DEX minSdk26; primo test APK obsoleto ricostruito con assembleDebugAndroidTest. Primo build interattivo in timeout: passaggio al percorso detached già previsto da D25.
- ANR **System UI** durante il controllo UI, chiuso con Wait; causa non isolata, non attribuita al filesystem né all'app. Prove audio automatiche riuscite.
- File env temporaneo in `/data/local/tmp` rimosso e assenza verificata; profilo di test e2e rimosso e profilo precedente ripristinato. Nessuna operazione mutativa su playlist/preferiti. Emulatore chiuso a fine incremento.

Fonti ufficiali: sorgente AndroidX MediaSessionService (branch release) e OkHttpDataSource **tag 1.11.1**, link in ARCHITECTURE. Developer.android.com non raggiungibile tramite webfetch; browser MCP non avviabile (cerca Chrome non installato), nessuna configurazione/installazione aggiunta. Android verificato tramite Gradle/ADB, non browser.

Documenti: PLAN, ARCHITECTURE, DESIGN, PROJECT_MEMORY e prompt locale aggiornati. **Discrepanza con prompt**: `git ls-files` conferma `docs/PHONE_TESTS.md` già tracciato; il file originario è preservato. Nuova checklist hardware esclusivamente in `local/PHONE_TESTS_W05.md`, ignore verificato insieme a NEXT_PROMPT.

Non eseguiti: Motorola/ascolto qualitativo, schermo spento prolungato su hardware, chiamate/focus concorrente/cuffie/Bluetooth/lockscreen completo (T05/T07), TLS reale, regressione UI completa wave04 in questo incremento. Coda modificabile/persistita, ripetizione/seek/volume esposti, timer, copertina notifica/player e qualità configurabile seguono i task pertinenti. Nessun commit/push. Prossima azione precisa: **W05-T02**.

## 2026-10-13 — Riscontro dell'utente sul Motorola G55 5G

Task: **W05-T01**, verifiche parziali **W05-T05/T07**, raggiungibilità **W03-T06**. Fonte: prove hardware eseguite e riferite dall'utente in chat; non esecuzione diretta dell'agente.

- Dispositivo: **Motorola G55 5G / Android 16**. Connessione: **Tailscale**.
- MP3: **OK**. FLAC: **OK**.
- Pausa/ripresa dalla notifica: **OK**.
- Schermo spento per 10 minuti: **OK**.
- Osservazione: nessun comando play/pausa/avanti/indietro dentro l'app; per pausa/ripresa l'utente ha minimizzato e usato il widget di sistema. È la parte ancora aperta di **W05-T02**, da rendere accessibile nell'app nel prossimo incremento.
- Questi ascolti confermano la raggiungibilità del server via Tailscale dal telefono. Non attestano LAN, VPN assente, QR/biometria, cambio profilo hardware, chiamate/cuffie/Bluetooth/lockscreen completo; W03-T04/T06 e W05-T05/T07 conservano i task aperti pertinenti.
- Aggiornati PLAN, PROJECT_MEMORY, NEXT_PROMPT locale e checklist hardware privata `local/PHONE_TESTS_W05.md`; codice/APK invariati. Verifica documentale tramite diff; nessun nuovo build/test automatico necessario. Modifiche preesistenti preservate; nessun commit/push.

Prossima azione precisa: **W05-T02**, controlli di riproduzione dentro l'app sopra MediaController, prima della scocca definitiva wave 06.

## 2026-10-13 — W05-T02: controlli di riproduzione nell'app

Task: **W05-T02 completato**. W05-T01/T06/T05 restano come già registrati (fine coda default e stop&clear sono coperti; timer e interruzioni complete no).

Implementazione:

- `PlaybackSnapshot`/`PlaybackFailure`: stato immutabile per la UI (metadati, posizione/durata effettiva, disponibilità comandi, ripetizione, volume Android) senza URL, credenziali o messaggi d'eccezione.
- `PlaybackConnection`: snapshot via `Player.Listener.onEvents` + polling 500 ms solo con media e connessione attiva (Media3 non emette eventi per il avanzamento regolare). Comandi sul main looper con `isCommandAvailable`: play/pausa (con prepare da idle e ripartenza da inizio coda dopo `STATE_ENDED`), `seekToPrevious`/`seekToNext`, seek ms limitato a durata e media seekable con chiave traccia+indice (un gesto non si applica alla traccia/profilo successivo), ripetizione OFF→ONE→ALL→OFF. Coda richiesta validata contro il profilo attivo; job pendente annullato sul disconnect; riconnessione onStart senza avvio automatico (verificato). Scollegamento riportato come CONNECTION senza crash.
- Volume: `AudioManager.STREAM_MUSIC` reale (slider, `setStreamVolume`; mai `Player.setVolume`, verificato gain 1.0); `volumeControlStream` sull'activity e sulla finestra del pannello; permesso normale `MODIFY_AUDIO_SETTINGS`. ExoPlayer con `setDeviceVolumeControlEnabled(true)`.
- `PlaybackControls` (shell, sopra la navigation bar/rail): titolo/artista, precedente/play-pausa/successivo, apertura pannello; barra a due righe con font scale >130% o larghezza <320dp; pannello `ModalBottomSheet` scrollabile con stato, seek con tempi, ripetizione e volume; slider con nodo unico accessibile (`clearAndSetSemantics` con `ProgressBarRangeInfo` + `setProgress`), after aver rilevato cursore ed etichetta esposti come nodi separati (diagnostica via `sendStatus`); indicatore di caricamento e messaggi di errore generici. Stringhe IT/EN.
- `PlaybackService`: reset di `repeatMode` al cambio profilo. Test diagnostico volume separato (`VolumeKeyDiagnosticTest`, opt-in `-e volumeKeyDiagnostic true`).

Verifiche (tutte su emulatore AOSP `walktape_aosp`; server reale via 10.0.2.2 con credenziali da file locale, poi rimosso):

- Build `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug -PrealServerTest=true`: **BUILD SUCCESSFUL**; lint 0 errori/18 warning (informazioni già note). JVM **45/45** (regressione + login reale).
- Android **6/6** (esecuzione finale ~114 s): 5 DataSource + `PlaybackServiceTest` esteso, che ora oltre a MP3/FLAC reali e cambio profilo esercita dall'UI: pausa/play con sincronizzazione bridge↔sessione, seek accessibile (10.000 ms precisi in pausa) e **seek touch iniettato** (~60% della traccia senza avviare la riproduzione), precedente/successivo con metadati aggiornati, gesto di seek non più valido ignorato sulla traccia successiva, ripetizione brano/coda con **wrap effettivo a fine traccia**, ripetizione off con **fine coda** e ripartenza dal primo brano con play, slider volume → volume Android a 0 con gain del player invariato e **intervallo device-volume reale** pubblicato dalla sessione, variazione volume da sistema riflessa nell'app, riconnessione bridge senza avvio automatico e con ripetizione conservata.
- UI reale via dump: home → album → "Riproduci album" → sessione PLAYING con coda 15; barra di trasporto sopra la navigazione con "Precedente o inizio brano/Pausa/Successivo/Apri controlli"; pannello con "In riproduzione", slider 0:48/4:46, "Ripetizione: disattivata"→"brano" via tap, "Volume multimediale: 5 / 15", pausa → "In pausa" e icona "Riproduci". Un dump fallito con "null root node" si è ripreso al tentativo successivo; primo tap su "Riproduci album" dopo la riapertura dell'app non ha avviato la riproduzione (sessione NONE), riuscito al secondo tentativo con logcat pulito: nessun errore ExoPlayer/Media3 né crash registrati; non isolato oltre.
- **Limitazioni registrate**: (1) su questo AVD l'iniezione di `KEYCODE_VOLUME_UP` (`input keyevent` e `sendKeyDownUpSync`) non modifica il volume musica, mentre `AudioManager` funziona; pertanto i tasti fisici restano da verificare sul Motorola (aggiunto alla checklist locale). (2) Il `MediaController` locale non espone i comandi di scrittura volume dispositivo; `deviceInfo.maxVolume` riflette però l'intervallo reale dopo `setDeviceVolumeControlEnabled(true)`. Nessuna delle due riguarda il comportamento utente dell'app su telefono, ma restano non attestati lì.
- Correzioni durante il lavoro: attesa posizione in pausa basata su campioni stabili (l'attesa fissa 300 ms falliva sotto carico), nomi azione accessibilità (`AccessibilityAction.ACTION_SET_PROGRESS.id`), semantica slider unificata, `setDeviceVolumeControlEnabled`, WAKEUP+KEEP_SCREEN_ON nei test per il display e la strategia volume.

Non eseguiti: prove su telefono dei nuovi comandi (l'utente testerà l'APK aggiornato), tasti volume fisici, interruzioni/cuffie/Bluetooth (T05/T07), TalkBack sui nuovi controlli (solo nodi verificati), font scale reale oltre la regola adattiva del layout. Nessun commit/push.

File principali: `playback/{PlaybackSnapshot,PlaybackConnection,PlaybackService}.kt`, `ui/components/PlaybackControls.kt`, `res/values{,-en}/playback.xml`, manifest, test `playback/{PlaybackServiceTest,VolumeKeyDiagnosticTest}.kt`. Documenti aggiornati: PLAN, ARCHITECTURE, DESIGN, memoria, prompt locale, checklist telefono locale.

Prossima azione precisa: **W05-T03** — coda modificabile, azioni dalla selezione multipla, shuffle senza ripetizioni e ripristino dell'ordine.

## 2026-10-13 — Riscontro dell'utente sui comandi in-app (T02)

Task: **W05-T02**, prove hardware. Fonte: riscontro dell'utente in chat.

- L'utente ha installato l'APK T02 sul Motorola G55 5G / Android 16 e conferma che **funziona** (comandi di riproduzione dentro l'app). Nessun dettaglio fornito sui singoli punti della checklist (seek, ripetizione, volume/tasti fisici, fine coda): la checklist locale resta con le voci non ancora attestate esplicitamente.
- Nessuna azione sul codice. Aggiornati WORKLOG, memoria, piano e checklist locale; commit dell'incremento W05-T01+T02 su richiesta esplicita.

## 2026-10-13 — Commit W05-T01+T02

Task: operazione Git richiesta esplicitamente dall'utente.

- Ispezionati status/diff/stage: 27 file, nessun file riservato né credenziali (uniche occorrenze `ND_USER`/`ND_PASS` sono i riferimenti al file di configurazione locale già usati dai test storici); `local/`, `local.properties` e `NEXT_PROMPT.md` restano esclusi.
- Commit **`74be2d0`** — "Wave 05: motore di riproduzione e controlli (T01-T02)" (27 file, 1.669 inserimenti/36 rimozioni) con messaggio descrittivo e verifiche allegate.
- Working tree pulita dopo il commit (documenti di progetto aggiornati prima dello stage). Nessun push: solo su nuova richiesta esplicita.

Prossima azione precisa: **W05-T03** — coda modificabile, azioni dalla selezione multipla, shuffle senza ripetizioni e ripristino dell'ordine.

## 2026-10-13 — W05-T03: coda modificabile, selezione e shuffle nativo

Task: **W05-T03 completato**, incremento locale dopo `74be2d0`. Note post-commit e bozza della sessione precedente preservate e investigate; nessun commit/push richiesto o eseguito.

- Corretto lo shuffle della bozza: ExoPlayer nativo con permutazione completa ancorata al corrente in `ServiceShuffle`, mantenuta nel servizio. Nessun rimpiazzo della coda o ricaricamento del corrente al toggle. Sorgente ufficiale Timeline **1.11.1** conferma la serializzazione degli indici shuffle: la UI percorre getFirst/getNext in ordine reale, con repeat OFF per leggere la lista completa. Ordine canonico intatto al toggle off, stato conservato al rebind UI.
- Normalizzati i MediaItems nel callback del servizio: UUID univoco per occorrenza, songId negli extras, URI originale logica e vincolata al profilo. Due copie dello stesso brano non condividono identità; rimozione, tap e move colpiscono ID stabili. Nuovi gruppi AFTER/NEXT o APPEND riconciliati con la permutazione senza perdita/doppioni/resurrezione; cambio profilo resetta anche shuffle.
- Inserimenti serializzati e in attesa dell'identità normalizzata; profilo validato prima di agire. NEXT/APPEND con coda vuota rimangono in pausa, NOW avvia; coda non vuota mantiene intenzione d'ascolto salvo NOW. Riordino manuale disattiva shuffle e usa moveMediaItem conservando sorgente/posizione.
- UI Coda da pannello: LazyColumn nell'ordine effettivo, corrente evidenziato, tap per ascoltare, X, menu Sposta su/Sposta giù/Rimuovi e azioni accessibili. Prototipo drag non ha superato i test touch (destinazione rimasta 0): rimosso il gesto/handle non operativo; il menu è verificato dall'UI. Riordino resta pienamente operativo, la specifica non impone il drag. Nuovi token/palette non introdotti.
- Selezione multipla Album/Brani: FlowRow di azioni NOW/NEXT/APPEND; risoluzione Room per profilo con bind IN in blocchi di 500, ordine visualizzato + ordine disco/traccia degli album, anche per ID fuori dal paging snapshot. Cambio selezione/profilo annulla la richiesta; record mancanti non vengono omessi silenziosamente. Stato busy/error inline. Nuova query `songsByIds`, nessuna migrazione/schema modificato.

Verifiche concluse:

- Build `assembleDebug assembleDebugAndroidTest` riuscita, ultimi APK reinstallati/precompilati su `walktape_aosp`.
- JVM `testDebugUnitTest -PrealServerTest=true`: **53/53, 0 failure/skip** (report HTML e XML controllati); 8 test QueueShuffle incl. permutazione completa/determinismo, duplicati come occorrenze, gruppi NEXT/APPEND e fixture 10.000 voci con modifiche (~90 ms JVM, non benchmark Android). Login reale incluso nella regressione.
- **Android 7/7**, 343,445 s: DataSource 5, regressione T01/T02 1, nuovo test coda reale 1. MP3/FLAC dal Navidrome via 10.0.2.2; cinque occorrenze con brani duplicati e corrente all'indice 3: toggle shuffle conserva sorgente e posizione 10 s in pausa, visita tutte le occorrenze senza ripetizioni, riallineamento UI mantiene shuffle, off ripristina ordine e copia esatta. Rimozione individuale, tap, move del corrente a posizione 11 s invariata, riordino da menu, sostituzione esterna senza ricreare vecchie voci verificati.
- **Android 2/2 selezione**, 117,991 s su profilo `UI check` con indice reale: brani APPEND da vuota in pausa, NEXT in shuffle collocato dopo corrente, NOW avvia l'inserito; due album con tutte le tracce in ordine disco/traccia. Code di test svuotate, ordinamenti originali ripristinati. Nessuna mutazione playlist/preferiti sul server.
- Lint `lintDebug` separato: **BUILD SUCCESSFUL (2m55s), 0 errori/22 warning**; avvisi prevalentemente versioni/target, attributi, plurali (anche conteggi coda), risorsa non usata e KTX; nessuna baseline nuova.
- Risolti durante verifiche: failure UI iniziale cold boot, identificazione righe duplicate tramite testTag/UUID, cache accessibilità con nodi eliminati e ricreati tra selezioni (`UiAutomation.clearCache` nel test), drag non verificato sostituito dal menu. Una build con JVM/lint in parallelo al carico emulatore ha perso la connessione al processo test (timeout 120 s, non assertion): ripetuti i controlli separati, tutti passati. Nessuna attribuzione della causa a kDrive.
- Env temporaneo `/data/local/tmp/walktape-test-server.env` e marker `/data/local/tmp/wt-seed` rimossi; assenza env verificata. Profili temporanei rimossi e profilo precedente riattivato; emulatore spento e ADB senza dispositivi. Config originale `local/test-server.env` preservata, mai stampata.

Fonti ufficiali: AndroidX Media3 **tag 1.11.1**, Timeline con `FIELD_SHUFFLED_WINDOW_INDICES`, Player/ShuffleOrder. Link e scelte in ARCHITECTURE; decisione D31 in memoria. File principali: playback/{PlaybackConnection,PlaybackSnapshot,PlaybackService,QueueShuffle,ServiceShuffle}, UI/{QueueSheet,PlaybackControls,LibraryScreen,LibraryViewModel}, LibraryDao e test pertinenti.

Non eseguiti: coda/shuffle/selezione sul Motorola, TalkBack manuale e gesture drag (non esposta), persistenza/riavvio del processo (T04), interruzioni complete/Bluetooth (T05/T07), TLS reale. Checklist telefono privata aggiornata, docs PHONE_TESTS già tracciato non modificato. Prossima azione precisa **W05-T04**: coda/posizione e shuffle per profilo in Room, migrazione esplicita e ripristino sempre in pausa.

## 2026-10-13 — Commit W05-T03

Operazione Git richiesta esplicitamente dall'utente.

- Ispezionati status, diff, log recente e stage; staged diff --check riuscito. 21 file intenzionali, nessun APK/artefatto, configurazione privata o valore segreto incluso. Verificato ignore di local/test-server.env, checklist telefono e NEXT_PROMPT.
- Commit **afe7f9f** — “Wave 05: coda modificabile, selezione multipla e shuffle (T03)”: 21 file, 1.148 inserimenti/16 rimozioni. Messaggio con risultati già verificati (53/53 JVM, 9/9 Android, lint 0 errori/22 warning), limite hardware e punto di ripresa T04.
- Commit riuscito senza hook falliti; working tree pulita subito dopo. Nessun push: main avanti di 2 commit su origin/main (74be2d0 e afe7f9f). Note post-commit di log/memoria restano locali.

Prossimo task **W05-T04**; i controlli telefono T03 restano aperti. Nessuna nuova build/test necessaria per l'operazione Git; codice invariato rispetto alle verifiche dell'incremento.

## 2026-10-13 — W05-T04: persistenza coda e posizione per profilo

Task: **W05-T04 completato**, incremento locale dopo `afe7f9f`, su richiesta di prosecuzione autonoma dell'utente (nota di prosecuzione in memoria). Nessun commit/push.

Implementazione:

- **Room v5**: `queue_state` + `queue_entry` con doppio CASCADE su profilo e indice unico entryId per profilo; migrazione esplicita 4→5 e schema 5.json esportato (glob verificato). `QueueDao` con transazioni `replace`/`load` e invarianti in `require`.
- `SavedQueue`/`SavedQueueItem`: solo stato musicale (mai URL autenticati/credenziali/token); `isValid()` copre duplicati, traversal come permutazione esatta, range posizione/profilo/ripetizione, durate non positive, coda vuota coerente. Test JVM dedicati.
- `QueueStore` (Graph.queues): scrittore unico su canale illimitato; `load`/`flush` barriere FIFO; update-only a layout invariato; failure per profilo con ultimo stato valido conservato; write post-rimozione scartati da FK; `failedProfiles` osservabile dalla UI.
- `PlaybackPersistence` nel servizio: cattura su main looper, tick 1 s solo in riproduzione; cambio profilo con save finale prima dello stop; restore senza prepare/play (STATE_IDLE: zero HTTP/audio, offline-safe); `ServiceShuffle.restore` installa la permutazione esatta senza generarne una nuova; profilo con load fallito resta senza owner per non sovrascrivere una coda corrotta; `onDestroy` salva lo stato finale. Play dopo restore riparte dall'inizio se la posizione salvata è a fine traccia (durata salvata nota).
- UI: “Continua ascolto” con brano in pausa e **Riprendi ascolto**; banner errore storage per profilo attivo; stringhe IT/EN. Regressione T01/T02 aggiornata: il cambio profilo ora **ripristina** la coda del profilo precedente anziché lasciare la sessione vuota.

Verifiche:

- Build `assembleDebug assembleDebugAndroidTest testDebugUnitTest -PrealServerTest=true` → BUILD SUCCESSFUL (4m10s dopo correzione `positionMs == 0L`). Corretto in corsa: caching di traversal/shuffle fuori dallo snapshot caldo.
- **JVM 58/58** (report HTML: 58 test, 0 fallimenti, 0 skipped, 1m07s): 5 nuovi `SavedQueueTest` (duplicati esatti, traversal non permutazione rifiutato, range, coda vuota coerente, durata ignota non scarta la posizione) + regressione con login reale.
- **Android `QueueStorageTest` 4/4** (16 s): migrazione 4→5 da schema 4.json reale con profilo/album/playlist preservati e code vuote; round-trip su disco tra due aperture del DB con isolamento due profili e coda vuota esplicita; ordering dello scrittore (25 salvataggi, load barriera), CASCADE alla rimozione profilo, write tardivo non resuscita; snapshot invalido non sovrascrive l'ultimo valido e riporta failure, con ripresa dopo salvataggio valido.
- **Android `QueueRestoreTest#profileSwitchAndServiceRestartRestoreWithoutHttpOrAutoplay` 1/1** (15 s, MockWebServer con soli 2 login fixture): restore di occorrenza corrente esatta con duplicati, posizione, ripetizione, shuffle e permutazione, `STATE_IDLE` e nessun autoplay; A→B→A con stato modificato salvato e ripristinato; `stopService` con distruzione verificata e riconnessione che ripristina l'ultimo stato (save finale di onDestroy); svuotamento esplicito conservato; **zero richieste di streaming** durante tutti i restore.
- **Processo separato**: fase seed (salva coda fixture e attiva profilo) → `am force-stop` dell'app → riapertura MainActivity (dumpsys: sessione assente/`NONE`, nessuna attività di riproduzione avviata dall'UI) → fase verify 1/1: il servizio ripristina coda/posizione/shuffle in pausa senza autoplay; marker nella cache privata dell'app cancellato.
- **Android regressione motore 8/8** (258 s): 5 DataSource + 3 del servizio, incluso il nuovo `realSavedFlacResumesAtPositionOnlyAfterExplicitPlay` con **FLAC reale** via 10.0.2.2: ascolto da 10 s, pausa+ripetizione coda+shuffle, cambio profilo, stato salvato con posizione >10 s verificato da `QueueStore`, ritorno al profilo con restore idle/in pausa sull'occorrenza salvata con shuffle, nessun autoplay dopo 1 s, **play esplicito in-app che riprende lo streaming dalla posizione salvata**. La regressione cambio profilo ora verifica il ripristino del profilo precedente.
- **Android `QueueSelectionTest` 2/2** (88 s) come regressione post-T04.
- **Lint: 0 errori / 22 warning** (report txt, invariati rispetto a T03). Nessuna baseline aggiunta.
- Env `/data/local/tmp/walktape-test-server.env` e marker `wt-seed` rimossi dopo l'uso (assenza verificata); profili fixture eliminati e profilo precedente riattivato in ogni test; emulatore spento, ADB senza dispositivi. Schema 5.json generato. Nessuna mutazione playlist/preferiti sul server.

Non eseguiti: prove telefono di T04 (riavvio app, cambio profilo, ripresa senza autoplay), T03 su telefono, interruzioni/cuffie/Bluetooth (T05), timer/fine coda come comportamento dedicato (T06), TLS reale, TalkBack. Nessun commit/push.

File principali: `core/db/{QueueEntities,QueueDao,WalkTapeDatabase}.kt`, `playback/{SavedQueue,QueueStore,PlaybackPersistence,ServiceShuffle,PlaybackConnection}.kt`, `Graph`, `HomeSections`, `PlaybackControls`, `values{,-en}/playback.xml`, test `SavedQueueTest` (JVM), `QueueStorageTest`, `QueueRestoreTest`, `PlaybackServiceTest`. Documenti: PLAN, ARCHITECTURE, DESIGN, memoria, prompt locale, checklist telefono locale.

Prossima azione precisa: **W05-T05** — audio focus/chiamate, scollegamento cuffie, comandi Bluetooth/notifica/lockscreen; gran parte della configurazione Media3 è già attiva (focus automatico, becoming-noisy), il task riguarda i comportamenti residui e le verifiche dedicate.

## 2026-10-14 — W05-T05: focus, noisy e comandi sessione su emulatore

Task: **W05-T05 parziale** (verifiche automatiche concluse, hardware aperto); l'utente ha autorizzato prosecuzione mentre prova T04. T04 e note locali preservati, nessun commit/push.

Implementazione/verifiche:

- Focus automatico e becoming-noisy Media3 già attivi da T01, mantenuti. Aggiunto `AudioOutputMonitor` in Graph/PlaybackService: `ForwardingAudioSink` delega tutte le operazioni al sink predefinito e conserva soltanto l'ultima scrittura gain in StateFlow volatile, reset al ciclo vita servizio. Nessuna elaborazione/campione audio, rete, log o persistenza. Nessuna nuova dipendenza/schema/permesso, nessun cambio UI/design.
- `FocusInterruptionsTest` opt-in su AOSP e Navidrome reale: richiedente focus distinto nello **stesso processo/UID**, concessione GRANTED verificata e notifiche reali del framework. Ducking → sink gain **0,2**, playback continua, `Player.volume=1` e STREAM_MUSIC invariati; regain → sink gain **1**. Perdita transitoria → playWhenReady mantenuto, output soppresso con reason focus transient, regain riprende; pausa manuale durante la seconda interruzione impedisce la ripresa. Perdita permanente → pausa e nessun autoplay, play manuale riacquisisce focus.
- Broadcast protetto `AUDIO_BECOMING_NOISY` sintetico via `su 0 am broadcast` **solo nel test AOSP**, esito di consegna controllato: pausa senza ripartenze, play manuale funziona. Non è uno scollegamento cuffie fisico; nessun root applicativo.
- Comandi framework `cmd media_session dispatch` play/pausa/next/previous con app in background, precedente vicino all'inizio in pausa, sessione media-button WalkTape verificata via dumpsys. Play/pausa con display **mWakefulness=Asleep** e posizione audio in avanzamento. Nessuna prova dei tasti/route Bluetooth o del widget lockscreen Motorola.
- Helper reali `playbackCandidates`/`toQueueItem` estratti in `RealLibrary.kt` per riuso nei test, durata musicale inclusa nei metadata. `PlaybackServiceTest.awaitUi` ora svuota cache UiAutomation su API≥33 prima di ricercare i nodi dinamici.

Esiti finali:

- Build app/androidTest riuscita; ultimo `assembleDebug assembleDebugAndroidTest lintDebug` **BUILD SUCCESSFUL (2m47s)**. APK reinstallati/precompilati sull'AVD. **Lint 0 errori/22 warning**, nessuna baseline nuova.
- **58/58 JVM**, 0 failure/skip, login reale incluso (XML RealServerTest del 2026-10-14 controllato); `testDebugUnitTest lintDebug -PrealServerTest=true` riuscito (7m53s, suite JVM 3m18s).
- Suite **T05 1/1**, 121,372 s, tutti i controlli descritti sopra superati.
- Regressioni pertinenti **9/9 su due esecuzioni**: prima suite 9 test (364,819 s) → 8 passati (coda reale/shuffle, FLAC salvato e ripreso solo su play, 5 DataSource e restore switch/restart senza HTTP/autoplay); UI MP3/FLAC/controlli fallita cercando “Ripetizione: brano”. Dopo refresh cache accessibilità, **ripetuto soltanto quel test: 1/1**, confermato da TestRunner `run finished: 1 tests, 0 failed, 0 ignored` (07:34:24–07:38:15). Il comando shell combinato install/precompile/test ha raggiunto timeout 300 s prima di restituire il riepilogo, ma il test si è concluso e l'esito è stato recuperato dal log Android. Nessun nuovo problema audio riscontrato.

Correzioni/limiti durante il lavoro:

- Prima bozza attendeva ducking su `MediaController.volume`: aspettativa errata, è il gain effettivo al sink a cambiare. Prima verifica monitor attendeva anche una scrittura iniziale 1: il sink usa il default implicito, il monitor resta null fino alla prima scrittura; ora controlla volume pubblico iniziale e scritture reali duck/regain, senza inventare osservazioni.
- `am broadcast` da UID shell rifiutato con SecurityException: corretto usando root AOSP nel test e verificando la consegna. `input keyevent KEYCODE_MEDIA_*` intermittente (una prova fermata sulla pausa background, una sulla pausa a display spento); passaggio al percorso ufficiale `media_session dispatch`, suite finale passata. Non dichiarato risolto/verificato il percorso tasto fisico sull'emulatore.
- Revisione documentale T04: chiarito che dopo load fallito `PlaybackPersistence` mantiene owner null; un normale evento player non recupera l'owner. Serve nuova lettura riuscita al cambio profilo/ricreazione servizio. Il recovery di QueueStore dopo save valido è un contratto separato; codice T04 invariato.
- Consultati sorgenti ufficiali Media3 **1.11.1** (AudioFocusManager, DefaultRenderersFactory, ForwardingAudioSink, MediaCodecAudioRenderer, ExoPlayerImpl, MediaSession resumption); link in ARCHITECTURE. Nessun receiver/callback `onPlaybackResumption` aggiunto; media key da processo/servizio freddo non verificato.
- Cleanup: profili temporanei rimossi e profilo precedente ripristinato nei finally; env e marker in `/data/local/tmp` rimossi con assenza verificata; emulatore spento. Credenziali locali originali conservate e mai stampate.

File/documenti: `playback/AudioOutputMonitor.kt`, Graph, PlaybackService, androidTest/{FocusInterruptionsTest,RealLibrary,PlaybackServiceTest}; PLAN, ARCHITECTURE, memoria, NEXT_PROMPT e checklist locale. DESIGN/SPEC senza nuovi requisiti o modifiche visive.

Non eseguiti: chiamate vere (risposta/rifiuto), cuffie/USB-C e Bluetooth fisici, widget lockscreen Motorola, T03/T04 hardware, cold-start da media key, TLS reale, TalkBack, timer/suggerimenti. **T05/T07 restano `[ ]`** per le prove hardware; checklist aggiornata in `local/PHONE_TESTS_W05.md`. Prossimo sviluppo indipendente: **W05-T06** (timer con breve dissolvenza e continuazione suggerita opzionale; arresto a fine coda default già verificato in T02).

## 2026-10-14 — Commit W05-T04+T05

Operazione Git richiesta esplicitamente dall'utente.

- Ispezionati status, diff e stage: 27 file intenzionali (codice, test, schema 5.json, documenti); nessun APK/artefatto di build, configurazione privata o valore segreto. Verificato ignore di `local/`, `local.properties` e `NEXT_PROMPT.md`; staged diff --check senza errori.
- Commit **`1252406`** — “Wave 05: persistenza coda per profilo e interruzioni audio (T04, T05 parziale)” (27 file, 2.039 inserimenti/62 rimozioni), messaggio con verifiche allegate e limiti hardware espliciti. Identità per-commit non persistita.
- Working tree pulita subito dopo il commit. **Nessun push**: main avanti di 3 su origin/main (74be2d0, afe7f9f, 1252406); push solo su nuova richiesta esplicita.

Prossima azione invariata: **W05-T06**; prove telefono T03/T04/T05 aperte.

## 2026-10-14 — W05-T06: checkpoint parziale su richiesta

- Utente: iniziare T06 e rimandare prove complessive/hardware alla fine di W05; successivamente richiesto commit e prosecuzione. Task resta `[ ]`.
- Bozza: SleepTimer in Graph agganciato al servizio (tempo/brano/coda, dissolvenza gain player, volume Android invariato); ContinuationSuggestion/SuggestionSelection, getSimilarSongs2, opt-in DataStore default off; controlli IT/EN nel pannello e toggle impostazioni. Fine album distinta dalla fine coda **ancora da implementare**.
- Build principali e androidTest riusciti dopo correzione import/coroutine; 67/67 JVM, 0 skipped (9 nuovi). Prima esecuzione Android con APK app stantio: NoSuchMethodError, ricompilato/reinstallato. Esecuzione successiva 3 test (178,507 s): fine coda reale/valutazione suggerimenti 1 passato; test timer fallito perché non riprendeva dopo pausa brano; apply fixture fallito perché Graph.playback non connesso. Correzioni nel working tree, inclusa attesa normalizzazione identità: **non ancora ricompilate/verificate**.
- Lint T06, regressioni, fine album, lifecycle e isolamento suggerimenti/timer restano aperti. Non presentare l'apply con byte fixture non decodificabili come streaming verificato. Test Motorola a fine wave.
- Commit checkpoint autorizzato; stage solo codice/test/documenti intenzionali, segreti/config locali/APK esclusi. Nessun push. Prossima azione: ricompilare entrambi gli APK e rieseguire i test dedicati, quindi completare gli aspetti mancanti.

## 2026-10-14 — Commit checkpoint T06 e completamento successivo

- Su richiesta esplicita: ispezionati status/diff/log ultimi 10, stage e diff --check, ignore dei file riservati verificati. Commit **`0a9e844` — “Wave 05: checkpoint timer e suggerimenti (T06 parziale)”**, 25 file (928 inserimenti/5 rimozioni), identità per-commit senza configurazione persistita. Working tree pulita subito dopo, main avanti di 4; nessun push. Il completamento descritto sotto **è successivo al checkpoint e non committato**.

Task **W05-T06 completato** per implementazione e verifiche pertinenti; W05-T05/T07 conservano verifiche hardware finali aperte, su richiesta dell'utente.

Implementazione successiva:

- SleepTimer: tempo monotono elapsedRealtime, pausa alla scadenza con gain temporaneo negli ultimi 3 s (STREAM_MUSIC invariato), cancel mid-fade ripristina 1, detach cancella prima del rilascio player. Nessun riferimento al player rilasciato e nessuna persistenza timer/process-death. Resta operativo senza controller UI.
- Fine **album distinto dalla coda**, basato su albumId negli extras: gruppo consecutivo dell'album corrente nel traversal reale, stop al cambio album/termine del gruppo. Brani ripetuti dello stesso album non interrompono il timer; albumId ignoto disabilita il comando. songMediaItem/DTO includono ID; PlaybackPersistence.restore arricchisce gli ID dall'indice Room per profilo, query in blocchi da 500, senza HTTP e senza cambio schema.
- Suggerimenti: DataStore default off, invalidazione su cambio profilo/coda, uscita da ENDED e disattivazione. Proposta con profilo e sorgente; apply ricontrolla opt-in/ENDED/sorgente/profilo e la consuma una sola volta, normalizzazione UUID asincrona attesa tramite conteggio+identità prima di seek/play. Deduplica ranking, no source/ID vuoti/durata non positiva, massimo 15, niente fallback casuale/autoplay.
- UI: Timer dopo volume/coda nel pannello scrollabile, Fine album e stato IT/EN, campo albumId nello snapshot reattivo; impostazioni scrollabili e toggle unico accessibile. Nessun nuovo token visivo. MainThread/lifecycle espliciti; cambio profilo cancella prima del restore, non tramite collector parallelo tardivo.

Verifiche finali:

- Build app+androidTest **BUILD SUCCESSFUL (2m37s)**; ultima `assembleDebugAndroidTest testDebugUnitTest lintDebug` **BUILD SUCCESSFUL (1m58s)**. Report JVM **67/67, 0 failure/skip**, suite ~57,581 s, login reale incluso; lint **0 errori/22 warning**. Nessuna baseline introdotta.
- **Android dedicati 3/3 su esecuzioni separate**: prima suite 174,062 s → timer e continuazione reale passati; fixture fallita mentre richiedeva la proposta prima dell'ack di normalizzazione coda. Attesa di UUID servizio aggiunta; ripetuto solo fixture → **1/1 in 10,36 s**. Timer reale: countdown/dissolvenza/pausa e gain ripristinato con bridge scollegato; annullamento senza stop; fine brano; fine coda; album A/A/B con copie duplicate, passa al secondo A continuando e si ferma su B in pausa; annullamento mid-fade ripristina gain e conserva playback.
- Suggerimenti: fixture verifica zero richieste se off, selezione risultati, failure→Unavailable, rifiuto apply su coda non terminata e clearing disattivando opt-in. Il test reale arriva a ENDED, valuta il server senza ripartenza; poi una **raccomandazione fixture con ID FLAC reale** viene accettata tramite il bridge: coda estesa, nuova identità UUID e **FLAC realmente in riproduzione dal Navidrome** con posizione >500 ms. Doppio tap non duplica la continuazione. Nessun test positivo usa byte audio finti; la raccomandazione positiva del server reale non è attestata da questa fixture.
- **Regressioni Android pertinenti 5/5 su esecuzioni separate**: suite 438,409 s → 4 passati (MP3/FLAC e controlli UI, FLAC salvato e ripreso manualmente, restore cambio profilo/restart zero HTTP, focus/noisy/media key incluso display spento); test coda fallito su “Shuffle survives release/rebind”. Rinforzata l'attesa dell'ordine shuffle con ack del secondo controller (non solo masking ottimistico del bridge), nessun cambio al motore shuffle; ripetuto solo quel test → **1/1 in 170,906 s**. Coda/menu/rimozione/shuffle/rebind passati. Non attribuita la failure a hardware/kDrive.
- Lint intermedio aveva rilevato StateFlow.value in composizione per Fine album: corretto usando albumId nello snapshot osservato. APK precedente stantio della bozza già corretto/reinstallato. Nessuna nuova dipendenza. Fonti wave 01/Media3 1.11.1 conservate; fetch pagina OpenSubsonic fallito per errore trasporto, non presentato come consultazione riuscita. Sorgente ufficiale Navidrome v0.62.0 media_retrieval consultato (non contiene getSimilarSongs2); nessuna capacità positiva reale dedotta da esso.
- Env/marker temporanei rimossi da /data/local/tmp, assenza verificata; profili e impostazioni test ripristinati nei finally; emulatore spento. Segreti originali locali conservati e mai stampati. Nessuna mutazione playlist/preferiti server.

File principali: playback/{SleepTimer,ContinuationSuggestion,SuggestionSelection,PlaybackConnection,PlaybackSnapshot,PlaybackService,PlaybackPersistence,SubsonicStream}, Graph, client/settings/UI/stringhe, test dedicati e regressione. PLAN/DESIGN/ARCHITECTURE/memoria/prompt/checklist aggiornati. SPEC invariata: Fine album è implementata, non sostituita con Fine coda.

Non eseguiti: collaudo Motorola dei nuovi timer/dissolvenza, chiamate/cuffie/Bluetooth/lockscreen completo, TalkBack/font scale manuali, timer preset 15–90 min su hardware, raccomandazioni positive prodotte realmente dal server, cold-start media key e TLS reale. Checklist finale W05 privata pronta. **Prossima azione W05-T07: test complessivi sul Motorola con APK aggiornato; T05/T07 restano aperti.**

## 2026-10-14 — Riscontro utente sulle azioni album e fine coda

Task: W05-T02/T03, avanzamento collaudo W05-T07. L'utente conferma «ha funzionato tutto» dopo le istruzioni specifiche: selezione album con pressione prolungata → Aggiungi in coda/Dopo il corrente/Riproduci ora; ultima traccia con ripetizione off → arresto, Play → primo brano della coda. Registrati questi punti come riusciti; nessuna estensione a persistenza/timer/chiamate/Bluetooth non ancora riferiti.

Checklist locale e memoria aggiornate; nessun cambio codice/APK, nessuna nuova build necessaria per questo riscontro. T05/T07 restano aperti; prossima azione: continuare gli altri punti della checklist finale W05. Nessun commit/push.

## 2026-10-14 — Esiti della checklist numerata finale W05

Fonte: utente in chat; prove hardware riferite, non eseguite dall'agente. **1 OK tutto, 2 OK tutto, 3 OK tutto, 4 OK tutto, 7 OK tutto**: coda/riordino/shuffle/background, repeat brano/coda, volume e tasti fisici, persistenza dopo chiusura/riavvio e ripresa senza autoplay, notifica/lockscreen. **9: solo Bluetooth testato, OK** (nessun dettaglio separato sui singoli passaggi).

Punti ancora aperti: 5 non provato (un solo profilo); 6 «credo OK» con prova completa 15 minuti ancora da effettuare; 8 chiamate da provare alla prossima chiamata, altra app audio non riferita; 9 cuffie cablate/USB-C non provate; 10 richiesta spiegazione, nessun esito. T05/T07 restano `[ ]`. Aggiornati PLAN, memoria e checklist privata; codice/APK invariati, nessuna build richiesta, nessun commit/push. Prossimo passo: spiegare opt-in suggerimenti prima di un nuovo ENDED, quindi raccogliere gli esiti mancanti.

## 2026-10-14 — Suggerimenti di continuazione confermati dall'utente

Task: W05-T06, collaudo finale W05-T07. Dopo la guida sul punto 10, l'utente riferisce «Punto 19 il pulsante compare e funziona»: dal contesto il riferimento è al pulsante Continua con brani simili. Registrati comparsa e funzionamento della continuazione **sul server/account reale dell'utente**, distinto dalla precedente prova positiva fixture dell'agente. Doppio tap/disattivazione non dettagliati separatamente; nessuna estensione a timer/chiamate ancora pendenti.

Aggiornati checklist locale, memoria e PLAN; nessun cambio codice/APK, nessuna build necessaria, nessun commit/push. Prossimo passo: esito timer 15 minuti/schermo spento e prove interruzioni. T05/T07 restano aperti.

## 2026-10-14 — Pubblicazione degli incrementi W05

Operazione richiesta dall'utente («un push sarebbe opportuno»).

- Ispezionati status, diff, log ultimi 10, remote e tracking; fetch origin riuscito, divergenza 0 remoti/4 locali. Rivisti i quattro incrementi pendenti e il diff del completamento T06. Ignore verificato per configurazione server, local.properties, checklist telefono e NEXT_PROMPT.
- Stage esplicito di **23 file** intenzionali; diff --check senza errori. Commit **`acf6c1a` — “Wave 05: completamento timer e continuazione suggerita (T06)”**, 256 inserimenti/66 rimozioni. Identità per-commit non persistita; nessun segreto, APK o file locale privato incluso. Codice invariato rispetto alle verifiche 67/67 JVM, 3/3 Android dedicati + 5/5 regressioni, lint 0/22: nessuna ripetizione di build per l'operazione Git.
- **Push ordinario riuscito** su origin/main: `26584bc..acf6c1a`, pubblicati T01/T02 `74be2d0`, T03 `afe7f9f`, T04/T05 parte automatica `1252406`, checkpoint T06 `0a9e844`, completamento `acf6c1a`.
- Verifica `git ls-remote origin refs/heads/main`: SHA remoto e HEAD locale **`acf6c1a525074fa6fd341299b01e924de18bc982`** identici, working tree pulita subito dopo la pubblicazione del codice. Memoria/log/prompt locale riallineati all'esito; note tracciate pubblicate con l'aggiornamento documentale dell'operazione. Nessuna release/APK pubblicata.

Punto di ripresa invariato: collaudo W05-T07, timer 15 minuti/schermo spento e interruzioni (chiamate/altra app audio); un solo profilo e cuffie cablate non provati. T05/T07 restano aperti, riscontri già riusciti conservati.

## 2026-10-14 — Handoff per avvio W06 e collaudo congiunto

Utente: «Adesso implementiamo W06 e poi lo testiamo insieme ai test rimanenti di W05. Scrivimi il prompt per ripartire in una nuova sessione». Approvata questa sequenza: avvio W06-T01 e prosecuzione T02–T06 sul motore W05, con test hardware congiunti al termine; W05-T05/T07 mantengono i controlli aperti. Perimetro SPEC invariato, nessuna funzione o verifica rimossa.

Verificato Git: HEAD/origin/main allineati su **ba37529**, working tree pulita prima delle note. Codice applicativo W05 pubblicato fino a acf6c1a, note Git in ba37529. Aggiornati PLAN e memoria con la sequenza; NEXT_PROMPT locale ignorato aggiornato con istruzioni operative, stato/test/prerequisiti e checklist da preservare. Nessun codice W06 implementato in questo handoff, nessuna build necessaria, nessun nuovo commit/push.

Prossima azione precisa: nuova sessione → leggere documenti/design → W06-T01 (scocca e tasti TPS-L2), poi cassetta/bobine/metadati/copertina, controlli reali e long-press seek, aptica/clic opzionali, accesso globale/coda/layout adattivi e verifiche accessibilità. Preparare a fine W06 il collaudo Motorola con i test W05 rimasti.

## 2026-10-15 — W06-T01–T05: player TPS-L2 e verifiche T06 parziali

Richiesta: implementare W06 prima del collaudo finale hardware W05. Letti documenti e status; preservate note locali PLAN/memoria/log. Nessun commit, push o release. Baseline ba37529.

Implementazione:

- Rotta Player aperta dalla mini-bar globale, scocca con materiali blu/acciaio/arancio, marchio WalkTape e MechanicalKey con target 56dp+, stato attivo e feedback. Schermata scrollabile, layout che dispone i tasti su più righe; player e coda affiancati da 840dp di contenuto disponibile.
- Cassetta Canvas con tape/reel, counter monospace, etichetta album basata su albumId (album omonimi distinti), playlist da origine omogenea o Mixtape. CoverArtById riusa cache limitata e profilo, copertina ingrandibile; vuoto senza art finta. Angolo bobine dal frame clock solo durante `isPlaying`; preferenze movimento ridotto tramite ContentObserver.
- Trasporto, repeat/shuffle/coda, seek slider e volume/timer/suggerimenti tutti dal servizio/bridge W05. STOP pausa conservando posizione; FF/REW long-press a passi crescenti 5/10/15 s, target cumulativo, key del media con UUID+URI+indice e sottogruppo tasti keyed. Fine gesto lungo non esegue lo skip; cambi traccia annullano l'input. Loop hold limitato al lifecycle RESUMED.
- DataStore aptica on/disattivabile e clic off/opt-in; click FX_KEY_CLICK soltanto con suoni Android attivi, haptics View secondo Android. Test persistenza delle due preferenze e ripristino valori originali.
- PlaylistDetail in sola lettura: nuovo comando play/tap traccia, risoluzione completa locale con ordine e duplicati, check profilo e errore se indice incompleto. Origine in extras e **Room v6**, colonna nullable originName per queue_entry, migrazione 5→6, schema 6.json. Writer e isolamento W05 conservati. Nessuna mutazione playlist server.
- Correzioni layout trovate durante T05: la Column fillMaxSize dentro NavigationRail occupava tutta la larghezza e azzerava il NavHost (riprodotto anche con sorgenti W05); sostituita con fillMaxHeight e peso al NavHost. Corretto accesso player dietro navbar laterale in landscape con inset orizzontali; eliminato scroll infinito attorno alla LazyColumn affiancata. Layout verificato dopo correzione.

Verifiche finali:

- Build `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug -PrealServerTest=true` riuscita (ultima completa 6m47s). **78/78 JVM**, zero failure/skip, login reale incluso, 8 label e 3 contrasto. **Lint 0 errori/22 warning**; rimossi warning nuovi di naming/risorse obsolete. Nessuna nuova baseline o dipendenza.
- **7/7 Android** storage+player/preferenze (171,854 s): migrazione 4→6 con profili/libreria/playlist conservati; 5→6 da schema 5 reale con coda/posizione e origin null; round-trip su disco due profili e origine playlist, writer/FK/invalid. Player reale: apri/indietro, label album, long-hold forward/rewind e track-change con dito ancora giù, no seek/skip sul nuovo brano, volume STREAM_MUSIC, repeat, shuffle, coda, cover e preferenze feedback.
- Ultima suite **9 test: 7 passati/2 falliti (676,452 s)**: PlayerUiTest 2 passati, PlaybackServiceTest 3 passati (MP3/FLAC+UI/seek/volume/repeat; FLAC salvato e ripreso manualmente; editing/shuffle/rebind), QueueRestoreTest switch/restart senza HTTP/autoplay passato, continuazione reale passata. Timer fallito su «Cancelled timer must not stop playback», fixture suggerimenti su ack identità. **Ripetuti separatamente gli esatti falliti senza modifiche al motore: timer 1/1 (117,892 s), fixture 1/1 (8,867 s)**. Quindi 9/9 su esecuzioni separate, non suite unica verde. Fixture playlist locale con brani reali esercita il medesimo comando del dettaglio, ordine A/B/A e seconda occorrenza avviata realmente, label aggiornata. Non prova una playlist esistente sul server/account (che non ne ha una attestata).
- Selezioni libreria: prima esecuzione rapida saltata per marker mancante, **non contata**; con wt-seed **2/2 (123,945 s)** nella fase iniziale dell'incremento. Timer 1/1 e suggerimenti 2/2 passati anche nella fase iniziale. Focus/noisy hardware non ripetuti né dichiarati nuovi esiti.
- Dump Android e screenshot: portrait IT, light/dark (sfondo notte 16,26,48; materiali acciaio/crema/blu invariati), landscape con libreria e player finalmente visibili, viewport tablet simulato 1920×1200/density160 con coda completa affiancata e timer/volume, EN (Player/Queue/Sleep timer), font_scale 2.0 con testo e tasti presenti. Sono controlli UI/semantica di base, non TalkBack manuale o collaudo tablet fisico. Prova riduzione movimento configurata, ma avvio da media_session su coda idle non ha attestato playback: osservazione bobine col movimento ridotto resta manuale.

Diagnostica e limiti:

- Diverse iterazioni Android hanno evidenziato test non aggiornati al passaggio sheet→route: vecchi descrittori repeat/coda, off-screen volume, riapertura sheet dal player, cache accessibilità e back durante dismiss. Corrette attese/scroll e uso della toolbar; preservate asserzioni su motore/sessione. Finestre wrap da duration−400 rese indipendenti dal poll entro 3 s; controllato effettivo ritorno prima del termine. Nel nuovo hold test attesa UUID normalizzati prima di catturare la key.
- Prima JVM combinata fallita senza failure XML conservata; due rerun green e build finali green, nessun esito iniziale trasformato in successo. Alcuni comandi PowerShell iniziali falliti (quoting -D, PNG via redirect, archivio aar); corrette procedure e PNG ripresi con adb pull. Test runner inizialmente rifiutava ritorno non-Unit; corretto.
- AVD sotto carico: avvii lenti, root accessibilità null e ANR anche System UI/com.android.phone; registrati, non attribuiti indistintamente all'app. Avvio cold senza snapshot e chiusura del dialog Wait hanno permesso di proseguire. Non usare screenshot stantii come evidenza: le misure finali sono state riprese sullo stato verificato via dump.
- Per la diagnosi rail, stash temporaneo dei sorgenti W06 e spostamento dei nuovi file nel temp approvato; prima build baseline fallita per nuovi file ancora presenti (nessuna evidenza dedotta), seconda build W05 pura riuscita e bug landscape riprodotto. Sorgenti W06 ripristinati, stash eliminato dal pop riuscito; nessuna modifica documentale utente rimossa.

Non eseguiti: TalkBack sul Motorola, ascolto/percezione aptica e clic, osservazione diretta bobine/riduzione movimento, review visiva finale, tablet fisico, cambio profilo hardware (utente ne ha uno), cuffie cablate/USB-C, timer 15 minuti a schermo spento con fade, chiamate/pausa manuale e altra app audio. **T06 resta aperto, come W05-T05/T07**; checklist privata congiunta pronta e checklist W05 preservata. APK debug aggiornato nel percorso standard, firma debug ordinaria, nessuna release. Prossimo passo: collaudo congiunto guidato dalla checklist locale.

Cleanup finale verificato: configurazione server e marker rimossi da `/data/local/tmp` (test di assenza riuscito); override size/density/font/rotazione/animation scales ripristinati, emulatore spento. File privati W05/W06, env locale e NEXT_PROMPT confermati ignorati da Git; nessun contenuto segreto stampato. Stash diagnostico assente dopo il ripristino. SHA256 APK debug finale: `471E157C8C0CF16EFD4C24F1959431C841B2CF6E3B12209C67FC2C94FE80AFA3`.

## 2026-10-15 — Riscontro generale W06 e handoff W07

Utente: «mi sembra tutto ok, se il W06 è stato completato allora scrivimi il prompt per il W07 che implementeremo in un'altra sessione». Registrato il riscontro generale positivo sul player. Non dedotti esiti specifici TalkBack/aptica/clic/riduzione movimento/timer/chiamate: **W06-T06 e W05-T05/T07 restano aperti**, checklist private preservate.

Verificati status Git, memoria, SPEC e task W07: working tree W06 modificato con nuovi sorgenti/test/schema6 non tracciati, da preservare integralmente; nessun commit/push/release. Nessuna build necessaria per questo handoff documentale, APK e verifiche precedenti invariati.

Aggiornati PLAN, PROJECT_MEMORY e prompt locale NEXT_PROMPT.md con ripresa **W07-T01 → T02–T06**, tutte le funzioni approvate (playlist/permesse, preferiti senza stelle, coda→playlist/nome mixtape, testi/dettagli, cronologia/scrobbling con recupero offline e deduplica). Indicati riuso dello stack e servizi W05/W06, Room v6, account/playlist dedicate per mutazioni, verifica API ufficiali, controlli reali distinti dalle fixture, regressioni pertinenti e limiti manuali conservati. Nessuna implementazione W07 avviata in questa sessione.

## 2026-10-15 — Commit e push W06 richiesti dall'utente

Richiesta esplicita «allora falli», riferita a commit e push.

- Ispezionati status, diff, log ultimi 10, remoto/tracking e nuovi file. Fetch origin riuscito, divergenza iniziale 0/0 su ba37529. Stage esplicito di 45 file intenzionali: codice, test, schema Room6 e documenti; diff --check senza errori, controllo pattern di chiavi/token senza riscontri.
- Ignore verificato per env/credenziali, local.properties, APK, entrambe le checklist e NEXT_PROMPT. Nessun segreto, APK o file privato incluso. Nessuna modifica al codice per la pubblicazione e nessuna nuova build necessaria: conservate le verifiche W06 precedenti e i relativi limiti.
- Commit **`93187a8` — «Wave 06: player TPS-L2, controlli e layout adattivi (T01-T05)»**, 45 file, 3.063 inserimenti/345 rimozioni. Identità per-commit conforme ai precedenti, nessun cambio persistente alla configurazione Git.
- Push ordinario riuscito **`ba37529..93187a8`** su origin/main. SHA locale/remoto **`93187a826513b97fd50e00143a9370fc9e1fea6f`** identici, working tree pulita subito dopo. Nessuna release creata.
- PLAN/memoria e NEXT_PROMPT locale aggiornati all'esito; note tracciate della pubblicazione incluse nel successivo commit documentale. Punto di ripresa **W07-T01**, controlli W06-T06 e W05-T05/T07 ancora aperti.

## 2026-10-15 — Rimozione skill locale deploy-an-app

Richiesta esplicita dell'utente: rimuovere la skill dal progetto, non pertinente all'app Android nativa. Working tree inizialmente pulita. Eliminati SKILL.md e 13 reference sotto `.agents/skills/deploy-an-app`, rimossa soltanto la voce corrispondente in `skills-lock.json`. Altre skill e configurazioni preservate; il riferimento descrittivo in review-an-app e il registro storico d'installazione non sono dipendenze operative e restano nella loro forma originale.

Controlli: directory della skill rimossa, lockfile JSON valido con le tre skill rimanenti e nessuna voce deploy-an-app, diff --check senza errori. PLAN e memoria aggiornati; nessuna build Android necessaria, nessun codice applicativo modificato. Nessun nuovo commit/push richiesto o eseguito; prossima azione funzionale W07-T01 invariata.

## 2026-10-15 — W07-T01–T05, incremento locale in verifica

Letti memoria/SPEC/PLAN/DESIGN/ARCHITECTURE e ultimi log. Baseline effettiva 40004d3, W06 già pubblicata in 93187a8: preservate rimozione skill, lockfile e note locali. Nessun commit/push/release.

Probe Navidrome 0.62.0 con account test e playlist `wt-test-*`: createPlaylist ritorna dettaglio, append e duplicati A/B/A preservati, ricostruzione ordine con remove-all+append e POST form riuscita, rename/delete persistiti e cleanup verificato (nessuna playlist residua). readonly presente anche in lista; playlistRole=false non blocca CRUD. Star/unstar brano, album e artista con lettura timestamp e ripristino. Testi: 1/60 brani ha contenuto reale non sincronizzato. Due scrobble identici id+time incrementano playCount a 2: server non idempotente; conteggi di probe restano sull'account test, non annullabili via API.

Fonti ufficiali consultate OpenSubsonic: updatePlaylist, scrobble, getLyricsBySongId e structuredLyrics (offset positivo anticipa, quindi start-offset). Client esteso con POST form e parametri ripetuti, senza log segreti. Corretta regressione intermedia GET che ometteva parametri, aggiunti test trasporto.

File: client/DTO, repository playlist/preferiti/testi, Graph, DAO/entità e Room v7+schema7, servizio/persistenza/snapshot/cassetta/coda, UI libreria/playlist/player/cronologia e risorse IT/EN. Eventi di ascolto dal vero ExoPlayer nel servizio; soglia min(50%,4min) su tempo monotono in riproduzione, stato listening_state ripristinato con UUID dell'occorrenza, record evento e stato in transazione. Invio per profilo serializzato, solo ACK marca sent; errori mantengono pending, retry servizio ogni 60s e manuale dalla cronologia. Nessuna garanzia exactly-once su risposta persa dopo accettazione server.

Verifiche intermedie: app/androidTest compilati; JVM 82 passati/1 errore server (iniziale timeout campione), isolato RealWave07ApiTest successivo 1/1 passato (14,938 s). Android Wave07StorageTest+QueueStorageTest iniziale 5 passati/3 failure migrazione DEFAULT NULL; fix applicato. Rerun con ScrobbleServiceTest: **9/9 (78,308 s)**, migrazioni 4/5/6→7, duplicati/origine/nome mixtape, isolamento preferiti e cache testi, persistenza eventi/reopen/503→ACK/niente reinvio, ExoPlayer nel servizio con PCM fixture: pausa, seek, riavvio, repeat. Non Navidrome/hardware per il PCM. Lint aveva un errore LocalContextGetResourceValueCall, corretto con LocalResources; verifica finale ancora in corso. Preparati ulteriori test permessi e UI con server reale. Nessuna checkbox W07 chiusa su queste sole verifiche intermedie.

### W07 — completamento funzionale T01–T05 e collaudo UI/server

- Build app/androidTest riuscita; suite JVM completa **84/84**, zero failure/skip, 43,270 s, login e CRUD sul server reale inclusi. Il test reale usa ora album newest+getAlbum, evitando il campionamento random lento sotto carico. Ultima build+lint successiva: **BUILD SUCCESSFUL (6m26s)**, **0 errori/22 avvisi**, senza baseline nuova. APK aggiornato nel percorso standard.
- Android `Wave07StorageTest`: aggiunto test owner diverso/readonly e versione cache obsoleta: quattro test passati. Questi rifiuti sono **fixture**, non un rifiuto dell'account reale su playlist condivisa.
- `Wave07UiServerTest`: azioni UI reali di rinomina, riordino, rimozione di un'occorrenza A/B/A, eliminazione; coda reale salvata come playlist nell'ordine mostrato con shuffle e copie, nome mixtape persistito, sheet metadati/testo reale. Prima positivo **1/1 (126,808 s)**; test ampliato con pulsante selezione multipla→playlist, aggiunte controllate sul server e ripristino: **1/1 (108,406 s)**. Playlist `wt-test-ui-*` eliminate dal finally; recupero mirato delle fixture create dal test in caso di crash di processo. Nessuna playlist utente modificata.
- Problemi trovati nel collaudo: ANR System UI dell'AVD (visibile nella diagnostica), selettore di test Rinomina che colpiva il titolo invece del pulsante (corretto cercando ancestor cliccabile), quindi **crash applicativo reale** per chiavi Compose duplicate nella coda ancora non normalizzata. Corretto con chiavi provvisorie indice+songId, UUID standard dopo normalizzazione; rerun UI passato. Questi fallimenti non sono contati come passati prima dei fix.
- Preferiti aggiornati **dopo ACK**, senza ottimismo/rollback: il codice iniziale era ottimistico, il comportamento finale serializza e conserva l'ultimo valore confermato su errore. Cuori nel colore primary per contrasto sui temi. Rimossa risorsa playlist_read_only superata e collegato Rimuovi nome, mantenendo lint a 22 avvisi.
- Regressioni W05/W06: sessione combinata 9 test **7 passati/2 falliti**, conclusione riscontrata anche dai log TestRunner dopo timeout del tool host a 600s. Player UI non trovava REW; restore fixture non raggiungeva lo stato atteso dopo quella UI. Ripetuti isolati **senza modifiche al motore**: restore **1/1 (11,546 s)** e player TPS-L2 **1/1 (152,848 s)**. Timer, due suggerimenti, tre PlaybackServiceTest (MP3/FLAC, restore FLAC, queue/shuffle) e preferenze passati nella combinata. Quindi 9/9 su esecuzioni separate, non suite unica verde.
- Riesaminato isolamento dei nuovi dialog: snapshot espone il profileId dalla URI logica del media; chiusura dialog al cambio profilo e guardie per nome/salvataggio/dettagli, evitando azioni sul profilo nuovo durante il passaggio. Build/lint delle guardie riusciti; verifica Android finale dedicata in corso.
- Aggiunto `RealScrobbleRecoveryTest`: **evento di recupero injected**, HTTP503 fixture→invio al vero Navidrome, lettura playCount/ACK e nessun reinvio dopo sender ricreato. Non confonderlo con l'origine dal player: quella è verificata da ScrobbleServiceTest con PCM. Esito finale riportato nel seguito.
- Stato piano: **T01–T05 [x]**, T06 resta parziale per il rifiuto reale su playlist condivisa di altro account test non disponibile; criterio di chiusura wave non dichiarato superato. Testi sincronizzati verificati con fixture, solo testo non sincronizzato disponibile nel campione reale. Nessuna nuova attestazione hardware W05/W06; checklist private preservate e nuova `local/PHONE_TESTS_W07.md` pronta.
- Tutti gli artefatti/configurazioni privati confermati ignorati da Git; diff --check senza errori. Documenti PLAN/MEMORY/NEXT_PROMPT/DESIGN/ARCHITECTURE aggiornati. Rimossi script e JSON dei probe nel temp approvato (nomi noti della sessione); credenziali originali in local/test-server.env preservate. Nessun commit/push/release.
- Ultima coda di test Android ostacolata da AVD: `failed to attach`, poi dopo reboot `DeadSystemException` di permissioncontroller/System UI. Arresto e riavvio cold dello stesso walktape_aosp senza snapshot; daemon Gradle fermati al termine delle build per liberare RAM. Non attribuiti questi crash del sistema ad Android all'app. Cleanup emulatore/env da confermare nel seguito.

### W07 — verifica conclusiva e cleanup

AVD dopo cold boot con override **temporanei** `-memory 2048 -cores 2 -no-snapshot -no-boot-anim` (config originale non modificata). Attesa esplicita sys.boot_completed e nuova precompilazione speed di app/test necessarie dopo avvii strumentazione falliti; poi runner operativo.

- **RealScrobbleRecoveryTest 1/1, 3,573 s**: evento injected in Room rappresenta il recupero, primo tentativo 503 fixture conservato, secondo al vero Navidrome marcato dopo ACK; getSong.playCount **+1**, terzo tentativo con sender nuovo senza aumento. Il conteggio di test resta nell'account dedicato (API non offre annullamento). Non è una prova di ascolto naturale: origine da ExoPlayer nel servizio testata separatamente con PCM.
- Prima coppia UI+restore dopo ultima guardia: restore passato, UI fallita su ordine shuffle atteso. Diagnosi: il test catturava la timeline provvisoria subito dopo toggle, prima dell'ACK/permutazione del servizio; la playlist aveva invece l'ordine effettivamente mostrato al salvataggio. Corretto **solo il punto di cattura nel test**, attendendo UUID normalizzati e nome persistito prima di confrontare ordine/copie al salvataggio; nessun allentamento del confronto esatto. Rerun UI **1/1, 152,373 s**, inclusa selezione multipla→playlist, editing, origine/duplicati, nome persistito, shuffle→playlist, dettagli/testo reale e guardie profilo. APK applicativo invariato dal build/lint verde, ricompilato/precompilato soltanto androidTest per questa correzione.
- Risultati finali: JVM 84/84 (ultima suite completa), build app/androidTest, lint 0/22; test storage/servizio e permessi fixture passati; UI/recovery reale/restore passati separatamente. Matrice regressioni 9/9 su esecuzioni separate, con i due fallimenti combinati conservati sopra. Non rivendicati TalkBack/hardware, testi sincronizzati sul server reale o rifiuto reale di playlist condivisa non disponibile.
- **Cleanup verificato**: `/data/local/tmp/walktape-test-server.env` rimosso, test di assenza con esito `env-removed`; nessun marker wt-seed/wt-seed-cleanup/wt-focus-test presente. Emulatore arrestato con `emu kill` riuscito. Override runtime non persistenti; nessuna impostazione display/font/animazione modificata in W07. Script/JSON temporanei probe eliminati, credenziali originali locali e checklist W05/W06 preservate. Checklist privata W07 pronta e ignorata. **SHA256 APK debug: 6713DD5BCC98E069D0963648CF1BA153F941472CFF592556EF1C483A26E5C40A**.

Punto di ripresa **W07-T06**: predisporre una playlist condivisa readonly di secondo account di test e verificare rifiuto reale update/delete (nessuna prova su playlist personali altrui). T01–T05 chiusi, T06 resta `[ ]`; nessun cambio di perimetro SPEC. Commit/push/release non eseguiti.

## 2026-10-15 — Riscontro W07 e pubblicazione richiesta

L'utente, dopo il percorso di collaudo e chiarimenti su singolo brano in coda/Cronologia ascolti, riferisce «bene, mi sembra tutto ok» e richiede esplicitamente commit e push. Registrato riscontro generale positivo; non dedotti esiti puntuali di ogni controllo telefono né del caso permessi condivisi T06, che resta aperto insieme ai residui W05/W06.

Ispezionati status, diff, log ultimi 10, nuovi file e remoto/tracking. Fetch origin riuscito: divergenza 0/0 su 40004d3. Modifiche intenzionali: W07 (codice/test/schema7/documenti) e precedente rimozione della skill deploy-an-app con sola voce lockfile corrispondente, già richiesta e documentata. Configurazioni di test, APK, checklist private e NEXT_PROMPT devono restare ignorati. Nessuna modifica applicativa richiesta per la pubblicazione: mantenute verifiche e relativi limiti del precedente incremento, nessuna nuova build necessaria.

Pubblicazione eseguita su richiesta:

- Commit **a07e905 — «Rimozione della skill deploy-an-app dal progetto Android»**: 15 file intenzionali, rimozione già autorizzata della skill e della sola voce lockfile; altre skill preservate.
- Commit **043e43c — «Wave 07: playlist, preferiti, testi e cronologia ascolti (T01-T05)»**: 61 file, 3.689 inserimenti/79 rimozioni, inclusi nuovi test e schema7. Stage esplicito, diff --check senza errori; controllo percorsi privati e pattern di chiavi/token senza riscontri. Credenziali/env, local.properties, APK, NEXT_PROMPT e tutte le checklist private confermati ignorati ed esclusi. Identità per-commit conforme ai precedenti, nessun cambio persistente Git config; hook non saltati.
- Push ordinario **40004d3..043e43c** su origin/main riuscito. SHA codice locale/remoto **043e43c8833eae6e565daba918dc22276c357243** identici, working tree pulita subito dopo. Nessuna release creata. Nessun codice cambiato per la pubblicazione, APK/test precedenti e relativi limiti invariati.
- PLAN/memoria/NEXT_PROMPT aggiornati all'esito; note tracciate dell'operazione nel successivo commit documentale. Punto di ripresa **W07-T06**, residui hardware W05/W06 ancora aperti. Riscontro generale utente positivo, non chiusura automatica della matrice manuale.

## 2026-10-15 — Handoff per tutta W08

Richiesta dell'utente: prompt per implementare la prossima wave in un'altra sessione. Letti stato Git, memoria, SPEC e W08 del piano. HEAD b18691b, codice W07 043e43c, working tree inizialmente pulita. Nessuna implementazione applicativa o nuova build in questo handoff documentale.

Sostituito il prompt locale NEXT_PROMPT.md con W08-T01–T07 completo: download persistenti, policy Wi-Fi, cache/contabilità, sorgenti locali e zero traffico in solo offline, origini multiple, playlist scaricate e conservazione singoli rimossi dal server, modifiche offline e conflitti senza perdita silenziosa, riconciliazione ACK ambigui e verifica isolamento/riavvio/spazio. Richiesto riuso dei layer esistenti, migrazioni da v7 e API stabili ufficiali, test reali distinti da fixture, APK/checklist privata W08 e cleanup. W07-T06 e hardware W05/W06 conservati aperti; prerequisito secondo account non blocca il lavoro indipendente W08. Memoria/piano aggiornati. Nessun nuovo commit/push/release autorizzato o eseguito.

## 2026-10-15 — Implementazione W08-T01–T06; W08-T07 parziale

Richiesta: implementare tutta W08 autonomamente, preservando handoff, dati e residui hardware. Letti memoria/SPEC/wave08/DESIGN/ARCHITECTURE e recenti voci; Git iniziale con sole note PLAN/PROJECT_MEMORY/WORKLOG già modificate, preservate. Base b18691b, codice W07 043e43c. Nessuna discovery/scaffold, backend, DSP W09, commit/push/release.

### File e implementazione

- **W08-T01/T02**: nuovi `core/db/DownloadEntities.kt`, `DownloadDao.kt`, `core/downloads/DownloadStore.kt`, `HttpResume.kt`, `DownloadEngine.kt`, `DownloadNetworkPolicy.kt`, `DownloadedPlaylistWorker.kt`; endpoint originale `downloadUrl` nel client. Una copia audio per profilo/songId, riferimenti single/album/playlist; UUID delle occorrenze e snapshot ordinato con duplicati distinti dal file fisico. Selezioni Brani/Album/Playlist, pulsanti dettaglio e foglio `DownloadsSheet`, stato/progresso reale, pausa/ripresa/rimozione. WorkManager 2.12.0 verificato sui metadati Google Maven; queue worker per profilo, input solo ID, credenziali Keystore rilette, vincoli rete/backoff e budget ordinario 8 minuti senza FGS dataSync. Job/rimozione profilo isolati, update-only per avanzamenti tardivi.
- Range: `.part` con sidecar ETag forte, If-Range e 206 coerente su offset/total/body length; 200 riparte, 416 invalida il parziale. Senza ETag forte riparte da zero. File validato con MediaExtractor prima di completamento/catalogo; envelope JSON e audio invalido rifiutati. Byte aggiornati anche dopo pausa/errore. Completion+catalogo nella stessa transazione nel Graph; late transfer dopo cancellazione profilo elimina il parziale e non registra local_audio.
- **W08-T03/T04**: `AudioCacheManager`, `ProtectedCacheEvictor`, `LocalFirstDataSource`, `ProfileNetworkGate`, `NetworkTraffic`; servizio e bridge esistenti riusati. Cache audio 1 GiB con LRU dinamica/lease (chiave aperta protetta), download espliciti fuori dalla cache; copertine 250 MiB riusate con trim/clear tollerante alle letture concorrenti. Limiti e byte reali su disco in Settings. Priorità locale→cache→rete, URI/profili controllati prima del cache hit. Gate solo offline fail-closed, attesa iniziale della policy, cancellazione chiamate HTTP e blocco di API/upstream/worker; indice/cover/testi/suggerimenti/verify/scrobble protetti. Skip configurabile e notice, ascolti pendenti conservati, zero autoplay.
- **W08-T05**: `download_group` registra sottoscrizioni anche vuote e JSON degli ID nell'ordine completo, incluse ripetizioni. Refresh automatico Wi-Fi (6h e post-indice) e manuale, origini indipendenti: le rimozioni di una playlist non toccano audio necessario ad altre origini. Snapshot e audio conservati con segnalazione se la playlist sparisce; metadati del singolo separati dall'indice e flag serverMissing dopo refresh. Gruppi riproducibili nello stesso bridge.
- **W08-T06**: `sync/OfflineOps.kt`, `OfflineSyncWorker.kt`, estensioni PlaylistRepository/StarRepository e `OfflineEditsSheet`. Scrittura locale+operazione atomica, serializzazione con drain/risoluzioni, pending/rifiuto/conflitto visibili. Create/rename/delete/aggiunta/rimozione/riordino e cuori offline. Strutturali con lista completa/baseline; create con lista ID server pre-invio e marker possibly-sent durevoli. Risposta persa: adozione solo di nuovo ID univoco e contenuto coerente; se mancano prove, conflitto conservando la versione locale, mai reinvio cieco. Scelta locale/server/nuova playlist riguarda l'intera versione locale; non resta una rinomina ritardata sull'originale. Owner/readonly/permessi rifiutati non diventano successi. Refresh indice evita di sostituire preferiti pendenti; PlaylistIndex protegge modifiche e tombstone locali. Resta il limite API senza CAS tra preflight e mutazione.
- Room **v10** e schemi **8/9/10**. 7→8: download/origini/operazioni/impostazioni/flag playlist; 8→9: serverMissing; 9→10: gruppi. All'inizio una modifica del v8 già installato sull'AVD ha fatto emergere hash incompatibile: corretto con migrazioni versionate, preservando DB e schemi intermedi, senza clear/reset. Coda/profili/libreria/testi/ascolti conservati.
- DESIGN/ARCHITECTURE/PLAN/memoria/NEXT_PROMPT aggiornati; checklist privata nuova `local/PHONE_TESTS_W08.md`. Originali W05/W06/W07 presenti e intatti; SPEC e wave09 non ridimensionate.

### Verifiche e difetti corretti

- Comando build finale: `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -PrealServerTest=true "-Dkotlin.compiler.execution.strategy=in-process" --console=plain`, JAVA_HOME al JBR Android Studio. Build app/test riuscite, **110/110 JVM** (realServerTest e RealWave07ApiTest inclusi, non saltati), lint **0 errori/28 avvisi**. Prime esecuzioni senza opt-in avevano 2 skip reali: non conteggiati come API test passati.
- Nuovi JVM: DownloadStore (origini, dedup file, riconciliazione, file reali temp), HttpResume (range errati/lunghezze/ETag), PlaylistOffline, OfflineNetworkGate (zero chiamate per API/cover/testi/mutazioni/scrobble), OfflineOps (ACK perso, baseline, conflitti, rifiuti, recupero SYNCING e scelta riferita a tutta la versione). Inizialmente 9 fallimenti: corretti difetti e fixture (fra cui baseline catturata dopo la modifica, fake CASCADE e aspettative sbagliate sui file rimasti nella playlist); nessuna suite iniziale dichiarata verde.
- Difetto importante corretto: `REPLACE` sul download avrebbe cascato le origini; ora Upsert per creazione/richiesta e update condizionali per progresso/completion. Corretti anche doppio conteggio dell'offset Range, collisioni filename sanitizzati, refresh che sovrascriveva playlist pendenti, stato di creazione non idempotente e contenuto locale da conservare in risoluzione.
- Android `walktape_aosp`, headless, override runtime 2048 MiB/2 core, APK app/test precompilati con `pm compile -m speed -f`. **Suite mirata finale 21/21, 48,054 s**, comprende Wave08Storage/Cache/RealDownload/RealSync/Worker/OfflineService + Wave07Storage/QueueStorage/CoverCache. Include budget spazio impossibile prima della rete (fixture, non filesystem pieno), JSON/audio corrotto rifiutati e profilo eliminato durante trasferimento senza parziali/catalogo resuscitati.
- Server reale: originale Navidrome scaricato, dimensione originale e hash SHA-256 identico nella rilettura locale con upstream bloccato. Sync reale su playlist dal nome UUID dedicato dell'account test: create offline+rename, A/B/A, modifiche locali aggregate, modifica concorrente server, conflitto senza sovrascrittura e scelta locale riscontrata dal server; playlist di prova eliminata nel finally. Nessuna cancellazione di file musicali sul server. Preferiti offline/ACK ambigui aggiuntivi verificati con fixture, non presentati come tutti casi reali.
- Worker effettivo WorkManager con credenziali rilette dal vault: pausa→resume→file PCM WAV completo, origini conservate. Primo tentativo fallito perché la fixture inventava un'origine playlist inesistente e il nuovo worker periodico consumava la risposta: corretto il test usando origine album, senza cambiare il motore.
- Servizio effettivo PCM fixture: copia locale riprodotta, traccia non disponibile saltata, notice, ascolto maturato nel tracker del servizio e rimasto pending; cover/testi/indice/verify/sender esercitati con contatore HTTP fermo in solo offline. Il primo tentativo controllava l'evento troppo presto rispetto al tick/commit: corretto attendendo il pending durevole prima del seek. **2/2 worker+servizio poi passati**, inclusi nella suite finale.
- `Wave08RestartTest`: seed e verify separati con marker solo ID/size. Download originale ripreso dopo **force-stop e nuovo processo (1+1 passati, verify 23,836 s)**, poi dopo **reboot effettivo dell'AVD (1+1 passati, verify 62,747 s)**. Queste prove precedono gli ultimi affinamenti di gate/cleanup, che non cambiano lo schema del job; non sono reboot hardware.
- Regressioni W05/W06/W07: prima combinata 11 passati+2 skip opt-in (274,573 s). Una ripetizione successiva: **10 passati, 1 fallito, 2 skip (313,003 s)** per richiesta fixture streaming partita prima della policy app caricata. Trovata race fail-closed iniziale; aggiunto `awaitReady` in servizio/worker e setup del test DataSource. Non attribuita ad ANR di sistema.
- **Ultima combinata sul medesimo APK finale: 34 scoperti, 32 passati, 2 skip opt-in, zero falliti, 333,652 s**. Comprende le 21 prove mirate sopra e DataSource, restore senza HTTP/autoplay, timer, servizio ascolti, UI playlist server reale e player TPS-L2. I 2 skip sono il seed/verify W05 da invocare separatamente: non sono 34 test passati. La suite storica W07 7/9+2 isolate resta un'esecuzione distinta.

### Stato e controlli mancanti

T01–T06 segnati verificati nei percorsi descritti; **T07 resta aperto**. Nessuna nuova prova Motorola: mancano handoff reale Wi-Fi/mobile e perdita rete durante download/ascolto/sync sul dispositivo, saturazione fisica durante scrittura, UI/adattività/TalkBack W08 e stress prolungato concorrenza. Rimozione durante trasferimento già verificata con fixture; matrice multiprofilo/rete hardware ancora incompleta. Restano da stressare file lunghi su rete lenta senza ETag e la finestra API priva di CAS. W07-T06 richiede ancora secondo account/playlist readonly reale; riscontri hardware W05/W06 conservati, nessuna deduzione dal generico «bene, tutto ok» precedente.

Pulizia eseguita: env copiato su AVD rimosso e `test -e` restituisce assente; marker W08 restart e W05 processo assenti, originali `local/test-server.env` e checklist W05/W06/W07 preservati. SHA256 APK finale **97B2C3F8532EEF8AD792EB962C63CDF79DC3957485C3272514BF9995A50BBF7E**. **AVD spento con `emu kill`; dopo attesa, `adb devices` vuoto**. `git check-ignore` conferma env, checklist W05/W06/W07/W08 e NEXT_PROMPT ignorati. Nessun commit/push/release.

## 2026-10-15 — Pubblicazione W08 su richiesta dell'utente

Richiesta esplicita: «fai commit e push». Controllati status, diff, nuovi file, ultimi dieci commit, remote/tracking e aggiornamento origin tramite fetch. Tutte le modifiche presenti appartenevano all'incremento W08 e alle note di handoff già preservate; nessun nuovo cambio applicativo in questa pubblicazione.

- Stage limitato a sorgenti/test Android e JVM, schemi Room 8/9/10, file di build/versioni e documenti DESIGN/PLAN/PROJECT_MEMORY/WORKLOG/ARCHITECTURE. `git diff --cached --check` senza errori; controllo percorsi privati/artefatti e pattern credenziali senza riscontri. Ignore di env, checklist W05/W06/W07/W08, NEXT_PROMPT e APK verificati.
- Commit **b90b9f3 — «Wave 08: download, cache e sincronizzazione offline (T01-T06)»**: **71 file, 10.086 inserimenti/89 rimozioni**. Identità per-commit conforme al repository (`Rosario Moscato`, indirizzo GitHub noreply già usato); nessuna modifica persistente alla configurazione Git e nessun hook saltato.
- Push ordinario **b18691b..b90b9f3** su origin/main riuscito. SHA codice locale/remoto **b90b9f33edf90ed9abfdd875ffb9ecba3e9f64b3** verificati identici con ls-remote; working tree pulita subito dopo. Note della pubblicazione nel presente aggiornamento documentale.
- Verifiche applicative e SHA APK restano quelli dell'incremento precedente: nessuna nuova build necessaria per la sola pubblicazione. T07 e residui W07/W05/W06 invariati; nessuna release o pubblicazione dell'APK. File privati locali preservati. PLAN/memoria/NEXT_PROMPT aggiornati; nuovi commit/push successivi solo su nuova richiesta.

## 2026-10-15 — Handoff per tutta W09

Richiesta dell'utente: «molto bene scrivimi prompt per implementare wave 09 in nuova sessione». Letti memoria, SPEC (riproduzione/qualità/offline), intera wave09 del piano, ricerca audio ARCHITECTURE e ultime voci del log; verificati status pulito e HEAD pubblicata **0902d82**, codice W08 **b90b9f3**. Riscontro positivo generale registrato senza dedurre esiti hardware puntuali.

Sostituito il prompt locale NEXT_PROMPT.md con l'handoff completo **W09-T01–T06**: qualità per rete/profilo e download opzionalmente ridotti; rappresentazioni/cache/Range separate; originali di default e fallback transcoding onesto; gapless/crossfade reale nello stesso motore logico; ReplayGain/tag/headroom, EQ/bassi, effetto cassetta, tutti inizialmente spenti. Vincoli per timer/focus/seek/shuffle/UUID/restore, zero rete anche nel preload offline, niente scrobble da preload né doppi eventi dai deck. Richiesti API stabili ufficiali, migrazioni da v10, misure del segnale e prove reali/fixture/hardware distinte, regressioni, APK e checklist privata W09 con cleanup. Proposte audio W01 da verificare, nessuna funzione sostituita o rimossa dal perimetro.

PLAN e PROJECT_MEMORY aggiornati alla ripresa W09-T01. W08-T07, W07-T06 e residui hardware W05/W06 conservati; checklist private precedenti da preservare. Nessun codice W09, build o collaudo nuovo in questa attività documentale. Nessun commit/push/release richiesto o eseguito; nuove note locali non committate. Controllo proporzionato: diff documentale e whitespace, presenza/ignore del prompt.

## 2026-10-15 — W09 implementazione locale e verifiche in corso

Task W09-T01–T06. Stato iniziale: HEAD 0902d82, note PLAN/MEMORY/WORKLOG già modificate preservate; nessun commit/push autorizzato. Letti handoff, specifica, wave09, architettura e DESIGN. Stack invariato. API audio ricontrollate sugli artefatti 1.11.1 e sorgenti ufficiali AndroidX (AudioOutput, BaseAudioProcessor); endpoint stream dalla documentazione OpenSubsonic.

- Room v11, migrazione esplicita 10→11 e schema11: qualità per profilo e rappresentazione/bitrate richiesto nei download. Test Android conservazione coda, profilo, download/origini e default originali **passato**. Nessun reset database.
- Qualità originale di default, Opus esplicito a bitrate ridotto per rete a consumo; nuovi download opzionalmente ridotti via stream, non download.view. Verifica iniziale host reale: maxBitRate96 → audio/ogg, chunked, niente ETag, Range ignorato (200); format=raw → originale. Selezione congelata per DataSource; chiavi originali/ridotte distinte. **Transcode non frammentato nella cache** per evitare concatenazione di byte rigenerati senza validatore: originali restano cachati, copie ridotte complete disponibili nei download. Ridotti mai ripresi con Range; parziali e finali distinti per codec/bitrate. Validazione sul parziale prima del rename, fatti MIME dal MediaExtractor, fallback originale riconosciuto, file completi/origini preesistenti conservati al cambio preferenza.
- DSP locale: processori PCM ReplayGain e cassetta nel DefaultAudioSink; parser TXXX/Vorbis, picchi/headroom/clamp, bypass byte-exact con effetti off, attivazione runtime. EQ/BassBoost reali via audiofx e sessionId condiviso; stato controllo negato/route indisponibile esplicito. Preferenze DataStore e UI IT/EN. Crossfade deck ausiliario nel servizio con stessa sorgente/gate, nessuna sessione/focus/coda/tracker secondari; guardie pausa/seek/timer/profilo e priorità locale. Ancora da consolidare il handover al player principale (seek), attribuzione tempo overlap e combinazioni complete: nessuna chiusura anticipata di T02/T06.
- JVM prima serie **128/128, zero skip/failure** (realServerTest). I primi test DSP avevano errori: parsing dB case-sensitive, flush richiesto prima del queueInput, flip output mancante, scala del rumore e drain test che poteva essere vacuo. Corretti; suite DSP 12/12 passata prima degli ultimi affinamenti. Successivamente rafforzato il test -6dB con conteggio esatto dei campioni e corretto bypass runtime/float: riesecuzione finale da fare.
- Build app e androidTest riuscite più volte con JAVA_HOME JBR e strategia in-process. Prima compilazione androidTest fallita per firma AudioOutput.write (booleano, buffer da avanzare), lambda finale clientProvider e predicate suspend: corretti contro API 1.11.1. Lint in una combinata ha superato timeout host 600s: esito finale non attestato.
- Android AOSP walktape_aosp, 2048MiB/2core runtime, app/test installati e **precompilati speed**. Prima coppia migrazione+qualità: **1 passato/1 fallito** (monitor qualità non inizializzato prima dei tracks), corretto. Prima suite engine **1 passato/4 falliti**: buffer vuoto nel bypass provocava vera IllegalArgumentException e blocco del renderer; corretto con guardia hasRemaining. Rerun engine+qualità **4 passati/2 falliti**: timestamp TIME_UNSET usato erroneamente come gap e capture non resettato alla ricreazione output. Corretto il test gapless a confronto PCM al seam; reset output della capture corretto.
- Combinata successiva 8 casi: **6 passati/2 falliti** (Opus catturato erroneamente come formato passthrough e crossfade capture bloccata nel handover). Corretto FormatSupport: solo audio/raw diretto, altri formati devono passare dal decoder MediaCodec; reset a ogni getAudioOutput. Rerun due casi: **W09RealQualityTest passato, crossfade fallito** sul confronto finestre non allineate (somma0,1626 vs corpo0,2589). I ramp A/B e il ritorno su item2 avanzato erano verificati prima del failure, ma non basta per chiamare completato il crossfade. Test ora allinea le finestre PCM per playout previsto, da rieseguire.
- Prova reale passata: MP3 e FLAC della libreria Navidrome→Opus96, HTTP200 e Range ignorato, download completo/validazione, decoder Android→PCM non nullo, cambio qualità senza sostituzione file/origini condivise. Nessun ascolto/scrobble o mutazione server prodotto da questo test. Audiofx test può riportare indisponibilità onesta: non è misura spettrale EQ sul telefono.
- Fixture codec: generati MP3/LAME e FLAC sintetici (tono440Hz,2s,44.1kHz, tag ReplayGain) con FFmpeg6.1.1 già nel container, directory /tmp dedicata rimossa dal finally. Script `tools/Generate-W09AudioFixtures.ps1`, output locale ignorato e copie AVD per test opt-in. `Wave09CodecGaplessTest` e `Wave09ServiceTest` aggiunti, non ancora eseguiti. Nessuna musica utente modificata, nessun software globale installato.

Stato incremento: in corso. Env/fixture temporanei AVD da rimuovere alla fine, AVD acceso; checklist W09/cleanup e documenti finali da completare. Residui W05–W08 e tutte le checklist private precedenti conservati.

### W09 — esiti finali, limiti residui e consegna locale

- Ulteriori correzioni T01: target Opus esplicito invece di formato server implicito; rappresentazione congelata per sorgente, file/parziali nominati per codec/bitrate; transcode esclusi dalla cache a frammenti (non esiste una prova sicura di identità senza validator). Factory HTTP con fallback raw su errore di formato/protocollo **solo a offset0**, niente switch codec a metà byte stream; MIME incoerente nei retry rifiutato. URI esposta a decoder/cache rimane logica senza token. Etichetta qualità pubblicata solo dai tracks del principale, non dalla preparazione del deck secondario; copie locali distinte da streaming. Wi-Fi resta originale anche se a consumo, opzione ridotta applicata al trasporto cellulare. Corretto anche il salvataggio dell'ultimo livello EQ al termine del drag e aggiunte etichette accessibili agli slider/durata0,5s corretta.
- API DSP in opt-in esplicito, onFlush(StreamMetadata), formati16bit/float coerenti, picchi lineari>1 e gain non finiti gestiti. I 60 errori del primo lint (UnsafeOptInUsageError nelle tre classi DSP) sono stati corretti senza baseline/soppressione della verifica. Ultima build/lint completa sul codice applicativo finale: `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -PrealServerTest=true "-Dkotlin.compiler.execution.strategy=in-process" --max-workers=2 --console=plain`, JAVA_HOME JBR. **129/129 JVM**, 0skip/failure/error; **lint0errori/30avvisi**; APK app/test prodotti. Il tool shell ha avuto anche ritorni Unknown:ChildProcess.kill all'avvio di processi: stato Gradle successivamente IDLE e ripetizione del comando BUILD SUCCESSFUL con task up-to-date verificata, non risultato dedotto solo dall'errore del tool.
- Codec gapless: test iniziale FLAC confrontava il cold-start ramp con l'inizio non rampato del secondo elemento; MP3 assumeva che anche il padding finale a fine coda fosse scartato. Misure rilevate: **FLAC88200/176400 frame; MP388751/176951 frame** per uno/due file di2s@44.1kHz. Il secondo elemento aggiunge esattamente88200 frame: il padding finale551 non viene inserito al seam. Corretto il test a confronto waveform corrispondente dopo il ramp, deltaframe esatto e tag ReplayGain realmente estratti. **Entrambi passati**, nessun taglio arbitrario di551 sample nel codice app.
- Crossfade capture: corretta semantica AudioOutput (write booleano, consumo buffer, formato PCM vs formato codificato, reset alla ricreazione); allineate finestre per playout e scartati dalla misura i buffer scritti ma non riprodotti dopo release. La combinata41casi aveva ancora un failure somma0,3250 vs corpo0,2589 (includeva la coda accodata del deck rilasciato). Dopo correzione **test crossfade passato** anche nella suite W09 finale. Dimostra due ramp reali/overlap e prosecuzione item entrante, **non elimina il limite architetturale del handover via seek**.
- Android combinata regressioni: **41eseguiti,39passati/2falliti,537,379s**. Failure crossfade sopra e controllo UI W05 non trovato con rootPackage=android. Dump successivo mostrava **Quickstep isn't responding**, non ANR WalkTape. Atteso/ripristinato AVD; prima ripetizione W05 con nodo diventato non cliccabile durante ricomposizione: corretto helper test a lookup/action rinnovati e ACTION_SHOW_ON_SCREEN, mantenendo tutte le verifiche effettive sul controller. Non indebolita la semantica musicale dell'app.
- Esecuzione W09+focus/worker/sync sull'APK intermedio: **13W09 passati**, poi AVD scomparso da ADB durante focus; risultato interrotto, **non16/16**. Gradle daemon fermati, coldboot dello stesso walktape_aosp, nessun wipe/config globale. Test app/test precompilati speed di nuovo. Coppia W05+crossfade precedente:1passato/1fallito; dopo coldboot/prefs/UI W05/W06: **4passati/1fallito (344,992s)**, solo W07UI in timeout40s. Rerun **W07UI+W08Worker+W08RealSync3/3,93,21s**: playlist reali test pulite nel finally, worker persistente e conflitti conservati/risolti. Successivo **FocusInterruptions+QueueRestore2passati/2skip opt-in,82,692s**: ducking al sink, focus/noisy/media keys in AVD (stesso UID, non chiamata reale) e restore profilo/servizio zeroHTTP/autoplay. I due protocolli seed/verify separati restano skip, non test passati. Non si rivendica una combinata unica W05–W09 tutta verde.
- **Suite W09 finale sull'APK definitivo:14/14,111,68s**, zero skip/failure: `Wave09CodecGaplessTest`2, `Wave09ServiceTest`1, `Wave09AudioEngineTest`6, `Wave09StorageTest`2, `Wave09QualityStreamTest`2, `Wave09RealQualityTest`1. Comprende timer durante crossfade+cassetta+ReplayGain(no tag→neutro) nel vero servizio, nessuno scrobble aggiuntivo da preload, pause/seek senza autoplay; gain+tape con headroom e bypass runtime misurati sul PCM; persistenza di tutte le preferenze DSP; migrazione; fallback501→raw e rifiuto cambio codec con offset; MP3+FLAC reali Navidrome→Opus96 scaricati, validati e decodificati. Test unit aggiuntivo misura davvero la variazione dei periodi del tono per wow/flutter (non solo media dell'ampiezza).
- Audiofx AVD effettivamente **Ready**, 5bande [60,230,910,3600,14000]Hz, range[-1500,1500]mB; setband500mB, enable e BassBoost400 con lettura di ritorno, rilascioIdle verificati. Preferenze persistenti testate tramite editDataStore e repository ricreato, poi ripristinate. È controllo del motore reale/sessione, **non misura della risposta spettrale o clipping dopo audiofx su route fisica**.
- **Stato task**: T01/T03/T04/T05 marcati verificati nei rispettivi criteri funzionali. **T02 parziale**: gapless verificato e crossfade operativo, ma completeSwap usa seek/rebuffering del principale e il tempo iniziale entrante realmente udibile durante overlap non è ancora attribuito al tracker durevole; completare architettura/attribuzione e test della continuità del mix. **T06 parziale**: restano questi casi, matrice completa combinata e collaudo Motorola/Bluetooth/risposta audiofx/clipping/batteria. Nessuna nuova prova hardware, nessun requisito rimosso da SPEC; residui W05–W08 invariati.
- Consegna: APK debug standard SHA256 **1573D341579EF5634355DC388E6B9E05D856828DFB848BA38CCEFC9C3D205F06**, `local/PHONE_TESTS_W09.md` pronta (checklist precedenti intatte). PLAN/MEMORY/NEXT_PROMPT/DESIGN/ARCHITECTURE aggiornati. Copie AVD env, fixtureMP3/FLAC e dumpUI noti rimosse; shell ha confermato **w09-temporary-files-absent**. `emu kill` riuscito, verifica adb vuoto nel controllo conclusivo. Originale `local/test-server.env` e tutte le checklist ignorati/preservati; fixture locali sintetiche conservate come output riproducibile ignorato. Nessun commit/push/release.

Prossimo intervento: **W09-T02** motore/handover continuo e attribuzione overlap, quindi chiusura matrice T06; non passare a W10 assumendo W09 completa.

## 2026-10-16 — Pubblicazione W09 su richiesta esplicita

Richiesta dell'utente: «fai commit e push». Ispezionati status, diff completo, nuovi file, ultimi10commit, remoto e tracking; fetch origin riuscito, divergenza iniziale0/0 su0902d82. Stage limitato ai43file intenzionali W09 (codice/test/schema11/UI/documenti/script fixture). Diff staged senza errori di whitespace; percorsi privati/artefatti e pattern di chiavi/token controllati senza riscontri. Env originale, NEXT_PROMPT, APK e checklist W05–W09 ignorati ed esclusi. Nessuna nuova modifica applicativa o build per la sola pubblicazione.

- Commit **c1cddd9 — «Wave 09: qualità e DSP, crossfade parziale (T01/T03-T05)»**:43file,4907inserimenti/83rimozioni; identità per-commit conforme al repository, nessuna configurazione Git persistente modificata o hook saltato.
- Push ordinario **0902d82..c1cddd9** su origin/main riuscito; SHA locale/remoto **c1cddd98effa7131eb38b7378a171de84fef9f35** identici, working tree pulita subito dopo. Note della pubblicazione nel successivo commit documentale.
- T02/T06 restano parziali; criteri e limiti tecnici/hardware invariati. Test e hash APK dell'incremento precedente conservati; nessuna release o pubblicazione dell'APK. PLAN/MEMORY/NEXT_PROMPT aggiornati; nuovi commit/push dopo questa pubblicazione soltanto su nuova richiesta.

## 2026-10-16 — Completamento W09-T02 e parte automatica T06

Autorizzazione utente «autorizzo, procedi», dopo proposta esplicita di consolidare il crossfade/tempo overlap e verificare le combinazioni prima del collaudo Motorola. Letti AGENTS/MEMORY/NEXT_PROMPT integrale/SPEC/wave09/ARCHITECTURE/voci recenti; Git iniziale pulito su82f0657 (codicec1cddd9). Nessuna discovery/scaffold o nuova richiesta commit/push. UI/identità TPS-L2 invariata, nessuna riduzione del perimetro; residui W05–W08 conservati.

### Motore e persistenza

- Riscritto `playback/CrossfadeCoordinator.kt`: un ExoPlayer logico stabile instrada la API **pubblica** ai deck e possiede i listener. Handover tramite promozione del deck già in riproduzione, senza seek/prepare/setMediaItems/riapertura sorgente o reset output sul successore. Coda canonica e permutazione nativa complete installate nel preload con UUID esatti; restano gli stessi MediaSession, bridge, ServiceShuffle e PlaybackPersistence. Eventi sintetici AUTO/timeline/metadati/tracks hanno la stessa identità logica e vengono emessi una volta, gli eventi del deck rilasciato sono filtrati. Comandi manuali/mutazioni invalidano il successore prima di applicarsi, evitando promozioni stale. Repeat-one/fine coda senza successore restano nativi; parametri playback/skipSilence copiati nel preload.
- `LogicalAudioFocus.kt`: unico AudioFocusManager ufficiale1.11.1, su HandlerThread come indicato dalla API; comandi al player sul main, focus automatico dei deck disabilitato. Volume logico separato dagli inviluppi/ducking, STREAM_MUSIC intatto; perdita transitoria congela entrambi, pausa manuale prevale sul regain, noisy/perdita permanente fermano l'intenzione. ReplayGain/cassetta per ciascun deck e audiofx su sessionId condiviso preservati; aggiornamenti preferenze applicati a tutti i deck vivi.
- `ScrobbleTracker`/`ListeningStore`/`ScrobbleDao`: clock entrante solo per isPlaying+inviluppo nonzero; nessuno stato/evento dal preload silenzioso. Scrittore FIFO con barriere load, salvataggio stato+eventi in transazione e promozione dello stesso eventId/flag emitted. Tempo precedente alla promozione incluso, pausa e seek non accreditano tempo saltato; repeat distinto. Prefisso già udibile mantenuto anche con edit/rimozione corrente che seleziona lo stesso successore; un altro passaggio lo sostituisce correttamente. ACK conservati dopo ricreazione, nessun doppio evento già confermato; resta il limite della risposta persa Navidrome.
- **Room v12**: `OverlapListeningStateEntity`, tabella overlap_listening_state, migrazione11→12 e schema12 esportato, schemi1–11 intatti. Ricevuta promoted/outgoingSnapshot conserva entrambi i passaggi se la promozione listening precede il salvataggio QueueStore; viene aggiornata dai salvataggi successivi del passaggio primario e protegge gli ACK. Test di riapertura Room, replay della finestra di crash tramite fixture, conservazione profili/qualità/code/download/origini e cascade profilo passati; nessun reset distruttivo. Regole R8 preliminari per interfacce pubbliche instradate e campi della ricevuta JSON (minify/release restano W12, non eseguiti qui).
- `SleepTimer.allowsOverlap(indiceUscente)` ora query pura. Durante un callback nativo AUTO il cursor fisico può già essere sul brano entrante: usare quello per aggiornare lastBoundary causava una pausa album anticipata. Controllo vincolato al vecchio indice canonico e distinto dal watcher; TRACK/ALBUM/QUEUE non fanno partire un deck oltre il confine. DURATION compone il proprio gain con ducking/crossfade, e tutti i deck si fermano a scadenza.
- Preload anticipato con margine10s anziché3, sufficiente anche al caso transcode non ancora in cache del server osservato nel test reale. Sempre silenzioso, same gate/local-first; fade effettivo limitato per brani brevi. Se non è pronto, resta la transizione nativa senza loop di richieste o player residui. Durata musicale di metadata come fallback per stream Opus non seekable con durata container ignota. Nessun nuovo backend/SaaS/servizio/account o dipendenza audio introdotti.

### Verifiche e correzioni intermedie

- Prime build app/androidTest compilate; una prima combinata Gradle ha superato timeout300s e una ripetizione ha restituito Unknown:ChildProcess.kill. Eseguita successiva build esplicita con BUILD SUCCESSFUL/UP-TO-DATE per confermare gli artefatti, non dedotto successo dal tool fallito. Corretto anche un unsafe-call nullable in tracker durante l'ultimo affinamento di recovery.
- Nuovi test `Wave09HandoverTest`, `Wave09OverlapStorageTest`, `Wave09OfflineHandoverTest`, `Wave09RealHandoverTest`; estesi engine/capture e test precedente qualità reale. Prima selezione **6/6,50,745s** (handover/clock/ACK/migrazione/timer servizio). Successiva **14/14,106,231s**, inclusi sorgenti reali e zeroHTTP offline. Sono fixture/capture/emulatore e server nei casi espressamente indicati, mai ascolto hardware.
- Prima combinata25: **23passati/2falliti,175,058s**. Test reale handover pretendeva posizione entrante fissa>1500 invece della conservazione del clock; corretto a posizione monotona prima/dopo, output singolo e nessuna riapertura della sorgente. Test qualità pretendeva HTTP200 su ogni transcode: rilevato **206 coerente su transcode completo nella cache del server**; uncached200 ignorato Range continua a esistere. Test ora valida entrambi e MIME/Content-Range; policy locale conservativa invariata. Rerun2/2 confermato dai log TestRunner (il tool host aveva restituito Unknown nonostante il processo terminato con0failure).
- Aggiunta conservazione del prefisso durante edit/rimozione corrente, con test dedicato del medesimo eventId/tempo. Combinata27 successiva: **25passati/2falliti,265,035s**. Failure su timer album (atteso cursor2, trovava1) ha individuato la race reale lastBoundary sopra; failure real incoming audible ha individuato il preload troppo breve per un transcode non pronto. Corrette entrambe **nel motore**, non risolte aumentando solo timeout/allargando soglie. Test mirati timer+server reale successivi2/2 (log TestRunner), quindi nuova combinata finale verde sotto.
- Capture test: allineamento dei buffer preparati ma non ancora riprodotti al momento PLAY, non al momento WRITE; distinzione buffer rilasciati/non riprodotti. Prova di handover verifica identità del player logico/deck entrante e **un solo output** mantenuto, sorgente non riaperta, clock che prosegue. PCM virtuale/capture non è misura fisica del mixer o risposta dopo audiofx.

### Esito conclusivo

- Build finale PowerShell/JAVA_HOME JBR: `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug -PrealServerTest=true "-Dkotlin.compiler.execution.strategy=in-process" --max-workers=2 --console=plain`, **BUILD SUCCESSFUL (6m15s ultima esecuzione)**; **129/129 JVM**, zero skip/failure/error; lint **0errori/29avvisi**. Nessuna baseline lint o hook saltati. AVD walktape_aosp, override runtime2048MiB/2core, APK app/test precompilati `pm compile -m speed -f` prima di instrument, daemon Gradle fermati prima delle prove pesanti.
- **Suite finale27/27 Android,276,661s**, zero skip/failure:26W09 più ScrobbleService. Include timer track/album/queue/duration, repeat-all al confine/repeat-one, shuffle/duplicati/skip/rimozioni, perdita focus simulata sul motore/pausa manuale/regain, DSP+timer+ducking con volume Android invariato/headroom PCM; clock udibile/ACK/receipts/ricreazione/edit, migrazioni, offline con tutti gli effetti+audiofx richiesti, indisponibile saltato e restore senza autoplay/HTTP.
- Server reale letto senza scrobble o mutazioni musicali dal test handover: **MP3→FLAC, FLAC→MP3, originale→Opus96** con stesso deck/sorgente/output entrante prima e dopo la promozione. Download originali/ridotti e conservazione origini della suite precedente ripetuti. I file codec sintetici MP3/LAME/FLAC esistenti sono stati riusati senza modificare musica/configurazione server.
- Regressioni aggiuntive sul nuovo player logico: **27/27 storage/cache/download/worker/sync/lyrics/suggerimenti,66,222s**; **3/3 UI playerW06+playlist/serverW07,160,139s**; **7passati+2skip opt-in focus/restore/timer/scrobble/servizioW05,365,513s**. I due protocolli seed/verify di processo separato non sono invocati, non sono9test passati. Focus/ducking/noisy/media key reali del frameworkAOSP con concorrente stessoUID, non telefonata/altra app fisica. Regioni mutate server solo playlist test dedicate con cleanup dei test esistenti. Non dichiarata una suite unica che sommi esecuzioni separate o versioni intermedie.
- **T02 [x]**. T06 parte automatica verificata; checkbox **T06 [ ]** per Motorola/Bluetooth, cambi route, risposta/clipping dopo audiofx, batteria e ascolto/interruzioni fisiche. Nessuna nuova attestazione hardware o chiusura automatica di W05–W08. No AndroidAutoW10 iniziata, nessun requisito SPEC sostituito/rimosso.
- APK debug standard SHA256 **CE703DD677FB758DB104379B055B29BB0414D0263D5234123B24C5CF7C2CBE1B**. PLAN/MEMORY/NEXT_PROMPT/ARCHITECTURE e checklist privata `local/PHONE_TESTS_W09.md` aggiornati; tutte le precedenti preservate. Copie env/fixture AVD rimosse, conferma **w09-overlap-temporary-files-absent**, `emu kill` riuscito e adb vuoto nel controllo finale. Originale `local/test-server.env` intatto e ignorato; nessun file privato pubblicato. Nessun commit/push/release in questo incremento.

Prossima azione: **W09-T06 collaudo Motorola**, raccogliere caselle della checklist W09 e residui precedenti; modificare codice solo se emerge un difetto riproducibile. Non considerare il capture o il framework AOSP prova hardware.

## 2026-10-16 — Riscontro generale positivo sul primo giro W09

Task W09-T06. L'utente risponde **«Tutto ok»** alle indicazioni per il primo giro breve (suono pulito, crossfade, comandi, effetti e offline). Registrato riscontro generale positivo; non dedotti versioneAndroid/route, esiti puntuali dei singoli passi, misure o prove prolungate non descritte. Restano da raccogliere i controlli hardware specifici: timer/schermo spento, interruzioni e pausa manuale, Bluetooth/cuffie/cambio route, risposta/clipping dopo audiofx, batteria/accessibilità. T06 e residui W05–W08 non chiusi automaticamente.

Aggiornati PLAN, memoria corrente, NEXT_PROMPT e nota della checklist privata, senza modificare caselle hardware. Nella memoria corretta anche la precedenza dello stato corrente: il blocco vecchio T02-parziale/schema11 era rimasto davanti allo stato consolidato; ora è esplicitamente storico e prevalgono T02 completato/Roomv12 e verifiche già registrate. Git mostra le modifiche applicative locali preesistenti, tutte preservate. Solo documentazione: nessuna build/test/emulatore o nuova implementazione necessaria, nessun commit/push/release o avvio W10 autorizzato dal riscontro.

## 2026-10-16 — Collaudi hardware differiti e pubblicazione del consolidamento richiesta

L'utente chiarisce «Tutto ok riguarda primo giro» e rimanda le prove residue perché ora non può. Conservati T06 e residui W05–W08 aperti. Proposto l'ordine commit/push W09, poi sviluppo indipendente W10 e collaudo congiunto successivo; l'utente risponde «bene, fai commit e push». Questa attività esegue la pubblicazione richiesta, senza avviare W10.

Preflight: status/diff/nuovi file e ultimi10commit ispezionati; origine/tracking origin/main su82f0657, fetch riuscito e divergenza0/0. Modifiche corrispondenti al consolidamento T02, schema12/migrazione, test e documentazione; file privati/checklist/APK/NEXT_PROMPT ignorati e preservati. Nessuna modifica applicativa o nuova build per la sola pubblicazione; restano valide le verifiche dell'incremento (129JVM, finale27Android, lint0/29) e i relativi limiti hardware. Esito commit/push registrato dopo l'esecuzione.

Pubblicazione eseguita:

- Stage limitato ai23file intenzionali, diff staged senza errori di whitespace, controllo percorsi privati/artefatti e pattern di chiavi/token senza riscontri. Nessun segreto, APK o checklist privata incluso.
- Commit **278e889 — «Wave 09: crossfade continuo e ascolti overlap persistenti (T02)»**:23file,3282inserimenti/294rimozioni, schema12 e sette nuovi file compresi. Identità per-commit conforme ai precedenti, nessuna configurazione Git persistente modificata o hook saltato.
- Push ordinario **82f0657..278e889** su origin/main riuscito; SHA locale/remoto **278e889e2d97ec036b292553d4fcb3ab97a53a36** verificati identici e working tree pulita subito dopo. Note nel successivo commit documentale.
- Memoria/PLAN/NEXT_PROMPT aggiornati. T06 e collaudi residui differiti per disponibilità dell'utente, non chiusi; W10 non avviata. Nessuna release o pubblicazione APK, nessuna nuova build/test per la sola pubblicazione. Ulteriori commit/push solo su nuova richiesta esplicita.

## 2026-10-16 — Handoff per tutta W10 Android Auto

Richiesta: prompt per continuare in nuova sessione. Letti memoria, SPEC (riproduzione/Auto/offline/design/verifiche), wave10 completa del piano, voci recenti e proposta Auto storica di ARCHITECTURE. Verificati status iniziale pulito e HEAD pubblicata **3febc1d**, codice consolidamentoW09 **278e889**. Nessun codice W10 avviato o nuovo collaudo in questa attività.

Preparato **NEXT_PROMPT.md W10-T01–T05**: catalogo/search/paginazione e download reali da Room, evoluzione del servizio/sessione Media3 esistente, stesso player logico/coda/bridge, comandi bidirezionali telefono/Auto, ID browse separati dagli UUID delle occorrenze, trust/isolamento e immagini senza credenziali, lifecycle e riconnessione senza autoplay. Gate awaitReady/zeroHTTP per ogni richiesta in solo offline, qualità/rappresentazioni/download condivisi e ledger overlap/ACK W09 preservati. API ufficiali effettive1.11.1 da verificare, guida sideload e test significativi, APK e checklist privata W10 con cleanup.

Esplicitato che MediaBrowser sull'AVD AOSP non equivale alla proiezione Auto; DHU può richiedere telefono/Android Auto/head-unit server e la prova in auto rimane separata. Mancanza attuale del telefono non blocca i task software indipendenti e non autorizza a segnare T05 passato senza prove. W09-T06 e gli altri residui restano differiti come chiarito dall'utente: «Tutto ok» riguarda solo il primo giro breve.

Precedente NEXT_PROMPT W09 conservato in **local/NEXT_PROMPT_W09_ARCHIVE_20261016.md** con intestazione di archivio, ignorato da Git; credenziali/checklist e fixture originali preservate. PLAN e PROJECT_MEMORY aggiornati a W10-T01. Nessun cambio SPEC/design, build/test o avvio emulatore richiesti per questo handoff documentale; controlli proporzionati su coerenza ID/stato, diff whitespace, percorsi e ignore. Nessun commit/push/release.

## 2026-10-16 — W10 Android Auto: software verificato e preparazione collaudo

Richiesta: implementare tutta W10 autonomamente, conservando player logico/coda/Roomv12 e i
collaudi hardware W05–W09 differiti. Base **3febc1d**, codice W09 **278e889**. Git iniziale con
PLAN/MEMORY/WORKLOG modificati per l'handoff: note, NEXT_PROMPT locale, checklist e credenziali
preservati. Letti handoff integrale, memoria, specifica, wave10, log recente, DESIGN e architettura.
Nessun commit/push/release richiesto o eseguito.

### Implementazione W10-T01–T03

- `PlaybackService` evolve in **MediaLibraryService.MediaLibrarySession** (API nested1.11.1),
  sullo stesso `CrossfadeCoordinator.player`. Sessione/coda, ServiceShuffle, timer, focus/noisy,
  STREAM_MUSIC, DSP, persistence e tracker sono unici; nessun motore/account/backend aggiuntivo.
- `playback/browse/AutoBrowse.kt` e `AutoBrowseItems.kt`: root La mia musica (Album/Artisti/Brani),
  Playlist, Download; ID stabili per profilo/tipo/risorsa codificata, separati dagli UUID
  `queue/...` delle occorrenze. Tipi Media3, copertine contentURI e stringhe IT/EN; nessuna UI
  Compose TPS-L2 automobilistica. Query locali in LibraryDao/BrowseDao/DownloadDao, pagine200
  e continuazioni legacy non annidabili: **10.003brani raggiungibili in51finestre**.
- Playlist lette a finestre per posizione; play espande selezioni complete con ordine/origine
  e copie A/B/A. Album/playlist incompleti non riprodotti troncati come falso successo. Artisti
  includono anche l'artista album/compilation. Ricerca locale brano/album/artista/playlist,
  massimo500risultati/query, nessuna cache di risultati fra query/profili; query vocali legacy
  Media3 via `RequestMetadata.searchQuery` nella stessa pipeline di risoluzione dei media.
- Download pubblicati solo con file canonico nel profilo, COMPLETED, byte positivi e dimensione
  coerente: non solo rigaDB/span cache. Consultazione file a finestre; snapshot ordinati anche
  vuoti, parziali e serverMissing, conteggio disponibili/totali e play gruppo completo solo
  se tutte le copie sono disponibili. File stale non presentati come scaricati.
- `CoverArtProvider` read-only: caller UID app/sistema/Auto con package appartenente all'UID,
  soltanto copertine del profilo attivo ricontrollato dopo il fetch. `CoverArtStore.file()`
  riusa cache/gate/validazione immagini e timeout; mai URL autenticati né percorsi audio/DB.
- Browse/search/root/rebind non preparano audio o ascolti; `awaitReady`/gate W08/W09 preservati.
  Profilo verificato da Room e collegamento servizio prima delle risposte/coda; risposte vecchie
  scartate. Osservazioni Room per profilo cancellate con coroutineScope/collectLatest;
  invalidazioni aggiornano i parent osservati/subscritti senza fetch impliciti.
- Restore/rebind senza autoplay e play esplicito della stessa occorrenza/posizione. Callback
  resumption con barriera QueueStore/owner/profilo e riapplicazione permutazione dopo il set
  del framework. Verificato restore→rebind→play, non una nuova kill-process/media-key cold-start.
- Manifest con automotive_app_desc media, azioni MediaLibraryService/MediaBrowserService e
  play-from-search. **Nessuna migrazione/dependency nuova**: Roomv12/schema1–12, Media3 1.11.1
  e WorkManager2.12.0 conservati. SPEC invariata; ARCHITECTURE/DESIGN aggiornati.

### Fonti ufficiali e compatibilità legacy

Firme verificate tramite `javap` sugli AAR1.11.1 e sorgenti ufficiali:
[MediaLibraryService](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaLibraryService.java),
[MediaSessionService](https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaSessionService.java),
[OkHttpDataSource](https://github.com/androidx/media/blob/1.11.1/libraries/datasource_okhttp/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java).
Root legacy: future completato suIO, non tramite post al main potenzialmente bloccato.

**Difetto reale individuato dal test framework legacy**: onGetSession rifiutava il placeholder
del primo bind. La prima correzione sulla stringa descritta dal javadoc non bastava: runtime1.11.1
usa **uid=-1 e `ControllerInfo.LEGACY_CONTROLLER_PACKAGE_NAME`** (`android.media.session.MediaController`).
Ora solo quel placeholder negativo ottiene il binder prima dell'identificazione; **onConnect
filtra il client reale**: UID app, framework trusted, gearhead con package→UID verificato.
Non si accettano controller esterni genericamente. Tracce diagnostiche temporanee rimosse;
root+subscribe con framework MediaBrowser ora passano anche nella suite finale.

### Verifiche e failure intermedi

- Gradle PowerShell/JAVA_HOME JBR, `"-Dkotlin.compiler.execution.strategy=in-process"`,
  --max-workers=2. Primo errore tool ChildProcess.kill seguito da invocazioni con esito
  Gradle esplicito, senza presumere successo. Corrette firme nested/callback3-arg, coroutine
  receiver/suspend, fakeDAO e costante voice MediaStore durante le compilazioni iniziali.
- Primo lint Auto: **MissingIntentFilterForMediaSearch**. Dichiarata l'azione e verificata
  la risoluzione vera via RequestMetadata, non un presunto intent voice nel callback media-button.
  Nessuna baseline lint introdotta. Finale app/androidTest/test/lint **BUILD SUCCESSFUL (7m56s)**,
  **137/137 JVM**, zero failure/error/skip, con RealServerTest e RealWave07ApiTest opt-in;
  **lint0errori/33avvisi** (suggerimentiKTX/plurali aggiuntivi rispetto ai29precedenti).
- JVM inizialmente non compilati per nuovi metodiDAO mancanti nei fake (vecchi report129 non
  contati come nuova verifica). Successiva136con3failure: slash codificati rifiutati e NUL
  accettato dal parser; corretti e aggiunti controlli overflow/continuazioni. Finale137 sopra.
- AVD walktape_aosp headless/swiftshader, runtime2048MiB/2core; boot molto lento. Primo install
  durante package-manager non pronto lasciava APK vecchio: ClassNotFound del test nuovo;
  reinstallati entrambi con Success e **precompilati speed** prima della strumentazione.
- Prime W10: failure sulla pagina negativa rifiutata dal **client** MediaBrowser (clamp resta
  testatoJVM) e timeout restore fixture. Un rerun isolato ha avuto anche login fixtureFailure;
  adottato dispatcher per endpoint invece di risposte enqueue fragili, senza attribuire una
  causa worker/server reale non dimostrata. Il restore isolato era già corretto.
- **Race della fixture**: il primo profilo viene attivato automaticamente; con servizio già
  vivo il seed della coda poteva essere superato da un salvataggio vuoto. Il solo stopService
  non garantiva distruzione immediata. Ora seed su profilo **inattivo**, parkingProfile attivo,
  flush e attivazione esplicita. Nessun cambio al motore audio/aumento timeout per mascherarlo.
- W10 finale sull'APK consegnato: **6/6 Android,78,603s**, zero failure/skip:
  Wave10AutoBrowseTest4 + Wave10RealBrowserTest1 + Wave10LegacyBrowserTest1. Comprende root/
  children/item/search, query consecutive, 10.003brani, UUID/copie/origini/shuffle/repeat e
  secondo controller telefono, file stale escluso, gruppi vuoti/parziali, cambio A→B/comando
  vecchio rifiutato, offline con contatore di **tutti** gli endpoint fermo (cover cached/
  uncached/foreign), restore/rebind senza richieste/audio. Framework legacy senza profilo
  mostra setup onesto senza deadlock. MP3/FLAC Navidrome reali riprodotti via ID browse e
  richiesta vocale Media3 risolta/suonata. WAV/PNG sintetici restano fixture, non server/hardware.
- Regressione W05/W08: **26scoperti,24passati+2skip opt-in,zero falliti,217,268s**: focus/duck/
  noisy/media keys, timer, restore, DataSource, coda/migrazioni, cache/download originale reale,
  sync/conflitto playlist test reale poi ripulita, worker e servizio offline. Seed/verify
  processo separato W05 sono assumption skip, anche se il runner stampa `OK (26tests)`.
- W09 combinata27 iniziale: **25passati/2falliti,305,5s**, audiofxState inatteso nell'offline
  dopo le altre suite e timeout incoming-audible reale. Entrambi poi passati isolati
  (**28,133s/82,716s**), senza cambi audio. Non è una suite27/27 verde. Sull'APK conclusivo:
  **12/12,117,878s** Handover/OverlapStorage/Service/Scrobble, **1/1offline,27,83s** e
  **1/1handover reale,85,352s** in esecuzioni separate. Promozione, overlap/ACK, timer e zeroHTTP
  preservati. Non sommate prove intermedie/versioni diverse come una combinata unica.

### Blocchi W10-T04/T05, consegna e cleanup

**T01–T03 [x] nei percorsi software disponibili; T04/T05 [ ]**. Guide developer.android.com
Auto/DHU non raggiungibili via WebFetch (transporterror); browser strumenti senza eseguibile
Chrome, nessuna installazione globale fatta. API consultate su fonti ufficiali sopra, ma
Unknown sources W01 non presentato come verifica corrente dell'APK W10 sul launcher.

`sdkmanager --list_installed`: **DHU/extras;google;auto non installato**; adb mostrava solo AVD,
nessun telefono fisico. DHU con head-unit server richiede Android Auto sul telefono; nessuna
proiezione/auto reale effettuata. Mancano abilitazione APK, launcher, assistente/rotella/touch,
copertine suhead-unit e matrice fisica Wi-Fi/mobile/VPN/USB-wireless. Guida **docs/ANDROID_AUTO.md**
con passaggi consultati/storici/preparati/eseguiti distinti, checklist **local/PHONE_TESTS_W10.md**.

APK debug standard SHA256 **A57F561527FCD1D641D9198E68876393CD665F6D999BC25A8AE56F819A5A3DBB**.
PLAN/MEMORY/NEXT_PROMPT/ARCHITECTURE/DESIGN aggiornati; handoff iniziale conservato sotto lo stato
corrente in NEXT_PROMPT. ChecklistW05–W09 preservate. Env e fixtureMP3/FLAC AVD rimossi con
`w10-temporary-files-absent`, marker queue-process-test.ids assente, nessun forward presente/creato.
**AVD spento e adb vuoto**. Rimossa solo la directory temporanea nota m3session con estrazioniAAR
nel temp approvato (assenza verificata), originali local/test-server.env/fixture locali intatti.
Ignore di segreti/APK/NEXT_PROMPT e checklistW05–W10 confermati. Nessun commit/push/release.

Prossima azione: **W10-T04 abilitazione APK sul Motorola, poi T05 DHU/auto reale** seguendo
guida/checklist quando il telefono è disponibile. W09-T06 e residui W05–W08 restano differiti,
senza nuove attestazioni hardware dedotte dal primo giro W09 positivo.

Controllo finale: `git diff --check` senza errori (solo avvisi LF→CRLF), HEAD3febc1d invariata,
ignore privati confermati, adb ancora vuoto. Working tree W10 lasciato locale non committato.

## 2026-10-16 — Pubblicazione W10 richiesta dall'utente

Richiesta esplicita «fai commit e push». Ispezionati status, diff applicativo/documentale,
nuovi file, ultimi10commit e remoto/tracking; fetch origin riuscito, divergenza iniziale0/0
su3febc1d. File intenzionali W10 e note dell'handoff precedente preservati; verificato ignore
di env, APK, NEXT_PROMPT e checklist private. Stage limitato a codice/test/risorse/documenti W10.
Nessuna modifica applicativa o nuova build per la sola pubblicazione; restano validi gli esiti
137JVM, lint0/33, suiteW10 finale6/6 e regressioni sopra. T04/T05/hardware restano aperti.
Esito commit/push registrato dopo l'operazione; nessuna release richiesta.

Pubblicazione eseguita:

- Stage limitato ai27file intenzionali W10 (codice/test/risorse/documenti); `git diff --cached
  --check` senza errori. Verificati percorsi privati/artefatti e pattern di chiavi/token/password
  senza riscontri nello stage; nessuna credenziale/APK/checklist privata inclusa.
- Commit **109b652 — «Wave 10: Android Auto, catalogo e continuità (T01-T03)»**:
  27file,2463inserimenti/75rimozioni. Identità per-commit conforme ai precedenti, nessuna
  configurazione Git persistente modificata e nessun hook saltato.
- Push ordinario **3febc1d..109b652** su origin/main riuscito. SHA locale/remoto
  **109b65218cf07ad3fe3f217a7c54e88fc0139d28** verificati identici con ls-remote; working tree
  pulita subito dopo. Note della pubblicazione nel presente aggiornamento documentale.
- PLAN/MEMORY/NEXT_PROMPT aggiornati. T04/T05 e residui hardware invariati; nessuna release
  o pubblicazione APK, nessuna nuova build per la sola operazione Git. File privati preservati.
  Ulteriori commit/push successivi a questa pubblicazione soltanto su nuova richiesta esplicita.

## 2026-10-16 — Handoff per tutta W11; collaudo Auto previsto domani

Richiesta utente: «scrivimi il prompt per implementare W11 in nuova sessione (i test li faccio
domani in auto)». Registrato collaudo Auto previsto per17ottobre2026, **non eseguito né superato**:
W10-T04/T05 rimangono aperti, insieme a W09-T06 e residui W05–W08. Il lavoro indipendente
W11 è richiesto esplicitamente senza attendere i controlli fisici wave08–10.

Letti AGENTS/memoria/NEXT_PROMPT precedente, wave11 completa, requisiti SPEC operatività/backup/
update e voci recenti; esaminati AppSettings e diagnostica/redattore esistenti per indicare
punti di estensione concreti. Verificati Git inizialmente pulito e HEAD **a49d0e7**, codiceW10
**109b652** pubblicato. Nessuna nuova discovery, modifica applicativa o esecuzione test/emulatore.

Preparato **NEXT_PROMPT.md W11-T01–T05**: impostazioni/sistema reali sui repository esistenti;
diagnostica da allowlist con anteprima/controllo dei dati ed export/share; backup versionato
di profili/preferenze/lista download/coda/posizione senza credenziali/audio e con nuovo login,
validazione/mapping/collisioni senza perdita dati; aggiornamenti GitHub manuali e automatici
opt-in, asset/APK/versionCode/package/firma, download e conferma dell'installer Android.
Esplicitati gate offline anche su GitHub, assenza autoplay/download silenziosi nell'import,
non trasferimento del ledger ascolti/pending_op come nuovi eventi, conservazione Roomv12 o
nuove migrazioni esplicite se necessarie, test significativi e prove reali/fixture distinte.
Nessuna release/nuova chiave per fabbricare un test di update; stato Releases da verificare.

Prompt W10 originale preservato integralmente in **local/NEXT_PROMPT_W10_ARCHIVE_20261016.md**
con intestazione storica aggiunta; nuovo punto corrente W11-T01 in memoria/piano. Checklist
W05–W10 ed esiti precedenti conservati; W11 dovrà preparare la propria checklist privata.
SPEC/DESIGN/ARCHITECTURE senza modifiche: nessun nuovo requisito o cambiamento visivo in questa
attività. Controlli proporzionati: diff documentale/whitespace, presenza e ignore dei prompt.
Nessun task W11 segnato completo e nessun commit/push/release richiesto o eseguito.

## 2026-10-17 — Wave 11 completa: impostazioni sistema, diagnostica, backup, aggiornamenti (T01–T05)

Richiesta: implementare tutta W11 dalla baseline a49d0e7. Working tree iniziale con sole note
dell'handoff (PLAN/WORKLOG/PROJECT_MEMORY) preservate. Letti memoria/NEXT_PROMPT W11/SPEC/wave11/
ARCHITECTURE/DESIGN; mappati i punti di estensione esistenti (SettingsRepository, DiagnosticsCollector/
Redactor, Graph, DownloadDao/Engine/Store, OfflineOps, LibrarySync, ProfileRepository, QueueStore).

**T01 — Impostazioni/sistema operative.** `Graph.librarySyncEngine` singleton: libreria e
impostazioni condividono lo stesso motore/Mutex (nessun refresh parallelo dello stesso profilo;
LibraryViewModel instradata su di esso). SettingsViewModel espone stati reali osservati da Room
(library_state ultimo aggiornamento+esito, SyncProgress condiviso, pending/conflitti pending_op,
download attivi/in pausa/falliti) e azioni con effetti reali: verifica connessione singola
(repository.verify con gate), aggiorna/annulla indice (syncEngine), invia modifiche
(offlineOps.drain), pausa tutti (pauseWork+pauseAll) e riprendi tutti (resumeAll+schedule).
`SettingsScreen.SystemStatusSection` con stato onesto (mai dati non caricati), date formattate,
errori per categoria come nei profili; nuovi entry Backup e Aggiornamenti. DownloadDao:
observeProblemCount (CONFLICT+FAILED) e observePausedCount.

**T02 — Diagnostica esportabile.** DiagnosticsCollector riscritto a **sezioni con allowlist**
(APP/DEVICE/STORAGE/LIBRARY/DOWNLOADS/SYNC/PLAYER/CONNECTIONS/PREFERENCES) con Sources iniettabili
per test (default bind Graph). CONNECTIONS (URL+username, nessun segreto) è **opt-in esclusa di
default** con avviso in UI. Nessun logcat/dump DB/telemetria. Redactor esteso in difesa in
profondità: parametri corti OpenSubsonic `p=/t=/s=/u=` (anche dopo `?/&/;/spazio`), header
Authorization/Cookie per intera riga, secret con etichetta (password/token/credentialId/
encryptedPassword…), blob base64 ≥48, query degli URL completa sostituita con `?***`; URL nudi
senza query sopravvivono. UI: interruttori per sezione, anteprima immediata senza ricampionare,
Copia, Esporta file (SAF CreateDocument text/plain) e Condividi file via FileProvider dedicato
`com.walktape.app.files` (cache-path reports/updates, sola lettura per chiamata).

**T03 — Backup manuale.** Formato JSON versionato `walktape.backup` v1 con **DTO espliciti**
(Gson già in dipendenze): profili (nome/URL/username/allowLocalHttp, **mai** credentialId/
encryptedPassword), preferenze dispositivo (senza flag `loaded`), lista da riscaricare (solo
download COMPLETED con metadati + origini + gruppi playlist con duplicati), coda con occorrenze
duplicate/UUID/traversal/posizione/repeat/shuffle/nome mixtape. Codec a validazione totale prima
dell'applicazione (format/versione/limiti 32 profili-5k coda-20k download-10MB/URL via
ServerAddress/permutazioni/prefissi queue/); file troncati/futuri/corrotti rifiutati interi.
Import: match per URL+username (merge nel profilo esistente, credenziali e file COMPLETED mai
degradati), altrimenti creazione con suffisso onesto "(2)" e **credentialId vuoto** (nuovo login);
righe download importate come PAUSED (nessun download silenzioso: claimNext non le prende);
coda sostituita per profilo matched **tranne** se attivo con media caricata (skip dichiarato);
preferenze applicate solo se richiesto e solo senza fallimenti; transazione per profilo.
`ProfileRepository.login` (verifica ping → nuova chiave Keystore, rimozione vecchia) + dialog
"Accedi" per profili importati con URL/username precompilati. Export/import via SAF con anteprima
contenuti obbligatoria prima di entrambi.

**T04 — Aggiornamenti GitHub Releases.** Client dedicato (mai il client Navidrome): OkHttp
proprio con redirect (asset su objects.githubusercontent.com), header ufficiali
(Accept/vnd.github+json, X-GitHub-Api-Version 2022-11-28, User-Agent) e NetworkTraffic.listener
(gate globale). `/repos/rosariomoscato/walktape/releases/latest`: 200 parse, **404→NoReleases
onesto**, 403/429 con rate limit→RateLimited, rete/JSON invalidi distinti. Versioni v MAVJOR.MINOR
.PATCH paragonate alla versionName; tag non paragonabili→UnknownVersion senza decisioni implicite.
Asset: selezione APK univoca con preferenza universal su ABI-specifici, ambiguità rifiutata.
Download in cache privata con `.part`, progresso, verifica dimensione, cancellazione con pulizia
(file troncati mai consegnati al verifier/installer). `ApkVerifier` fail-closed: package ==
com.walktape.app, versionCode strettamente maggiore, **firma uguale all'installato** (digest
SHA-256 certificati; nessuna disinstallazione proposta per firme incompatibili). Installazione:
permesso REQUEST_INSTALL_PACKAGES, canRequestPackageInstalls→altrimenti pagina Sorgenti
sconosciute limitata a WalkTape, poi Intent ACTION_VIEW su URI FileProvider: conferma sempre di
Android. Controllo automatico opt-in **default off** (DataStore), max 1/die, check-only; in Solo
offline il gate (awaitReady+offlineOnly(-1)) blocca senza consumare il giorno. API verificate su
docs.github.com (settembre 2026) e stato reale: **HTTP 404, nessuna release pubblicata**.

**T05 — Verifiche.** JVM **172/172** (0 falliti, 2 skip preesistenti): BackupCodecTest (roundtrip,
not-json/format/versione/URL/duplicati/traversal/entryId/limiti/preferenze/coda vuota, JSON senza
campi credenziali), UpdateLogicTest (parsing/versioni/decisioni pure/asset/MockWebServer
parse-404-ratelimit-invalid-header), RedactorTest esteso (aggiornate 2 attese alla policy u=/s=
per requisito W11; nuovi casi p/t/s/u, header, blob, query URL). FakeDownloadDao esteso ai nuovi
metodi DAO. Android su walktape_aosp (APK precompilati pm compile): **BackupStorageTest 4/4**
(roundtrip due profili in DB isolati sandbox: coda duplicati/traversal/repeat/posizione/mixtape,
download COMPLETED→PAUSED, nessun profilo attivo né credenziali, zero richieste al server del
profilo; merge matched con credenziali/file conservati e coda sostituita; collisione nome→"(2)";
skip coda su profilo attivo con media; troncato/versione futura/traversal invalido lasciano i
dati intatti), **DiagnosticsReportTest 1/1** (allowlist, CONNECTIONS opt-in assente dal default,
nessun materiale credenziali), **UpdateFlowTest 5/5** (parse release reale HTTP, 404 onesto,
download con progresso→rifiuto Corrupt su non-APK, **gate Solo offline blocca con 0 richieste**
tramite ProfileNetworkGate isolato, APK installato letto dal verifier→NotNewer). Regressioni
percorsi toccati: **ProfilesInstrumentedTest 4/4 + QueueStorageTest 5/5**. **Upgrade reale con
dati persistenti**: marker seme (profilo+coda) nel DB dell'app via test temporaneo → APK
versionCode 2 (stessa firma debug) `install -r` → app avviata senza crash, marker intatto →
ritorno a v1 con `install -r -d` → marker intatto; test temporaneo rimosso e marker pulito.
Build app/androidTest e **lint 0 errori/45 avvisi** (rimosse 2 stringhe poi inutilizzate;
PluralsCandidate/UnusedResources preesistenti). **Room v12 invariata**: nessuna migrazione.

Controlli NON eseguiti (registro onesto): navigazione UI via dump delle nuove schermate — l'AVD
ha subito crash di system_server (bluetooth headless) e tempesta ANR System UI/startup sotto
carico host (D25/D28): processi app vivi, zero FATAL walktape in logcat, suite strumentata verde
sullo stesso APK, ma il dump non è stato possibile; da ripetere insieme ai collaudi telefono.
Confronto firma tra due APK firmati diversamente, dialog installer di sistema e flusso
unknown-sources interattivo, download/cancellazione a metà file su rete reale e release GitHub
reale (inesistente: 404) restano prove telefoniche/hardware o W12. TalkBack/adattività delle
nuove superfici in `local/PHONE_TESTS_W11.md`.

File principali: nuovi `core/backup/*` (Models/Codec/Manager), `core/updates/*` (Models/
GitHubReleases/ApkVerifier/Manager), `ui/screens/backup|updates/*`, `res/{values,values-en}/
system.xml`, `res/xml/file_paths.xml`; modificati Graph (librarySyncEngine/backupManager/
updateManager/auto-check), AppSettings/SettingsRepository (preferenze update), DownloadDao/
ProfileDao, ProfileRepository.login, DiagnosticsCollector/Redactor, Settings ViewModel/Screen,
Diagnostics ViewModel/Screen, Profiles ViewModel/Screen (login), LibraryViewModel, NavHost/
MainActivity, Manifest (REQUEST_INSTALL_PACKAGES, FileProvider). APK debug finale SHA256
**658F31DA5E388ACB169874673DDC0E3D26D2254C10FC86767C37E05F22DA1DFD**. Emulatore spento, adb
vuoto, temporanei di test rimossi (file storici in temp non toccati). Nessun commit/push/release;
versionCode tornato a 1 dopo la prova upgrade.

**Pubblicazione su richiesta esplicita dell'utente** (17ottobre2026): codice/test/documenti W11
in **2805aa9 — Wave 11: impostazioni sistema, diagnostica, backup e aggiornamenti (T01-T05)**,
49 file/4140 inserimenti/94 rimozioni. Push ordinario a49d0e7..2805aa9 riuscito; SHA locale e
remoto `2805aa9e9bcfb64520fe11c40cc276c7f2259c5e` verificati identici, working tree pulita subito
dopo. Esclusi come da prassi: local/ (credenziali, checklist W05–W11, archivi NEXT_PROMPT),
NEXT_PROMPT.md corrente, APK. Nessuna release creata. Collaudi telefono W11 e Auto W10-T04/T05
restano aperti; altri commit/push soltanto su nuova richiesta.

## 2026-10-17 — Handoff per W12 (prompt di ripresa)

Richiesta utente: «Scrivimi il prompt per continuare con W12 in una nuova sessione». Attività
solo documentale: nessun codice, test, build o commit/push/release. Verificato git status
pulito dopo la pubblicazione 530e83a (solo file locali ignorati toccati).

Preparato **NEXT_PROMPT.md W12-T01→T07** (verifica complessiva e distribuzione): cancello
qualità con suite complete in esecuzioni separate; matrice end-to-end server reale/
offline/backup limitata al hardware disponibile; impegni trasversali SPEC (prestazioni,
accessibilità, lingue, privacy); icona originale con **approvazione utente obbligatoria**;
README/guide/privacy/licenza MIT e note di rilascio; firma stabile con keystore **locale mai
in Git** e decisione utente sulla chiave (raccomandato riusare la debug per preservare dati e
percorso upgrade in-app); APK firmato con versionCode/versionName da concordare e
**pubblicazione repository/release solo su richiesta esplicita**; chiusura perimetro con
registro onesto di verificato/aperto/backlog. Prompt post-W11 (collaudi) preservato integralmente
in local/NEXT_PROMPT_POSTW11_ARCHIVE_20261017.md con archivi precedenti; nuovo punto corrente
in memoria/piano. Checklist W05–W11 e credenziali originali intatte; collaudi utente
(W10-T04/T05 auto, telefono W11) restano pendenti e distinti da qualsiasi fixture.

## 2026-10-17 � Wave 12: cancello qualit�, coerenza UI, firma e preparazione distribuzione (T01�T07)

Task: W12-T01, W12-T02 (parte emulatore/server reale), W12-T03 (porzioni emulatore/codice),
W12-T04, W12-T05, W12-T06 (preparazione), W12-T07. Attivita' su HEAD 530e83a con working
tree contenente solo le note handoff W12 (preservate).

Attivita':

- **T01 cancello qualita'**: build app+androidTest+lint+JVM `-PrealServerTest=true`
  (processo detached, JBR, in-process, max-workers 2): **172/172 JVM** zero fail/skip,
  lint **0 errori/45 avvisi**. Suite Android su walktape_aosp in esecuzioni separate con
  APK precompilati `pm compile -m speed -f` e server reale via 10.0.2.2:
  G1 core/migrazioni/profili **12/12 (75,9s)**; G2 coda/playback **21 OK (169,7s)** con gli
  skip opt-in attesi (QueueSelectionx2 senza wt-seed; VolumeKeyDiagnostic opt-in); G3
  servizio/focus/PlayerUi **6/6 (512,5s)**; G4 W07Storage **4/4** + W07UiServer **1/1 dopo
  correzioni ambiente** (v. sotto); G5 W08 **13/13 (45,6s)**; G6 W09 **25/25** +
  RealQuality **1/1 dopo cold reboot**; G7 W10 **6/6 (64,5s)**; G8 W11 **10/10 (8,1s)**;
  seeded UI: UiSeed seed **1/1**, LibraryNavigation **1/1 (151,4s)**, QueueSelection
  **2/2 (190,5s)**, PlayerUi **2/2 (148,4s)**; VolumeKeyDiagnostic opt-in eseguito
  (fallimento iniezione = limitazione AVD gia' documentata; tasti fisici confermati
  dall'utente su telefono in W05).
- **Difetti corretti prima di procedere**: 4 difetti di infrastruttura nel test W04-era
  LibraryNavigationTest (stale-node click retry; IME aperto da SET_TEXT su Compose
  attuale -> chiusura condizionata via dumpsys input_method; albero a11y stantio dopo
  ricomposizione selezione -> clearCache nel polling; stringa playlist readonly W07).
  Dettagli e diagnostica "visible:" in ARCHITECTURE W12. Errore mio nel build script
  firma (java.util vs accessor `java`) corretto con import esplicito Properties.
- **Fail ambientali registrati, non difetti app**: W07UiServer timeout 40s x2 sotto carico
  + login fallito a 2min dal cold boot (stesso login verde nel canarino subito dopo) ->
  **1/1 dopo cold reboot**; Wave09RealQuality PCM tutto-zero x2 per decoder Opus c2
  silente su un boot dell'AVD (server verificato: transcode Opus valido; MP3/FLAC decode
  verdi nello stesso giro) -> **1/1 dopo cold reboot**. ANR di sistema (phone/media/
  systemui/quickstep) e tempesta ANR a fine giornata per RAM host esaurita (0,8GB liberi):
  risolta fermando i daemon Gradle; boot poi completato. Un rollback qcow2 aveva
  "resuscitato" i temporanei rimossi prima di emu kill: pulizia rieseguita con `sync`
  prima dello shutdown e verifica ls finale.
- **T02 richieste utente**: TopAppBar+freccia Indietro su Diagnostica/Backup/Update/
  Profili; versione reale "WalkTape 0.1.0" a fondo Impostazioni; crediti esatti
  "� 2026 Rosario Moscato - handcrafted by a human, upgraded by agents." con nome-link
  https://rosmoscato.xyz. Verifica manuale via dump+tap su emulatore: tutte le quattro
  sotto-pagine tornano alla provenienza (freccia `content-desc="Indietro"`); versione
  coerente con Aggiornamenti app ("Versione installata: 0.1.0") e Diagnostica; il tap sul
  nome apre il browser (verificato su WebViewBrowserActivity resumed); ricerca "adele" su
  tab Album con risultati corretti (21/19/25) guidata manualmente; long-press selezione
  funzionante nell'app (il difetto era nell'attesa del test). Dettagli libreria con back
  gia' coperti da LibraryNavigationTest. Stringhe IT/EN simmetriche (lint
  MissingTranslation 0 errori; action_back EN riusato).
- **T03**: default SPEC puntuali verificati nel codice (cache 1024MiB/250MiB, wifiOnly=1,
  skipUnavailable=1, haptics=1, suggerimenti/clic/autocheck/lock/crossfade/ReplayGain/EQ/
  bassi/cassetta=0, allowBackup=false + dataExtractionRules). Misure walktape_aosp (non
  rappresentative telefono): cold start TotalTime 8,57s, PSS 83MB con indice 6.403 brani.
  Ambiguita' documentale registrata: "solo offline inizialmente on" (DESIGN/WORKLOG W08)
  vs default implementato off con gate fail-closed al load: comportamento conforme SPEC,
  nessun cambio applicato. TalkBack/font200%/batteria restano telefono (checklist W12).
- **T04**: proposte icona (3 varianti GDI+/SVG, anteprima PNG in local/) -> **utente
  approva A "Cassetta"**: sostituito ic_launcher_foreground.xml (adaptive+monochrome,
  background #2A4D9B invariato). README.md, LICENSE MIT, docs/PRIVACY.md, docs/INSTALL.md
  creati; bozza note di rilascio local/DRAFT_RELEASE_NOTES.md; checklist
  local/PHONE_TESTS_W12.md.
- **T05/T06**: decisione utente: **riuso chiave debug** come firma stabile e **v0.1.0 con
  versionCode 2**. local/signing.env (ignored) + signingConfigs condizionale; nessun
  segreto in file committati. Build release: APK **139A391A1C130AA4E788CE9C4740B314
  6ED7BFF497FCEA49070C8A48D5FFA74E**, V2 signer CN=Android Debug, cert SHA-256
  **60e9a71f990819c44b2a07a6fc9254925a0d87414c36495f85feb9ffde7b178c**. Upgrade reale:
  marker dati -> `adb install -r` release v2 sopra debug v1 Success -> versionCode 2,
  marker intatto, app resumed senza crash, vuoto onesto post-cleanup. APK debug per
  collaudi telefono (icona nuova, code 2): **BA766F2CD5BD8AA818FAEDBD1D0E386BB84D94A5F
  1A08B2FBE438560E9FEC95F**. Nessun tag/release/push pubblicati (manca richiesta).
- **T07**: PLAN/DESIGN/ARCHITECTURE/WORKLOG/PROJECT_MEMORY/NEXT_PROMPT aggiornati;
  emulatori spenti, adb vuoto, temporanei /data/local/tmp rimossi con sync verificato
  (ls finale: solo dalvik-cache). local/test-server.env e checklist W05�W12 intatti.

Verifiche non eseguite (registro onesto): collaudi hardware utente (W10-T04/T05 auto,
telefono W11, checklist W12 incluse TalkBack/font200%/batteria/Bluetooth/chiamate reali),
download update da release GitHub reale (API 404: nessuna release pubblicata), confronto
firm tra APK realmente diversi e dialog installer interattivi (restano telefono), misure
prestazioni su telefono. W09-T06 e residui W05�W08 invariati aperti.

Stato: W12-T01/T04/T05/T06(prep)/T07 completati; T02/T03 aperti sulle sole porzioni
hardware/utente. Prima versione non dichiarata completa: collaudi pendenti.

Prossimo passo: raccogliere gli esiti delle checklist utente (W10 auto, W11 telefono, W12)
e correggere gli eventuali difetti reali; pubblicazione release GitHub solo su richiesta
esplicita (bozza note pronta; APK e digest gia' preparati).

## 2026-10-17 � Collaudi telefono W12 sez. A+B: tutti positivi

Task: W12-T02 (porzione telefono), W12-T03 (TalkBack superfici principali).

Riscontri utente registrati punto per punto da `local/PHONE_TESTS_W12.md` (checklist
aggiornata dall'utente; nessun difetto segnalato):

Sez. A navigazione/informazioni (tutte [x]): frecce Indietro funzionanti da Diagnostica,
Backup e trasferimento, Aggiornamenti app e Profili (con barra inferiore raggiungibile);
versione "WalkTape �" coerente con Aggiornamenti app e release; crediti esatti con tap sul
nome che apre rosmoscato.xyz nel browser senza aperture automatiche; sotto-pagine La mia
musica (album/artista/genere/playlist) confermate; **TalkBack: frecce annunciate e link
crediti annunciato come link** (prima attestazione TalkBack reale delle superfici W12).

Sez. B matrice e2e (tutte [x]): login reale LAN/Tailscale con indicizzazione progressiva
MP3/FLAC; streaming MP3/FLAC con pausa/ripresa da notifica e schermo spento; Solo offline
zero richieste; ripristino coda dopo riavvio app senza autoplay; download con
pausa/ripresa e riproduzione offline; backup export+import con nuovo login.

Ambiente: Motorola, APK debug code 2 SHA256 BA766F2CD5BD8AA818FAEDBD1D0E386BB84D94A5F
1A08B2FBE438560E9FEC95F (upgrade in-place dalla build W11, stessa firma debug).

Non estesi ad altri controlli: restano aperti W10-T04/T05 (Android Auto in auto),
checklist telefono W11, batteria/font 200% reali (W09-T06 e residui W05�W08), misure
prestazioni su telefono. Nessun difetto da correggere; nessuna modifica applicativa.

Stato: W12-T02telefono/T03-TalkBack attestate; T02/T03 restano aperti sulle porzioni
Auto/batteria. Prossimo passo: checklist W11 telefono e W10 auto quando disponibili;
pubblicazione release solo su richiesta esplicita.

## 2026-10-17 � Collaudi telefono W11 (parziali): 12/21 positivi, nessun difetto

Task: telefono W11 (checklist `local/PHONE_TESTS_W11.md` aggiornata dall'utente).

Riscontri registrati punto per punto (checkmark come da file; voci non spuntate restano
aperte, non superate):

- **Diagnostica [3/4]**: sezioni on/off con Connessioni esclusa di default e avviso quando
  inclusa; anteprima coerente con copia/esporta senza token/password; Copia/Esporta/
  Condividi funzionanti. TalkBack delle sezioni (facoltativo) non eseguito.
- **Backup [3/6]**: esporta con anteprima e JSON senza password; re-import dello stesso
  file con merge che conserva credenziali e file gi� scaricati; file troncato/di altra app
  rifiutato con dati intatti. Restano: import su ambiente pulito (badge Accesso richiesto
  + nuovo login + attivazione), brani da riscaricare in pausa con consenso esplicito e
  nessun download automatico, coda ripristinata con duplicati/shuffle/posizione/mixtape.
- **Aggiornamenti [3/5]**: "Nessuna release pubblicata per ora" su 404 reale; Solo offline
  blocca il controllo senza richieste; auto-check default off e "Ultimo controllo" max
  1/giorno quando acceso. Le due voci restanti dipendono da una release esistente o da un
  APK con firma diversa (non disponibile: nessun difetto).
- **Regressione [3/3]**: verifica/attivazione profilo; refresh indice da La mia musica;
  riproduzione/coda/restore senza autoplay dopo le prove.
- **Sezione 1 "Stato e recupero" [0/6]**: nessuna voce spuntata � resta interamente da
  collaudare (profilo attivo/indice, Verifica, Aggiorna indice con annullamento, modifiche
  pending, pausa/riprendi tutti i download, gate in Solo offline). Non esteso con gli
  esiti W12 sez. B (controlli diversi).
- Ambiente (data/modello/server) non compilato nel file: registrato qui per trasparenza.

Nessun difetto segnalato: nessuna correzione applicativa. APK collaudo: debug code 2
BA766F2C�FEC95F (upgrade in-place, stessa firma).

Stato: checklist telefono W11 parziale (12/21). Restano inoltre W10-T04/T05 auto,
W12 sez. C riferimenti, batteria/font 200% reali e residui W05�W09. Pubblicazione
release solo su richiesta esplicita.

## 2026-10-17 � Collaudi telefono W11 sez.1 "Stato e recupero": 6/6 positivi

Task: telefono W11 (completamento sez.1 della checklist).

Riscontri utente registrati punto per punto, tutti [x] senza difetti: sezione Stato e
recupero con profilo attivo e indice reale (ultimo aggiornamento/conteggi); Verifica
connessione positiva con server raggiungibile e errore pulito con server fermo; Aggiorna
indice con progresso annullabile e data/conteggi aggiornati a fine corsa; contatore
modifiche coerente con "Invia adesso" che svuota la coda con rete e conflitti rimandati
alla gestione in La mia musica; pausa/riprendi tutti con effetti reali in Gestisci
download e contatori attivi/pausa/errori coerenti; con Solo offline verifica/aggiornamento
indice disabilitati senza richieste.

Checklist telefono W11 ora **18/24**: restano TalkBack diagnostica (facoltativo), 3 voci
backup su ambiente pulito (badge Accesso richiesto, da-riscaricare in pausa, coda con
duplicati/shuffle/mixtape) e 2 voci aggiornamenti dipendenti da release/firma diversa.
Nessuna correzione applicativa.

Stato: W11 telefono sostanzialmente collaudato; restano W10-T04/T05 auto, batteria/
font 200% reali (residui W05�W09) e le 6 voci sopra. Pubblicazione release solo su
richiesta esplicita.

## 2026-10-17 — Segnalazione Rinforzo bassi: diagnosi e correzione UI

Task: W09-T04/W12-T02. Utente: «In Opzioni la funzione Rinforzo bassi non funziona».
Sintomo preciso e uscita audio ancora da confermare; non attribuito a un limite hardware.
Ispezionati SystemEffectsController, PlaybackService e SoundSection: sessione condivisa
generata dal servizio e assegnata ai deck; preferenze applicate dal collector.
Difetto concreto trovato nella UI: a intensità zero il controller disabilita audiofx;
la UI usava quello stato per spegnere lo switch e nascondere lo slider, rendendo
impossibile aumentare nuovamente l'intensità. SoundSection ora usa le preferenze per
switch, visibilità e valore dello slider; lo stato effettivo rimane distinto.
Build debug e lint con strategia in-process, max-workers=2, log temporaneo
`wt-bass-fix.log`: **BUILD SUCCESSFUL in 12m03s**. APK debug SHA256
`D2FAAEE9036DF1F0F47C1C50CE060EF18404E844EBABE98BFCF15CC1F389E0A1`.
Nessuna prova di risposta acustica o UI su dispositivo per questa correzione.
Segnalazione utente aperta: questa correzione non dimostra risolto l'eventuale mancato
rinforzo udibile. Nessun commit/push/release.

## 2026-10-17 — Diagnostica USB reale: clic e feedback aptico

Task: W06-T05/T06, segnalazione hardware aperta. Motorola G55 5G autorizzato via USB,
Android16, WalkTape versionName0.1.0/versionCode2. Letture sole impostazioni mirate:
`sound_effects_enabled=0`, `haptic_feedback_enabled=1`, ringer normale (2), volume
SYSTEM non muted (5/7). Confermato il gate che sopprime i clic nell'implementazione
attuale quando i suoni sistema sono spenti. Nessuna impostazione modificata.
Vibrator manager registra diversi eventi di WalkTape `performHapticFeedback(constant=1)`
CLICK completati (circa52–57ms), scala LOW; presenti anche eventi LONG_PRESS.
Questi dati attestano richieste elaborate da Android, non la percezione tattile né la
correlazione con uno specifico tasto player. Da eseguire pressione manuale controllata
con successiva lettura dell'evento e riscontro utente. Nessuna conclusione di guasto
hardware, nessun aggiramento delle preferenze sistema, nessuna modifica codice.

### Riscontro successivo — aptica Coda percepita sul Motorola

Utente: «premuto, vibrazione sentita» dopo la richiesta di premere il tasto Coda del
player con aptica attiva. Confermata percezione reale su quel controllo. Nuova lettura
vibrator_manager: eventi WalkTape completati alle18:23 (~53–54ms, scala LOW), coerenti
con le pressioni recenti; nessuna attribuzione arbitraria di un singolo timestamp.
Nessuna modifica codice o impostazioni. Non esteso a tutti i controlli o a clic sonori:
questi ultimi restano bloccati da sound_effects_enabled=0, attivazione globale da
concordare con l'utente.

### Suoni al tocco abilitati su richiesta dell'utente

Utente: «puoi farlo tu per me?». Sul solo Motorola collegato: lettura iniziale
sound_effects_enabled=0, scrittura1 e lettura finale1 riuscite. Preferenza globale Android
ora attiva, precedente0; nessun altro parametro modificato. Prossima prova: tasto Coda
con Clic dei tasti ON in WalkTape. Percezione sonora non ancora attestata; eventuale
ripristino a0 da concordare con l'utente.
ripristino a0 da concordare con l'utente.

### Conferma utente — clic percepito e preferenza da mantenere

Riscontro: «lo sento e va bene, lascia tutto così» sulla prova del tasto Coda.
Clic sonoro confermato sul Motorola dopo attivazione dei suoni al tocco Android.
L'utente approva di lasciare sound_effects_enabled=1: nessun ripristino richiesto.
Nessuna modifica applicativa per clic/aptica; esito limitato al controllo collaudato.
## 2026-10-17 — Player: tasti distribuiti su tutta la larghezza

Task: W06 UI/W12-T02, su richiesta utente di due file armoniose a larghezza piena.
PlayerScreen.KeysPanel usa ora pesi per i MechanicalKey nelle FlowRow: prima fila
alta60dp con precedente/successivo peso1 e Play/Pausa/Stop peso1,25; seconda fila
alta56dp, larghezze uguali. Spazi10dp anche sulle righe aggiuntive in wrapping.
Base56dp, adattività telefono/tablet e comandi/semantica/hold/feedback invariati.
DESIGN e memoria aggiornati. Build assembleDebug+lintDebug riuscita in8m13s,
diff --check senza errori. Nessuna nuova prova strumentata o visiva su dispositivo:
layout da far giudicare all'utente sul telefono, senza dichiararlo già collaudato.
APK debug SHA256 `3928C4D3A9BE3224EDEEE9ECBDA2F93A4E722B88DF6729F29B208A8532825251`;
include la precedente fix Rinforzo bassi. APK release non aggiornato. Nessun commit/push.

## 2026-10-17 — Splash con logo, versione e credits richiesto dall'utente

Task: W12 UI/W06, nuovo requisito registrato in SPEC e DESIGN. LaunchIntroduction
Compose con icona Cassetta, nome, versione PackageManager e claim autore approvato.
Fondo blu/token crema, testo scalabile e scroll, nessuna animazione/rete/suono/autoplay.
MainActivity presenta il benvenuto per1200ms nominali al primo ingresso launcher del
processo (AtomicBoolean), poi mantiene gate settings loaded e biometria. Deadline
elapsedRealtime e stato salvato non ricominciano il timer alla rotazione; ingressi
da notifica e rientri nello stesso processo non richiedono il benvenuto. Nessuna nuova
Activity/dipendenza. Tema v31 coordina colore e icona dello splash Android12+.
Fonte developer.android.com splash-screen tentata ma non raggiungibile (transport error);
attributi API31 compilati contro SDK corrente.
Verifiche: assembleDebug+lintDebug BUILD SUCCESSFUL8m25s, diff --check senza errori.
Nessuna prova lifecycle strumentata o visiva sul telefono eseguita in questo incremento:
da verificare avvio launcher, ritorno notifica, rotazione, landscape/font200% e lock.
APK debug SHA256 `55D9199904A171F84424203318D0202290EB649379D83448FEE2CC10296E0998`,
comprende le precedenti fix tasti/bassi. Release139A391A resta antecedente; non pubblicare
quel vecchio APK come se contenesse le nuove funzioni. Memoria/handoff aggiornati.
Nessuna installazione su telefono, nessun commit/push/release.

## 2026-10-17 — UI approvata; risposta Rinforzo bassi ancora da diagnosticare

Utente: «le modifiche introdotte sono ok», riferito a splash/tasti, ma «Rinforzo bassi
quando attivato mi sembra che riduca solo il volume». Registrata approvazione UI e
nuovo sintomo audio; non esteso a verifica acustica superata.
Diagnostica USB readonly Motorola: uscita speaker, traccia attiva sulla sessione16385
di WalkTape; EqualizerBundle NXP abilitato, Dynamic Bass Boost NXP presente con controllo
del client ma disabilitato nell'istantanea; Music Volume Listener e DAP Dolby attivi.
Questo non prova che Dolby sia la causa né attesta la risposta in frequenza.
Richiesta prova controllata stesso brano/volume con bassi accesi per leggere lo stato
attivo. Nessun cambio a effetti/preferenze/volume o codice; difetto W09-T04 aperto.

### Bassi attivi — stato reale e fonte NXP/LVM

Utente: «bassi attivi». Lettura USB sulla stessa sessione16385: Dynamic Bass Boost
NXP enabled=y/suspended=n, controllo client concesso, tracce attive. Volume musica
speaker ancora4/15, EQ e Dolby attivi. Non è uno switch che rimane spento.
Consultato sorgente ufficiale AOSP (main):
https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/media/libeffects/lvm/wrapper/Bundle/EffectBundle.cpp
Contenuto base64 decodificato in memoria: EFFECT_CMD_SET_DEVICE su speaker (anche
A2DP_SPEAKER/BLE_SPEAKER/SCO_CARKIT) imposta bBassTempDisabled e disabilita il motore
senza cancellare bBassEnabled; LvmEffect_limitLevel calcola attenuazione gainCorrection
anche dalla contribuzione BassBoost. Spiegazione coerente con il sintomo riferito,
non prova del comportamento esatto della variante OEM né risposta in frequenza misurata.
Da verificare con cuffie e rappresentare onestamente lo stato route nella UI.
Nessuna modifica a volume/Dolby/impostazioni/code o DSP durante la diagnosi.

### Riscontro cuffie Bluetooth — rinforzo lieve e incerto

Utente: «Con cuffie bluetooth forse un minimo rinforzo dei bassi si avverte».
Riscontro soggettivo prudente, registrato senza trasformarlo in prova positiva certa:
nessun modello cuffie/intensità/misura specificati. Compatibile con risposta dipendente
dalla route, non prova della causa su speaker né esclusione di EQ/Dolby. Nessun cambio
applicativo, nessun task audio hardware chiuso. Resta da gestire onestamente in UI la
disponibilità del rinforzo sull'uscita corrente.

## 2026-10-17 — D41: rinforzo bassi sospeso su speaker e ripristino su cuffie

Task W09-T04/W12-T02. Approvazione utente: «per adesso va bene soluzione suggerita,
procedi». SPEC/DESIGN emendati: sospensione su speaker e route ignota, preferenza e
intensità conservate per ritorno cuffie; niente DSP sostitutivo o compensazione volume.

Implementazione: BassBoostRouting puro aggrega output/deck attivi; speaker prevale,
route ignota fail-closed, cuffie tutte consentono opt-in/intensità>0. Provider Media3
standard delegato da RoutedAudioOutputProvider osservando AudioTrack reale tramite
getter pubblico e OnRoutingChangedListener (fonti ufficiali Media3 1.11.1 consultate).
Notifiche serializzate main, token per output, guardia atomica release; osservazione
interrotta in pausa/stop/release. SystemEffectsController distingue richiesta utente da
stato effettivo audiofx, aggiorna solo bassi alle transizioni e conserva EQ indipendente.
UI IT/EN con motivo esplicito su speaker/unknown, switch non interattivo e slider su
cuffie anche a zero. Nessuna migrazione/dipendenza aggiunta; volume Android/Dolby intatti.

Verifiche intermedie: build app/androidTest,177/177 JVM (0fail/skip, compresi5 nuovi
test policy overlap/zero/opt-in/pausa/release e API reali), lint0errori/45avvisi.
Controller Android isolato1/1: effetto reale5bande, route simulate consentita/bloccata/
consentita, richiesta preservata senza altro apply, EQ e intensità invariati.
Primo Wave09OfflineHandoverTest fallito timeout30s (runtime58,981s), non escluso.
Diagnostica temporanea rilevava AudioTrack reale tipo2/speaker; rerun1/1 in26,941s.
Rimossa diagnostica: nuovo timeout esplicito «actual AudioTrack route must be observed»,
effects Ready/UNKNOWN. Corretto difetto osservatore: al play la route può essere ancora
null e il callback iniziale non aggiornarla successivamente in modo affidabile.
Aggiunta rilettura500ms soltanto in play, cancellata a pause/stop/release; nessun tracing
diagnostico nel codice finale. Nuova build/verifiche finali in corso, log
`wt-bass-route-verified.log`; esito da completare nella nota seguente.

Test offline aggiornato alla policy approvata: verifica route reale conosciuta e stato
bassi coerente, non richiede più bassi ON su speaker; verifica opt-in/intensità200
persistenti, volume STREAM_MUSIC invariato, zeroHTTP e restore senza autoplay.
Checklist privata W12 sezioneD aggiunta senza alterare esiti utente A/B. Telefono non
modificato/installato in questo incremento, release precedente non aggiornata.

### Esiti finali D41

Codice definitivo senza log diagnostico, polling route500ms solo in play:
build app/androidTest/JVM/lint **BUILD SUCCESSFUL15m36s**, **177/177 JVM**, zero
failure/skip (API reali incluse), **lint0errori/45avvisi**. Report finali letti.
APK app/test installati e precompilati speed sull'AOSP, telefono non modificato.
Esecuzioni Android finali separate: controller1/1(0,694s), Wave09OfflineHandover1/1
(27,269s: route reale rilevata, gate bassi coerente, preferenza/intensità conservate,
volume Android invariato, zeroHTTP offline e restore senza autoplay), Wave09Service1/1
(26,525s: timer durante overlap, entrambi i deck fermati e niente ascolti dal preload).
Non sommate come suite unica. Timeout intermedi documentati sopra; nessun test escluso.
SHA256 debug `C222F47A06F590A5D9A692844CA85DBFADF93C49EB202A9467122870624ABBBF`.
Diff --check senza errori. Emulator shutdown con sync, /data/local/tmp solo dalvik-cache,
adb vuoto verificato. Aggiornati SPEC/DESIGN/ARCHITECTURE/README/PLAN/memoria/handoff e
checklistW12D. Nessun commit/push/release. APK release precedente non riallineato.
Prossimo passo: installazione in-place del debug sul Motorola e riscontri puntuali della
sezioneD per speaker↔cuffie; risposta acustica non dichiarata superata da queste prove.

## 2026-10-17 — Riscontro D41 positivo e richiesta commit/push W12

Utente: «funziona, fai commt e push». Registrata conferma generale positiva sulla
correzione bassi per route; non chiuse automaticamente tutte le singole voci checklistD
né le altre prove hardware (Auto/batteria/font). Nessun nuovo difetto segnalato.
Autorizzati commit e push del lavoro W12 e degli incrementi successivi (splash/tasti/
feedback/bassi). Preflight: status, diff e ultimi10commit revisionati; origine/tracking
main allineati a530e83a, fetch riuscito, divergenza0/0. Esclusioni confermate tramite
git check-ignore per local/signing.env, test-server.env, NEXT_PROMPT e APK.
Rifinite le nuove guide prima della pubblicazione: release ancora non disponibile,
permesso sorgente APK per browser/file manager distinto da quello updater; privacy
distingue telemetria da sync/ascolti funzionali e descrive i permessi effettivi.
Nessuna modifica applicativa ulteriore: valgono177/177 JVM,lint0/45 e tre Android
separati1/1 già eseguiti sul codice finale, più riscontro utente. Nessuna nuova build
necessaria per queste sole note. Esito commit/push da registrare dopo esecuzione.
