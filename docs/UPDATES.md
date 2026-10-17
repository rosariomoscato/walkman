# WalkTape — Aggiornamenti

Funzione W11-T04. Controlli e download dalle **GitHub Releases** di
`rosariomoscato/walktape`. Nessun aggiornamento forzato, nessuna installazione automatica:
Android chiede sempre conferma.

## Percorso

Impostazioni → **Aggiornamenti app**:

1. **Controlla aggiornamenti**: interroga `api.github.com` con un client dedicato (senza
   credenziali né token). "Nessuna release pubblicata" è un esito onesto, non un errore.
2. Se c'è una versione più recente: **novità** del rilascio e **Scarica APK** (asset scelto
   solo se univoco: build universal preferite alle ABI-specifiche; ambiguità rifiutata).
3. L'APK viene verificato **prima** di qualsiasi installazione: stesso package
   `com.walktape.app`, versionCode crescente e **firma identica** a quella installata. Un APK
   di altra app, un downgrade, un file corrotto o una firma incompatibile vengono rifiutati con
   motivo esplicito; per firme incompatibili non si propone mai la disinstallazione (perderebbe
   i dati).
4. **Installa aggiornamento**: se Android non consente ancora installazioni da WalkTape, viene
   aperta l'impostazione "origine sconosciuta" **solo per questa app**; poi il installer di
   sistema mostra la conferma.

## Controllo automatico

Interruttore **opt-in, inizialmente spento**: al massimo un controllo al giorno, solo un
controllo — nessun download o installazione automatica. L'esito dell'ultimo controllo è
riportato nella schermata. In **Solo offline** nessun controllo viene effettuato (nemmeno
quello giornaliero viene "consumato").

## Note

- Il download avviene nella cache privata dell'app con file temporaneo: un download interrotto
  non lascia APK parziali e può essere annullato.
- La firma stabile per gli aggiornamenti e la pipeline di pubblicazione sono parte della
  wave 12.
