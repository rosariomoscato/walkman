# WalkTape — Backup e trasferimento

Funzione W11-T03. Backup **manuale** su file JSON versionato (`walktape.backup`, versione 1):
nessun cloud WalkTape, nessun invio automatico, nessun account aggiuntivo.

Guida per [WalkTape v0.1.0](RELEASE_NOTES.md): il formato backup rimane v1,
indipendente dalla versione dell'APK. Per installare su un altro telefono: [INSTALL.md](INSTALL.md).

## Cosa include (e cosa no)

| Incluso | Escluso |
| --- | --- |
| Profili: nome, URL, nome utente, HTTP locale | **Password e chiavi** (mai, nemmeno cifrate) |
| Preferenze del dispositivo (tema, suoni, cache, DSP…) | Flag tecnici di caricamento |
| Lista brani da riscaricare (download completi) e gruppi playlist | File audio, cache, temporanei |
| Coda: occorrenze anche duplicate, ordine shuffle, posizione, repeat, nome mixtape | Ascolti/scrobble, conflitti e modifiche pendenti |

Playlist e preferiti non viaggiano nel file: si recuperano dal server con il nuovo accesso.

## Esportare

Impostazioni → **Backup e trasferimento** → **Esporta backup**: l'anteprima mostra profili,
code salvate, brani da riscaricare e se includere le preferenze; poi scegli il file con il
selettore di Android (nessun permesso storage all'app).

## Importare (cambio telefono o ripristino)

1. **Importa backup** → il file viene letto e **validato interamente** (versione, dimensioni,
   riferimenti, permutazioni): file troncato/corrotto/futuro è rifiutato senza toccare nulla.
2. L'anteprima dichiara cosa succederà: i profili con lo **stesso server e nome utente** vengono
   uniti (le loro credenziali e i file già scaricati restano; la coda viene sostituita — se il
   profilo attivo sta riproducendo, la sua coda non viene toccata); i profili nuovi vengono creati
   con suffisso onesto "(2)" in caso di omonimia e **richiedono un nuovo accesso**.
3. I brani da riscaricare compaiono in **Gestisci download** in pausa: nessun download,
   riproduzione o richiesta di rete parte dall'importazione.
4. Da **Profili** → **Accedi** per ogni profilo importato (password mai inclusa nel backup).

Le preferenze del dispositivo si applicano solo se richiesto nell'anteprima e solo se tutti i
profili sono stati importati senza errori.
