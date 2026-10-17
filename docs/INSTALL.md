# WalkTape — installazione e connessione

Come installare WalkTape e collegarlo al proprio server Navidrome.

## 1. Installare l'APK

La prima GitHub Release non è ancora pubblicata; questa procedura descrive
l'installazione quando l'APK sarà disponibile nella pagina Releases.

1. Scarica l'APK `walktape-universal-…apk` dall'ultima
   [release](https://github.com/rosariomoscato/walktape/releases).
2. Aprilo sul telefono ("Scarica" completata → tocca il file, o da File manager).
3. Android può chiedere il permesso di installare app sconosciute per il browser o
   gestore file che apre l'APK. Per gli aggiornamenti avviati dentro WalkTape, invece,
   il permesso riguarda WalkTape. Non è un'autorizzazione globale per tutte le app.
4. Conferma l'installazione nell'installer di sistema.

**Aggiornamenti**: si installano sopra la versione precedente (stessa firma verificata
dall'app: vedi [UPDATES.md](UPDATES.md)). Una build con firma diversa richiede la
disinstallazione: i dati dell'app andrebbero persi, la musica sul server no.

## 2. Rendere Navidrome raggiungibile

WalkTape si collega al server che già possiedi (compatibilità dichiarata: solo Navidrome).

- **Stessa LAN**: usa l'indirizzo del computer sul router, es. `http://192.168.1.10:4533`.
- **Fuori casa (Tailscale o altra VPN già attiva)**: usa l'indirizzo IP/MagicDNS di Tailscale
  del computer, es. `http://100.x.y.z:4533` o `http://nas:4533`. WalkTape non installa né
  configura la VPN: la rete deve essere già raggiungibile dal telefono.
- **HTTPS**: consigliato quando il server è esposto oltre la propria VPN/LAN. HTTP è
  consentito nei contesti locale/VPN e l'app mostra sempre il tipo di connessione.

Consigli rapidi per Tailscale: installa Tailscale su computer e telefono, accedi con lo stesso
account, verifica dal telefono che `http://<ip-tailscale-del-pc>:4533` apra Navidrome nel
browser, poi usa lo stesso indirizzo in WalkTape.

## 3. Aggiungere il profilo in WalkTape

1. Al primo avvio scegli **Aggiungi profilo**.
2. Inserisci nome (libero), URL del server, username e password Navidrome.
3. **Verifica e indicizza**: l'operazione scarica l'indice della libreria nel tempo; il
   progresso è visibile in La mia musica.
4. In alternativa, su un altro telefono con il profilo già configurato: **Condividi QR**
   genera un codice con URL/nome/username (mai la password, che si digita a mano).

Si possono configurare più profili (uno attivo per volta): dati, download e code restano
isolati; il cambio profilo arresta la riproduzione e ripristina la coda del profilo scelto.

## 4. Primo ascolto e download

- **La mia musica** → album, artisti, brani, generi, playlist; il player TPS-L2 si apre dalla
  barra in basso.
- Per l'offline: seleziona brani/album/playlist → **Scarica**. Impostazioni → Download e
  storage per i limiti di cache, la rete mobile e la modalità **Solo offline**.
- Android Auto: collega il telefono come di consueto, WalkTape appare tra le app multimediali
  (vedi [ANDROID_AUTO.md](ANDROID_AUTO.md)).

## Problemi comuni

- **"Server non raggiungibile"**: controlla URL e rete (LAN/Tailscale attiva su entrambi i
  dispositivi). La diagnostica in Impostazioni mostra cosa vede l'app senza esporre segreti.
- **Aggiornamento rifiutato (firma incompatibile)**: l'APK installato ha una firma diversa
  dall'APK della release. L'app non propone mai di disinstallare da sola: valuta tu se
  rimuovere la build precedente (i dati app locali andranno persi).
- **Login dopo import backup**: il backup non contiene password; ogni profilo importato va
  sbloccato con un nuovo accesso (badge "Accesso richiesto").
