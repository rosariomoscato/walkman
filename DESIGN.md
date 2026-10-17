# WalkTape — design system

Stato: v1 stabilita in wave 02 sullo stack Compose/Material3; vincolante per le schermate. Le scelte qui definite derivano dall'identità approvata (reinterpretazione del Sony TPS-L2) e dai vincoli di accessibilità di `SPEC.md`. Ogni modifica va concordata e registrata nel log.

## Principi

1. **Riferimento TPS-L2, non imitazione**: la scocca blu/argento del walkman ispira palette, materiali e tasti meccanici; le liste e i form restano elenchi leggibili in stile Material3, il player (wave 06) è l'unico ambiente a interpretazione libera.
2. **Suono pulito = interfaccia sobria**: default senza animazioni superflue; rispetto di `prefers-reduced-motion`/riduzione movimento Android.
3. **Nessun dato finto**: stati vuoti onesti, mai segnaposto che sembrino contenuti reali.
4. **Il tema scuro non è un filtro**: palette costruita per contrasto su fondo blu notte, non inversione automatica.

## Palette — token (implementati in `ui/theme/Color.kt`)

| Token | Hex | Uso |
| --- | --- | --- |
| `WtBlue` | #2A4D9B | Chassis: primary chiaro, tasti primari, barre attive |
| `WtBlueDeep` | #1B3268 | Contenitori profondi, primary container |
| `WtBlueNight` | #101A30 | Fondo tema scuro, superfici notturne |
| `WtBlueSoft` | #8FA8E8 | Primary su fondo scuro (testo/tasti) |
| `WtSteel` | #C9CED6 | Alluminio spazzolato: superfici secondarie, testo su scuro |
| `WtSteelDark` | #9AA1AC | Bordi, outline, testo secondario |
| `WtOrange` | #F06A21 | Accento arancione TPS-L2: etichette di sezione, azioni emphasized, REC |
| `WtCream` | #F4F1EA | Etichetta cassetta: fondo tema chiaro, testo su primari |
| `WtInk` / `WtInkSoft` | #17181C / #3C3F47 | Testo principale/secondario tema chiaro |

Regole:
- L'arancione è **accento** mai fondo esteso; touch target e focus visibili.
- Il tema dinamico Android (Material You) resta **disattivato**: l'identità è del marchio.
- `dynamicColor` non implementato; colore fisso per coerenza screenshot/diagnostica.

## Tipografia

- Font di sistema (Roboto/accordo dispositivo) in v1: `Typography` Material3 con `headlineMedium` (26sp, bold) per titoli schermata, `titleLarge`/`titleMedium` per righe, `labelSmall` (11sp, bold, +1.2 tracking) per etichette di sezione in arancione maiuscolo.
- Font display dedicato per il marchio/player: da valutare in wave 06 (requisito: licenza aperta, numeri tabulari per tempi, leggibilità a 14sp).
- Testo scalabile con FontScale di sistema fino a 200%: layout verificati con caratteri ingranditi (wave 06/12).

## Materiali e superfici

- **Chassis**: `WtBlue`/`WtBlueDeep` con bordo 1.5dp `outline` e ombra bassa (2dp): sensazione di corpo macchina senza peso visivo.
- **Tasti meccanici** (`MechanicalKey`): superficie `secondaryContainer` (o `tertiary` per emphasized), bordo outline, angoli 8dp, pressione con scala 0.96 e senza indicatore ripple (il feedback è aptico/tattile, configurabile e disattivabile).
- **Etichetta cassetta** (player, wave 06): `WtCream` con tipografia idonea; bobine animate solo durante riproduzione, ferme in pausa.

## Spaziature e griglia

- Margine orizzontale schermata: 16dp (telefono), 24dp da `medium`/espanso.
- Sezioni: intestazione `SectionHeader` + 8dp; liste: righe 56–64dp minime.
- Touch target ≥ 48dp ovunque; controlli del player ≥ 56dp.
- Layout adattivo: `WindowSizeClass`; `Compact` = navigazione inferiore, `Medium`/`Expanded` = navigation rail; due riquadri solo dove previsto (player+coda su `Expanded`).

## Stati

