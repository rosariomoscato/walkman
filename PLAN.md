# WalkTape — piano di lavoro

## Come usare il piano

- Il perimetro approvato è in `SPEC.md`; le decisioni e il punto di ripresa sono in `PROJECT_MEMORY.md`.
- `[ ]` significa da fare; `[x]` significa completato e verificato. Un task parziale resta aperto.
- Gli ID dei task sono stabili: citarli in `WORKLOG.md`, anche per attività parziali e blocchi.
- Una wave è completata solo quando tutti i suoi task e il suo criterio di uscita sono verificati.
- Registrare verifiche, risultati e limiti nel log. Non equiparare codice scritto a comportamento verificato.
- Le wave sono incrementi di sviluppo, non riduzioni del perimetro: tutte le funzioni approvate restano nella prima versione.
- Nuove richieste e cambi di perimetro devono essere concordati; le idee future vanno nel backlog.

Manutenzione strumenti 2026-10-15: rimossa su richiesta la skill di progetto `deploy-an-app` e la relativa voce nel lockfile. Nessun cambio ai task funzionali o al punto di ripresa W07-T01.
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
- [x] W05-T03 — Implementare coda modificabile, azioni dalla selezione multipla, shuffle senza ripetizioni nella selezione e ripristino dell'ordine.
- [x] W05-T04 — Persistire coda e posizione per profilo; ripristinare senza avvio automatico.
- [ ] W05-T05 — Gestire audio focus, chiamate, scollegamento cuffie e comandi Bluetooth/notifica/lockscreen.
- [x] W05-T06 — Implementare arresto a fine coda, continuazione suggerita opzionale e timer con dissolvenza.
- [ ] W05-T07 — Verificare riproduzione a schermo spento e interruzioni su emulatore e Motorola, distinguendo gli esiti.

Criterio di uscita: ascolto end-to-end reale e comportamento della sessione/coda verificato, comprese le interruzioni.

Incremento 2026-10-13: **W05-T01 completato**. MediaSessionService/ExoPlayer con DataSource OkHttp autenticato per richiesta, originali `format=raw`, URI di coda senza segreti e vincolate al profilo; arresto/svuotamento al cambio profilo. Riproduzione da dettaglio album e da traccia. Build debug/androidTest + lint riusciti (0 errori, 18 warning), 45/45 JVM con login reale, 6/6 Android (5 DataSource con fixture HTTP + 1 end-to-end MP3/FLAC sul server reale via emulatore, pausa e cambio profilo inclusi). UI reale: album con coda di 15 brani, sessione PLAYING; breve prova background/schermo spento con posizione in avanzamento e servizio foreground confermati via dumpsys. Non è prova Motorola né ascolto qualitativo. T02–T07 restano aperti: la configurazione base di focus/becoming-noisy e i comandi predefiniti Media3 non completano tutte le verifiche di quei task. Checklist hardware privata: `local/PHONE_TESTS_W05.md`. Prossima azione: **W05-T02**.

Riscontro hardware utente 2026-10-13 (**W05-T01/T05/T07**, reachability W03-T06): Motorola G55 5G, Android 16, **Tailscale** — MP3 OK, FLAC OK, pausa/ripresa dalla notifica OK, schermo spento 10 minuti OK. Comandi interni all'app assenti nell'APK T01, esigenza confermata per **W05-T02**. T05/T07 restano `[ ]`: chiamate/interruzioni/cuffie/Bluetooth/lockscreen completo non ancora verificati sul telefono. LAN e cambio profilo hardware non riferiti. Evidenza fornita dall'utente, distinta dai test eseguiti dall'agente.

Completamento 2026-10-13: **W05-T02 completato**. Barra di trasporto nell'app (precedente/play-pausa/successivo, metadati) e pannello con seek preciso (accessibile e touch, gesti non applicati a tracce cambiate), ripetizione off/brano/coda con effetti reali sulla coda, volume Android via slider sincronizzato con il sistema; riallineamento onStop/onStart della sessione senza avvio automatico. Verifiche: build/lint ok (0 errori/18 warning), 45/45 JVM (login reale), 6/6 Android sul server reale in emulatore (MP3+FLAC, comandi UI↔sessione, seek, ripetizioni, fine coda e ripartenza, volume Android, cambio profilo); UI reale su emulatore confermata via dump (barra + pannello + stato "In pausa"/ripetizione "brano"). **Riscontro utente: i comandi in-app funzionano sul Motorola G55 5G / Android 16.** Limitazione registrata: comandi volume dispositivo non esposti dal controller locale; iniezione tasti volume non efficace sull'AVD — tasti fisici da verificare sul Motorola. Prossima azione: **W05-T03**.

