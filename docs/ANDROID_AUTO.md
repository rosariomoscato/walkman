# WalkTape su Android Auto

## Stato della verifica W10

L'APK debug espone lo stesso `PlaybackService` come **MediaLibraryService Media3 1.11.1**.
Telefono, notifica e browser usano lo stesso player logico W09, la stessa coda e gli stessi UUID.
Android Auto disegna l'interfaccia; non viene proiettata la scocca Compose TPS-L2.

Il contratto del browser viene verificato sull'AVD **walktape_aosp**. Questo non è DHU né
proiezione Android Auto. Non sono ancora attestati visibilità dell'APK nel launcher Auto,
controlli vocali di Google/assistente, touch/rotella dell'head unit o ascolto in auto.

Al controllo locale del 2026-10-16:

- `adb devices -l` mostra soltanto l'AVD, nessun telefono fisico collegato;
- `sdkmanager --list_installed` non elenca `extras;google;auto`; il DHU non è installato;
- il browser web degli strumenti non si avvia perché manca il suo eseguibile Chrome;
- le guide `developer.android.com` non sono raggiungibili da WebFetch (errore trasporto).

**W10-T04/T05 restano aperti.** La procedura seguente prepara il collaudo, non dichiara
eseguiti passaggi telefonici né ricertifica come attuale la ricerca storica W01.

## Percorso nell'interfaccia Auto

- **La mia musica** → Album, Artisti, Brani.
- **Playlist** → contenuto locale memorizzato nell'ordine della playlist, copie comprese.
- **Download** → snapshot delle playlist scaricate e Brani scaricati.
- I gruppi indicano `disponibili/totali` offline; se il server ha rimosso la playlist viene
  indicato lo snapshot conservato. Il play dell'intero gruppo richiede tutte le copie complete;
  le singole copie valide restano navigabili. Una playlist vuota rimane visibile.
- Un album/playlist riproducibile si espande nella coda unica; una traccia selezionata da sola
  crea una selezione di un brano. Le occorrenze in coda ricevono UUID distinti anche per A/B/A.
- Finestre da 200 elementi; **Altri risultati** mantiene raggiungibili le raccolte grandi
  anche con un browser legacy che richiede tutti i figli in una sola chiamata.
- Ricerca locale unificata, al massimo 500 risultati per query; restringere la query se serve.
  Le richieste vocali Media3 con `RequestMetadata.searchQuery` sono risolte localmente;
  il percorso end-to-end dell'assistente rimane da provare su Auto.

Browse, ricerca e riconnessione non preparano l'audio e non generano ascolti.
La coda salvata viene caricata in pausa; un play esplicito riprende l'occorrenza e la posizione.
Solo offline mantiene il gate di rete per API, audio, copertine, preload, worker e ascolti.
Le immagini sono URI `content://com.walktape.app.art/...`, mai URL Navidrome autenticati;
sono servite soltanto ai chiamanti ammessi e per il profilo attivo.

## Prerequisiti del collaudo con APK esterno al Play Store

1. Telefono compatibile con Android Auto, app Android Auto aggiornata e connessione
   USB dati affidabile (oppure modalità wireless supportata dall'auto).
2. Installare `app/build/outputs/apk/debug/app-debug.apk` sul telefono, autorizzando
   l'installazione dalla sorgente scelta quando Android lo richiede. Non disinstallare una
   copia contenente dati per aggirare una firma diversa: l'aggiornamento richiede la stessa firma.
3. Aprire WalkTape sul telefono, configurare Navidrome e verificare MP3/FLAC e download.
   `localhost:4533` è il computer; sul telefono usare l'indirizzo LAN/Tailscale del computer.
4. Nelle impostazioni Android Auto, abilitare la modalità sviluppatore (nella procedura W01:
   dieci tocchi sulla voce Versione e conferma), poi **Origini sconosciute / Unknown sources**
   per le app multimediali esterne al Play Store. Posizioni e nomi dei menu possono variare:
   confrontare prima la guida ufficiale corrente indicata sotto.
5. Collegare l'head unit, verificare che WalkTape compaia e annotare versioni telefono,
   Android Auto e head unit. Il debug sideload qui preparato non equivale a pubblicazione Play.

I passi 1–5 non sono stati eseguiti su un telefono in questo incremento.

## Desktop Head Unit (DHU) — da eseguire quando il telefono è disponibile

Riferimento ufficiale: <https://developer.android.com/training/cars/testing/dhu>.

1. Installare il pacchetto **Android Auto Desktop Head Unit emulator** da SDK Manager
   (SDK Tools), verificando la versione e i prerequisiti della guida ufficiale.
2. Sul telefono abilitare le opzioni sviluppatore Android Auto e avviare
   **Start head unit server** dal menu sviluppatore; abilitare debug USB e autorizzare il PC.
3. Collegare il telefono USB e identificare il suo seriale, senza selezionare per errore l'AVD.
4. Procedura classica USB/head-unit server:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <seriale-telefono> forward tcp:5277 tcp:5277
   & "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
   ```

5. Collaudare browse, ricerca/play, comandi bidirezionali, scollegamento/ricollegamento,
   profili, download e assenza rete con la checklist privata `local/PHONE_TESTS_W10.md`.
6. Al termine chiudere DHU/head-unit server e rimuovere **solo** il forward creato per la prova:

   ```powershell
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <seriale-telefono> forward --remove tcp:5277
   ```

Questi comandi non sono stati eseguiti in questa sessione: non è disponibile il telefono e il
DHU non è installato. Non usare l'AVD AOSP come sostituto della proiezione Auto.

## Fonti e provenienza

- API e requisiti del servizio verificati sui sorgenti ufficiali **tag 1.11.1** e sulle firme
  degli artefatti locali tramite `javap`:
  <https://github.com/androidx/media/blob/1.11.1/libraries/session/src/main/java/androidx/media3/session/MediaLibraryService.java>
- La fonte documenta anche `android.media.browse.MediaBrowserService` per i client legacy,
  i flag browsable/playable obbligatori e il rischio di deadlock del root legacy.
- Guide da ricontrollare sul telefono/host quando raggiungibili:
  <https://developer.android.com/training/cars/media>,
  <https://developer.android.com/training/cars/testing>,
  <https://developer.android.com/training/cars/testing/dhu>.
- La nota Unknown sources di `docs/ARCHITECTURE.md` W01 è una ricerca precedente,
  non evidenza di abilitazione dell'APK W10 sulla versione Android Auto del telefono.

Nessun account WalkTape, backend, servizio SaaS, nuova chiave di firma o release richiesti.
