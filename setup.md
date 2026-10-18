# Predisposizione, verifiche e avvio

Verificato su Windows il 18 ottobre 2026. Eseguire i comandi dalla cartella indicata. Usare `develop` per lo sviluppo e `main` per le versioni integrate.

## Versioni adottate

| Componente | Versione verificata |
|---|---|
| JDK | 25.0.2 |
| Spring Boot | 4.1.1 |
| Maven Wrapper | Maven 3.9.11, wrapper ufficiale only-script |
| PostgreSQL | 18, istanza locale sulla porta 5432 |
| Node.js / npm | 24.22.0 / 11.19.1 |
| Angular | 22.2.3, componenti standalone e caricamento della pagina differito |
| Bootstrap | 5.3.8, SCSS; nessun framework UI concorrente |
| TypeScript | 6.0.2 |
| Test backend | JUnit Jupiter 5.13.4, H2 in memoria, Spring reale |
| Test frontend | Vitest, dipendenze esatte risolte in `package-lock.json` |

Java 25 è la versione di compilazione. Boot 4.1 supporta lo stack adottato. Il pin JUnit 5 rispetta le regole del repository: i test di integrazione avviano e chiudono esplicitamente il contesto Spring, senza l'estensione JUnit 6 di Spring 7. Jackson 2.20.1 è usato esplicitamente per archivio e snapshot; il trasporto HTTP usa il mapper fornito da Boot.

`.mvn/settings.xml` limita Maven a Central HTTPS e usa `.local/m2`. Evita che mirror privati globali di altri progetti influenzino questo progetto; non modifica le configurazioni globali.

## Configurazione privata

Creare `backend/.env.local` dal modello `backend/.env.example`, senza sovrascrivere un file già presente. Il backend lo legge come file properties dalla propria directory di lavoro. Non usare `export`, spazi attorno ai nomi o virgolette dei file shell. Non pubblicarlo.

| Variabile | Significato |
|---|---|
| `DB_URL` | URL JDBC del database locale dedicato |
| `DB_USERNAME` | Ruolo applicativo dedicato |
| `DB_PASSWORD` | Password privata del ruolo |
| `ODDSPAPI_KEY` | Chiave gratuita; necessaria solo per risposte non ancora in cache |
| `SERVER_PORT` | Opzionale per avvio Java diretto, default 8080; il launcher imposta esplicitamente 8080 |
| `MASTERBET_DATA_DIR` | Opzionale, default `../.local/data` dalla cartella backend |

Database previsto: `masterbet_local`; ruolo: `masterbet_app`; host: `127.0.0.1:5432`. La password non è documentata nel repository. `environment.md` è locale, ignorato e non contiene segreti.

## PostgreSQL

Su questa workstation database e ruolo sono già stati creati e verificati. Non ripetere il bootstrap. Il ruolo non è superuser, non può creare database o ruoli ed è limitato da regole HBA specifiche all'accesso a MasterBet da localhost. Le regole degli altri utenti sono preservate.

Per una nuova installazione, un amministratore crea database e ruolo dedicati. `scripts/bootstrap-postgres.ps1` conserva la procedura speciale usata, con accesso amministrativo temporaneo su localhost, ripristino immediato dell'autenticazione originale, backup locale e controlli contro risorse preesistenti. Richiede autorizzazione esplicita e il parametro `-ApproveTemporaryLocalAdminAccess`; non è una procedura ordinaria di avvio né un reset password. Non riavvia il servizio.

Flyway applica `backend/src/main/resources/db/migration/`. Schema `public`, tabelle per partite, risultati, modelli e snapshot; vincoli sui gol HT/FT. V2 aggiunge schedine immutabili e stato del job; V3 una revisione separata della proposta, blocco dopo consultazione ed esiti indipendenti. Non modificare migrazioni già applicate. Gli istanti sono stringhe ISO-8601 UTC e i payload JSON conservano il dettaglio normalizzato. I test usano esclusivamente H2 in memoria e non le credenziali locali. Nessuna estensione PostgreSQL richiesta.

## Installazione, build e test

