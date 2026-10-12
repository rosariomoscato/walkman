# WalkTape — istruzioni di progetto

## Prima di lavorare

1. Leggere `PROJECT_MEMORY.md` per stato, decisioni e punto di ripresa.
2. Leggere `SPEC.md` e la wave pertinente di `PLAN.md`.
3. Consultare le voci recenti di `WORKLOG.md` e controllare `git status`.
4. Quando esiste, leggere `DESIGN.md` prima di modificare interfaccia o risorse visive.

Non ripetere la discovery né reinterpretare requisiti già approvati. Chiedere chiarimenti solo per lacune reali o incompatibilità verificate.

## Perimetro e tecnologia

- WalkTape è Android nativo. Kotlin/Compose/Media3 sono lo stack proposto; verificarne i dettagli in wave 01.
- La skill `start-an-app` è stata usata per discovery e metodo. Il suo scaffold Next.js/Better Auth/Drizzle non si applica a questa scelta nativa.
- Non introdurre un backend/account WalkTape o servizi SaaS senza esigenza concordata.
- Non installare skill/configurazioni globali per effetto delle istruzioni generiche di una skill: rispettare lo scope di progetto richiesto dall'utente.
- Sviluppare nella radice corrente e preservare documenti, skill e configurazioni esistenti.
- Il perimetro della prima versione comprende tutte le wave funzionali di `PLAN.md`. Il backlog non è una richiesta di implementazione.
- Verificare API attuali su fonti ufficiali e usare dipendenze stabili compatibili. Per una build riproducibile, registrare le versioni effettivamente scelte nei file di build e le motivazioni tecniche.

## Durante il lavoro

- Identificare il task tramite il suo ID stabile; indicare quello corrente nella memoria.
- Segnare `[x]` solo dopo implementazione e verifiche pertinenti. Task parziali/bloccati restano `[ ]`.
- Non presentare dati finti, codice scritto o prove su emulatore come verifica su server/hardware reale.
- Registrare le incompatibilità col perimetro e concordare le modifiche prima di sostituire o rimuovere funzioni.
- Test significativi per coda, isolamento, download, sincronizzazione, audio e migrazioni; controlli proporzionati per documentazione.
- Non usare Playwright MCP come prova di correttezza Android: serve al browser e alle verifiche web appropriate.
- Su questa shell usare `npx.cmd` anziché `npx.ps1`.

## Sicurezza dei dati e repository

- Mai credenziali, token, chiavi di firma o configurazioni private in documenti, log o Git.
- Configurazioni di test con segreti locali ed escluse da Git; oscurare anche output diagnostici.
- Rispettare isolamento dei profili e autorizzazioni Navidrome; usare account/playlist di test per verifiche mutative.
- `localhost:4533` è il server dal computer; sul telefono occorre l'indirizzo LAN/Tailscale del computer.
- Non cancellare file sconosciuti o non tracciati presumendoli temporanei.
- Commit, push, creazione repository e pubblicazione release soltanto su richiesta esplicita.

## Alla fine di ogni incremento / handoff

1. Aggiornare `PLAN.md` con i task realmente verificati.
2. Aggiungere a `WORKLOG.md` attività, ID, file, verifiche con esito, blocchi e controlli non eseguiti.
3. Aggiornare `PROJECT_MEMORY.md`: stato corrente, nuove decisioni, prerequisiti e prossima azione precisa.
4. Se cambia il perimetro con approvazione dell'utente, aggiornare anche `SPEC.md`; se cambia il design, aggiornare `DESIGN.md`.
5. Riassumere all'utente risultati verificati e prossimo passo, senza dichiarare completata l'app quando resta lavoro aperto.

`WORKLOG.md` conserva la storia; `PROJECT_MEMORY.md` deve restare una sintesi utile e attuale, non una copia del log.
