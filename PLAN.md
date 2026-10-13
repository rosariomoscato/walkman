# WalkTape — piano di lavoro

## Come usare il piano

- Il perimetro approvato è in `SPEC.md`; le decisioni e il punto di ripresa sono in `PROJECT_MEMORY.md`.
- `[ ]` significa da fare; `[x]` significa completato e verificato. Un task parziale resta aperto.
- Gli ID dei task sono stabili: citarli in `WORKLOG.md`, anche per attività parziali e blocchi.
- Una wave è completata solo quando tutti i suoi task e il suo criterio di uscita sono verificati.
- Registrare verifiche, risultati e limiti nel log. Non equiparare codice scritto a comportamento verificato.
- Le wave sono incrementi di sviluppo, non riduzioni del perimetro: tutte le funzioni approvate restano nella prima versione.
- Nuove richieste e cambi di perimetro devono essere concordati; le idee future vanno nel backlog.
- Durante le wave aggiornare memoria e log, senza aspettare la fine del progetto.

## Wave 00 — Specifica e continuità

Dipendenze: approvazione della specifica, già ricevuta.

- [x] W00-T01 — Trascrivere il perimetro approvato in `SPEC.md`.
- [x] W00-T02 — Creare piano per wave con ID stabili e criteri di uscita.
- [x] W00-T03 — Creare registro delle attività e memoria persistente di progetto.
- [x] W00-T04 — Definire in `AGENTS.md` il protocollo di ripresa e aggiornamento.

Criterio di uscita: documenti coerenti e nessuna funzionalità applicativa dichiarata completata.

## Wave 01 — Fattibilità e ambiente Android

Dipendenze: wave 00.

- [x] W01-T01 — Verificare JDK, Android SDK, strumenti di build, emulatori e accesso ADB; registrare ciò che manca.
- [x] W01-T02 — Verificare release stabili e compatibilità di Kotlin, Compose, Media3 e librerie selezionate; decidere minSdk e targetSdk.
- [x] W01-T03 — Verificare le API di Navidrome/OpenSubsonic necessarie: autenticazione, libreria, playlist, preferiti, testi, scrobbling e transcoding.
- [x] W01-T04 — Verificare fattibilità di gapless, crossfade, ReplayGain, equalizzatore ed effetto cassetta nelle combinazioni previste.
- [x] W01-T05 — Verificare Android Auto, APK distribuiti fuori dal Play Store e requisiti di abilitazione e test.
- [x] W01-T06 — Verificare raggiungibilità di `http://localhost:4533`; predisporre account di test e configurazione locale esclusa da Git.
- [x] W01-T07 — Documentare architettura, dipendenze scelte, risultati della ricerca e decisioni tecniche; discutere eventuali incompatibilità col perimetro.

Criterio di uscita: percorso tecnico documentato, ambiente di build disponibile e vincoli non risolti esplicitamente registrati.

## Wave 02 — Fondamenta native e design system

Dipendenze: wave 01.

- [x] W02-T01 — Creare progetto Android nativo nella radice attuale, preservando documentazione, skill e configurazione OpenCode.
- [x] W02-T02 — Impostare architettura, archivio locale con migrazioni, dipendenze e gestione della configurazione.
- [x] W02-T03 — Scrivere `DESIGN.md`: TPS-L2, token, tipografia, materiali, spaziature, stati, accessibilità e temi.
- [x] W02-T04 — Creare componenti Compose condivisi e navigazione di base, con layout adattivi.
- [x] W02-T05 — Predisporre italiano/inglese, preferenze del dispositivo e diagnostica locale con oscuramento dei dati sensibili.
- [x] W02-T06 — Verificare compilazione, avvio, navigazione, temi e layout su emulatore disponibile.

Criterio di uscita: shell nativa avviabile, design documentato e nessun dato dimostrativo presentato come dato reale del server.

## Wave 03 — Profili e connessione reale

Dipendenze: wave 02.

- [x] W03-T01 — Implementare client Navidrome/OpenSubsonic, autenticazione e gestione degli errori di protocollo e rete.
- [x] W03-T02 — Implementare più profili con uno attivo, credenziali protette e isolamento dei dati.
- [x] W03-T03 — Creare configurazione guidata e verifica connessione; supportare HTTPS e HTTP nei contesti concordati.
- [ ] W03-T04 — Implementare condivisione/scansione QR di configurazione senza password e blocco biometrico opzionale.
- [x] W03-T05 — Implementare cambio/rimozione profilo, conferme per i download e ripristino dello stato separato.
- [ ] W03-T06 — Verificare login reale, credenziali errate, server irraggiungibile, VPN assente e isolamento fra due profili.

