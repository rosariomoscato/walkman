# WalkTape — memoria di progetto

Ultimo aggiornamento: 2026-10-17 (release pubblica v0.1.0/code2 con APK aggiornato e checksum; documentazione riallineata; Auto e collaudi hardware residui aperti).

## Punto corrente — v0.1.0 pubblicata; collaudi residui

- **W12-T06/T07 — release pubblicata su richiesta esplicita dell'utente**:
  https://github.com/rosariomoscato/walktape/releases/tag/v0.1.0
  Release normale latest (non draft/prerelease), pubblicata2026-10-17T19:31:05Z.
  Tag annotato sul sorgente `afdb6a57e381c574b2df94e4d17446b90dc2dd39`, include
  splash/tasti/D41. Docs aggiornati in decdbc6 e note finali nel successivo commit.
- **Artefatto pubblico corrente**: `walktape-universal-0.1.0.apk`,0.1.0/code2,
  release non-debuggable,19.914.927byte, quattroABI. SHA256
  `c1ccacb4632d39da11c4bd0344cbe092a90f1eca7d6badc253ca04be72668957`.
  Firma V2, certificato `60e9a71f990819c44b2a07a6fc9254925a0d87414c36495f85feb9ffde7b178c`.
  Due asset pubblici (APK+SHA256SUMS.txt); copie locali ignorate in local/. Il vecchio
  candidato139A391A non distribuito. Non sostituire l'asset o spostare il tag esistente.
- **Verifiche pubblicazione**: assembleRelease/lintRelease riusciti12m53s,lint0/45;
  codice applicativo invariato dal finale177/177 JVM e regressioni Android D41.
  Download draft riscaricato con firma/hash verificati; dopo pubblicazione API latest
  anonima200 (tag0.1.0), download APK pubblico200 e SHA identico, checksum pubblico
  coerente; digest asset GitHub coincidente. Primo download PowerShell timeout tool,
  ripetuto con curl pubblico (19.914.927byte completi). Passaggio debugcode2→releasecode2
  installato sull'AOSP con marker conservato; hash base.apk identico. Avvio resumed/
  Statusok, ma ANR di sistema e un abort AndroidXProfileInstaller durante DeadSystemException
  documentati: non dichiarare smokeUI o aggiornamento futuro in-app completamente verdi.
- **Prossima azione precisa**: collaudi residui W10Auto, W11 sei voci originarie più
  due prove post-pubblicazione aggiunte alla checklist, e hardware
  W05–W09/W12. Le build0.1.0 di sviluppo sono pari versione per il checker: primo APK
  pubblico da installare manualmente in-place, stessa firma, nessuna disinstallazione.
  Aggiornamenti futuri con versionName>0.1.0 e code>2, da concordare. Nessuna nuova
  pubblicazione/commit/push senza richiesta. Guida manutentore docs/RELEASING.md;
  note pubbliche docs/RELEASE_NOTES.md. SPEC/DESIGN e checklist precedenti preservati.
  Pulizia finale: dopo boot completo, su0 ha verificato e rimosso il marker nell'app;
  /data/local/tmp solo dalvik-cache, sync e shutdown eseguiti, adb vuoto verificato.
  Copie temporanee di download rimosse, originali pubblicati conservati in local/.

### Baseline W12 e riscontri precedenti (storico)

- **Commit e push eseguiti su richiesta esplicita**: codice/test/documenti W12 e
  incrementi successivi in **bc93c3c — Wave 12: verifiche, interfaccia e bassi per uscita**,
  35file/1576inserimenti/95rimozioni. Push ordinario530e83a..bc93c3c riuscito,
  SHA locale/remoto `bc93c3c4826b256066ad2764bd22c74a78e20ce0` identici e working tree
  pulita subito dopo. Note della pubblicazione nel commit documentale successivo.
  Esclusi local/, segreti/chiavi, APK e NEXT_PROMPT; nessun tag o GitHub Release creato.
  **Prossima azione**: raccogliere W10Auto, residui W11 e voci puntuali W12D/hardware;
  riallineare APK release alle ultime modifiche prima di un'eventuale pubblicazione
  autorizzata. Nuovi commit/push/release solo su nuova richiesta.

- **Ultimo riscontro D41**: utente «funziona, fai commt e push». Registrato come
  conferma generale positiva della correzione bassi per route, non attestazione separata
  di tutte le voci W12D né della risposta acustica. Commit e push delle modifiche W12
  autorizzati esplicitamente; preflight remoto main allineato a530e83a, diff revisionato,
  segreti/APK/local e NEXT_PROMPT esclusi. Pubblicazione GitHub Release non richiesta.

- **Task corrente W09-T04/W12-T02 — correzione bassi dipendente dalla route (D41)**:
  utente approva sospensione su speaker/route ignota con avviso, preferenza conservata
  e ripristino su cuffie. Implementati osservatore AudioTrack reale Media3 e aggregazione
  deck attivi, gating audiofx distinto dalla preferenza, UI IT/EN. Nessun volume/Dolby
  modificato, nessun DSP alternativo. Finale app/androidTest/JVM/lint riuscita
  (`wt-bass-route-verified.log`,15m36s):177/177 JVM,0skip,lint0/45. Android su AOSP in
  esecuzioni separate: controller1/1(0,694s), offline/handover1/1(27,269s), timer durante
  overlap1/1(26,525s). Timeout intermedi non esclusi: rilevamento iniziale route corretto
  con polling500ms solo in play oltre ai callback, cancellato a pause/stop/release.
  APK debug corrente `app/build/outputs/apk/debug/app-debug.apk` SHA256
  `C222F47A06F590A5D9A692844CA85DBFADF93C49EB202A9467122870624ABBBF`.
  Non installato dall'agente sul telefono; utente conferma «funziona» dopo la consegna,
  senza dettagli separati per ogni voce D. Release precedente non riallineata.
  Raccogliere eventuali dettagli speaker↔cuffie (W12 checklist sez.D), senza rifare
  discovery né dichiarare superati altri collaudi hardware.
  Emulatore spento, adb vuoto, nessun env/fixture temporaneo residuo.

### Incrementi precedenti — cronologia sintetica (superati dal punto corrente)

- **Riscontro cuffie Bluetooth bassi**: utente «forse un minimo rinforzo dei bassi si
  avverte». Registrare come percezione lieve/incerta, non test superato o misura.
  Coerente con dipendenza dalla route del BassBoost sistema, ma non dimostra causa del
  calo su speaker né esclude interazioni EQ/Dolby. Nessun incremento automatico di
  volume o DSP alternativo. Resta aperta gestione esplicita disponibilità/limiti route.

- **Diagnosi aggiornata bassi su Motorola**: dopo «bassi attivi», audio_flinger conferma
  BassBoost NXP enabled=y, suspended=n, controllo client concesso, sessione16385 con
  tracce attive; uscita speaker e STREAM_MUSIC4/15 invariato. EQ e Dolby attivi.
  Fonte ufficiale AOSP `frameworks/av/media/libeffects/lvm/wrapper/Bundle/EffectBundle.cpp`
  (main) letta e decodificata: `EFFECT_CMD_SET_DEVICE` esclude SPEAKER/A2DP_SPEAKER/
  BLE_SPEAKER/SCO_CARKIT con `bBassTempDisabled`, mantenendo stato logico enabled;
  `LvmEffect_limitLevel` calcola gainCorrection includendo bBassEnabled.
  È una spiegazione coerente del calo su speaker, non prova del codice binario OEM né
  misura acustica. Prossimo passo: confronto cuffie stesso brano/volume, poi gestione
  UI/route esplicita per non presentare rinforzo operativo su speaker non supportato.
  Nessun bypass Dolby, compensazione volume o sostituzione DSP autorizzata/applicata.

