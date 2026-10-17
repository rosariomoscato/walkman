# WalkTape — Aggiornamenti

Funzione W11-T04. Controlli e download dalle **GitHub Releases** di
`rosariomoscato/walktape`. Nessun aggiornamento forzato, nessuna installazione automatica:
Android chiede sempre conferma.

## Prima release pubblica

**[v0.1.0](https://github.com/rosariomoscato/walktape/releases/tag/v0.1.0)**,
`versionCode2`, asset `walktape-universal-0.1.0.apk` e `SHA256SUMS.txt`.
La firma è la stessa delle build di sviluppo del progetto.

Chi ha già una build chiamata0.1.0 vedrà **versione aggiornata**, non un'offerta di
aggiornamento: il confronto usa versionName, non il tipo debug/release. Per passare al
primo APK pubblico, scaricarlo e installarlo manualmente senza disinstallare.
Le prossime release dovranno avere versionName e versionCode maggiori (code>2).
La prima pubblicazione non dimostra da sola il percorso completo di aggiornamento
verso una futura versione: download/installer interattivi restano da collaudare.

## Percorso

Impostazioni → **Aggiornamenti app**:

1. **Controlla aggiornamenti**: interroga `api.github.com` con un client dedicato (senza
   credenziali né token). HTTP404 è gestito come "Nessuna release pubblicata"; con
   v0.1.0 pubblicata, l'API latest deve invece restituire la release.
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
- Firma e pipeline sono predisposte in W12: la chiave resta locale e fuori da Git.
  Il digest pubblico del certificato e le istruzioni manutentore sono in [RELEASING.md](RELEASING.md).