Impostare `JAVA_HOME` al proprio JDK 25. Nella cartella `backend`:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Su macOS/Linux l'equivalente è `./mvnw test` / `./mvnw package`. I launcher PowerShell qui forniti sono per Windows. Prima di ricreare il JAR su Windows arrestare l'applicazione con `scripts/stop-local.ps1`: il file in esecuzione è bloccato dal sistema.

Nella cartella `frontend`:

```powershell
npm ci
npm run build
npm test -- --watch=false
```

Budget iniziale frontend: avviso 550 kB, limite 650 kB, senza Bootstrap JavaScript. Il bundle di produzione include la localizzazione italiana; la pagina di analisi è caricata separatamente.

In questa workstation i certificati di rete sono nello store Windows. Quando necessario, nella sola sessione corrente:

```powershell
$env:NODE_OPTIONS='--use-system-ca'
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
```

La verifica TLS resta attiva. Per l'importazione Java usare gli stessi due parametri `-D` prima di `-jar` se la rete richiede lo store Windows. Non impostare `NODE_TLS_REJECT_UNAUTHORIZED=0`.

## Importazione storica e addestramento

Da `backend`, dopo `package`, con il database disponibile:

```powershell
java -jar target/masterbet-backend-0.1.0.jar --import-scenario --server.port=0
```

Il comando importa lo scenario iniziale al 29 aprile: scarica solo le risposte assenti in cache, verifica il dataset, addestra i due modelli (campionato e calendario esteso), calcola le probabilità e pubblica in un'unica transazione. Esce con `MASTERBET_IMPORT_OK`. Usare il job web descritto sotto per avanzare, non ripetere l'importazione iniziale per creare nuovi step. La semplice apertura della pagina non chiama OddsPapi.

Prima importazione da zero: circa 151 richieste fatturate, più 8 richieste storiche attualmente non conteggiate dal piano, secondo le regole del provider verificate. I dati mancanti o anomali possono richiedere indagini aggiuntive. Il client verifica il piano gratuito e conserva una riserva configurata di 1 richiesta (`masterbet.api-reserve`); non tenta nuovi account o piani a pagamento. Alla verifica locale del 18 ottobre, dopo l'avanzamento, il contatore è **241/250**. Le regole del provider e la quota possono cambiare se la chiave viene usata altrove.

Le risposte originali sono in `.local/data/raw`, con richiesta priva di chiave, istante di acquisizione e SHA-256. Non cancellare questa cartella per riprovare: gli errori si analizzano sui dati originali. I controlli impediscono la pubblicazione di un dataset incompleto; uno snapshot precedente rimane consultabile.

Ripetizione verificata senza accesso API, con chiave deliberatamente vuota:

```powershell
java -jar target/masterbet-backend-0.1.0.jar --import-scenario --server.port=0 --masterbet.odds-api-key=
```

Lo stesso archivio produce lo stesso hash dei dati. Modelli e snapshot sono salvati nel database. Una nuova importazione sostituisce lo scenario con lo stesso ID dopo i controlli, senza creare una seconda simulazione.

## Avvio locale

Doppio clic su **`dev-local.bat`** nella radice del progetto, oppure da terminale:

```bat
dev-local.bat
```

Il batch funziona anche se chiamato da un'altra cartella. Usa Windows PowerShell senza modificare le policy globali, cerca un JDK 25 tramite `JAVA_HOME` o fra le installazioni in `%ProgramFiles%\Java`, e abilita lo store certificati Windows per il processo Java/Maven. Richiede Node.js disponibile nel PATH, dipendenze frontend già installate con `npm ci`, configurazione privata e PostgreSQL avviato. Non richiede privilegi amministrativi per l'avvio ordinario.

Il launcher controlla prima entrambe le porte e i processi registrati, poi compila il backend con `mvnw package -DskipTests` (log `.local/build-backend.log`). La compilazione di avvio non sostituisce i test obbligatori prima della pubblicazione. Apre due console visibili, con titoli `MasterBet - backend - porta 8080` e `MasterBet - frontend - porta 4200`, e attende la risposta dei servizi fino a 60 secondi per servizio. Una porta occupata produce un errore con il PID, senza terminare il programma che la usa e senza scegliere una porta alternativa.