- **Riscontro successivo: splash e disposizione tasti approvati dall'utente** («le
  modifiche introdotte sono ok»). Nuovo sintomo audio W09-T04/W12-T02 aperto: Rinforzo
  bassi ora attivabile, ma all'ascolto sembra solo ridurre il volume. Non risolto né
  attribuito al solo volume. USB reale: audio_flinger mostra sessione WalkTape16385,
  EQ NXP attivo, BassBoost NXP presente ma disattivato nell'istantanea, Dolby attivo;
  route speaker. Nessun parametro modificato. Prossima azione precisa: utente mantiene
  brano/volume e attiva bassi, poi leggere stato enabled/controllo/route mentre attivo
  e confrontare, senza compensazione di volume arbitraria o disabilitare Dolby.

- **Task corrente W12 UI: splash richiesto dall'utente**. Implementato benvenuto con
  icona, nome, versione reale e credits (1200ms, nessun effetto musicale/rete), una volta
  per processo su launcher; timer monotono salvato per rotazione, gate preferenze/lock
  conservato. Splash sistema Android12+ blu coordinato via tema v31. Build/lint
  riusciti (`wt-launch-introduction.log`,8m25s), diff --check senza errori. APK debug
  SHA256 `55D9199904A171F84424203318D0202290EB649379D83448FEE2CC10296E0998`
  include splash, tasti ridistribuiti e fix Rinforzo bassi. Non ancora
  installato o collaudato su telefono; release precedente resta antecedente a queste fix.

- **Task corrente W06/W12 UI: armonizzazione tasti player richiesta dall'utente**.
  Implementati pesi FlowRow: ogni fila riempie il riquadro, altezza uniforme per fila,
  Play/Pausa e Stop peso1,25 rispetto a precedente/successivo peso1; fila secondaria
  pesi uguali. Target base56dp e wrapping conservati. Build debug/lint riuscita
  (`wt-player-key-layout.log`,8m13s), diff whitespace senza errori. APK debug SHA256
  `3928C4D3A9BE3224EDEEE9ECBDA2F93A4E722B88DF6729F29B208A8532825251`, include
  anche la correzione UI Rinforzo bassi. Giudizio visivo telefono ancora da fare;
  release precedente non ricostruita con queste modifiche.

- **Nuova segnalazione telefono: clic dei tasti e feedback aptico non percepibili**
  anche con opzioni app abilitate; l'utente non trova la voce Android dei suoni al tocco.
  Diagnosi aperta W06-T05/T06: `KeyFeedback.press()` chiama
  `View.performHapticFeedback(VIRTUAL_KEY)` e il clic usa `SOUND_EFFECTS_ENABLED` +
  `AudioManager.FX_KEY_CLICK`; entrambi collegati ai tasti del player TPS-L2.
  Diagnostica USB reale successiva su Motorola G55/Android16: dispositivo autorizzato,
  WalkTape 0.1.0 code2. `sound_effects_enabled=0` (gate clic effettivamente chiuso),
  `haptic_feedback_enabled=1`, modalità suoneria normale, volume SYSTEM non muted (5/7).
  `dumpsys vibrator_manager` registra più eventi WalkTape `VIRTUAL_KEY`/CLICK completati
  (~52–57ms), scala LOW; ciò non attesta la percezione tattile né prova ancora quale
  controllo li abbia generati. Nessuna impostazione modificata. Prossimo passo: pressione
  manuale di un tasto player noto e correlazione con evento recente, poi riscontro utente.
  **Riscontro successivo: «premuto, vibrazione sentita» sul tasto Coda del player**:
  aptica di quel controllo confermata sul Motorola; nuovi eventi WalkTape completati
  presenti nel servizio vibrazione (18:23, circa53–54ms, scala LOW). Nessun cambio
  codice o impostazioni necessario per questa prova. Clic ancora non verificati:
  suoni di sistema disattivati; eventuale attivazione globale solo con consenso utente.
  **Su richiesta esplicita successiva dell'utente**, abilitati via USB i suoni al tocco
  sul Motorola: `sound_effects_enabled` letto 0, impostato 1 e rilettura1 confermata.
  Stato attuale1; precedente0. **Conferma utente: «lo sento e va bene, lascia tutto così»**:
  clic percepito sul tasto Coda e preferenza globale da mantenere attiva, nessun ripristino.
  Aptica e clic confermati su quel controllo reale; non estendere a ogni tasto o route.
  Non aggirare i controlli di sistema né dichiarare verificato l'effetto hardware.

- **Difetto hardware segnalato dall'utente: «Rinforzo bassi non funziona»**. Task corrente
  **W09-T04/W12-T02**, diagnosi aperta: da distinguere interruttore che non si attiva
  da assenza di effetto udibile (route ancora da conoscere). Trovato e corretto nel
  codice un blocco indipendente della UI a intensità zero: switch e visibilità/valore
  dello slider seguono ora la preferenza utente, non lo stato effettivo audiofx che
  disabilita l'effetto a zero. Build debug/lint **BUILD SUCCESSFUL (12m03s)**, log
  `wt-bass-fix.log`; APK debug SHA256
  `D2FAAEE9036DF1F0F47C1C50CE060EF18404E844EBABE98BFCF15CC1F389E0A1`.
  Non dichiarare risolto il problema udibile senza riscontro telefono.
  Gli APK e digest W12 sottostanti precedono questa correzione.

- **W12 eseguita su HEAD 530e83a** (working tree pulita prima delle modifiche W12; nessun
  commit/push/release eseguito: non richiesto). **T01 cancello qualità completo**: 172/172
  JVM, lint 0/45, tutte le suite Android in esecuzioni separate su walktape_aosp+server
  reale (G1 12/12, G2 21 OK+skip opt-in, G3 6/6, W07 4/4+1/1, W08 13/13, W09 25/25+1/1,
  W10 6/6, W11 10/10, seeded UI 1/1+2/2+2/2). Fail ambientali (ANR/decoder Opus silente/
  RAM host) risolti con cold reboot e documentati; **4 difetti corretti erano tutti
  infrastruttura test W04-era** (retry stale-node, IME su SET_TEXT, clearCache a11y,
  stringa readonly W07), nessun difetto app.
- **Richieste utente T02 implementate e verificate via dump+tap**: TopAppBar con freccia
  Indietro su Diagnostica/Backup/Aggiornamenti/Profili; "WalkTape 0.1.0" a fondo
  Impostazioni (coerente con Aggiornamenti/Diagnostica); crediti "© 2026 Rosario Moscato -
  handcrafted by a human, upgraded by agents." con nome→https://rosmoscato.xyz (browser
  aperto al tap). Room v12 invariata, nessuna migrazione.
- **Decisioni utente W12 (2026-10-17)**: icona definitiva = **variante A "Cassetta"**
  (asset sostituiti; B/C archiviate in local/); **firma stabile = riuso chiave debug**
  (local/signing.env ignored + signingConfig condizionale, nessun segreto in Git);
  **versione = v0.1.0 versionCode 2**. APK release firmato preparato e verificato:
  SHA256 `139A391A1C130AA4E788CE9C4740B3146ED7BFF497FCEA49070C8A48D5FFA74E`, cert SHA-256
  `60e9a71f990819c44b2a07a6fc9254925a0d87414c36495f85feb9ffde7b178c`; upgrade reale via
  adb debug v1→release v2 con dati persistenti e app senza crash. APK debug telefono
  (code 2, icona nuova): `BA766F2CD5BD8AA818FAEDBD1D0E386BB84D94A5F1A08B2FBE438560E9FEC95F`.