- **Vuoto**: `EmptyState` con icona in disco `surfaceVariant`, titolo e corpo onesti (nessun dato dimostrativo).
- **Caricamento**: indicatori lineari sottili in `WtOrange`; mai spinner a schermo intero sulle liste.
- **Errore rete**: banner inline con azione riprova; assenza server = stato vuoto, non errore bloccante.
- **Offline**: indicazione esplicita persistente dove rilevante (wave 08).
- **Focus** visibile su ogni controllo interattivo (outline 2dp `WtOrange`).

## Accessibilità (vincoli)

- Contrasto: testo ≥ 4.5:1, componenti grandi ≥ 3:1 (verificato su entrambi i temi).
- TalkBack: `contentDescription` su icone funzionali; ordine di lettura logico; etichette di sezione navigabili.
- Riduzione movimento: niente parallasse/niente animazioni decorative quando il sistema la richiede.
- Temi: chiaro/scuro/sistema (`ThemeMode` in DataStore); dimensioni font di sistema rispettate.

## Localizzazione

### Schermate libreria (wave 04)

- La mia musica espone il progresso dell'indicizzazione, ricerca locale e tab Album/Artisti/Brani/Generi; righe album con copertina e metadati, senza comandi di riproduzione finti.
- Dettaglio album con copertina, artista album e tracce nell'ordine disco/numero; artista traccia visibile anche nelle compilation.
- Selezione con pressione prolungata, checkbox e riepilogo/annullamento. Comandi di coda, playlist e download saranno collegati nelle wave dei rispettivi servizi.
- Mantiene palette, margini e tipografia della wave 02. Le sezioni home approvate ancora mancanti restano task aperti nel piano.

Incremento W04-T02/T03: apertura sulla tab Panoramica con sezioni Continua ascolto, Aggiunti/Ascoltati di recente, Preferiti, Download e Playlist. Le sezioni senza dati usano testo vuoto esplicito; riproduzione e download sono collegati nelle wave dedicate. Tab Playlist con dettaglio nell'ordine server e tab Tutti per ricerca unificata; filtri genere/anno e chip disponibilità offline. Sono comandi di consultazione, senza azioni di modifica playlist premature; token visivi invariati.

Incremento W05-T01: dettaglio album con `FilledTonalButton` “Riproduci album” e icona Play; tap sulla traccia avvia l'album dalla posizione selezionata. Righe interattive con altezza minima 48dp. Sono comandi collegati al servizio reale; la scocca/player definitiva resta wave 06. Palette e tipografia invariate. La sezione Continua ascolto indica l'assenza di una coda **salvata**, in attesa della persistenza W05-T04.

Incremento W05-T02: barra Material3 sopra la navigazione, visibile solo con una traccia nella sessione, anche nei dettagli/impostazioni/diagnostica e con navigation rail. Titolo/artista, precedente/play-pausa/successivo e apertura del pannello, icone funzionali con descrizioni IT/EN e tasti 56dp. Con font scale >130% o larghezza <320dp la barra passa su due righe. Il pannello ModalBottomSheet è scrollabile, con metadati/stato, seek continuo e tempi, ripetizione off/brano/coda e volume multimediale Android; si chiude svuotando la coda. Seek disabilitato senza durata reale/seekability, comandi disabilitati quando la sessione non li espone; caricamento lineare e messaggio errore senza dettagli sensibili. Nessuna nuova palette, animazione decorativa o scocca definitiva in questo incremento.

Incremento W05-T03: dal pannello si apre **Coda**, elenco LazyColumn nell'ordine effettivo d'ascolto. Brano corrente evidenziato, tap per ascoltare un'occorrenza, X per rimuoverla, menu per **Sposta su/Sposta giù/Rimuovi** e corrispondenti azioni accessibili. ID distinti anche per copie dello stesso brano, chiavi UI stabili e touch target 48dp. Nessun gesto drag dichiarato operativo: il prototipo non ha superato il test touch e il riordino è disponibile dal menu. Shuffle visibile con indicatore attivo e azione per tornare all'ordine originale, senza spostare o ricaricare il corrente; riordinare manualmente disattiva shuffle (avviso nel pannello). Nelle tab Album/Brani, selezione multipla con Riproduci ora/Dopo il corrente/Aggiungi in coda, FlowRow adattivo, caricamento e stato di indisponibilità esplicito. Aggiunta a coda vuota rimane in pausa; solo Riproduci ora avvia l'ascolto. Palette e tipografia invariate.

