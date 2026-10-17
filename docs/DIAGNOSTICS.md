# WalkTape — Diagnostica

Funzione W11-T02. La diagnostica è **solo locale**: nessun invio automatico, nessuna telemetria.

## Cosa contiene

Il rapporto è costruito da un'allowlist di campi, organizzato in nove sezioni:

| Sezione | Contenuto |
| --- | --- |
| App | versione/versionCode/package, lingua |
| Dispositivo | produttore, modello, Android/API, ABI, display |
| Spazio | byte disponibili/totali e uso per profilo (musica, cache audio, copertine) |
| Indice | per profilo: ultimo aggiornamento, esito, conteggi; eventuale refresh in corso |
| Download | conteggi per stato e categorie di errore (mai messaggi grezzi) |
| Sync | operazioni offline per stato (attesa/conflitto/rifiutato) |
| Player | stato volatile (connesso, media, shuffle, repeat, timer) senza titoli |
| Connessioni | **opt-in**: URL e nome utente dei profili (esclusa dal default) |
| Preferenze | impostazioni di dispositivo, nessun segreto |

Password, token, credentialId, testo cifrato, URL autenticati, header Authorization/Cookie e
blob codificati non entrano mai nel rapporto; tutto il testo passa comunque da un redattore che
oscura anche parametri OpenSubsonic corti (`p/t/s/u=`) e le query degli URL.

## Uso

1. Impostazioni → **Diagnostica**: attiva/disattiva le sezioni da includere (Connessioni mostra
   un avviso quando inclusa).
2. **Copia rapporto** (clipboard), **Esporta file** (SAF, salva un `.txt` dove scegli) o
   **Condividi file** (condivisione Android con accesso temporaneo in sola lettura).

In **Solo offline** non cambia nulla: il rapporto è costruito localmente. L'anteprima si
aggiorna senza ricampionare i dati.