Completamento 2026-10-13: **W05-T03 completato**. Coda visibile nell'ordine d'ascolto, tap sull'occorrenza, rimozione, menu Sposta su/giù e azioni accessibili; copie duplicate con UUID distinti. Shuffle nativo gestito nel servizio, corrente mantenuto senza ricaricarlo, ordine originale conservato, sopravvive al rebind UI; nuovi gruppi NEXT/APPEND rispettano la posizione richiesta. Azioni multiple Album/Brani risolte da Room per profilo (anche fuori dalle pagine caricate); NEXT/APPEND non avviano coda vuota, NOW avvia. Build APK/app-test, **53/53 JVM** (login reale, fixture shuffle 10.000 occorrenze ~90 ms JVM), **7/7 Android** del motore (343 s, regressione audio/controlli + identità/shuffle/menu) + **2/2 Android** su selezioni dalla libreria reale (118 s, album in ordine disco/traccia e brani NEXT/NOW/APPEND), lint 0 errori/22 warning. Prototipo drag non verificato rimosso: riordino operativo dal menu, come richiesto dalla specifica. Nessuna prova Motorola del nuovo incremento; checklist privata aggiornata. Prossimo task **W05-T04**.

Completamento 2026-10-13: **W05-T04 completato**. Room v5 (queue_state/queue_entry con CASCADE su profilo, migrazione esplicita 4→5, schema esportato): coda canonica con occorrenze duplicate, permutazione shuffle, occorrenza corrente, posizione, ripetizione. `QueueStore` con scrittore FIFO (load/flush come barriere), aggiornamento leggero a layout invariato, failure per profilo senza sovrascrivere l'ultimo stato valido, write post-rimozione scartati. `PlaybackPersistence` nel servizio: snapshot su eventi+1 s in riproduzione, save finale prima di cambio profilo/onDestroy, restore **senza prepare/play** (idle, nessuna rete né audio), shuffle ripristinato dalla permutazione salvata; coda corrotta non sovrascritta. Home "Continua ascolto" reale con Riprendi ascolto; banner errore storage. Verifiche: build ok, **58/58 JVM** (login reale), Android **4/4 storage** (migrazione da schema 4 reale, round-trip due profili, ordering/cascade/invalid), **1/1 switch+restart servizio** (zero richieste streaming), **1+1 processo separato con force-stop**, **8/8 regressione motore** incl. nuovo test FLAC reale salvato in posizione e ripreso solo su play esplicito, **2/2 selezioni**; lint 0 errori/22 warning. Emulatore spento, env/marker rimossi. Prove telefono da fare; prossima azione: **W05-T05**.

Incremento 2026-10-14: **W05-T05 verificato per la parte emulatore, resta `[ ]` per hardware**. Focus gestito da Media3: ducking con gain effettivo al sink 0,2→1 e volume Android/pubblico invariati; perdita transitoria con suppression e auto-resume, pausa manuale che annulla la ripresa, perdita permanente senza autoplay. Evento protetto AUDIO_BECOMING_NOISY sintetico via root sull'AVD AOSP → pausa e ripresa solo manuale. Comandi Android `media_session dispatch` play/pausa/avanti/indietro in background; play/pausa e avanzamento reale a display Asleep. Nuovo monitor locale volatile delle scritture gain al sink, senza elaborazione audio. Verifiche: suite T05 **1/1** (121 s), regressioni pertinenti **9/9 su due esecuzioni** (8 passati nella prima, test UI ripetuto dopo refresh cache accessibilità: 1/1), **58/58 JVM** con login reale, build e lint 0 errori/22 warning. `input keyevent` multimediali intermittenti sull'AVD: non attestano tasti fisici/Bluetooth. Chiamate, cuffie fisiche, route Bluetooth e lockscreen Motorola ancora da provare (`local/PHONE_TESTS_W05.md`); T07 aperto. Env/marker rimossi, emulatore spento. Prossimo sviluppo indipendente: **W05-T06**.

Checkpoint 2026-10-14 **W05-T06 ancora `[ ]`**: bozza timer tempo/brano/coda e suggerimenti opt-in; fine album distinta ancora mancante. Build e 67/67 JVM riusciti; Android 1/3 passato, due correzioni da ricompilare/verificare. Le prove hardware complessive saranno eseguite alla fine di W05, su richiesta dell'utente; nessuna chiusura anticipata di T05/T07.

Completamento successivo 2026-10-14: **W05-T06 verificato** dopo il checkpoint `0a9e844`. Timer a tempo (15/30/60/90 min), fine brano, **fine album distinto** e fine coda, dissolvenza gain negli ultimi 3 s, annullamento e ripristino gain, countdown monotono senza controller UI. Suggerimenti DataStore opt-in default off, risposta getSimilarSongs2 secondo disponibilità server, conferma esplicita per continuare, ranking/deduplica e vincoli profilo/sorgente/coda terminata. Verifiche finali: build app/androidTest, **67/67 JVM**, **3/3 Android dedicati + 5/5 regressioni pertinenti su esecuzioni separate**, lint **0 errori/22 warning**. Raccomandazione positiva fixture con ID reale, accettazione → **FLAC effettivamente riprodotto da Navidrome**, mai byte audio finti. T05/T07 ancora aperti per hardware; prossimo passo **collaudo complessivo finale W05 sul Motorola**, checklist privata aggiornata. Il completamento dopo il checkpoint resta locale non committato.

