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

- Italiano predefinito (`values/`), inglese (`values-en/`), scelta per-app via `AppCompatDelegate.setApplicationLocales` + `locales_config` (it, en); date/numeri con formati di sistema.

## Icona app (provvisoria)

- Adaptive icon v1: fondo `#2A4D9B`, foreground vettoriale con due bobine in `#F4F1EA` e dettaglio `#F06A21`. Icona definitiva originale prevista in wave 12 (W12-T04).

## Fuori perimetro in v1

- Skin alternative (backlog B04), animazioni non funzionali, temi dinamici Material You.