Criterio di uscita: collegamento reale al server e gestione dei profili verificati senza esposizione di credenziali.

## Wave 04 — Libreria e ricerca

Dipendenze: wave 03.

- [x] W04-T01 — Implementare indicizzazione progressiva, aggiornamento e consultazione locale della libreria.
- [x] W04-T02 — Creare “La mia musica”, album, artisti, brani e generi con copertine gestite tramite cache limitata.
- [x] W04-T03 — Implementare ricerca, filtri e ordinamenti persistenti concordati.
- [x] W04-T04 — Gestire compilation, artisti dell'album, dischi e numeri di traccia correttamente.
- [x] W04-T05 — Implementare selezione multipla e predisporre le azioni contestuali; rendere disponibili solo i comandi operativi, integrati nelle wave 05, 07 e 08.
- [x] W04-T06 — Verificare navigazione e prestazioni con la raccolta di circa 10.000 brani e comportamento offline.

Criterio di uscita: libreria reale navigabile e ricercabile, senza caricamento indiscriminato della raccolta in memoria; verifiche prestazionali registrate.

Completamento 2026-10-13 (secondo incremento): Panoramica con sezioni concordate (continua ascolto/aggiunti/ascoltati/preferiti/download/playlist, stati vuoti onesti per i servizi delle wave successive), playlist in lettura per profilo con ordine e ripetizioni preservati, ricerca unificata su tutti i tipi, filtri genere/anno/disponibilità offline su query parametrizzate, ordinamenti persistenti incl. playlist. Verifiche: 39/39 test JVM (login reale incluso), 12/12 + 4/4 + 1/1 test Android, regressione UI completa su libreria reale (tab, dettagli, ricerca, selezione, ordinamenti, playlist, filtri), fixture 10.000 brani: pagina filtrata di 80 in ~487 ms a freddo / ~205 ms in media a caldo (emulatore AOSP sotto carico). Account di test senza playlist sul server: pipeline playlist verificata con fixture locale e API reali di lista; dettaglio playlist reale da ripetere quando esisterà una playlist di prova (wave 07). Consultazione offline già verificata nel primo incremento; le liste restano locali. **Wave 04 completata.**

## Wave 05 — Motore di riproduzione e coda

Dipendenze: wave 04.

- [x] W05-T01 — Implementare servizio di riproduzione e sessione multimediale con streaming MP3/FLAC.
- [x] W05-T02 — Implementare play/pausa, precedente/successivo, seek, ripetizione e volume di sistema.
- [ ] W05-T03 — Implementare coda modificabile, azioni dalla selezione multipla, shuffle senza ripetizioni nella selezione e ripristino dell'ordine.
- [ ] W05-T04 — Persistire coda e posizione per profilo; ripristinare senza avvio automatico.
- [ ] W05-T05 — Gestire audio focus, chiamate, scollegamento cuffie e comandi Bluetooth/notifica/lockscreen.
- [ ] W05-T06 — Implementare arresto a fine coda, continuazione suggerita opzionale e timer con dissolvenza.
- [ ] W05-T07 — Verificare riproduzione a schermo spento e interruzioni su emulatore e Motorola, distinguendo gli esiti.

Criterio di uscita: ascolto end-to-end reale e comportamento della sessione/coda verificato, comprese le interruzioni.

Incremento 2026-10-13: **W05-T01 completato**. MediaSessionService/ExoPlayer con DataSource OkHttp autenticato per richiesta, originali `format=raw`, URI di coda senza segreti e vincolate al profilo; arresto/svuotamento al cambio profilo. Riproduzione da dettaglio album e da traccia. Build debug/androidTest + lint riusciti (0 errori, 18 warning), 45/45 JVM con login reale, 6/6 Android (5 DataSource con fixture HTTP + 1 end-to-end MP3/FLAC sul server reale via emulatore, pausa e cambio profilo inclusi). UI reale: album con coda di 15 brani, sessione PLAYING; breve prova background/schermo spento con posizione in avanzamento e servizio foreground confermati via dumpsys. Non è prova Motorola né ascolto qualitativo. T02–T07 restano aperti: la configurazione base di focus/becoming-noisy e i comandi predefiniti Media3 non completano tutte le verifiche di quei task. Checklist hardware privata: `local/PHONE_TESTS_W05.md`. Prossima azione: **W05-T02**.