Riscontro utente 2026-10-14, collaudo W05: azioni selezione album Aggiungi in coda/Dopo il corrente/Riproduci ora e fine coda con ripartenza dal primo brano **OK**. Gli altri punti del collaudo hardware non sono ancora attestati; T05/T07 restano aperti.

Riscontro successivo sulla checklist numerata: **1–4 e 7 OK tutto**, **Bluetooth OK**. Coda/riordino/shuffle, ripetizione, volume/tasti fisici, persistenza chiusura/riavvio e notifica/lockscreen confermati. Un solo profilo (cambio non provato); timer preliminare «credo OK», 15 minuti ancora da provare; chiamate in attesa, altra app audio/cuffie cablate non riferite, suggerimenti da guidare. **T05/T07 restano aperti**.

Riscontro successivo: **punto 10 suggerimenti OK**, l'utente conferma che il pulsante compare e funziona sul proprio server/account dopo la guida. T05/T07 ancora aperti per timer/interruzioni finali; nessun esito nuovo per gli altri punti pendenti.

## Wave 06 — Player TPS-L2


Dipendenze: wave 05; design wave 02.

Sequenza approvata dall'utente 2026-10-14: sviluppare **W06-T01–T06** sul motore W05 disponibile e collaudare poi il player **insieme ai punti hardware W05 ancora aperti**. Questa autorizzazione anticipa lo sviluppo W06 rispetto alla chiusura formale W05; W05-T05/T07 non sono segnati completati e le verifiche restano richieste. Build/test automatici e controlli accessibilità pertinenti proseguono durante W06. Punto di ripresa W06-T01.

- [x] W06-T01 — Realizzare scocca blu/argento/arancione e tasti meccanici con marchio WalkTape.
- [x] W06-T02 — Implementare cassetta, bobine, etichetta dinamica e copertina ingrandibile.
- [x] W06-T03 — Collegare tutti i tasti al motore reale; aggiungere pressioni prolungate e avanzamento preciso.
- [x] W06-T04 — Implementare feedback aptico e clic opzionali, rispettando pausa e riduzione movimento.
- [x] W06-T05 — Integrare accesso al player da ogni schermata, coda e layout telefono/tablet/orizzontale.
- [ ] W06-T06 — Verificare contrasto, lettore schermo, caratteri ingranditi, touch target e coerenza con `DESIGN.md`.

Criterio di uscita: player riconoscibile come reinterpretazione TPS-L2, utilizzabile e collegato alle funzioni reali.

Incremento 2026-10-15: **T01–T05 implementati e verificati; T06 parziale**. Player come rotta globale aperta dalla mini-bar: materiali blu/argento/arancio, cassetta e copertina privata ingrandibile, bobine guidate da `isPlaying`, trasporto/seek/volume STREAM_MUSIC/shuffle/repeat/coda/timer/suggerimenti sul bridge W05. Pressione prolungata con target cumulativo e cancellazione al cambio occorrenza; STOP è pausa senza perdita della posizione. Preferenze aptica on/clic off, rispettati i toggle Android; movimento ridotto osservato dal sistema. Etichetta album per ID comune, playlist per provenienza omogenea, altrimenti Mixtape. Riproduzione playlist dal dettaglio in sola lettura, ordine/copie preservati e indisponibilità esplicita se indice incompleto. **Room v6**, migrazione 5→6 con origine per occorrenza persistita.

Verifiche: build app/androidTest, **78/78 JVM**, lint **0 errori/22 avvisi**; Android **7/7 player+preferenze+storage** (migrazioni 4→6 e 5→6, round-trip e isolamento), poi **9/9 player/regressioni su esecuzioni separate** (7 passati nella suite unica; timer e fixture suggerimenti falliti e ripetuti separatamente con successo). Le fixture playlist sono locali con brani/audio reali, non playlist create sul server. Selezioni libreria **2/2** nella fase precedente della sessione. Verificati dump/screenshot chiaro/scuro, landscape con rail, tablet simulato con coda affiancata, EN e font 200%; corretti rail che consumava tutta la larghezza, inset laterale della mini-bar e scroll annidato. Restano **TalkBack manuale, percezione aptica/clic, osservazione bobine/riduzione movimento su telefono e approvazione visiva** in T06/collaudo privato `local/PHONE_TESTS_W06_W05.md`. Non chiusa l'intera wave né W05-T05/T07. APK debug aggiornato, nessun commit/push/release.

