# WalkTape — firma e pubblicazione APK

## Artefatto v0.1.0

| Campo | Valore |
| --- | --- |
| Tag | `v0.1.0` |
| Commit sorgente | `afdb6a57e381c574b2df94e4d17446b90dc2dd39` |
| Package | `com.walktape.app` |
| Versione / code | `0.1.0` / `2` |
| Tipo | release, non debuggable, minify disabilitato |
| Android minimo / target | API26 / API36 |
| ABI incluse | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` |
| Asset | `walktape-universal-0.1.0.apk` |
| SHA256 APK | `c1ccacb4632d39da11c4bd0344cbe092a90f1eca7d6badc253ca04be72668957` |
| Certificato SHA256 | `60e9a71f990819c44b2a07a6fc9254925a0d87414c36495f85feb9ffde7b178c` |

L'APK include splash, tasti ridistribuiti e gestione bassi per uscita D41. Il precedente
candidato locale con hash `139A391A…` non è l'artefatto di questa pubblicazione.
Il codice applicativo è quello del commit sorgente; gli aggiornamenti documentali della
pubblicazione sono successivi su `main`.

## Verifiche della distribuzione

Release normale latest pubblicata il2026-10-17T19:31:05Z; API latest senza autenticazione
HTTP200 e tagv0.1.0, due soli asset APK+checksum. APK riscaricato pubblicamente:
HTTP200,19.914.927byte e SHA256 identico alla tabella. Checksum pubblico coerente;
firma/certificato verificati anche sul download precedente alla pubblicazione.
Passaggio debugcode2→releasecode2 installato sull'AOSP con marker dati conservato e
digest base.apk identico. Il dumpUI non è attestato come smoke completamente verde
per ANR di sistema dell'AVD: fallimenti e cleanup registrati nel WORKLOG.

## Chiave e configurazione locale

Per scelta dell'utente si riusa la chiave delle build debug iniziali come firma stabile,
così gli aggiornamenti in-place conservano i dati. Il certificato mostra `CN=Android Debug`;
la verifica d'identità usa il digest riportato sopra, non il nome del certificato.

`app/build.gradle.kts` legge facoltativamente **`local/signing.env`**, escluso da Git:

- `WALKTAPE_KEYSTORE`: percorso della keystore locale;
- `WALKTAPE_KEYSTORE_PASSWORD`, `WALKTAPE_KEY_ALIAS`, `WALKTAPE_KEY_PASSWORD`.

Keystore e password non vanno in documenti, log o repository. Conservare un backup
privato esterno al repository della keystore e della configurazione: perdere la chiave
impedisce gli aggiornamenti con la stessa identità. Una copia del solo APK non è un
backup della chiave. Le build eseguite altrove con una nuova chiave debug non sono
automaticamente compatibili con gli APK ufficiali.

## Build verificata

Toolchain e versioni fissate sono in [ARCHITECTURE.md](ARCHITECTURE.md).
Da PowerShell nella radice del progetto, con la configurazione locale pronta:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleRelease :app:lintRelease "-Dkotlin.compiler.execution.strategy=in-process" --max-workers=2 --console=plain

$apk = "app\build\outputs\apk\release\app-release.apk"
Get-FileHash $apk -Algorithm SHA256
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --verbose --print-certs $apk
```

Controllare package/versione, firma e variante release prima di distribuire; senza
configurazione firma non considerare pubblicabile un APK unsigned. Registrare il
commit sorgente e l'hash dell'artefatto effettivo, senza confonderli con build precedenti.

## Pubblicazione del manutentore

Commit, push, tag, release e upload richiedono richiesta esplicita dell'utente.
Procedura della prima versione:

1. Congelare il commit sorgente, eseguire build e verifiche e preparare l'APK universal.
2. Preparare `SHA256SUMS.txt` (hash SHA256 e nome file) e aggiornare le note di rilascio.
3. Creare/pushare il tag sul commit sorgente; caricare APK e checksum in una release draft.
4. Riscaricare l'asset e confrontarne il digest, quindi pubblicare come release normale
   latest. L'API `/releases/latest` esclude draft e prerelease: usarle non rende
   automaticamente disponibile la release al controllo in-app.
5. Verificare URL pubblico, metadati e download; registrare gli esiti e fare commit/push
   della documentazione aggiornata.

Caricare **un solo APK universal** per release: più APK ambigui vengono rifiutati
dall'update checker. Il checksum `.txt` non interferisce con la selezione APK.
Non sostituire silenziosamente un APK già distribuito sotto lo stesso tag: per correggere
il pacchetto creare una nuova versione.

## Versioni successive

Incrementare **sia versionName sia versionCode** oltre0.1.0/code2. Le build di sviluppo
già chiamate0.1.0 non ricevono un'offerta in-app per la prima release con lo stesso nome:
installazione manuale in-place, come descritto in [UPDATES.md](UPDATES.md).
Un nuovo nome versione con code non crescente verrebbe comunque rifiutato dal verifier.

La pubblicazione non chiude i collaudi Auto/hardware residui: [note di rilascio](RELEASE_NOTES.md).