Riscontro hardware utente 2026-10-13 (**W05-T01/T05/T07**, reachability W03-T06): Motorola G55 5G, Android 16, **Tailscale** — MP3 OK, FLAC OK, pausa/ripresa dalla notifica OK, schermo spento 10 minuti OK. Comandi interni all'app assenti nell'APK T01, esigenza confermata per **W05-T02**. T05/T07 restano `[ ]`: chiamate/interruzioni/cuffie/Bluetooth/lockscreen completo non ancora verificati sul telefono. LAN e cambio profilo hardware non riferiti. Evidenza fornita dall'utente, distinta dai test eseguiti dall'agente.

Completamento 2026-10-13: **W05-T02 completato**. Barra di trasporto nell'app (precedente/play-pausa/successivo, metadati) e pannello con seek preciso (accessibile e touch, gesti non applicati a tracce cambiate), ripetizione off/brano/coda con effetti reali sulla coda, volume Android via slider sincronizzato con il sistema; riallineamento onStop/onStart della sessione senza avvio automatico. Verifiche: build/lint ok (0 errori/18 warning), 45/45 JVM (login reale), 6/6 Android sul server reale in emulatore (MP3+FLAC, comandi UI↔sessione, seek, ripetizioni, fine coda e ripartenza, volume Android, cambio profilo); UI reale su emulatore confermata via dump (barra + pannello + stato "In pausa"/ripetizione "brano"). **Riscontro utente: i comandi in-app funzionano sul Motorola G55 5G / Android 16.** Limitazione registrata: comandi volume dispositivo non esposti dal controller locale; iniezione tasti volume non efficace sull'AVD — tasti fisici da verificare sul Motorola. Prossima azione: **W05-T03**.

## Wave 06 — Player TPS-L2

Dipendenze: wave 05; design wave 02.

- [ ] W06-T01 — Realizzare scocca blu/argento/arancione e tasti meccanici con marchio WalkTape.
- [ ] W06-T02 — Implementare cassetta, bobine, etichetta dinamica e copertina ingrandibile.
- [ ] W06-T03 — Collegare tutti i tasti al motore reale; aggiungere pressioni prolungate e avanzamento preciso.
- [ ] W06-T04 — Implementare feedback aptico e clic opzionali, rispettando pausa e riduzione movimento.
- [ ] W06-T05 — Integrare accesso al player da ogni schermata, coda e layout telefono/tablet/orizzontale.
- [ ] W06-T06 — Verificare contrasto, lettore schermo, caratteri ingranditi, touch target e coerenza con `DESIGN.md`.

Criterio di uscita: player riconoscibile come reinterpretazione TPS-L2, utilizzabile e collegato alle funzioni reali.

## Wave 07 — Playlist, preferiti, testi e cronologia

Dipendenze: wave 04 e 05.

- [ ] W07-T01 — Implementare CRUD delle playlist, riordino, azioni dalla selezione multipla e rispetto delle autorizzazioni del server.
- [ ] W07-T02 — Implementare preferiti per brani/album/artisti e sincronizzazione con Navidrome.
- [ ] W07-T03 — Salvare la coda come playlist e supportare nome personalizzato della mixtape.
- [ ] W07-T04 — Implementare testi disponibili sul server e dettaglio tecnico/musicale del brano.
- [ ] W07-T05 — Implementare cronologia e comunicazione degli ascolti, con eventi offline e prevenzione dei duplicati.
- [ ] W07-T06 — Verificare le modifiche con account di test e playlist dedicate, inclusi errori e permessi insufficienti.

Criterio di uscita: operazioni persistenti riscontrabili sul server, testi reali e nessun inoltro duplicato degli ascolti nei casi verificati.

## Wave 08 — Download e sincronizzazione offline

Dipendenze: wave 05 e 07.

- [ ] W08-T01 — Implementare download di brani/album/playlist, azioni dalla selezione multipla e metadati di appartenenza ai download.
- [ ] W08-T02 — Implementare lavoro persistente, avanzamento, pausa/ripresa/annullamento, retry e vincoli Wi-Fi/rete mobile.
- [ ] W08-T03 — Implementare cache audio da 1 GB, copertine da 250 MB, limiti modificabili e contabilità dello spazio.
- [ ] W08-T04 — Implementare modalità solo offline, priorità copie locali, gestione dei brani indisponibili e riconnessione.
- [ ] W08-T05 — Sincronizzare playlist scaricate preservando i brani conservati anche da altre origini e i download singoli rimossi dal server.
- [ ] W08-T06 — Implementare modifiche offline a playlist/preferiti, coda delle operazioni e risoluzione esplicita dei conflitti.
- [ ] W08-T07 — Verificare perdita rete, riavvio, spazio insufficiente, duplicati, cancellazioni e isolamento fra profili.

Criterio di uscita: ascolto e gestione offline verificati con riconnessione, senza perdita silenziosa delle modifiche.

## Wave 09 — Qualità ed elaborazione audio