## Wave 07 — Playlist, preferiti, testi e cronologia

Dipendenze: wave 04 e 05.

Handoff 2026-10-15: l'utente riferisce «mi sembra tutto ok» sul nuovo player e richiede il prompt W07 per un'altra sessione. Riscontro generale positivo W06, non attestazione puntuale dei controlli manuali residui: W06-T06 e W05-T05/T07 restano aperti. Punto di ripresa **W07-T01**, poi T02–T06; nessun codice W07 iniziato nell'handoff. Preservare tutto il working tree W06 locale non committato (Room v6 compresa). Prompt aggiornato in NEXT_PROMPT.md, ignorato da Git.

Pubblicazione successiva richiesta dall'utente: **commit `93187a8`**, W06-T01–T05, test, schema6 e handoff pubblicati su origin/main con push ordinario riuscito; SHA remoto/locale verificati identici. Lo stato locale non committato dell'handoff sopra è quindi superato dalla pubblicazione. Note dell'operazione aggiornate separatamente; prompt e checklist private rimangono ignorati, nessuna release/APK pubblicata. W06-T06 e W05-T05/T07 restano aperti.

- [x] W07-T01 — Implementare CRUD delle playlist, riordino, azioni dalla selezione multipla e rispetto delle autorizzazioni del server.
- [x] W07-T02 — Implementare preferiti per brani/album/artisti e sincronizzazione con Navidrome.
- [x] W07-T03 — Salvare la coda come playlist e supportare nome personalizzato della mixtape.
- [x] W07-T04 — Implementare testi disponibili sul server e dettaglio tecnico/musicale del brano.
- [x] W07-T05 — Implementare cronologia e comunicazione degli ascolti, con eventi offline e prevenzione dei duplicati.
- [ ] W07-T06 — Verificare le modifiche con account di test e playlist dedicate, inclusi errori e permessi insufficienti.

Criterio di uscita: operazioni persistenti riscontrabili sul server, testi reali e nessun inoltro duplicato degli ascolti nei casi verificati.

Incremento W07 2026-10-15: **T01–T05 implementati e verificati, T06 parziale**. Room v7 (migrazione 6→7 e schema): nome mixtape, readonly playlist, cache testi, eventi e passaggio d'ascolto durevole. CRUD/ordine/duplicati/preferiti riscontrati sul server, testo reale non sincronizzato; ultimo test UI selezione→playlist, editing, coda shuffle→playlist e guardie profilo **1/1 (152,373 s)**. JVM **84/84**, build app/androidTest e lint **0 errori/22 avvisi**. Android storage/servizio **9/9** e test aggiuntivo permessi fixture passato; recovery evento injected→ACK/playCount reali **1/1 (3,573 s)**. Regressioni W05/W06 **7/9 nella combinata + 2/2 isolate**, non suite unica verde. Corretto crash Compose per copie duplicate prima della normalizzazione UUID. Navidrome non deduplica scrobble id+time identici: limite ACK perso documentato. **T06 aperto**: rifiuto reale su playlist condivisa di un secondo account test ancora da predisporre; la fixture non lo sostituisce. Residui hardware W05/W06 invariati; checklist privata W07 e APK pronti, env temporaneo rimosso e AVD spento. Nessun commit/push/release.

Riscontro utente W07 successivo: «bene, mi sembra tutto ok», con richiesta esplicita di commit e push. Riscontro generale positivo registrato, senza chiudere T06 o dedurre controlli hardware puntuali non riferiti. **Pubblicato W07 in 043e43c** (61 file: codice, test, schema7 e documenti), manutenzione skill/lockfile in a07e905; push ordinario su origin/main riuscito e SHA codice locale/remoto verificati identici. Note della pubblicazione aggiornate nel successivo commit documentale. Nessuna release/APK pubblicata, file privati ignorati; stato funzionale sopra invariato.

## Wave 08 — Download e sincronizzazione offline

Dipendenze: wave 05 e 07.

Handoff 2026-10-15: richiesto il prompt per sviluppare tutta W08 in un'altra sessione, salvato in NEXT_PROMPT.md ignorato da Git. Ripresa W08-T01 → T07 sulla base funzionale W05/W07 pubblicata (HEAD b18691b; codice W07 043e43c). W07-T06 resta aperto per permessi condivisi sul server reale; se il prerequisito manca, non blocca il lavoro indipendente W08 e non viene segnato superato. Residui hardware W05/W06 e checklist private preservati. Nessun codice W08 avviato, commit/push non richiesti per questo handoff.

