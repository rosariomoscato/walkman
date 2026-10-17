# WalkTape — specifica approvata

Stato: approvata dall'utente nella conversazione iniziale. Trascrizione del perimetro, non attestazione di implementazione.

## Obiettivo e piattaforma

Player Android nativo per Navidrome con identità visiva ispirata al Sony TPS-L2. Primo pubblico: proprietario, amici e familiari; evoluzione futura verso distribuzione pubblica.

- Kotlin, Jetpack Compose e Media3 come stack proposto, da validare tecnicamente.
- Android 8 come minimo proposto; priorità a Motorola G55 e successivi su Android 15/16.
- Telefono/tablet, verticale/orizzontale, italiano/inglese con scelta della lingua.
- Codice open source, licenza MIT, APK firmati e distribuzione GitHub Releases.
- Nessun account WalkTape aggiuntivo, pubblicità o telemetria automatica.

## Dati e proprietà

Navidrome resta la fonte di musica, metadati, playlist, preferiti e ascolti. WalkTape conserva localmente profili, credenziali protette, indice della libreria, copertine, cache, download, preferenze, coda/posizione e operazioni offline.

Un solo profilo attivo; dati e download isolati per profilo. Preferenze estetiche del dispositivo; rete/qualità e connessione per profilo. Nessuna funzione per cancellare i file musicali sul server.

## Connessione e primo avvio

- Profili multipli con nome, URL e credenziali Navidrome; verifica e indicizzazione progressiva.
- Tailscale già attivo sul telefono; WalkTape non configura né sostituisce la VPN.
- HTTPS supportato; HTTP per i contesti locale/VPN concordati, con indicazione del tipo di connessione.
- OpenSubsonic dove disponibile; compatibilità dichiarata inizialmente solo con Navidrome.
- QR con URL/nome profilo senza password; inserimento manuale sempre disponibile.
- Blocco biometrico/PIN del dispositivo opzionale, inizialmente spento; controlli musicali disponibili a schermo bloccato.
- Cambio profilo: arresto riproduzione e ripristino della coda relativa. Rimozione profilo con conferma dei download, senza modifiche al server.

## Libreria

- Avvio su “La mia musica”: continua ascolto, aggiunti/ascoltati di recente, preferiti e download.
- Album, artisti, brani, generi e playlist; player raggiungibile da ogni schermata.
- Ricerca unificata e filtri per genere, anno e disponibilità offline.
- Ordinamenti persistenti: album per titolo/artista/anno/aggiunta; artisti alfabetici; brani per titolo/artista/album/durata; playlist per nome/aggiornamento quando disponibile.
- Compilation e multidisco: distinguere artista traccia/album, rispettare disco e numero traccia.
- Selezione multipla per coda, playlist e download.
- Playlist: creare, rinominare, eliminare, aggiungere/rimuovere/riordinare brani; rispettare i permessi sulle playlist condivise.
- Preferiti brani/album/artisti sincronizzati secondo le capacità del server; niente stelle nella prima versione.
- Testi del server, sincronizzati quando disponibili; dettagli con artista, album, anno, formato, bitrate e durata.
- Indice consultabile offline; caricamento progressivo e prestazioni adeguate a circa 10.000 brani.

## Riproduzione

- Play/pausa, precedente/successivo, seek preciso e pressione prolungata per avanzare/arretrare.
- Ripetizione brano/coda; shuffle senza ripetere prima di attraversare la selezione, mantenendo il brano corrente e ripristinando l'ordine quando disattivato.
- Coda: riproduci subito/dopo, aggiungi in fondo, riordina, rimuovi, nome personalizzato e salvataggio come playlist.
- Persistenza coda e posizione per profilo; nessun avvio automatico alla riapertura.
- Gapless; crossfade opzionale regolabile inizialmente spento.
- Timer a tempo/fine brano/fine album con breve dissolvenza.
- Volume multimediale Android; servizio/sessione per schermo spento, notifica, lockscreen e Bluetooth.
- Pausa scollegando le cuffie, ripresa manuale ricollegandole; audio focus/interruzioni secondo Android.
- Arresto a fine coda come default; suggerimenti opzionali secondo disponibilità server.
- Android Auto nella prima versione: libreria, playlist e download nell'interfaccia prevista dalla piattaforma.

## Qualità e suono