- **Documentazione pubblica creata**: README.md, LICENSE MIT, docs/PRIVACY.md,
  docs/INSTALL.md; bozza note di rilascio local/DRAFT_RELEASE_NOTES.md; checklist
  local/PHONE_TESTS_W12.md. DESIGN/ARCHITECTURE/PLAN/WORKLOG aggiornati.
- **Prossima azione precisa**: nella nuova sessione raccogliere gli esiti delle checklist
  utente ancora pendenti (**W10-T04/T05 auto**; telefono W11 ultime 6 voci: TalkBack
  diagnostica facoltativo, 3 backup su ambiente pulito, 2 dipendenti da release/firma
  diversa) e correggere eventuali difetti reali;
  **pubblicazione GitHub (tag v0.1.0, release, caricamento APK) solo su richiesta
  esplicita** — processo, digest e note di rilascio già pronti. W09-T06 e residui
  W05–W08 restano aperti e non superati.
- **Collaudi telefono W12 sez. A+B ricevuti e registrati (2026-10-17, tutti positivi,
  nessun difetto)**: frecce Indietro su tutte le sotto-pagine Impostazioni/Profili,
  versione coerente, crediti+link browser, sotto-pagine libreria, **TalkBack reale delle
  superfici W12 (frecce e link annunciati)**; login LAN/Tailscale+indicizzazione,
  streaming MP3/FLAC con notifica/schermo spento, Solo offline zero richieste, restore
  coda senza autoplay, download pausa/ripresa+offline, backup export/import. Dettagli nel
  WORKLOG. Restano fuori da questi riscontri: Auto (W10-T04/T05), checklist W11 telefono,
  batteria/font 200% reali e residui hardware W05–W09.
- **Collaudi telefono W11 [18/24] ricevuti (2026-10-17, tutti positivi, nessun difetto)**:
  sez.1 Stato e recupero **6/6** (indice reale, verifica, aggiornamento annullabile,
  pending/Invia adesso, pausa/riprendi tutti, gate Solo offline); diagnostica 3/4 (manca
  solo TalkBack facoltativo); backup 3/6 (restano import su ambiente pulito,
  da-riscaricare in pausa, coda con duplicati/shuffle/mixtape); aggiornamenti 3/5 (2
  richiedono release o firma diversa); regressione 3/3. Dettagli nel WORKLOG.

## Baseline W11 pubblicata — dettagli in WORKLOG/ARCHITECTURE del 17 ottobre

- **W11 pubblicata su richiesta esplicita** nel commit **2805aa9 — Wave 11: impostazioni sistema,
  diagnostica, backup e aggiornamenti (T01-T05)** (49 file/4140 inserimenti/94 rimozioni); push
  ordinario a49d0e7..2805aa9 riuscito, SHA locale/remoto `2805aa9e9bcfb64520fe11c40cc276c7f2259c5e`
  identici, working tree pulita subito dopo. Note della pubblicazione nel commit documentale
  **530e83a** (push riuscito). Esclusi local/, checklist, NEXT_PROMPT e APK; nessuna
  release. Dettagli tecnici e decisioni D38–D40 nel WORKLOG/ARCHITECTURE del 17ottobre.
- W11 verifiche: **172/172 JVM**, build app/androidTest, lint **0/45**, Android **W11 10/10**,
  regressioni profili/coda **9/9**, **upgrade reale v1→v2→v1 via adb con dati persistenti**.
  APK W11 SHA256 658F31DA5E388ACB169874673DDC0E3D26D2254C10FC86767C37E05F22DA1DFD (sostituito
  in W12 dai build sopra). Navigazione UI via dump W11 rimandata in W12 **eseguita** (vedi
  punto corrente). GitHub Releases resta vuoto (404 onesto).

## Baseline W10 pubblicata — T04/T05 ancora da collaudare

- **W10-T01–T03 [x]** per implementazione e contratto software verificato; **T04/T05 [ ]**.
  `PlaybackService` ora MediaLibraryService con una sola MediaLibrarySession sul player logico
  W09. Root La mia musica (Album/Artisti/Brani), Playlist e Download; ID browse per profilo,
  UUID per occorrenza, origini/duplicati, ricerca locale (max500), pagine200 e continuazioni
  legacy su10.003brani. Copertine content autorizzate, file download verificati, snapshot
  vuoti/parziali/serverMissing. Nessuna modifica schema/deps: **Roomv12, Media3 1.11.1,
  WorkManager 2.12.0**. Continuano focus/timer/volume/DSP e ascolti precedenti.
- **Trust legacy**: il primo bind usa uid=-1 e `ControllerInfo.LEGACY_CONTROLLER_PACKAGE_NAME`;
  solo quel placeholder ottiene il binder; autorizzazione reale anche in onConnect (UID app,
  trusted framework o gearhead con package→UID verificato). Non tornare al solo filtro
  onGetSession della bozza: impediva il collegamento del browser framework.
- Verifiche finali: build app/androidTest, **137/137 JVM**, lint **0/33**; **W10 Android6/6,
  78,603s** (moderno/legacy, server realeMP3/FLAC, query vocaleMedia3, profili, copie/shuffle,
  offlinezeroHTTP e restore/rebind/play). Regressioni W05/W08 **24passati+2skip opt-in,
  217,268s**. W09: prima27→25passati/2falliti, entrambi poi verdi isolati; conclusivo12/12
  più1offline+1handoverreale su esecuzioni separate. Non sommare come suite unica.
- **Collaudo Auto previsto dall'utente domani**: leggere `docs/ANDROID_AUTO.md` e `local/PHONE_TESTS_W10.md`;
  quando disponibile il Motorola, verificare la procedura corrente Unknown sources/launcher,
  installare il pacchetto SDK DHU e usare il vero head-unit server Android Auto, poi collaudo
  in auto (rete Wi-Fi/mobile/VPN, scollegamenti, controlli e assistente). Ora nessun telefono
  collegato/DHU installato; guide developer.android.com non raggiungibili, API consultate
  dai sorgenti ufficiali1.11.1. MediaBrowserAVD non è DHU né auto reale. T04/T05 rimangono aperti.
- APK standard SHA256 **A57F561527FCD1D641D9198E68876393CD665F6D999BC25A8AE56F819A5A3DBB**.
  Guida/architettura/design/piano/log aggiornati, checklistW10 aggiunta e precedenti preservate.
  Env/fixture temporanei AVD rimossi e assenza verificata, nessun forward creato, marker
  processo assente, AVD spento e adb vuoto. Credenziali originali ignorate intatte.
- **W10 pubblicata su richiesta esplicita** nel commit **109b652 — Wave 10: Android Auto,
  catalogo e continuità (T01-T03)**,27file/2463inserimenti/75rimozioni, note handoff preservate.
  Push ordinario3febc1d..109b652 riuscito; SHA locale/remoto
  `109b65218cf07ad3fe3f217a7c54e88fc0139d28` identici e working tree pulita subito dopo.
  Note della pubblicazione nel successivo commit documentale. Nessuna release o APK pubblicato;
  nuovi commit/push soltanto su nuova richiesta. CodiceW09 precedente278e889.
  W09-T06 e residui W05–W08 restano differiti, non superati. Primo giro W09 positivo invariato.

## Handoff iniziale W10 — storico, superato dall'incremento sopra