- [x] W08-T01 — Implementare download di brani/album/playlist, azioni dalla selezione multipla e metadati di appartenenza ai download.
- [x] W08-T02 — Implementare lavoro persistente, avanzamento, pausa/ripresa/annullamento, retry e vincoli Wi-Fi/rete mobile.
- [x] W08-T03 — Implementare cache audio da 1 GB, copertine da 250 MB, limiti modificabili e contabilità dello spazio.
- [x] W08-T04 — Implementare modalità solo offline, priorità copie locali, gestione dei brani indisponibili e riconnessione.
- [x] W08-T05 — Sincronizzare playlist scaricate preservando i brani conservati anche da altre origini e i download singoli rimossi dal server.
- [x] W08-T06 — Implementare modifiche offline a playlist/preferiti, coda delle operazioni e risoluzione esplicita dei conflitti.
- [ ] W08-T07 — Verificare perdita rete, riavvio, spazio insufficiente, duplicati, cancellazioni e isolamento fra profili.

Criterio di uscita: ascolto e gestione offline verificati con riconnessione, senza perdita silenziosa delle modifiche.

Incremento W08 2026-10-15: **T01–T06 implementati con verifiche mirate; T07 parziale**. Room **v10**, migrazioni esplicite 7→8→9→10 e schemi conservati; WorkManager **2.12.0**. Download originali privati, origini condivise e snapshot playlist (vuote e duplicati inclusi), pausa/ripresa/retry, Range+ETag forte, validazione audio. Cache audio LRU con lease sui file in uso, cache copertine riusata e limiti/spazio modificabili. Stesso servizio/bridge con locale→cache→rete, gate solo offline e ascolti pendenti. Operazioni playlist/preferiti durevoli, baseline pre-invio per creazioni e riconciliazione degli ACK ambigui, conflitti espliciti e scelta locale/server/nuova playlist.

Verifiche: build app/androidTest; **110/110 JVM** con API reali opt-in; lint **0 errori/28 avvisi**. Suite mirata Android **21/21 (48,054 s)**: W08 + regressioni storage W05/W07/copertine. Include originale Navidrome e hash locale identico, CRUD offline/conflitto/risoluzione contro playlist reale dedicata poi rimossa, WorkManager reale con credenziali Keystore, servizio PCM offline con contatore HTTP fermo e ascolto pendente; rimozione profilo durante il trasferimento senza catalogo/parziali resuscitati. Recupero download originale dopo force-stop/nuovo processo **1+1 passati**, poi dopo reboot AVD **1+1 passati**. **Ultima combinata**, con le precedenti prove e regressioni servizio/UI W05/W06/W07 sullo stesso APK finale: **32 passati + 2 saltati opt-in, zero falliti (333,652 s)**. I due skip sono i seed/verify separati W05, non controlli eseguiti. Corretta una race di startup del gate con attesa esplicita; l'esecuzione precedente con un fallimento resta nel log, non è presentata come verde.

**T07 resta aperto**: nessuna nuova prova telefono; passaggio reale Wi-Fi/mobile e perdita rete durante download/ascolto/sync sul dispositivo, esaurimento reale spazio durante scrittura (verificato solo preflight con budget impossibile), collaudo UI/accessibilità/adattività delle nuove superfici e stress concorrenza/rimozione profilo sotto trasferimento. Checklist privata `local/PHONE_TESTS_W08.md`; residui W07-T06 e hardware W05/W06 invariati. Nessuna dichiarazione di completamento dell'intera wave o dell'app; nessun commit/push/release.

Pubblicazione successiva su richiesta esplicita dell'utente: **b90b9f3 — Wave 08: download, cache e sincronizzazione offline (T01-T06)**, 71 file fra codice/test/schemi/documenti. Push ordinario su origin/main riuscito e SHA locale/remoto verificati identici; note nel successivo aggiornamento documentale. Credenziali, checklist private, NEXT_PROMPT e APK esclusi. T07 e residui hardware rimangono aperti; nessuna release.

## Wave 09 — Qualità ed elaborazione audio

Dipendenze: wave 08; ricerca audio wave 01.

Handoff 2026-10-15: l'utente richiede il prompt per implementare tutta W09 in una nuova sessione. Salvato NEXT_PROMPT.md completo (locale ignorato), ripresa W09-T01 → T06 dalla HEAD pubblicata 0902d82/codice W08 b90b9f3 e Room v10. Funzioni W08-T01–T06 disponibili; W08-T07, W07-T06 e collaudi hardware W05/W06 rimangono aperti e documentati, senza bloccare genericamente i task indipendenti W09. Ricerca W01 da verificare sulle API/versioni effettive, non considerata implementazione del DSP. Nessun task W09 avviato o segnato completo in questo handoff, nessun nuovo commit/push richiesto.

- [x] W09-T01 — Implementare qualità per rete/profilo, download originali o ridotti e comportamento quando il transcoding manca.
- [x] W09-T02 — Implementare gapless e crossfade regolabile inizialmente spento.
- [x] W09-T03 — Implementare ReplayGain album/brano, inizialmente spento, e gestione dei metadati mancanti.
- [x] W09-T04 — Implementare equalizzatore e rinforzo bassi opzionali, inizialmente spenti.
- [x] W09-T05 — Implementare effetto cassetta opzionale con lieve fruscio e oscillazione, inizialmente spento.
- [ ] W09-T06 — Verificare MP3/FLAC, transizioni, seek, combinazioni di effetti, clipping e ascolto Bluetooth sul dispositivo reale.

