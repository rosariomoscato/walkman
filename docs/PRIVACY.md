# WalkTape — informativa privacy

WalkTape è un client per il tuo server Navidrome: **nessuna telemetria o invio automatico
di diagnostica**, nessun analytics, nessun tracker e nessun servizio cloud WalkTape.
Le comunicazioni funzionali, anche in background, sono descritte qui sotto.

## Con chi parla l'app

| Destinatario | Quando | Dati |
| --- | --- | --- |
| **Il tuo server Navidrome** (configurato da te) | login, indicizzazione, streaming/download, playlist/preferiti, ascolti | credenziali del profilo, richieste API, scrobble degli ascolti |
| **api.github.com / GitHub Releases** | solo quando premi "Controlla aggiornamenti" (o con controllo automatico opt-in, max 1/die) | richiesta anonima senza token; scarica l'APK solo su tua conferma |
| **Nessun altro** | — | — |

In **Solo offline** l'app non effettua alcuna richiesta di rete, nemmeno verso GitHub.

## Cosa resta sul dispositivo

- Profili (nome, URL, username) e **credenziali cifrate** (AES-GCM, chiavi non esportabili in
  Android Keystore, associate al profilo).
- Indice della libreria, copertine, cache audio e download nella memoria privata dell'app.
- Preferenze, code, cronologia ascolti locale e eventi in attesa di invio al server.

## Cosa l'app non fa mai

- Non apre connessioni verso servizi di analisi o pubblicità; GitHub è contattato per
  gli aggiornamenti secondo le modalità indicate sopra.
- Non include credenziali, token o audio nel backup (`walktape.backup`) o nella diagnostica.
- Non invia la diagnostica: la esporti tu manualmente, e la sezione con URL/username è
  **opt-in** ed esclusa dal report predefinito (ulteriore oscuramento automatico applicato).
- Non partecipa al backup cloud di Android (`allowBackup=false`, `dataExtractionRules` esclude tutto).

## Permessi

- `INTERNET`: streaming/download dal tuo server e controllo aggiornamenti su richiesta.
- `REQUEST_INSTALL_PACKAGES`: installazione dell'APK di aggiornamento **dopo la tua conferma**
  nell'installer di sistema (mai automatica).
- Notifica multimediale: gestita dalla sessione audio Android; l'app attuale non
  richiede il permesso `POST_NOTIFICATIONS` per notifiche generiche.
- `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_MEDIA_PLAYBACK`: riproduzione in background.
- Biometria (facoltativa, off di default): sblocco dell'app con il PIN/impronta del dispositivo.

## Rimozione dei dati

Disinstallare l'app rimuove profili, credenziali, indice, cache e download (memoria privata).
La libreria sul server Navidrome non viene mai modificata o cancellata da WalkTape se non su
esplicita azione dell'utente (playlist/preferiti), e i file musicali sul server non sono
cancellabili dall'app.
