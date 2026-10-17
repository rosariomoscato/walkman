# WalkTape v0.1.0

Prima versione pubblica — Android 8.0+ (API 26), Navidrome.
`versionName=0.1.0`, `versionCode=2`, package `com.walktape.app`.

## Installazione

- APK universal: **walktape-universal-0.1.0.apk**.
- Checksum: **SHA256SUMS.txt**, nella stessa pagina release.
- [Release v0.1.0](https://github.com/rosariomoscato/walktape/releases/tag/v0.1.0).
- [Guida installazione e Tailscale](https://github.com/rosariomoscato/walktape/blob/main/docs/INSTALL.md).

SHA256 APK:
`c1ccacb4632d39da11c4bd0344cbe092a90f1eca7d6badc253ca04be72668957`.

Firma stabile identica alle build di sviluppo di questo progetto: l'APK si installa
sopra di esse senza disinstallare. Le build di test già denominate **0.1.0** risultano
già aggiornate nel controllo in-app: per passare al pacchetto release, scaricare e
installare manualmente questo APK. I futuri aggiornamenti richiederanno versionName
e versionCode crescenti e la stessa firma.

## Funzioni

- Profili Navidrome multipli con credenziali cifrate in Android Keystore, dati e download
  isolati; QR senza password e blocco biometrico facoltativo.
- Libreria con album/artisti/brani/generi/playlist, ricerca locale, filtri, ordinamenti,
  preferiti e testi forniti dal server.
- Player TPS-L2 con cassetta animata, tasti a larghezza piena, coda con duplicati,
  shuffle/repeat, nome mixtape, timer e volume multimediale Android.
- Splash con logo, versione e credits; ritorno esplicito dalle sotto-pagine;
  italiano/inglese, temi chiaro/scuro/sistema e layout adattivo.
- Originali MP3/FLAC e qualità ridotta Opus configurabile; crossfade, ReplayGain,
  equalizzatore ed effetto cassetta opzionali, suono pulito di default.
- Rinforzo bassi disponibile sulle cuffie secondo il dispositivo; sospeso su altoparlante
  o route non rilevata, con avviso e preferenza/intensità conservate.
- Download brani/album/playlist con pausa/ripresa, cache limitata, modalità Solo offline
  a zero richieste; modifiche offline a playlist/preferiti e risoluzione conflitti.
- Ascolti con deduplica locale e ACK durevole; limite di idempotenza lato Navidrome
  documentato (nessuna garanzia exactly-once se si perde la risposta del server).
- Catalogo Android Auto moderno/legacy sullo stesso servizio e sulla stessa coda.
- Backup manuale senza credenziali/audio, diagnostica esportabile senza invii automatici,
  aggiornamenti GitHub con verifica package/versionCode/firma e conferma Android.

## Verifiche e controlli ancora aperti

- Codice finale: **177/177 test JVM**, lint debug **0 errori / 45 avvisi**; suite Android
  W12 in esecuzioni separate su AOSP con fixture e server reale Navidrome 0.62.0.
- APK release riallineato al codice finale: build e lint release riusciti
  (**0 errori / 45 avvisi**), firma V2 verificata; processo e digest certificato in
  [RELEASING.md](https://github.com/rosariomoscato/walktape/blob/main/docs/RELEASING.md).
- Ultime regressioni bassi: controller, handover offline e timer durante overlap superati
  in tre esecuzioni Android distinte. Esiti e fallimenti intermedi nel WORKLOG.
- Motorola G55 / Android 16: checklist W12 A/B positiva, W11 18/24; splash/tasti e correzione
  bassi hanno ricevuto riscontri positivi dell'utente, senza chiudere voci non attestate.
- **Ancora aperti**: proiezione Android Auto/DHU e collaudo in auto, matrice residua
  chiamate/route/audiofx/batteria/font ingranditi, trasferimento backup su ambiente pulito
  e percorso interattivo completo di un futuro aggiornamento in-app.

La pubblicazione dell'APK non equivale al superamento di questi collaudi.
Compatibilità dichiarata solo con Navidrome; niente Play Store, Cast/DLNA o libreria
di file locali indipendente dal server.

## Documentazione e licenza

[Android Auto](https://github.com/rosariomoscato/walktape/blob/main/docs/ANDROID_AUTO.md) ·
[Backup](https://github.com/rosariomoscato/walktape/blob/main/docs/BACKUP.md) ·
[Aggiornamenti](https://github.com/rosariomoscato/walktape/blob/main/docs/UPDATES.md) ·
[Diagnostica](https://github.com/rosariomoscato/walktape/blob/main/docs/DIAGNOSTICS.md) ·
[Privacy](https://github.com/rosariomoscato/walktape/blob/main/docs/PRIVACY.md) ·
[Licenza MIT](https://github.com/rosariomoscato/walktape/blob/main/LICENSE).

© 2026 [Rosario Moscato](https://rosmoscato.xyz) - handcrafted by a human, upgraded by agents.