Incremento W09 2026-10-15: **T01/T03/T04/T05 implementati e verificati; T02/T06 parziali**. Room v11, migrazione10→11 e conservazione verificate. Qualità originale di default, Opus ridotto persistente per profilo, fallback raw a fresh open e rifiuto cambio codec nei retry; ridotti non ricomposti nella cache a frammenti senza validatore, download completi prioritari/origini conservate. Server reale: MP3+FLAC→Opus96, download e PCM Android. ReplayGain da tag realmente estratti, gain/peak/bypass misurati; EQ/bassi audiofx con sessione condivisa, supporto reale AVD5bande, livelli/enable/release e preferenze persistenti verificati; cassetta con hiss e variazione dei periodi misurata, bypass runtime pulito. **129/129 JVM**, build app/androidTest, lint **0 errori/30 avvisi**; **suite Android W09 finale14/14 (111,68s)** senza skip/failure. Regressioni W05–W08 passate in esecuzioni separate; combinata precedente41 casi era39 passati/2 falliti, risolti/ripetuti separatamente, non presentata come suite unica verde.

**T02 resta aperto**: gapless MP3/LAME e FLAC verificato sul seam PCM e crossfade con due ramp reali, ma handover al principale via seek può rebufferizzare; attribuzione durevole del tempo entrante durante l'overlap ancora da completare. **T06 aperto** per questi residui, matrice completa con effetti/focus/transizioni e risposta/clipping audiofx/Bluetooth sul Motorola non misurati. Nessuna nuova prova hardware. APK debug e checklist `local/PHONE_TESTS_W09.md` pronti; env/fixture temporanei AVD rimossi con assenza verificata, AVD spento. W05–W08 residui invariati, nessun commit/push/release.

Pubblicazione successiva su richiesta esplicita: **c1cddd9 — Wave 09: qualità e DSP, crossfade parziale (T01/T03-T05)**,43file; push origin/main riuscito e SHA locale/remoto verificati identici. Note nel successivo commit documentale. Stato funzionale invariato (T02/T06 aperti); APK, credenziali, checklist private e NEXT_PROMPT esclusi, nessuna release.

Incremento autorizzato 2026-10-16 su82f0657: **T02 completato**, parte automatica T06 verificata. Player logico ExoPlayer stabile nello stesso servizio/sessione/bridge; il deck già udibile diventa attivo senza seek/prepare/riapertura della sorgente o ricreazione dell'output. Preload silenzioso fino a10s prima della finestra; fade limitato per brani brevi, fallback nativo se il successore non è pronto. Focus unico Media3 su thread dedicato, volume pubblico/timer separato da ducking e inviluppi. **Room v12**, migrazione11→12/schema12 e stato overlap con eventId/ACK e ricevute di promozione per crash fra listening e coda; prefisso udibile conservato anche nelle modifiche della coda. Corretta race del timer album con query pura sul confine uscente.

Verifiche: build app/androidTest e **129/129 JVM**, lint **0errori/29avvisi**; suite finale **27/27 Android,276,661s** (26W09+regressioneScrobbleService), zero skip/failure. Include passaggi server reali MP3→FLAC, FLAC→MP3, originale→Opus senza riapertura dell'entrante; capture decoder/output e continuità della posizione; timer/focus/seek/skip/rimozioni/shuffle/repeat/duplicati, clock udibile/pausa/ACK/restore, effetti combinati, offline con zeroHTTP, migrazioni e persistenza. Regressioni aggiuntive: **27/27 storage/download/sync,66,222s**, **3/3 UIW06–W07,160,139s**, **7passati+2skip opt-in servizio/focus/restoreW05,365,513s**, in esecuzioni separate. Fallimenti intermedi e correzioni registrati nel WORKLOG.

**T06 resta aperto per Motorola/Bluetooth, cambi route, risposta/clipping dopo audiofx, batteria e ascolto fisico**; nessuna nuova attestazione hardware o chiusura dei residui W05–W08. APK debug e checklist privata W09 aggiornati; nessun nuovo commit/push/release autorizzato in questo incremento. Nessun requisito SPEC rimosso.

Riscontro successivo utente2026-10-16: **«Tutto ok»** dopo il primo giro breve di collaudo proposto. Esito generale positivo registrato; nessuna attestazione puntuale di route, timer prolungato, chiamate o misure. T06 e residui precedenti restano aperti fino alla conferma dei controlli specifici; nessuna nuova attività W10 o pubblicazione avviata.