Incremento W05-T04: la sezione **Continua ascolto** della Panoramica mostra il brano in pausa del profilo attivo (titolo/artista) con **Riprendi ascolto**; nessuna ripartenza automatica alla riapertura dell'app né notifica finché il player resta idle. Banner inline in `errorContainer` solo quando il salvataggio/ripristino della coda fallisce per il profilo attivo, con testo di recupero esplicito. Nessun nuovo token, animazione o componente della scocca; la sezione vuota precedente resta per i profili senza coda.

- Italiano predefinito (`values/`), inglese (`values-en/`), scelta per-app via `AppCompatDelegate.setApplicationLocales` + `locales_config` (it, en); date/numeri con formati di sistema.

## Icona app (provvisoria)

Incremento W05-T06: nel pannello scrollabile, dopo volume/coda, sezione Timer con preset 15/30/60/90 min, **Fine brano/Fine album/Fine coda** e disattivazione; conto alla rovescia testuale. Fine album disabilitato senza albumId noto; si riferisce al gruppo consecutivo dell'album corrente nell'ordine d'ascolto, non al titolo dell'album né all'intera coda. Nelle impostazioni scrollabili, interruttore suggerimenti **default off**, riga toggleable con unico ruolo Switch/etichetta; a fine coda pulsante di continuazione solo con risultati del server, mai autoplay. Palette invariata, layout FlowRow e stringhe IT/EN. Core verificato su emulatore; TalkBack, font ingranditi e ascolto della dissolvenza sul Motorola restano prove manuali finali.

- Adaptive icon v1: fondo `#2A4D9B`, foreground vettoriale con due bobine in `#F4F1EA` e dettaglio `#F06A21`. Icona definitiva originale prevista in wave 12 (W12-T04).

## Player TPS-L2 — W06 (2026-10-15)

- Il lettore è una rotta dedicata: la freccia della mini-bar la apre da libreria, dettagli, profili, impostazioni e diagnostica. Sulla rotta player la mini-bar è sostituita dalla scocca; Indietro ritorna alla schermata precedente senza avviare audio.
- Scocca con gradiente verticale WtBlue→WtBlueDeep, bordo acciaio e angoli 20dp; fascia superiore metallica chiara e marchio **WalkTape** in carattere di sistema bold italic. Decisione sul display font: mantenere il font di sistema, senza nuovo asset/font commerciale; contatore monospace per cifre allineate. La fascia/arancio decorativo non comunica stati musicali.
- Tasti con gradiente acciaio, icone WtInk; PLAY arancio con icona WtBlueNight (contrasto leggibile anche nel tema chiaro). Stato attivo shuffle/repeat con indicatore arancio dentro un pozzetto scuro e descrizione accessibile. Target ≥56dp, trasporto in FlowRow per larghezze ridotte, pressione 0,96 e nessun ripple. STOP significa **pausa**, conserva coda/posizione e non espelle contenuti.
- Finestra blu notte, cassetta crema, bobine/tape disegnati con Canvas: angolo aggiornato dal frame clock solo con `PlaybackSnapshot.isPlaying`; fermo in pausa, buffering o soppressione del focus. Riempimento bobine proporzionale alla posizione della traccia (rappresentazione visiva, non durata complessiva del nastro). Disabilitare animazioni Android congela le bobine; le preferenze sono osservate anche a schermata aperta.
- Etichetta: nome playlist se tutte le occorrenze hanno la stessa provenienza; altrimenti nome album solo se condividono un albumId noto e un titolo coerente; code miste/identità ignote → **Mixtape**. Nessuna etichetta su coda vuota. La copertina arriva dalla cache privata del profilo e si apre in un dialog grande; assenza di art = riquadro vuoto, azione disabilitata. Titolo/artista/album completi restano nella sezione scrollabile fuori dalla cassetta.
- REW/FF: tap precedente/successivo, pressione prolungata a passi 5/10/15 s ogni 350 ms dopo la soglia Android; nessun salto di traccia al rilascio del gesto prolungato. Slider con seek in ms e azioni accessibili ±10 s. Cambio occorrenza ricrea il sottogruppo di tasti e invalida il gesto; chiave include UUID, URI logica e indice. Volume sempre Android STREAM_MUSIC.
- Preferenze dispositivo: feedback aptico attivo/disattivabile, clic opzionali inizialmente spenti. Haptics tramite API View che rispettano Android; clic FX_KEY_CLICK soltanto se abilitati sia nell'app sia nei suoni di sistema. Nessun suono automatico su pause/transizioni. Non è l'effetto cassetta audio W09.
- Compact/medium: player scrollabile con coda in sheet; da 840dp **di contenuto disponibile**: player e coda in due pannelli, scroll indipendenti con altezza finita per LazyColumn. I tasti si dispongono su più righe. La mini-bar rispetta gli inset laterali Android in landscape. Temi classico/scuro/sistema: materiali della macchina fissi, superfici esterne dal tema.
- Verifiche automatiche: 3 test contrasto per testi/icone, etichetta per ID, semantica di trasporto/seek/coda/cover, IT/EN, dump con font 200% e viewport tablet. La piccola icona sulla cover occupa un target 56dp. **TalkBack, percezione aptica/clic e approvazione visiva sul Motorola restano controlli manuali aperti W06-T06**. Le misure di colore degli screenshot non sostituiscono una review visiva/manuale completa.