- Originale su Wi-Fi e download; ridotta configurabile su rete mobile e opzionalmente nei download.
- Transcoding del server quando configurato; indicazione esplicita quando manca e uso dell'originale.
- ReplayGain album/brano, equalizzatore, rinforzo bassi ed effetto cassetta opzionali, inizialmente spenti.
- Precisazione approvata nel collaudo W12: rinforzo bassi sospeso sull'altoparlante con
  avviso esplicito; disponibile sulle cuffie secondo le capacità del dispositivo.
  Preferenza e intensità conservate al cambio uscita, senza compensare il volume Android;
  uscita non rilevata/non supportata segnalata, effetto non applicato finché incerta.
- Effetto cassetta: lieve fruscio e oscillazione del suono; default pulito.
- Verificare gapless/crossfade/effetti anche combinati, sui formati MP3/FLAC e hardware di riferimento.
- Ascolti inviati a Navidrome, incluso recupero degli eventi offline evitando duplicati; eventuale Last.fm/ListenBrainz gestito dal server.

## Offline e storage

- Download brani/album/playlist nella memoria privata interna, eliminati disinstallando l'app.
- Download espliciti conservati fino alla rimozione; niente cancellazione automatica per recuperare spazio.
- Cache audio temporanea iniziale 1 GB, copertine 250 MB; limiti modificabili e pulizia automatica della cache.
- Download solo Wi-Fi inizialmente, rete mobile abilitabile; streaming su entrambe; modalità solo offline senza richieste di rete dell'app.
- Gestione progressi, pausa/ripresa/annullamento, retry, spazio insufficiente e lavoro in background secondo Android.
- Playlist scaricate aggiornate su Wi-Fi e manualmente. Rimuovere un download legato a una playlist solo se non conservato anche da altre origini.
- Copie locali prioritarie; avvio sulla libreria memorizzata se offline; riconnessione e salto brani indisponibili configurabile, inizialmente attivo e segnalato.
- Modifiche offline a playlist/preferiti in attesa; conflitti segnalati conservando la versione locale, con scelta o salvataggio come nuova playlist.
- Download singoli conservati quando il brano sparisce dal server, con stato esplicito.

## Design

- Un modello TPS-L2 nella prima versione, marchio WalkTape e icona originale.
- Schermata di benvenuto al lancio con logo, versione e credits, circa un secondo;
  non ripetuta ai normali rientri nell'app e senza provocare riproduzione automatica.
- Blu, argento, accenti arancioni, superfici metalliche, tasti meccanici e finestra cassetta.
- Bobine animate solo in riproduzione; etichetta album/playlist, titolo/artista; “Mixtape” per coda mista.
- Copertina dedicata ingrandibile; comandi shuffle/ripetizione/coda visibili.
- Vibrazione disattivabile; clic opzionali inizialmente spenti; rispetto del movimento ridotto.
- Tema classico/scuro/sistema, font ingranditi, lettore schermo e touch target adeguati.
- Liste leggibili coerenti col player, layout adattivo; Android Auto usa i propri vincoli visivi.
- Decisioni visive da formalizzare in `DESIGN.md` prima delle schermate definitive.

## Operatività, backup e distribuzione

- Impostazioni con connessione, indicizzazione, sincronizzazione, download, spazio e azioni di recupero.
- Diagnostica esportabile senza password/token e senza invii automatici.
- Backup manuale: profili (URL/nomi utente), preferenze, lista da scaricare, coda e posizione; esclusi credenziali e audio. Mostrare contenuti prima di esportare.
- Import: nuovo accesso per profilo e proposta di riscaricare la musica; playlist/preferiti recuperati dal server.
- Aggiornamenti GitHub: controllo manuale, automatico facoltativo, novità e download APK; installazione confermata da Android, nessun aggiornamento forzato.
- Firma stabile per gli aggiornamenti, chiavi escluse da Git.
- README/guide e informativa privacy coerenti con le funzioni reali; licenza MIT; nessun banner cookie per l'app nativa priva di tracking.

## Verifiche concordate

Navidrome 0.62.0 (`1b46b977`) installato sul computer, URL locale `http://localhost:4533`, accesso anche via Tailscale. Transcoding da verificare. Account di test da predisporre senza privilegi amministrativi; playlist di prova dedicate.

Utente disponibile per prove APK su Motorola G55 e Android Auto. Verificare build, test significativi, streaming/download, offline/rete, isolamento, sincronizzazione, audio, prestazioni e accessibilità. Distinguere test automatici, emulatore e hardware; nominare i controlli non eseguiti.

## Fuori dalla prima versione

Audiolibri, Cast/DLNA, file locali, altre skin, sincronizzazione automatica fra telefoni, supporto verificato ad altri server e Google Play. Eventuale microSD da valutare successivamente, non prevista nella prima versione.