Chiarimento successivo: il riscontro riguarda il **primo giro**, il resto è differito per indisponibilità dell'utente. Proposto e concordato salvare il consolidamento W09 e procedere poi allo sviluppo indipendente W10, conservando T06/residui hardware aperti. Richiesta esplicita corrente di commit/push; nessuna implementazione W10 avviata nella pubblicazione.

Consolidamento pubblicato su richiesta in **278e889 — Wave 09: crossfade continuo e ascolti overlap persistenti (T02)** (23file); push origin/main riuscito e SHA locale/remoto verificati identici. Note nel successivo commit documentale; T06 hardware rimane differito, nessuna release e nessun avvio W10.

Criterio di uscita: impostazioni audio realmente operative, suono pulito predefinito e limiti delle combinazioni documentati e concordati.

## Wave 10 — Android Auto

Dipendenze: wave 08 e 09.

Handoff2026-10-16: richiesto il prompt per sviluppare tutta W10 nella nuova sessione; salvato NEXT_PROMPT completo e archiviato il precedente W09 in local/ (ignorati). Ripresa **W10-T01 → T05** sulla HEAD pubblicata3febc1d/codiceW09278e889, Roomv12. Funzioni W08/W09 disponibili; W09-T06 hardware e residui W05–W08 esplicitamente differiti dall'utente, non superati e non blocchi generici del lavoro software indipendente W10. Riutilizzare servizio/sessione/bridge e proprietario logico del player, zeroHTTP offline; distinguere MediaBrowserAVD, DHU e auto reale. Nessun codice/taskW10 avviato o segnato completo in questo handoff, nessun nuovo commit/push richiesto.

- [x] W10-T01 — Esporre catalogo, playlist, download e ricerca alla sessione compatibile Android Auto.
- [x] W10-T02 — Implementare navigazione e comandi secondo i vincoli Android Auto, senza replicare la scocca TPS-L2 in auto.
- [x] W10-T03 — Gestire connessione assente, cambio rete e continuità tra telefono e auto.
- [ ] W10-T04 — Documentare e verificare l'abilitazione di APK esterni al Play Store nell'ambiente di test.
- [ ] W10-T05 — Verificare con gli strumenti Auto disponibili e con prove dell'utente in auto, registrando separatamente gli esiti.

Incremento2026-10-16: **T01–T03 implementati e verificati nei percorsi software disponibili**.
Unico MediaLibraryService/sessione sul player logico W09, root La mia musica/Playlist/Download,
finestre e continuazioni legacy (10.003 brani), ricerca locale e richieste vocali Media3,
ID per profilo risolti in UUID di coda, playlist A/B/A e origini, download con file realmente
completi e snapshot vuoti/parziali/serverMissing, immagini content autorizzate e gate offline.
Il bind legacy placeholder è distinto dal trust in onConnect; corretto grazie al test framework.
Nessuna nuova dipendenza o migrazione: Roomv12/Media3 1.11.1/WorkManager 2.12.0 invariati.

Build finale app/androidTest + **137/137 JVM** con API reali opt-in, lint **0errori/33avvisi**.
**W10 finale6/6 Android (78,603s)**: MediaBrowser moderno e framework legacy, MP3/FLAC Navidrome
reali e richiesta vocale Media3, offlinezeroHTTP/copertine, profili, restore/rebind senza autoplay,
comandi condivisi/shuffle/repeat/copie, paginazione completa. Non è proiezione Android Auto.
Regressioni separate: **24passati+2skip opt-in (217,268s)** W05/W08; W09 combinata27 con
25passati/2falliti poi entrambi passati isolati; infine sull'APK conclusivo **12/12 (117,878s)**
handover/overlap/servizio/ascolti, **1/1 offline (27,83s)** e **1/1 handover reale (85,352s)**.
Dettagli/failure intermedi nel WORKLOG; nessuna somma presentata come suite unica verde.

**T04/T05 restano aperti**: guida sideload/DHU in `docs/ANDROID_AUTO.md`, checklist privata
`local/PHONE_TESTS_W10.md`; manca verifica effettiva Unknown sources/launcher, DHU e auto reale,
voce dell'assistente, copertine/head unit e matrice fisica Wi-Fi/mobile/VPN/scollegamenti.
SDK senza DHU e nessun telefono collegato; guide developer.android.com non raggiungibili in questa
sessione, API verificate sui sorgenti ufficiali 1.11.1. Le verifiche hardware di T03 confluiscono
in T05, non sono dichiarate superate. Residui W05–W09 e checklist precedenti preservati.
APK debug aggiornato; temporanei rimossi, AVD spento. Nessun commit/push/release.

