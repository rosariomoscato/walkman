# WalkTape — prove sul Motorola

Procedura originaria W03/W04, conservata come riferimento. Gli esiti successivi sono
registrati in WORKLOG/PLAN; non tutte le voci di questa matrice sono automaticamente
superate dai collaudi più recenti. Versione pubblica: [v0.1.0](RELEASE_NOTES.md).

## Preparazione

- APK pubblico: [release v0.1.0](https://github.com/rosariomoscato/walktape/releases/tag/v0.1.0),
  oppure debug locale per sviluppo: `app/build/outputs/apk/debug/app-debug.apk`.
- Sul telefono usare l'indirizzo LAN oppure Tailscale del computer che ospita Navidrome. `localhost` e `10.0.2.2` non sono indirizzi del server utilizzabili dal telefono.
- Inserire le credenziali direttamente nell'app; non copiarle in chat, screenshot o report.

## W03-T04/T06 — connessione e profili

1. Aprire **Profili e connessione → Aggiungi profilo**, inserire nome, URL e credenziali. Per l'URL HTTP locale/VPN attivare il consenso esplicito. Toccare **Verifica e salva**.
2. Confermare che un tentativo con password errata produca un errore e non salvi un profilo.
3. Con un indirizzo Tailscale, provare la connessione a VPN attiva. Disattivare Tailscale e riprovare: registrare se il server diventa irraggiungibile. Una route alternativa LAN può mantenerlo raggiungibile: in tal caso la prova VPN assente non è conclusiva.
4. Aggiungere un secondo profilo con nome diverso; alternare **Attiva** tra i due. Verificare che resti uno solo attivo e che l'indice della libreria sia separato.
5. Da un profilo, aprire **Condividi QR**. Visualizzare il QR su un altro schermo e usare **Scansiona QR** sul telefono. Confermare URL/nome/utente, inserire manualmente la password e verificare la connessione.
6. In **Impostazioni → Blocco app**, attivare il blocco se il telefono dispone di biometria o credenziali di sistema. Verificare apertura, annullamento e sblocco; ripetere dopo chiusura e riavvio del processo. La semplice presenza dell'interruttore non verifica la protezione.
7. Rimuovere il secondo profilo, controllando il riepilogo dei dati locali prima della conferma. L'altro profilo e il server devono restare disponibili.

## Wave 04 — indice e navigazione

1. Dalla home avviare **Aggiorna libreria**. Durante l'indicizzazione consultare gli elementi già caricati.
2. Alternare Album/Artisti/Brani/Generi; aprire un album e controllare titolo, artista, tracce e durate. Provare un album multidisco e una compilation.
3. Cercare un nome noto, svuotare la ricerca e cambiare ordinamento. Chiudere/riaprire per controllare la persistenza dell'ordinamento.
4. Disattivare temporaneamente la rete: l'indice già memorizzato deve restare consultabile.
   Questo passo verifica il solo indice; ascolto offline e download sono funzioni distinte
   implementate nelle wave successive e da verificare separatamente.

## Esiti da raccogliere

Per ogni prova: modello e versione Android, percorso LAN/Tailscale, esito e passaggio che non ha funzionato. Non includere password, token o URL con parametri di autenticazione.