Dipendenze: wave 08; ricerca audio wave 01.

- [ ] W09-T01 — Implementare qualità per rete/profilo, download originali o ridotti e comportamento quando il transcoding manca.
- [ ] W09-T02 — Implementare gapless e crossfade regolabile inizialmente spento.
- [ ] W09-T03 — Implementare ReplayGain album/brano, inizialmente spento, e gestione dei metadati mancanti.
- [ ] W09-T04 — Implementare equalizzatore e rinforzo bassi opzionali, inizialmente spenti.
- [ ] W09-T05 — Implementare effetto cassetta opzionale con lieve fruscio e oscillazione, inizialmente spento.
- [ ] W09-T06 — Verificare MP3/FLAC, transizioni, seek, combinazioni di effetti, clipping e ascolto Bluetooth sul dispositivo reale.

Criterio di uscita: impostazioni audio realmente operative, suono pulito predefinito e limiti delle combinazioni documentati e concordati.

## Wave 10 — Android Auto

Dipendenze: wave 08 e 09.

- [ ] W10-T01 — Esporre catalogo, playlist, download e ricerca alla sessione compatibile Android Auto.
- [ ] W10-T02 — Implementare navigazione e comandi secondo i vincoli Android Auto, senza replicare la scocca TPS-L2 in auto.
- [ ] W10-T03 — Gestire connessione assente, cambio rete e continuità tra telefono e auto.
- [ ] W10-T04 — Documentare e verificare l'abilitazione di APK esterni al Play Store nell'ambiente di test.
- [ ] W10-T05 — Verificare con gli strumenti Auto disponibili e con prove dell'utente in auto, registrando separatamente gli esiti.

Criterio di uscita: percorso di ascolto in auto verificato; eventuale prova hardware mancante resta un blocco esplicito.

## Wave 11 — Impostazioni, backup e aggiornamenti

Dipendenze: wave 08–10.

- [ ] W11-T01 — Completare impostazioni di dispositivo/profilo e schermata sistema con stati e azioni operative.
- [ ] W11-T02 — Implementare report diagnostico esportabile con esclusione di segreti e controllo dei dati inclusi.
- [ ] W11-T03 — Implementare export/import di backup senza credenziali/audio, incluse coda, posizione e lista da riscaricare.
- [ ] W11-T04 — Implementare controllo aggiornamenti GitHub Releases, novità, download e conferma Android; controllo automatico facoltativo.
- [ ] W11-T05 — Verificare importazioni invalide, cambio telefono, aggiornamento dell'app e migrazioni senza perdita dei dati.

Criterio di uscita: l'utente può diagnosticare, trasferire configurazione e aggiornare l'app nei percorsi verificati.

## Wave 12 — Verifica complessiva e distribuzione

Dipendenze: tutte le wave precedenti.

- [ ] W12-T01 — Eseguire build, analisi statica e test significativi per isolamento, coda, sincronizzazione, download e migrazioni.
- [ ] W12-T02 — Eseguire la matrice end-to-end con server reale, rete/offline, MP3/FLAC, Motorola Android 15/16 e Android Auto.
- [ ] W12-T03 — Verificare prestazioni, batteria, storage, accessibilità, lingue e tutti gli impegni di `SPEC.md`.
- [ ] W12-T04 — Preparare icona originale, README, guida installazione/Tailscale/Auto, privacy, licenza MIT e note di rilascio.
- [ ] W12-T05 — Predisporre firma e processo riproducibile per gli APK, mantenendo chiavi e credenziali fuori da Git.
- [ ] W12-T06 — Preparare APK firmato; pubblicare repository/release solo con richiesta esplicita dell'utente per tali operazioni.
- [ ] W12-T07 — Chiudere la verifica del perimetro; aggiornare log/memoria con esiti, problemi residui e backlog.

Criterio di uscita: prima versione conforme al perimetro approvato; i controlli non eseguiti e i blocchi non sono presentati come superati.

## Backlog — fuori dalla prima versione

- [ ] B01 — Audiolibri e ripresa per singolo contenuto.
- [ ] B02 — Google Cast e DLNA.
- [ ] B03 — Libreria di file locali indipendenti da Navidrome.
- [ ] B04 — Skin ispirate ad altri Walkman.
- [ ] B05 — Sincronizzazione automatica di coda e posizione tra telefoni.
- [ ] B06 — Supporto dichiarato e verificato ad altri server OpenSubsonic.
- [ ] B07 — Pubblicazione su Google Play.
- [ ] B08 — Valutare storage dei download su microSD.

Il backlog non implica approvazione di sviluppo: ogni elemento va definito prima di iniziarlo.