- L'utente chiede il prompt per continuare in una nuova sessione. Preparato **NEXT_PROMPT.md per tutta W10**, punto di partenza **W10-T01**, poiT02–T05 (catalogo/search Auto, navigazione/comandi, rete/offline/continuità, sideload e verifiche). HEAD pubblicata **3febc1d**, codice consolidamento W09 **278e889**. Working tree inizialmente pulita; questa attività aggiunge solo handoff/note locali, nessun codice W10 avviato o task segnato completo, nessun nuovo commit/push autorizzato.
- **Prossima azione precisa nella nuova sessione**: leggere integralmente NEXT_PROMPT e wave10, verificare le API MediaLibraryService/MediaLibrarySession1.11.1/Auto ufficiali e implementare W10-T01 sullo stesso player logico/servizio/bridge. Proseguire autonomamente conT02–T05 nei percorsi disponibili, senza aspettare il collaudo hardware differito. AVD MediaBrowser è test del contratto, non DHU/auto reale; documentare prerequisiti telefonici mancanti senza bloccare le funzioni indipendenti. Conservare T06 e residui precedenti aperti.
- Prompt W09 precedente archiviato integralmente in **local/NEXT_PROMPT_W09_ARCHIVE_20261016.md**, ignorato come NEXT_PROMPT e checklist. Prevale il nuovo handoff W10; niente ritorno a schema11 o vecchio completeSwap con seek.

- **T01–T05 completati; T06 aperto**. Consolidamento T02 e verifiche automatiche locali su82f0657: player logico stabile, promozione senza seek/ricaricamento, focus unico e clock overlap durevole con **Room v12**, migrazione11→12/schema12. Il vecchio stato T02 parziale sotto è storico; non ripristinare completeSwap con seek né lo schema11.
- Verifiche dell'incremento:129/129JVM, build app/androidTest, lint0errori/29avvisi; finale27/27Android (26W09+ScrobbleService),276,661s. Regressioni aggiuntive27/27core,3/3UI,7passati+2skip opt-in servizio/focus/restore in esecuzioni separate. Esiti e correzioni nel WORKLOG e ARCHITECTURE.
- **Riscontro utente 2026-10-16: «Tutto ok»**, in risposta al primo giro breve proposto (suono/crossfade/comandi/effetti/offline). Registrato come riscontro generale positivo, senza inventare esiti puntuali, route, versioneAndroid o misure. Non chiude automaticamente T06 o i residui W05–W08.
- L'utente precisa che «Tutto ok riguarda primo giro» e rinvia il resto perché adesso non può. **Collaudo hardware residuo differito**, non superato: timer15min/schermo spento, interruzioni/pausa manuale, Bluetooth/cuffie/cambio route, risposta/clipping audiofx, batteria/accessibilità. Concordato salvare W09 e proseguire con W10 indipendente dai collaudi differiti; pubblicazione eseguita come sotto. La richiesta attuale prepara il prompt, senza avviare l'implementazione.
- **Pubblicato su richiesta esplicita** in **278e889 — Wave 09: crossfade continuo e ascolti overlap persistenti (T02)**,23file/3282inserimenti/294rimozioni. Push ordinario82f0657..278e889 riuscito; SHA locale/remoto `278e889e2d97ec036b292553d4fcb3ab97a53a36` identici, working tree pulita subito dopo. Note di pubblicazione nel successivo commit documentale. Nuovi commit/push soltanto su nuova richiesta; nessuna release.
- APK standard SHA256 **CE703DD677FB758DB104379B055B29BB0414D0263D5234123B24C5CF7C2CBE1B**, checklist privata `local/PHONE_TESTS_W09.md` e precedenti preservate. AVD spento, copie env/fixture temporanee rimosse nell'incremento precedente; credenziali, APK, checklist e NEXT_PROMPT esclusi dalla pubblicazione.

## Baseline precedente W09-T02 parziale — storico, superato dal punto corrente

**Incremento autorizzato in corso 2026-10-16 — task W09-T02**, poi verifiche automatiche T06. Baseline82f0657, working tree inizialmente pulita. In implementazione player logico stabile/promozione deck senza seek e stato overlap durevole; nessun nuovo commit/push autorizzato. I risultati seguenti sono la baseline precedente fino all'aggiornamento finale.

- **T01/T03/T04/T05 [x], T02/T06 [ ]**. W09 non è conclusa e nessun requisito è stato rimosso. Su successiva richiesta esplicita «fai commit e push», codice/test/schema11/documenti pubblicati in **c1cddd9 — Wave 09: qualità e DSP, crossfade parziale (T01/T03-T05)**:43file,4907inserimenti/83rimozioni. Push ordinario riuscito, SHA locale/remoto `c1cddd98effa7131eb38b7378a171de84fef9f35` verificati identici. Note della pubblicazione nel successivo commit documentale; nessuna release. Note precedenti preservate, nuovi commit/push soltanto su nuova richiesta.
- **Room v11**: migrazione10→11 e schema11 esportato. Qualità per profilo (originale Wi-Fi/download, Opus mobile/download opt-in), rappresentazione nei file e URL/chiavi distinte; nessun riuso frammenti transcode senza ETag/Range affidabile. Originali cachati, ridotti completi nei download e sempre locali prioritari. Fallback raw solo fresh-open, cambio codec a offset rifiutato; URI cache/sessione logica senza credenziali. Default e gate awaitReady W08 preservati.
- ReplayGain ID3/Vorbis effettivi, album/brano/off, assenza neutra e picchi/clamp; cassetta hiss/wow/flutter con bypass byte-exact e toggle runtime. EQ/BassBoost audiofx su sessionId condiviso, controllo/route indisponibile espliciti e rilascio. Preferenze DSP dispositivo DataStore, qualità rete Room per profilo, UI IT/EN accessibile. Suono pulito predefinito, STREAM_MUSIC invariato.
- **T02 parziale reale**: seam PCM MP3/LAME e FLAC verificato; overlap crossfade operativo misurato, timer durante overlap ferma entrambi i deck e preload senza doppio ascolto. Però `CrossfadeCoordinator.completeSwap` fa seek sul principale e rilascia l'ausiliario: handover non ancora attestato continuo su tutte le sorgenti. Il tracker principale non accredita ancora il tempo udibile iniziale entrante durante overlap. Occorre consolidare il motore logico e l'attribuzione durevole, non solo aumentare timeout dei test.
- Verifiche finali: build app/androidTest; **129/129 JVM** (0skip/failure), lint **0 errori/30 avvisi**. **W09 Android finale14/14,111,68s**, zero skip/failure sull'APK finale (codec/tag, gain/tape/combined/runtime bypass, EQ reale5bande/enable/release, persistenza preferenze, migrazione, cache/fallback/codec guard, server reale MP3+FLAC→Opus96 e download, servizio timer/preload). Fixture/capture ≠ hardware; risposta/clipping audiofx e batteria/Bluetooth Motorola ancora da misurare.
- Regressioni W05–W08 passate **in esecuzioni separate**. Combinata41casi precedente39passati/2falliti; crossfade capture corretto per buffer rilasciati non riprodotti, UI W05 ripetuta dopo coldboot con lookup/click fresco e passata. UI W06 2/2; W07 UI fallita in combinata poi passata con worker/sync reale W08 **3/3,93,21s**; focus+restore **2passati/2saltati opt-in,82,692s**. L'AVD è scomparso durante una precedente esecuzione focus: non conteggiata come suite verde. Dettagli/fallimenti intermedi nel WORKLOG.
- **Prossima azione precisa: W09-T02**. Rivedere handover/deck ownership (stessa sessione/bridge/UUID/coda) per evitare seek+rebuffering a fine fade, quindi attribuire overlap solo realmente udibile al passaggio corretto, mai preload. Aggiungere misura della continuità nel mix e regressioni seek/skip/repeat/duplicati/profilo/timer/ducking combinate. T06 comprende anche risposta EQ/bassi/clipping e collaudo hardware con `local/PHONE_TESTS_W09.md`. Residui W05–W08 invariati.
- APK `app/build/outputs/apk/debug/app-debug.apk`, SHA256 **1573D341579EF5634355DC388E6B9E05D856828DFB848BA38CCEFC9C3D205F06**. Checklist W09 pronta e precedenti preservate. Env/fixture/UI dump temporanei AVD rimossi e assenza verificata; **AVD spento**. Originale `local/test-server.env` intatto e ignorato; fixture sintetiche locali riproducibili con `tools/Generate-W09AudioFixtures.ps1`, nessuna musica utente modificata. Nuovi commit/push soltanto su richiesta esplicita.