I log scorrono nelle rispettive console. **Chiudere la console backend arresta solo Java e libera 8080; chiudere quella frontend arresta Node e i suoi processi figli, liberando 4200.** Ogni console appartiene a un Windows Job Object indipendente con `KILL_ON_JOB_CLOSE`, senza handle ereditabili: la chiusura elimina il relativo albero di processi anche se il gestore PowerShell non esegue un blocco `finally`. [Comportamento documentato da Microsoft](https://learn.microsoft.com/windows/win32/procthread/job-objects). Il piccolo helper C# viene compilato in memoria da PowerShell: non serve installare un SDK .NET.

La finestra iniziale del batch si chiude automaticamente dopo un avvio riuscito; restano le due console dei servizi. In caso di errore il batch attende un tasto, salvo `--no-pause`. Le console gia aperte restano visibili per leggere gli errori: un servizio che termina scrive il codice di uscita e attende Invio. Per riprovare chiudere le console rimaste oppure eseguire `scripts/stop-local.ps1`. Non sono applicate chiusure automatiche dell'altro servizio quando una console viene chiusa.

Avvio PowerShell alternativo, con percorso JDK esplicito se necessario:

```powershell
.\scripts\start-local.ps1 -JavaHome $env:JAVA_HOME
```

Su questo PC aggiungere `-UseWindowsTrustStore` se si usa direttamente lo script PowerShell (il batch lo passa già). Il comando registra PID delle console, percorso e data di avvio in `.local`. La telemetria Angular è disabilitata soltanto nel processo frontend per evitare il prompt iniziale. Non esegue importazioni e non modifica impostazioni globali.

- Interfaccia: http://127.0.0.1:4200
- Salute HTTP: http://127.0.0.1:8080/api/health
- Scenario: `GET /api/scenario`
- Quote: `GET /api/matches/{fixtureId}/quotes`
- Schedine e bilanci: `GET /api/betting`
- Stato dell'avanzamento: `GET /api/advance-job`

Angular inoltra `/api/**` al backend sulla porta 8080 tramite `proxy.conf.json`. Per avvio in terminali separati, dopo la build: `java -jar target/masterbet-backend-0.1.0.jar --server.port=8080` da `backend`, `npm start` da `frontend`. Entrambi ascoltano solo localhost. Eventuali altri servizi sulle porte richieste devono essere chiusi dal loro proprietario: il launcher non li arresta. Non è predisposta un'esposizione pubblica o un'autenticazione multiutente.

Arresto dei soli processi avviati dal launcher:

```powershell
.\scripts\stop-local.ps1
```

Per un solo servizio: `scripts/stop-local.ps1 -Component backend` oppure `-Component frontend`. Prima dell'arresto vengono verificati eseguibile, comando e data di avvio, per evitare di terminare un PID riutilizzato. Funziona anche con i manifest della precedente versione senza console visibili.

I log Spring Boot vengono anche salvati in `.local/backend.log`, oltre a essere visibili nella console. Il frontend mostra i log direttamente nella propria console; i vecchi file `backend.out.log`, `backend.err.log`, `frontend.out.log` e `frontend.err.log` non vengono piu aggiornati. Non condividere i log senza verificarne il contenuto.

Test di regressione del ciclo di vita delle console:

```powershell
.\scripts\test-console-lifecycle.ps1
```

Usa due processi PowerShell isolati, ciascuno con figlio e nipote Node temporanei, in `.local/console-tests`. Verifica l'arresto dell'intero albero e l'indipendenza dei due gruppi anche con terminazione brusca del proprietario. Non avvia MasterBet, non apre porte e non accede a database o API. Eseguirlo insieme alle verifiche obbligatorie quando cambiano i launcher.

## Audit indipendente

Dalla radice, con backend avviato e Python 3 disponibile (solo libreria standard):

```powershell
.\scripts\export-snapshot.ps1 -PostgresHome 'C:\Program Files\PostgreSQL\18'
python -B scripts/audit-data.py
python -B scripts/audit-betting.py
```

L'export è consentito soltanto per il database locale documentato e comprende snapshot storici, proposte attive e consultazioni degli esiti. L'audit dati verifica hash, punteggi, periodi, prezzi/timestamp, esclusione del futuro dai dati del modello, esiti mancanti/sospesi, statistiche e API. I nuovi risultati sono confrontati con i referti in `.local/data/nff`. Se è presente `.local/audit/nff-results.json`, verifica anche i 47 referti della validazione iniziale; altrimenti dichiara quel riscontro `NOT_RUN`. L'audit schedine ricalcola probabilità dai pesi e dalle caratteristiche, importi e bilanci delle due varianti e verifica gli esiti consultati contro NFF. Rapporti in `.local/audit/report.json` e `betting-report.json`.

Le verifiche obbligatorie prima della pubblicazione sono `mvnw test`, `npm run build`, `npm test -- --watch=false`, più gli audit reali quando cambiano acquisizione o calcoli. Nessuna modalità mock applicativa è presente: gli stub sono limitati ai test e i contratti GET/POST reali sono condivisi con i modelli TypeScript.

## Avanzamento e schedine salvate

Aprire «Schedine e avanzamento», scegliere il valore dell'unità e il limite per il prossimo step (massimo 2 unità), poi premere «Avanza uno step e calcola le schedine». Il capitale iniziale non si modifica dopo il primo salvataggio. Le impostazioni non riscrivono le schedine correnti.

Contratto di avvio: `POST /api/advance-job`, risposta 202 con stato del job. Corpo JSON:

```json
{"expectedScenarioId":"eliteserien-2026-round8","config":{"unitValue":5,"initialBankrollUnits":100,"maxRoundUnits":2}}
```

Questo esempio avanza **oltre** lo step 8: non eseguirlo per una semplice verifica di salute. Polling `GET /api/advance-job` ogni 1,5 secondi; un ID scenario non corrente restituisce 409. Lo stato è persistente; i job con proprietario terminato sono marcati falliti alla lettura e possono essere ripetuti. La cache e le schedine già congelate sono conservate in caso di errore, mentre la pubblicazione del nuovo snapshot e delle nuove schedine è transazionale. Non correggere manualmente lo stato di un job con processo ancora attivo.

Il pianificatore usa finestre di 7 giorni e salta settimane senza eventi. Prima dei download OddsPapi verifica il costo delle richieste calendario non in cache. Le nuove quote storiche sono scaricate prima dei nuovi risultati; questi ultimi provengono da NFF. I calendari di contesto sono richiesti in intervalli inferiori a 10 giorni, filtrati per le squadre pertinenti. Lo step 7 → 8 ha richiesto due chiamate fatturate (calendario stagione e contesto), sei richieste quote storiche non conteggiate e referti NFF gratuiti. Gli step futuri dipendono dalla copertura e dalla quota residua.

Il job salva automaticamente fino a cinque schedine per modello. Quattro singole da 0,4 u e una doppia da 0,25 u richiedono sei partite idonee; con cinque partite vengono proposte cinque singole. Non vengono aumentati rischio o esposizione per raggiungere un numero prefissato. Puntate arrotondate per difetto ai centesimi; ritorno lordo con arrotondamento HALF_UP e netto ottenuto sottraendo la puntata.

`POST /api/betting/save` con `{"expectedScenarioId":"..."}` salva la prima anteprima o applica la politica a cinque a una proposta legacy del solo scenario corrente. Conserva la proposta originale e rifiuta la revisione dopo la consultazione di esiti. È idempotente; non permette di riscrivere schedine già liquidate né di scegliere retroattivamente quelle vincenti.

L'occhio invia `POST /api/betting/{scenarioId}/{variant}/{slipId}/reveal`, dove `variant` è `cups` o `league`. Prima del download blocca le scelte e verifica l'identità NFF della partita. Conserva risultato, fonte, esito e importi nella sola tabella `betting_reveal`; né training né liquidazione al taglio leggono questa tabella. Il referto viene richiesto solo dopo kickoff +24 ore rispetto al presente reale. Un risultato mancante produce attesa o errore esplicito, mai un punteggio sostitutivo. La liquidazione simulata avviene al successivo avanzamento che ammette il risultato nel passato.

I referti HTML originali sono archiviati in `.local/data/nff`, con hash e data di acquisizione. La cache può contenere risultati successivi al taglio aperti con l'occhio: questo è previsto, ma devono restare esclusi dallo snapshot del modello. La consultazione salvata viene recuperata riaprendo l'occhio dopo un riavvio. La versione corrente del parser è specifica per NFF Eliteserien 2026; se HTML, calendario o identità non coincidono, interrompe l'operazione.