Pubblicazione successiva su richiesta esplicita: **109b652 — Wave 10: Android Auto, catalogo
e continuità (T01-T03)**,27file. Push ordinario su origin/main riuscito e SHA locale/remoto
identici; working tree pulita subito dopo. Note nel successivo commit documentale. T04/T05
e collaudi hardware restano aperti; APK/credenziali/checklist/NEXT_PROMPT ignorati ed esclusi,
nessuna release. Nessuna nuova build per la sola pubblicazione.

Criterio di uscita: percorso di ascolto in auto verificato; eventuale prova hardware mancante resta un blocco esplicito.

## Wave 11 — Impostazioni, backup e aggiornamenti

Dipendenze: wave 08–10.

Handoff2026-10-16: l'utente richiede il prompt per implementare tutta W11 in una nuova sessione
e prevede i test **domani in auto**. Autorizzato lo sviluppo indipendente **W11-T01 → T05** sulle
funzioni W08/W09/W10 disponibili, senza attendere la chiusura dei collaudi hardware. W10-T04/T05,
W09-T06 e residui precedenti restano aperti: il collaudo previsto non è un esito già acquisito.
Baseline pubblicata a49d0e7/codiceW10 109b652, Roomv12; NEXT_PROMPT W11 completo e precedente
handoff W10 archiviato in local/ ignorato. Solo preparazione documentale: nessun codice W11,
task completato o nuovo commit/push/release in questo handoff.

Incremento 2026-10-17: **W11-T01–T05 [x]** per implementazione e contratto software verificato
(apparato automatico; collaudo telefono da fare con `local/PHONE_TESTS_W11.md`). Nessun cambio
schema: **Roomv12** invariata, nessuna nuova migrazione. Dettagli, limiti e controlli non eseguiti
nel WORKLOG 2026-10-17.

- [x] W11-T01 — Completare impostazioni di dispositivo/profilo e schermata sistema con stati e azioni operative.
- [x] W11-T02 — Implementare report diagnostico esportabile con esclusione di segreti e controllo dei dati inclusi.
- [x] W11-T03 — Implementare export/import di backup senza credenziali/audio, incluse coda, posizione e lista da riscaricare.
- [x] W11-T04 — Implementare controllo aggiornamenti GitHub Releases, novità, download e conferma Android; controllo automatico facoltativo.
- [x] W11-T05 — Verificare importazioni invalide, cambio telefono, aggiornamento dell'app e migrazioni senza perdita dei dati.

Criterio di uscita: l'utente può diagnosticare, trasferire configurazione e aggiornare l'app nei percorsi verificati.

## Wave 12 — Verifica complessiva e distribuzione

Prima distribuzione pubblicata su richiesta: **[v0.1.0 / versionCode2](https://github.com/rosariomoscato/walktape/releases/tag/v0.1.0)**, APK release
riallineato a splash/tasti/D41, firma stabile e checksum. Artefatto e processo in
[RELEASING.md](docs/RELEASING.md), verifiche/limiti nelle [note](docs/RELEASE_NOTES.md).
La pubblicazione non chiude T02/T03 né i collaudi hardware precedenti.

Incrementi UI post-collaudo: tasti player a larghezza piena e splash con logo/versione/
credits implementati e compilati (debug+lint); successivo riscontro visivo positivo.
La build release della prima distribuzione ora include questi incrementi e D41.

Successivo riscontro utente: splash/tasti approvati. Rinforzo bassi per route concordato
in D41: sospensione su speaker/uscita ignota, preferenza conservata per cuffie. Verifiche
software superate (177JVM,lint0/45, controller/offline/timer Android separati1/1 ciascuno),
riscontro generale positivo utente «funziona» ricevuto; voci puntuali del collaudo D41
non chiuse automaticamente e matrice Auto/hardware restante ancora aperta.

Dipendenze: tutte le wave precedenti.

- [x] W12-T01 — Eseguire build, analisi statica e test significativi per isolamento, coda, sincronizzazione, download e migrazioni.
- [ ] W12-T02 — Eseguire la matrice end-to-end con server reale, rete/offline, MP3/FLAC, Motorola Android 15/16 e Android Auto. *(emulatore/server reale e telefono sez.A/B verificati; Auto e residui hardware ancora aperti)*
- [ ] W12-T03 — Verificare prestazioni, batteria, storage, accessibilità, lingue e tutti gli impegni di `SPEC.md`. *(porzioni emulatore/codice e TalkBack W12 confermati; batteria/font 200% e residui ancora aperti)*
- [x] W12-T04 — Preparare icona originale, README, guida installazione/Tailscale/Auto, privacy, licenza MIT e note di rilascio.
- [x] W12-T05 — Predisporre firma e processo riproducibile per gli APK, mantenendo chiavi e credenziali fuori da Git.
- [x] W12-T06 — Preparare APK firmato; pubblicare repository/release solo con richiesta esplicita dell'utente per tali operazioni.
- [x] W12-T07 — Chiudere la verifica del perimetro; aggiornare log/memoria con esiti, problemi residui e backlog.

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