## Handoff iniziale W09 (storico)

- L'utente chiede il prompt per implementare **tutta W09 in una nuova sessione**: preparato NEXT_PROMPT.md completo, ignorato da Git. Ripresa **W09-T01**, poi T02–T06 (qualità, gapless/crossfade, ReplayGain, EQ/bassi, effetto cassetta e verifiche combinate). Questa attività è solo handoff documentale, nessun codice W09 avviato. HEAD pubblicata **0902d82**, codice W08 **b90b9f3**; working tree pulita prima delle nuove note locali. Le funzioni W08-T01–T06 sono disponibili per lo sviluppo indipendente W09; T07 e gli altri collaudi residui restano aperti. «Molto bene» è un riscontro generale, non attestazione di nuovi test hardware. Nessun nuovo commit/push/release richiesto.

- Implementati **W08-T01–T06** sulla baseline b18691b/043e43c, preservando note dell'handoff e file privati W05/W06/W07. **Commit e push eseguiti su richiesta esplicita**: codice, test, schemi e documenti in **b90b9f3**; SHA locale/remoto `b90b9f33edf90ed9abfdd875ffb9ecba3e9f64b3` verificati identici. Note della pubblicazione nel successivo aggiornamento documentale; nessuna release. **Room v10**: 7→8 download/origini/operazioni/impostazioni e flag playlist; 8→9 serverMissing; 9→10 snapshot/sottoscrizioni playlist scaricate anche vuote. Schemi 8/9/10 e migrazioni esplicite; non tornare a v7 né modificare uno schema già installato senza migrazione.
- WorkManager **2.12.0** verificato su Google Maven: worker ordinari a budget 8 minuti (finestra framework 10), backoff e vincoli rete; originali e parziali in `profiles/<id>/music`. Range ripreso solo con ETag forte e risposta coerente, altrimenti restart sicuro. Cache audio 1 GiB distinta dai download, eviction con lease; copertine 250 MiB riusate; limiti e spazio nelle impostazioni.
- Servizio/bridge W05 riusati: download locale prima di cache/rete, skip indisponibili configurabile e segnalato. Gate solo offline su client/API e upstream, cancellazione chiamate attive, worker sospesi; ascolti conservati. Nessun autoplay da restore/riavvio, nessun ascolto generato dalla UI.
- Playlist scaricate: snapshot ordinato con duplicati e marker anche per playlist vuote; refresh Wi-Fi periodico/manuale, file condivisi per origine, snapshot conservato se la playlist sparisce. Download singoli indipendenti dall'indice, metadati sufficienti e stato serverMissing dopo refresh.
- Modifiche offline: transazione locale+op, code per profilo e worker di riconnessione, contenuto completo/baseline per modifiche strutturali, baseline ID server persistita prima di create. ACK ambiguo senza prova univoca → conflitto, mai reinvio cieco di create. Risoluzioni esplicite locale/server/nuova copia; scelta riferita all'intera versione locale. Rifiuti rimangono visibili. Il limite exactly-once dello scrobbling Navidrome resta invariato.
- Verifiche: **110/110 JVM** con realServerTest; build app/androidTest; lint **0/28**. Suite mirata Android **21/21 (48,054 s)**, poi **combinata finale 32 passati + 2 saltati opt-in, zero falliti (333,652 s)**, con servizio/player/UI server W05/W06/W07. Originale reale+hash, sync/conflitto su playlist test reale ripulita, worker effettivo, servizio PCM con zero HTTP e ascolti pending, rimozione profilo durante scrittura senza dati download resuscitati, migrazioni 4/5/6/7→10 e upgrade AVD intermedi. Download recuperato dopo force-stop e dopo reboot AVD, seed/verify **1+1** per percorso. I due skip sono i protocolli separati W05, non test passati. Race startup del gate trovata in una ripetizione e corretta con `awaitReady`; la precedente combinata con un fallimento resta registrata nel log.
- **W08-T07 non chiuso**: manca la matrice hardware Wi-Fi/mobile/perdita rete, spazio realmente esaurito durante la scrittura (preflight fixture passato), UI/accessibilità/adattività W08 e stress rimozione/concorrenza sotto trasferimento. Non sono blocchi delle altre wave inventati: sono controlli non ancora eseguiti. Checklist privata W08 pronta; quelle precedenti preservate. W07-T06 richiede ancora secondo account/playlist condivisa readonly reale; hardware W05/W06 resta aperto.
- **Prossima azione precisa**: nella nuova sessione leggere integralmente NEXT_PROMPT.md e wave09, verificare le API audio/qualità attuali contro lo stack effettivo e avviare **W09-T01**. Preservare zero rete in solo offline, chiavi distinte per rappresentazioni originali/ridotte e semantica di Range/download. Proseguire con T02–T06 nello stesso servizio/sessione, mantenendo suono pulito predefinito e ascolti per occorrenza. Conservare W08-T07 e raccogliere separatamente gli esiti Motorola con la checklist W08; non chiudere quella wave prima delle prove mancanti. Il protocollo W08 restart e i relativi comandi restano documentati nel WORKLOG.
- APK debug: `app/build/outputs/apk/debug/app-debug.apk`, SHA256 **97B2C3F8532EEF8AD792EB962C63CDF79DC3957485C3272514BF9995A50BBF7E**, invariato per la pubblicazione del codice. Env temporaneo AVD rimosso e assenza verificata; marker W08 restart e W05 processo assenti. File privati originali e checklist precedenti presenti; ignore confermati ed esclusi dal commit insieme all'APK. **AVD spento, adb devices vuoto**. Nessuna release; altri commit/push soltanto su nuova richiesta.

## Handoff W08 iniziale (storico; superato dal punto corrente)

### Punto di partenza — handoff W08-T01 → T07; residuo W07-T06 e hardware W05/W06 preservati

- L'utente chiede il prompt per implementare la prossima wave in un'altra sessione: preparato **NEXT_PROMPT.md per tutta W08**, senza avviare codice W08. Base pubblicata **b18691b** (note), **043e43c** (codice W07). Working tree pulita prima di questo handoff, nuove note locali non committate. Ripresa **W08-T01**, poi T02–T07: download/cache/sorgenti locali/solo offline/sync playlist e preferiti con conflitti. W07-T06 resta aperto; il prerequisito del secondo account non blocca il lavoro indipendente W08 basato sulle funzioni W05/W07 disponibili.