### Incremento W07 — playlist, preferiti, testi e cronologia

- Playlist: creazione dalla tab Playlist, aggiunta brani/album dalla selezione multipla tramite scelta della destinazione o nuova playlist. Dettaglio con rinomina/eliminazione (conferma), menu per occorrenza Sposta su/giù/Rimuovi e azioni accessibili; nessun drag implicito. Playlist di altri utenti o readonly in sola lettura con motivo esplicito. Errori inline, senza riportare messaggi grezzi del server.
- Preferiti: **cuore pieno/vuoto**, mai stelle o valutazioni. Stato attivo nel colore primary del tema, descrizione Aggiungi/Rimuovi dai preferiti e target 48dp. Header album/artista e righe brani; la Panoramica riflette gli aggiornamenti Room dopo ACK.
- Coda: azioni Nome della mixtape e Salva coda come playlist in FlowRow. Il salvataggio conserva l'ordine mostrato, incluso shuffle e duplicati, con nota nel dialog. Nome nel titolo coda ed etichetta cassetta: origine playlist omogenea, altrimenti nome scelto, poi album noto omogeneo/Mixtape. Campo vuoto o Rimuovi nome torna all'etichetta derivata; nuova coda azzera il nome, modifiche della stessa coda lo mantengono.
- Player: Dettagli brano apre sheet scrollabile con dati reali (artista/album/anno/genere/formato/bitrate/durata), testo statico o righe sincronizzate evidenziate e seguite dalla posizione del player. Cache per profilo, testo assente/errore espliciti e Riprova; nessun testo di esempio in app.
- Cronologia dalla Panoramica: ultimi 30 eventi locali con titolo/artista/data e stato attesa/ACK server; retry manuale. Palette e tipografia preesistenti. Testi sincronizzati automatizzati con fixture; testo reale non sincronizzato verificato in sheet su emulatore. TalkBack e review sul telefono delle nuove superfici restano nella checklist privata W07.

### Incremento W08 — download, solo offline e sincronizzazione

- Azione **Scarica** da selezione Brani/Album/Playlist (checkbox playlist etichettata), **Scarica album**, **Scarica playlist** anche vuota e **Aggiorna download**. Download nella Panoramica con apertura del foglio **Gestisci download**: stato reale, byte/progresso, pausa/ripresa/rimozione e riproduzione; gruppi playlist conservano ordine e duplicati. Una playlist non più sul server conserva lo snapshot locale con indicazione esplicita.
- Impostazioni **Download e storage** per il profilo: solo Wi-Fi inizialmente on, mobile opt-in, solo offline e skip indisponibili inizialmente on. Spazio letto dai file, limiti cache con chip adattivi, pulizia distinta dai download. Cache audio in uso protetta: un limite può essere temporaneamente superato finché la sorgente viene chiusa. Palette/materiali/tipografia TPS-L2 invariati.
- Banner persistente **Solo offline** nella libreria; nel player notice quando si salta un brano indisponibile, senza falso esito di download. Nessun autoplay alla riconnessione.
- Le modifiche locali sono esplicitamente pendenti: riepilogo nella libreria, foglio **Gestisci sincronizzazione**, stato invio/rifiuto/conflitto e retry. Dettaglio playlist con pending e risoluzione locale/server/nuova copia. Cuori locali pendenti non vengono presentati come ACK server. IT/EN e FlowRow/chip per evitare overflow dei nuovi controlli; TalkBack e collaudo adattivo completo W08 restano nella checklist telefono.

## Incremento W09 — qualità e suono (2026-10-15)

