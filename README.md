# WalkTape

Player Android nativo per [Navidrome](https://www.navidrome.org), con un'identità visiva ispirata al Sony Walkman TPS-L2: scocca blu, tasti meccanici, cassetta con bobine che girano solo quando suona.

Open source (licenza MIT), nessuna pubblicità, nessuna telemetria, nessun account aggiuntivo: la tua musica resta sul tuo server.

**Versione corrente: [v0.1.0](https://github.com/rosariomoscato/walktape/releases/tag/v0.1.0)**
— [Scarica APK universal](https://github.com/rosariomoscato/walktape/releases/download/v0.1.0/walktape-universal-0.1.0.apk)
· [Novità e verifiche](docs/RELEASE_NOTES.md)

## Cos'è

WalkTape riproduce la libreria del tuo server Navidrome sul telefono (e in Android Auto) con:

- **Profili multipli** con credenziali protette (Android Keystore), dati e download isolati per profilo.
- **Libreria completa**: album, artisti, brani, generi, playlist, preferiti, testi; ricerca unificata e ordinamenti persistenti.
- **Player TPS-L2**: coda con occorrenze distinte, shuffle/ripetizione, timer di spegnimento, crossfade opzionale, ReplayGain, equalizzatore, effetto cassetta (tutti inizialmente spenti: suono pulito predefinito).
- **Rinforzo bassi** opzionale per cuffie: sospeso su altoparlante o uscita non rilevata, con avviso e preferenza conservata; resa dipendente dal dispositivo, nessuna compensazione automatica del volume.
- **Offline reale**: download di brani/album/playlist, cache limitata, modifiche offline a playlist/preferiti con recupero e gestione dei conflitti.
- **Ascolti** inviati a Navidrome con deduplica locale e recupero offline; nessuna garanzia exactly-once se il server accetta l'evento ma la risposta si perde.
- **Android Auto** nell'interfaccia prevista dalla piattaforma.
- **Backup/trasferimento** senza credenziali né audio, diagnostica esportabile con oscuramento dei dati sensibili, aggiornamenti da GitHub Releases con verifica della firma.
- Italiano/inglese, chiaro/scuro/sistema, accessibilità (TalkBack, font ingranditi, movimento ridotto), telefono e tablet.

## Requisiti

- Android 8.0 (API 26) o successivo; collaudi hardware su Motorola G55 con Android 16.
- Un server [Navidrome](https://www.navidrome.org) raggiungibile dal telefono:
  - in **LAN** (stessa rete Wi-Fi), oppure
  - via **[Tailscale](https://tailscale.com)** o altra VPN: WalkTape non configura la VPN, la usa se già attiva.
- HTTPS supportato; HTTP consentito nei contesti locale/VPN (l'app indica il tipo di connessione).
- Compatibilità dichiarata: **solo Navidrome** (API Subsonic/OpenSubsonic).

## Installazione

WalkTape non è su Google Play. L'APK firmato è distribuito tramite
[GitHub Releases](https://github.com/rosariomoscato/walktape/releases).

1. Scarica `walktape-universal-0.1.0.apk` dalla release v0.1.0; il relativo hash è nel file `SHA256SUMS.txt` della stessa release.
2. Aprilo sul telefono e conferma l'installazione (Android può chiedere il permesso di installare app sconosciute al browser o al gestore file usato).
3. Al primo avvio: aggiungi il profilo con URL, utente e password Navidrome (o scansiona il QR generato da un altro dispositivo).

**Avviso firme**: ogni APK è firmato con la stessa chiave. Un aggiornamento installato sopra una versione precedente deve avere firma e package identici: l'app verifica tutto questo prima di proporti l'installazione. Se in passato hai installato una build di sviluppo con firma diversa, Android richiederà la disinstallazione (con perdita dei dati dell'app, mai della musica sul server).

Guida dettagliata: [docs/INSTALL.md](docs/INSTALL.md).

**Per chi usa già una build di test 0.1.0:** il controllo in-app la considera la stessa
versione. Il primo passaggio al pacchetto release si fa installando manualmente l'APK
sopra quello attuale, con la stessa firma e senza disinstallare. Versione pubblica:
`0.1.0`, `versionCode 2`.

## Limiti

- Solo Navidrome (nessun altro server OpenSubsonic dichiarato/verificato).
- Nessun Play Store, nessun servizio cloud WalkTape, nessuna sincronizzazione automatica tra telefoni.
- I download vivono nella memoria privata dell'app: disinstallare significa rimuoverli (per questo il backup contiene la lista, non i file).
- File locali indipendenti, Cast/DLNA e skin alternative sono fuori dalla prima versione ([backlog](PLAN.md)).
- Restano collaudi aperti su Android Auto in auto/DHU e parti della matrice hardware,
  batteria, accessibilità e trasferimento backup: dettaglio nelle [note di rilascio](docs/RELEASE_NOTES.md).

## Documentazione

- [Installazione e Tailscale](docs/INSTALL.md)
- [Android Auto](docs/ANDROID_AUTO.md)
- [Backup e trasferimento](docs/BACKUP.md)
- [Aggiornamenti](docs/UPDATES.md)
- [Diagnostica](docs/DIAGNOSTICS.md)
- [Privacy](docs/PRIVACY.md)
- [Note di rilascio e verifiche](docs/RELEASE_NOTES.md)
- [Firma, checksum e processo di pubblicazione](docs/RELEASING.md)
- [Architettura](docs/ARCHITECTURE.md) e [design system](DESIGN.md)

## Privacy in breve

Nessuna telemetria, nessun invio automatico di diagnostica, nessun tracker. L'app comunica
con il server Navidrome configurato (anche per sincronizzazione e ascolti) e con GitHub
per gli aggiornamenti, manualmente o con controllo automatico facoltativo. Diagnostica
e backup escludono password/token. Dettagli: [docs/PRIVACY.md](docs/PRIVACY.md).

## Sviluppo

Kotlin, Jetpack Compose, Media3/ExoPlayer, Room, WorkManager. Build con Android Studio / Gradle:

```powershell
.\gradlew.bat :app:assembleDebug
```

Stato del progetto, wave e verifiche: [PLAN.md](PLAN.md), [WORKLOG.md](WORKLOG.md).

## Licenza

[MIT](LICENSE) — © 2026 Rosario Moscato - handcrafted by a human, upgraded by agents.