- Riscontro utente W07: **«bene, mi sembra tutto ok»**, dopo indicazioni su aggiunta singolo brano alla coda e posizione di Cronologia ascolti. Registrato come riscontro generale positivo sul collaudo, non attestazione puntuale di ogni voce. Su richiesta esplicita, **commit e push eseguiti**: codice/test/schema7/documenti W07 in **043e43c**, precedente manutenzione skill/lockfile in **a07e905**. Push ordinario riuscito, SHA codice locale/remoto `043e43c8833eae6e565daba918dc22276c357243` verificati identici. Note dell'operazione nel successivo commit documentale. T06 e controlli specifici hardware W05/W06 restano aperti; nuovi commit/push solo su nuova richiesta.
- Modifiche preesistenti preservate e pubblicate: rimozione skill deploy-an-app, lockfile e note documentali. Nessuna release creata; APK/credenziali/checklist private e NEXT_PROMPT restano locali ignorati. W06 era già pubblicata: il prompt iniziale descriveva uno stato precedente.
- Implementazione W07 pubblicata: endpoint playlist CRUD/POST form, star/unstar, testi e scrobble; repository e UI playlist con azioni da selezione; cuori per brani/album/artisti; salvataggio ordine visualizzato della coda (shuffle compreso), nome mixtape persistito; dettagli/testi nel player; cronologia locale e tracker nel servizio con stato d'ascolto durevole e invio con ACK/retry.
- Room **v7**, schema esportato: playlist.readOnly, queue_state.mixtapeName, lyrics, scrobble_event, listening_state. Migrazione esplicita 6→7. Il primo test ha trovato DEFAULT NULL mancante per mixtapeName: corretto; migrazioni 4/5/6→7 poi passate.
- Server reale: create/rename/delete, append A/B/A e ricostruzione ordine via POST verificati con playlist dedicate, poi eliminate. Star/unstar brano/album/artista verificati e ripristinati. `playlistRole=false` non impedisce CRUD su Navidrome; usare owner + readonly, non quel flag. Un testo reale non sincronizzato trovato su campione 60 (1/60), parsing/sincronia con fixture. Preferiti Room aggiornati dopo ACK; nessun ottimismo che lasci falsi successi offline.
- **Limite idempotenza verificato**: due scrobble con stesso id+time producono playCount=2 su Navidrome 0.62.0. Dedup locale tramite eventId e ACK durevole; non promettere exactly-once se il server accetta ma la risposta si perde. I due ascolti di probe restano sull'account test (non annullabili via API).
- Verifiche: **84/84 JVM** con API reali, build app/androidTest riuscita e lint **0 errori/22 avvisi**. Android **9/9** storage+servizio PCM fixture (78,308 s): migrazioni, pausa/seek/restart senza autoplay, stessa occorrenza senza duplicato, repeat distinto, offline503→ACK/no reinvio. Quattro test storage W07 passati con controllo owner/readonly/stale aggiunto. UI server **1/1**, estesa con selezione→playlist e ripetuta **1/1 (108,406 s)**: CRUD/copie/ordine, salva coda shuffle e nome, testo reale. Corretto crash reale Compose con chiavi duplicate prima degli UUID; le chiavi provvisorie ora includono indice.
- Regressioni W05/W06: **7/9 nella combinata**, restore e player UI falliti; entrambi **passati isolati senza cambio motore** (11,546 s e 152,848 s). Quindi 9/9 su esecuzioni separate, non suite unica verde. Timeout host a 600s durante combinata, conclusione letta dai log TestRunner. ANR System UI nell'AVD; nessuna nuova prova Motorola.
- Ultimo affinamento verificato: snapshot.profileId dalla URI logica, dialog coda chiusi/guardati al cambio profilo; ultimo test UI **1/1 (152,373 s)**. Corretto nel test il confronto shuffle: catturare l'ordine visualizzato al salvataggio, non la timeline provvisoria subito dopo toggle. Restore senza autoplay passato anche nell'ultima esecuzione separata. **RealScrobbleRecoveryTest 1/1 (3,573 s)**: evento injected, primo tentativo HTTP503 fixture poi ACK/playCount reali Navidrome (+1), nuovo sender senza reinvio. Provenienza dal player verificata separatamente con PCM nel servizio.
- Cleanup finale: AVD spento, copia env rimossa e assenza verificata; nessun marker seed/focus presente. Test finali dopo cold boot/precompilazione su stesso AVD con override runtime temporanei 2048 MiB/2 core per carico host (nessuna modifica persistente a config). Script/JSON probe nel temp rimossi, file originali local/ e checklist W05/W06 preservati. APK debug SHA256 **6713DD5BCC98E069D0963648CF1BA153F941472CFF592556EF1C483A26E5C40A**; invariato per la pubblicazione. Nessuna release.
- **Prossima azione precisa**: nella nuova sessione leggere NEXT_PROMPT.md e wave08, poi avviare W08-T01 riusando Room v7, local_audio e servizio/bridge. Conservare **W07-T06**: predisporre playlist condivisa readonly di un secondo account Navidrome di test e verificare il rifiuto reale update/delete quando disponibile. La fixture non sostituisce il caso server. APK standard aggiornato e checklist privata W07, originali W05/W06 preservati; residui hardware aperti.

Il punto di ripresa precedente seguente è storico, superato dall'incremento W07 sopra.

## Handoff precedente — W07-T01 (storico), con controlli W05/W06 residui conservati

- L'utente riferisce **«mi sembra tutto ok»** sul player e chiede il prompt per implementare W07 in un'altra sessione. Registrato come riscontro generale positivo, senza dedurre prove puntuali TalkBack/timer/chiamate non dichiarate. Prossimo sviluppo **W07-T01**, poi T02–T06; questa sessione prepara soltanto il prompt, non implementa W07. W06-T06 e W05-T05/T07 restano formalmente aperti per i controlli specifici residui e non bloccano le dipendenze W07 (W04/W05 disponibili).