- Impostazioni qualità nel gruppo del profilo: Originale/Ridotta su rete mobile, chip bitrate e download ridotti opzionali. I file già scaricati non vengono sostituiti cambiando preferenza. Sound settings di dispositivo separati: crossfade con durata, ReplayGain off/brano/album, EQ con bande realmente fornite da audiofx, bassi e cassetta. Tutti inizialmente spenti, componenti Material3 e palette TPS-L2 invariati.
- Slider con descrizione accessibile, durata in frazioni di secondo e range reali. Se il sistema non fornisce gli effetti la UI mostra indisponibilità; nessuna banda dimostrativa. Dettagli brano: qualità/formato osservato e ReplayGain tag/assenza neutra, distinti dai metadati originali dell'indice.
- Prove automatiche del player W06 ripetute; TalkBack, adattività e resa sonora sul telefono delle nuove impostazioni ancora da collaudare con `local/PHONE_TESTS_W09.md`. Crossfade funzionale ma T02 aperto sui limiti di handover/attribuzione overlap, descritti in ARCHITECTURE/WORKLOG.

## Incremento W10 — interfaccia Android Auto

- UI disegnata dalla piattaforma Auto, senza riprodurre la scocca TPS-L2. Root: La mia musica,
  Playlist, Download; Album/Artisti/Brani dentro La mia musica. Nomi categoria IT/EN e metadati
  reali con tipi Media3 per album, artista, playlist e musica.
- Finestre grandi tramite Altri risultati; download con conteggio disponibili/totali e stato
  snapshot conservato se la playlist è assente sul server. Setup sul telefono quando il profilo
  manca; vuoti onesti e nessuna riproduzione provocata da browse/riconnessione.
- Palette, player Compose e token TPS-L2 invariati. Layout effettivo head unit, lingua, voce,
  rotella/touch e visualizzazione copertine in proiezione richiedono ancora DHU/auto reale.

## Incremento W11 — impostazioni sistema, diagnostica, backup, aggiornamenti (2026-10-17)

- Impostazioni: nuova sezione **Stato e recupero** dentro Sistema con soli dati reali osservati
  (profilo attivo, esito verifica, indice con ultimo aggiornamento/conteggi/progresso lineare in
  WtOrange, modifiche in attesa/conflitti, download attivi/pausa/errori). Azioni materiali
  (Verifica, Aggiorna indice, Annulla, Invia adesso, Metti tutti in pausa/Riprendi tutti) come
  TextButton esistenti; rinvio onesto alla gestione conflitti in La mia musica. Righe voce con
  icona primaria per **Backup e trasferimento** e **Aggiornamenti app**, accanto a Diagnostica.
- Diagnostica: interruttori Material3 per le nove sezioni (CONNECTIONS con avviso rosso quando
  inclusa), anteprima monospace sempre aggiornata senza ricampionare, tasti Copia/Esporta file/
  Condividi file (MechanicalTextKey, emphasized su Condividi). Nessun dato dimostrativo.
- Backup: schermata testo+descrizione onesta (inclusi/esclusi), anteprima obbligatoria in dialog
  con checkbox preferenze, risultato import per profilo (creato/unito, coda, da riscaricare
  aggiunti/conservati, errori in `error`). Profili importati con badge **Accesso richiesto** e
  dialog di nuovo accesso con campi precompilati e password mai precompilata.
- Aggiornamenti: versione installata, stato con esiti onesti (nessuna release pubblicata, rate
  limit, offline), novità come testo, progresso download lineare, rifiuti verifica con motivi
  espliciti (firma incompatibile senza proporre disinstallazione), invito unknown-sources
  limitato all'app e conferma sempre di Android. Interruttore controllo automatico default off
  con testo "solo controllo".
- Palette/tipografia/materiali TPS-L2 invariati; FlowRow per le azioni multiple; IT/EN completo;
  barre di navigazione nascoste sulle tre sotto-schermate come Diagnostica. TalkBack/adattività
  su telefono da collaudare con `local/PHONE_TESTS_W11.md`.

## Incremento W12 — coerenza navigazione e informazioni (2026-10-17)

Distribuito nell'[APK pubblico v0.1.0](https://github.com/rosariomoscato/walktape/releases/tag/v0.1.0):
icona Cassetta approvata, splash/versione/credits e disposizione dei tasti invariati
rispetto al codice collaudato. Versione visualizzata letta dal pacchetto, non duplicata
negli asset. Dettagli artefatto e checksum in docs/RELEASING.md.