- **W06-T01–T05 completati**: rotta player TPS-L2, cassetta/bobine/cover reale ingrandibile, etichette album per ID/playlist per origine/Mixtape, trasporto e seek prolungato protetti al cambio occorrenza, volume STREAM_MUSIC, repeat/shuffle/coda, timer/suggerimenti W05 riusati. Aptica disattivabile on e clic off di default. IT/EN, temi, telefono/orizzontale e coda affiancata su viewport tablet simulata.
- **W06-T06** parziale: contrasto JVM, semantica via Android, font 200% e target touch implementati/verificati; riscontro generale utente positivo, ma TalkBack reale, aptica/clic e bobine/riduzione movimento non attestati singolarmente. W05-T05/T07 ancora aperti: timer 15 min a schermo spento con fade, chiamate/pausa manuale e altra app audio. Un solo profilo e cuffie cablate/USB-C non provati.
- **Room v6**: `queue_entry.originName` nullable; migrazione 5→6 e schema 6.json. Origine playlist per occorrenza persiste tra riavvii; aggiunte senza origine rimuovono l'etichetta playlist omogenea. Dettaglio playlist offre riproduzione dalla cache completa, mantiene copie/ordine e segnala indice incompleto. CRUD/nome mixtape/salvataggio playlist restano W07. Playlist positiva **fixture locale con brani/audio reali**, non prova di playlist sul server reale.
- Ultime verifiche: build app/androidTest e **78/78 JVM** con login reale, lint **0 errori/22 avvisi**. Android **7/7 player/preferenze/storage** e **9/9 player+regressioni su esecuzioni separate** (suite unica 7 passati/2 falliti; timer e fixture suggerimenti ripetuti separati e passati). Include guardia cambio traccia durante hold, azione playlist con duplicati e audio reale, restore profilo/servizio zero HTTP/autoplay, migrazioni 4→6/5→6. Selezioni **2/2** nella fase iniziale W06. Nessuna nuova prova Motorola.
- Corretti bug layout: rail `fillMaxSize` consumava larghezza; NavHost ora weighted. Mini-bar rispetta inset laterale Android in landscape. Coda inline senza scroll verticale infinito annidato; bobine frame clock solo `isPlaying`, animation scales osservate. Hold fermato anche fuori RESUMED; chiave seek contiene UUID. Screenshot/dump luce/scuro, landscape, tablet simulato, EN e font 200% eseguiti; non equivalgono a TalkBack/hardware.
- APK debug aggiornato: `app/build/outputs/apk/debug/app-debug.apk`. Checklist privata **`local/PHONE_TESTS_W06_W05.md`**; W05 originale preservata. **Prossima azione precisa**: nella nuova sessione leggere il prompt W07 in NEXT_PROMPT.md, documenti e codice esistente playlist/preferiti/testi, verificare le API attuali e iniziare W07-T01. Raccogliere separatamente gli esiti manuali residui. **Codice/test/schema6/documenti W06 pubblicati su origin/main nel commit `93187a8`**, su richiesta esplicita: push ordinario riuscito, SHA locale/remoto verificati identici e working tree pulita subito dopo. Note della pubblicazione nel successivo aggiornamento documentale. I file privati e il prompt restano locali ignorati; nessuna release creata. Nuovi commit/push soltanto su nuova richiesta.
- Cleanup finale: env/marker emulatore rimossi con assenza verificata, override display/font/rotazione/animation scales ripristinati, AVD spento. Ignore dei file privati verificato; stash diagnostico ripristinato e assente.
- Manutenzione strumenti: rimossa su richiesta la skill locale `.agents/skills/deploy-an-app` con le sue reference e la voce in `skills-lock.json`, non pertinente al deployment Android nativo. Nessun cambio applicativo; ripresa W07-T01 invariata. Rimozione locale non committata.

## Baseline dell'handoff precedente (conservata, superata dal punto corrente)

- Task di ripresa **W06-T01**, poi **W06-T02–T06**: player nativo TPS-L2 completo sul motore W05 già operativo. L'utente autorizza esplicitamente a sviluppare W06 **prima** degli ultimi test hardware W05; il collaudo telefono sarà **congiunto W06 + W05 restante**. W05-T05/T07 restano aperti, nessuna rinuncia ai test né cambio di perimetro. Leggere DESIGN/ARCHITECTURE prima della UI; prompt locale NEXT_PROMPT aggiornato. Questa sessione prepara l'handoff, non avvia codice W06.
- Base W05 verificata: **T06 completo**, timer tempo/brano/album/coda e suggerimenti opt-in, **67/67 JVM**, **3/3 Android dedicati + 5/5 regressioni** su esecuzioni separate, lint **0/22**. Preservare persistenza/coda/volume Android/focus e collegare i nuovi controlli allo stesso servizio/sessione, senza duplicare il motore.
- **Prossima azione precisa**: nella nuova sessione partire da W06-T01, ispezionare l'UI esistente e DESIGN, implementare W06 con verifiche automatiche durante gli incrementi. Al termine preparare la checklist congiunta che conserva timer 15 minuti/schermo spento, chiamate e altra app audio. Esiti W05: 1–4 e 7 tutti OK, Bluetooth e pulsante/continuazione suggerimenti OK; un solo profilo, cambio non provato; cuffie cablate non provate; timer «credo OK» da validare. Emulatore spento, env rimossi. T05/T07 restano `[ ]`.
- Fase: **sviluppo W05 pubblicato su GitHub fino a `acf6c1a`**, su richiesta dell'utente: T01/T02 `74be2d0`, T03 `afe7f9f`, T04+T05 parte automatica `1252406`, checkpoint T06 `0a9e844`, completamento T06 e riscontri hardware `acf6c1a`. Push ordinario riuscito, SHA del codice locale/remoto verificati identici; note di pubblicazione nel presente aggiornamento documentale. T05/T07 restano aperti. Commit/push successivi solo su nuova richiesta esplicita.
- Baseline di ripresa **`ba37529`**, note della pubblicazione W05 incluse; HEAD/origin/main allineati e working tree pulita prima delle note di questo handoff. Note nuove locali non committate; nessun nuovo commit/push autorizzato.
- Verifiche T05: suite reale **1/1** (121 s) con gain sink 0,2→1 senza cambiare volume pubblico/Android, perdita transitoria con suppression/auto-resume e pausa manuale rispettata, perdita permanente senza autoplay, noisy sintetico via root AOSP con pausa/ripresa manuale, `media_session dispatch` in background e a display Asleep. Richiedente focus nello stesso processo/UID, non telefonata reale. `input keyevent` media intermittente sull'AVD, Bluetooth/tasti fisici/lockscreen hardware non attestati. Regressioni **9/9 su due esecuzioni** (refresh cache accessibilità nel test UI dopo failure), **58/58 JVM** con login reale, build/lint 0 errori/22 warning. Nessun receiver/callback cold-start da media key aggiunto o verificato. Env/marker rimossi; emulatore spento.
- Verifiche T04: build ok; **58/58 JVM** con login reale; Android **4/4 storage** (migrazione 4→5 da schema 4 reale, round-trip due profili, ordering/cascade/invalid), **1/1 switch+restart servizio senza richieste streaming**, **1+1 processo separato con force-stop**, **8/8 regressione motore** (incl. FLAC reale ripreso dalla posizione salvata solo dopo play esplicito; cambio profilo ora ripristina il profilo precedente), **2/2 selezioni**; lint 0 errori/22 warning. Room v5 con schema 5.json esportato. Emulatore spento; env/marker rimossi.
- Wave 05 ancora aperta: **T05/T07** (T06 implementato). Confermati sul telefono coda/riordino/shuffle, ripetizione, volume/tasti fisici, persistenza chiusura/riavvio senza autoplay, notifica/lockscreen e suggerimenti (pulsante/continuazione); azioni selezione album e fine coda/ripartenza già confermate. Restano timer completo e interruzioni; cambio profilo/cuffie cablate non provati. Nessuna chiusura dell'intera matrice W05.
- Verifiche T02: build/lint ok (0 errori/18 warning); 45/45 JVM (login reale); 6/6 Android sul server reale in emulatore con comandi UI esercitati da test (play/pausa, seek accessibile+touch, precedente/successivo, ripetizioni con wrap reale, fine coda e ripartenza, volume Android con gain player invariato, riconnessione senza autoplay); UI reale verificata via dump (barra sopra la navigazione, pannello con stato/tempi/ripetizione/volume). APK debug aggiornato nel percorso standard.
- Tasti volume fisici **confermati OK dall'utente nel collaudo 2026-10-14**; resta la limitazione dell'iniezione tasti sull'AVD. TalkBack manuale/font scale sui nuovi controlli restano non attestati (wave 06/12).
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

- D41 — Collaudo Motorola: bassi sistema sospesi su speaker o uscita ignota, avviso
  esplicito e preferenza/intensità mantenute per ritorno alle cuffie; approvazione
  utente «per adesso va bene soluzione suggerita, procedi». Nessun aumento automatico
  del volume né DSP alternativo. Fonte route: AudioTrack effettivo, non cuffie connesse.

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
- D32 — W05-T04: Room v5 `queue_state`/`queue_entry` con doppio CASCADE su profilo e indice unico entryId; `SavedQueue` solo stato musicale (mai URL/credenziali) con validazione strutturale; `QueueStore` scrittore unico FIFO dove load/flush fanno da barriera, update-only a layout invariato, failure per profilo senza perdere l'ultimo stato valido, write post-rimozione scartati. `PlaybackPersistence` nel servizio: snapshot su eventi+tick 1 s in riproduzione, save finale prima di cambio profilo/onDestroy, restore **senza prepare/play** (STATE_IDLE, zero HTTP/audio, offline-safe) con permutazione esatta reinstallata da `ServiceShuffle.restore`; load fallito = profilo senza owner (non sovrascrive coda corrotta; una nuova lettura riuscita al cambio profilo/ricreazione servizio ristabilisce l'owner, non un normale evento player). Play dopo restore riparte da capo se la posizione salvata è a fine traccia. UI: Continua ascolto reale con Riprendi ascolto; banner errore storage.
- D33 — W05-T05: focus/noisy restano automatici Media3; monitor locale volatile `AudioOutputMonitor` delega il sink predefinito e registra solo l'ultima scrittura gain (null prima della prima chiamata, reset onCreate/onDestroy), nessun campione audio/log/persistenza. Ducking si verifica al sink, non su `Player.volume`. Test focus con listener concorrente stesso UID, noisy protetto via `su 0` solo sull'AVD AOSP; media key via `cmd media_session dispatch` perché `input keyevent` intermittente. Non equiparare questi percorsi a chiamate/cuffie/Bluetooth/lockscreen reali. Fonte: ARCHITECTURE e WORKLOG T05.
- D34 — W05-T06: timer volatile sul main del servizio, elapsedRealtime, gain player temporaneo e STREAM_MUSIC invariato; niente ripristino dopo processo morto, cancel su profilo/lifecycle e transizione manuale. Fine album basato su ID/gruppo corrente, distinto da fine coda. Suggerimenti dispositivo DataStore off fino a opt-in; getSimilarSongs2 dopo ENDED, failure/empty senza fallback/autoplay; accettazione singola serializzata e vincolata al profilo/brano sorgente, proposte invalidate da modifica coda/disattivazione. Verifiche positive fixture delle raccomandazioni separate dallo streaming reale. Riferimenti: ARCHITECTURE e log T06.

## Ambiente noto e dati di riferimento

- D35 — W09 qualità: raw originale di default; Opus esplicito a bitrate scelto solo su cellulare/download opt-in. Nessuna cache a frammenti del transcode senza validatore/Range affidabile; copie ridotte complete nei download. Fallback raw solo fresh-open, URI cache/sessione logica. Motivo/esiti nel WORKLOG W09.
- D36 — W09 DSP: preferenze audio di dispositivo, processori PCM nella chain del sink con bypass byte-exact, audiofx su sessione condivisa. Crossfade nel servizio unico ancora parziale sui punti documentati; non confondere i test dei ramp con continuità del handover né capture PCM con risposta hardware.
- D37 — Supera la parte crossfade parziale di D36: player logico ExoPlayer stabile con instradamento della API pubblica, promozione del deck udibile senza seek, focus unico esterno ai deck e ledger overlap Roomv12 con ricevuta di promozione. Testare l'indice uscente nei confini timer; mantenere distinti stato logico, inviluppi/ducking e volume Android. Riferimenti: WORKLOG e ARCHITECTURE incremento2026-10-16.
- D38 — W11-T02 diagnostica: report costruito **solo da allowlist** in sezioni (APP/DEVICE/STORAGE/LIBRARY/DOWNLOADS/SYNC/PLAYER/CONNECTIONS/PREFERENCES) con Sources iniettabili; CONNECTIONS (URL/username) è opt-in esclusa dal default. Redactor come difesa in profondità con policy estesa ai parametri corti `p/t/s/u`, header Authorization/Cookie, secret etichettati, blob ≥48 char e query URL integrali. Export: SAF CreateDocument + FileProvider `com.walktape.app.files` sola lettura per chiamata. Nessuna raccolta/invio automatico.
- D39 — W11-T03 backup: formato `walktape.backup` v1 con DTO espliciti Gson e validazione totale **prima** di applicare; contenuti approvati solo profili senza credenziali, preferenze (senza `loaded`), download COMPLETED (→PAUSED all'import, mai download silenziosi), code con UUID/traversal/mixtape. Match import per URL+username (merge; credenziali/file esistenti intatti), collisioni nome→suffisso "(2)", coda del profilo attivo con media non toccata. Nuovo accesso obbligatorio via `ProfileRepository.login` (nuova chiave Keystore). Mai ledger ascolti/ACK/overlap/pending_op nel backup.
- D40 — W11-T04 update: client GitHub dedicato (redirect, header API 2022-11-28, gate Solo offline via `networkGate.offlineOnly(-1)` dopo awaitReady); 404=nessuna release onesto; decisioni versione solo su tag paragonabili; asset APK univoco (universal>ABI, ambiguità rifiutata); verifica fail-closed package/versionCode/firma **senza** mai proporre disinstallazione; installazione solo via installer Android con URI FileProvider; auto-check opt-in default off, max 1/die, check-only e non consuma il giorno in offline. Pipeline release/firma stabile restano W12.

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
- Checklist telefono wave 05 privata in `local/PHONE_TESTS_W05.md`: comandi in-app T02 (barra+pannello, seek, ripetizione, **tasti volume fisici**), coda T03, persistenza T04 (riavvio app, cambio profilo, nessun autoplay), MP3/FLAC, notifica, cambio profilo e schermo spento prolungato; interruzioni/cuffie/Bluetooth in T05/T07. **Discrepanza verificata**: `docs/PHONE_TESTS.md` è già tracciato da Git, non ignorato come indicato nel prompt; nessuna nuova prova telefono aggiunta a quel file. `NEXT_PROMPT.md` e la nuova checklist in `local/` sono ignorati.
- Repository GitHub per release, identità distributore/contatto privacy, chiave di firma (wave 12).
- Aggiornamenti patch disponibili (AGP 9.4.1, plugin compose 2.4.21): valutare al prossimo incremento.

## Stato dei file e del lavoro

- Progetto Android nella radice: `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, `gradlew(.bat)`, `app/` (sorgenti, risorse, schemi Room 1/2/3/4, test JVM e androidTest). APK debug verificato su emulatore (v0.1.0).
- Pubblicati: wave 03 in `c97fe7d`, wave 04 completa in `fa17b0e` + `26584bc`. Le eventuali note documentali successive al push restano locali; altri commit/push solo su nuova richiesta esplicita.
- Codice e riscontri W05 pubblicati nel completamento **`acf6c1a`** e nei quattro commit precedenti; note dell'operazione aggiornate nei documenti. APK T06 locale, nessuna release pubblicata. Room v5. Local/test-server.env, checklist privata, NEXT_PROMPT e artefatti esclusi. Restano collaudo timer 15 minuti e interruzioni hardware; controllare lo stato Git alla ripresa.
- `local/` e `.playwright-mcp/` esclusi da Git. `local.properties` escluso da Git. `NEXT_PROMPT.md` è un documento locale, escluso da Git su richiesta dell'utente (non pubblicato).
- Consultare `git status` alla ripresa: non assumere che file non tracciati siano eliminabili.

## Protocollo di handoff

Alla fine di un incremento: aggiornare checkbox verificate, aggiungere log con ID task e prove, poi aggiornare questa memoria con stato, blocchi e **prossima azione precisa**. Registrare qui nuove decisioni con ID, motivazione e riferimento al log; non accumulare output grezzi o segreti.