### Rinforzo bassi: disponibilità per uscita approvata dall'utente

Su speaker il comando appare spento/disabilitato con avviso «Rinforzo bassi non
disponibile sull’altoparlante», preferenza e intensità conservate per le cuffie.
Su route cuffie rilevata switch e slider tornano operativi con la preferenza salvata
(slider accessibile anche a intensità zero). Spiegazione: resa dipendente da cuffie/
effetti dispositivo. Prima di avviare audio o su route ignota avviso di sospensione,
non un falso stato disponibile. Soli token/componenti esistenti, testi IT/EN e unica
semantica Switch disabilitata quando non applicabile. Nessuna compensazione volume.
La disponibilità segue gli AudioTrack realmente in riproduzione (entrambi durante
overlap), non la lista di dispositivi collegati. Non ancora prova acustica della resa.

### Splash richiesto dall'utente

Benvenuto Compose locale al primo lancio launcher del processo: icona Cassetta originale,
nome WalkTape, versione reale da PackageManager e claim autore già approvato (testo qui
non interattivo, link permanente in Impostazioni). Fondo WtBlue, testo WtCream,
durata nominale1200ms non bloccante; nessuna animazione/suono/rete. Logo decorativo,
testo scalabile, area scrollabile per landscape/font grandi. Deadline monotona salvata
evita di ricominciare il timer alla rotazione. Notifica e rientri nel processo esistente
non ripetono il benvenuto. Gate impostazioni/biometria rimane dopo il benvenuto.
Android12+ mantiene splash nativo con icona app e fondo blu coordinato; non è introdotta
una seconda Activity. Splash e tasti approvati dall'utente nel collaudo successivo;
nessuna estensione implicita a tutti i casi lifecycle/font/viewport.

### Affinamento tasti player richiesto dall'utente

Le due file di tasti occupano tutta la larghezza disponibile: pesi FlowRow distribuiscono
lo spazio per ogni riga. Trasporto alto60dp, precedente/successivo uguali (peso1),
Play/Pausa e Stop più larghi (peso1,25); fila secondaria alta56dp con tasti tutti
di uguale larghezza (peso1). Spazi orizzontali/verticali10dp. Base56dp preserva i
target touch e l'andata a capo su viewport stretti: anche le righe aggiuntive distribuiscono
lo spazio disponibile. Nessun cambio a icone, comandi, hold/seek, aptica o clic.
La coda omessa nel layout tablet affiancato lascia redistribuire gli altri tasti.

- Le sotto-pagine di Impostazioni (Diagnostica, Backup e trasferimento, Aggiornamenti app) e
  Profili ora hanno una `TopAppBar` con **freccia Indietro** (`action_back`) come i dettagli
  de La mia musica: ogni schermata raggiungibile torna alla provenienza anche senza il gesto
  di sistema. Titolo in `titleLarge` della barra; il testo introduttivo resta sotto la barra.
  Le barre di navigazione restano nascoste sulle tre sotto-pagine W11; Profili mantiene la
  barra come già in W05.
- Fondo Impostazioni: **versione reale** "WalkTape <versionName>" letta da PackageManager
  (coerente con Aggiornamenti app e Diagnostica `app.version`) e **crediti autore**
  "© 2026 Rosario Moscato - handcrafted by a human, upgraded by agents." con il nome quale
  link (`LinkAnnotation.Url`) a `https://rosmoscato.xyz`, aperto solo al tap esplicito;
  corpo in `bodySmall`/`onSurfaceVariant`, link in `primary`. Nessuna risorsa localizzata
  del claim (testo di marchio identico IT/EN).
- Icona definitiva: **variante A "Cassetta" approvata dall'utente** (2026-10-17) e sostituita
  in `ic_launcher_foreground.xml`: corpo cassetta crema `#F4F1EA` con bordo `#9AA1AC`,
  bobine `#1B3268` con mozzo crema, finestra nastro `#F06A21`, viti steel; adaptive con
  background `#2A4D9B` e monochrome sullo stesso vettore, contenuto nella safe zone.
  Varianti non scelte (B "Bobine e onda", C "Finestra TPS-L2") archiviate con SVG e
  anteprima in `local/` (W12).

## Fuori perimetro in v1

- Skin alternative (backlog B04), animazioni non funzionali, temi dinamici Material You.
