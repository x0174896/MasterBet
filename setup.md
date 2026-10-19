# Predisposizione, verifiche e avvio

Aggiornato su Windows il 19 ottobre 2026 per il laboratorio multicampionato. Eseguire i comandi dalla cartella indicata. Usare `develop` per lo sviluppo e `main` per le versioni integrate.

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

Flyway applica `backend/src/main/resources/db/migration/`. Schema `public`, tabelle per partite, risultati, modelli e snapshot; vincoli sui gol HT/FT. V2 aggiunge schedine immutabili e stato del job; V3 una revisione separata della proposta, blocco dopo consultazione ed esiti indipendenti; V4 gli archivi `betting_round_revision` e `betting_reveal_revision` per gli importi precedenti al ricalcolo monetario. Non modificare migrazioni già applicate. Gli istanti sono stringhe ISO-8601 UTC e i payload JSON conservano il dettaglio normalizzato. I test usano esclusivamente H2 in memoria e non le credenziali locali. Nessuna estensione PostgreSQL richiesta.

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

Se Vitest segnala `ENOENT` su file temporanei nella sandbox Windows, usare una cartella temporanea del progetto nella sola sessione corrente. Dalla cartella `frontend`:

```powershell
New-Item -ItemType Directory -Path ../.local/tmp -Force | Out-Null
$env:TEMP=(Resolve-Path ../.local/tmp).Path
$env:TMP=$env:TEMP
npm test -- --watch=false
```

La cartella resta ignorata da Git; nessuna impostazione globale viene modificata. I test backend con Mockito richiedono un ambiente che consenta l'aggancio locale dell'agent alla JVM; una sandbox che lo blocca non costituisce un esito dei test.

Budget iniziale frontend: avviso 550 kB, limite 650 kB, senza Bootstrap JavaScript. Il bundle di produzione include la localizzazione italiana; la pagina di analisi è caricata separatamente.

In questa workstation i certificati di rete sono nello store Windows. Quando necessario, nella sola sessione corrente:

```powershell
$env:NODE_OPTIONS='--use-system-ca'
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
```

La verifica TLS resta attiva. Per l'importazione Java usare gli stessi due parametri `-D` prima di `-jar` se la rete richiede lo store Windows. Non impostare `NODE_TLS_REJECT_UNAUTHORIZED=0`.

## Laboratorio multicampionato corrente

Flyway V5–V10 aggiunge archivio competizioni/partite/quote/revisioni, job/checkpoint, lock di avvio, elaborazioni/step/proposte e spoiler. V10 aggiunge `lab_campaign_stage`, `lab_fixture_alignment` e proprietà/errori tecnici dei job. V1–V4 e i dati sorgenti precedenti restano conservati. Nessuna dipendenza applicativa aggiunta. Il formatter Google Java Format 1.37.0 è stato usato solo come strumento temporaneo locale, non è necessario per avvio/build.

Aprire `/admin` nel frontend: prima password locale scelta dall'utente (almeno 12 caratteri), poi sessione HttpOnly/SameSite Strict di otto ore. Salt e hash PBKDF2-HMAC-SHA256 (310.000 iterazioni) in `.local/data/private/admin-credential.json`, mai password in chiaro o Git. Sessioni in memoria: dopo riavvio si accede di nuovo. Nessun account o chiave nuova presso fornitori.

Ordine ordinario: catalogo comune; risultati e quote indipendenti, anche in parallelo; calcolo offline dopo i download. Scegliere campionati, date, massimo tre bookmaker e aggiornamento forzato nell'admin. Risultati della stessa stagione antecedenti a gennaio vengono importati per la preparazione. Quote assenti hanno checkpoint ricontrollabili; l'aggiornamento forzato conserva le versioni precedenti. I calendari vengono ricontrollati per i rinvii, i risultati definitivi riusati. Il calcolo crea una nuova elaborazione e tutte le settimane/modalità; cambiarne la consultazione non ripete download/addestramento.

Ripresa quote: `DOWNLOADED` indica solo risposta grezza acquisita; `IMPORTED` certifica che tutti gli esiti dell'incontro sono salvati. Dopo un'interruzione gli incontri conclusi `IMPORTED` e `NO_DATA` sono riutilizzati, mentre gli altri vengono riprocessati dalla cache senza riscaricare risposte presenti. Nessuna cancellazione della cache per errori di parsing: gli archivi voluminosi sono supportati con limite dell'involucro di 128 milioni di caratteri, hash verificato e limiti originali del payload conservati. Gli aggiornamenti discordanti restano archiviati; se coinvolgono l'ultimo prezzo utilizzabile, l'esito è indisponibile senza ripiego. Nessuna nuova dipendenza o variazione dei contratti HTTP.

Da `backend`, dopo `package`, sono disponibili comandi locali equivalenti. Aggiungere i parametri TLS già documentati quando necessari; `--server.port=0` assegna una porta temporanea senza interferire con l'app aperta.

```powershell
java -jar target/masterbet-backend-0.1.0.jar --lab-action=catalog --server.port=0
java -jar target/masterbet-backend-0.1.0.jar --lab-action=results --lab-from=2026-01-01 --lab-to=2026-10-18 --server.port=0
java -jar target/masterbet-backend-0.1.0.jar --lab-action=quotes --lab-from=2026-01-01 --lab-to=2026-10-18 --server.port=0
java -jar target/masterbet-backend-0.1.0.jar --lab-action=calculate --lab-from=2026-01-01 --lab-to=2026-10-18 --server.port=0
java -jar target/masterbet-backend-0.1.0.jar --lab-action=campaign --lab-from=2026-01-01 --lab-to=2026-10-18 --server.port=0
```

Opzionali: `--lab-competitions=384,82` (ID Calcio, non OddsPapi), `--lab-refresh` per ricaricare le sorgenti. `campaign` esegue in sequenza risultati, riconciliazione, quote, riconciliazione finale, controlli, calcolo; riserva il flusso durante tutte le fasi. Non avviarlo mentre sono attivi altri job. L'admin offre lo stesso comando «Campagna automatica» con periodo, campionati, bookmaker e utile netto scelti. I comandi singoli restano disponibili fuori dalla campagna.

Processi e avanzamento sono persistiti e visibili nell'admin anche da CLI. Le fasi risultati/quote/riconciliazione iniziale completate vengono riutilizzate ripetendo la stessa configurazione senza `--lab-refresh`; una fase incompleta riparte usando cache e checkpoint degli incontri. Riconciliazione finale, controlli e calcolo si ripetono. Errori tecnici bloccano le fasi successive; lacune previste sono avvisi `GAP:`. Campionati senza HT/FT o senza quote abbinate non entrano nel modello. Anche il comando di calcolo singolo verifica le ultime fasi sorgenti e gli import quote incompleti.

MLS: se `schedule-rounds` è vuoto si usa il calendario gratuito senza `roundId`; non è una giornata artificiale. Canadian Soccer League 2026 è risultata vuota anche da questo percorso: resta segnalata ed esclusa. Le identità sono verificate per campionato/stagione; associazioni inferite richiedono due date e due avversari verificati, senza confronti approssimativi dei nomi. Le prove vengono salvate, gli originali delle quote provvisorie archiviati e il trasferimento è transazionale. Non cancellare raw, quote o checkpoint per recuperare un abbinamento.

Dopo crash/riavvio ripetere il comando di campagna: non esiste un servizio di riavvio automatico del PC. Un calcolo interrotto conserva gli step e si ripete come nuova elaborazione. Finché il processo resta attivo, i passaggi fra le fasi avvengono senza intervento dell'utente.

Backup/reset legacy: dalla radice eseguire `scripts/backup-lab.ps1` sul solo DB locale documentato; il dump custom viene verificato con `pg_restore --list` e SHA-256. Poi da backend `java -jar target/masterbet-backend-0.1.0.jar --lab-action=reset-legacy --server.port=0`. Il reset verifica nuovamente manifest/hash, archivia e rimuove solo le righe delle vecchie schedine, senza cancellare sorgenti/snapshot/modelli. Non è un reset completo del laboratorio.

### Contratto API corrente

| Metodo e percorso | Contratto |
|---|---|
| `GET /api/lab/coverage` | Catalogo, risultati, quote riconciliate/da abbinare, squadre con sette precedenti e intervallo coperto |
| `GET /api/lab/runs` | Elaborazioni, stato, configurazione, hash, tempi reali/simulati |
| `GET /api/lab/workspace` | `runId`, `cursor` ISO UTC facoltativo, `cups` boolean, `competition` ID Calcio o 0, `page` da 0; 50 proposte, saldi, sequenza completa del grafico, modello dello step consultabile |
| `GET /api/lab/statistics` | `runId`, `cursor`, `cups`, `competition`; statistiche per competizione/stagione con dati entro il taglio |
| `POST /api/lab/reveal` | `{runId,betId}`; spoiler persistito, nessun effetto monetario |
| `GET /api/lab/admin/session` | `{configured,authenticated}` |
| `POST /api/lab/admin/login` | `{password,setup}`; `setup:true` solo al primo accesso locale |
| `POST /api/lab/admin/logout` | Revoca la sessione corrente |
| `GET /api/lab/admin/jobs` | Richiede sessione; stati/contatori/messaggi/errori dei processi |
| `POST /api/lab/admin/catalog?refresh=false` | Job del catalogo |
| `POST /api/lab/admin/results` / `quotes` | `{competitionIds:[],from,to,bookmakers:[...],refresh:false}`; vuoto=tutti i campionati comuni pertinenti; date da gennaio 2026, download fino a oggi+14 giorni |
| `POST /api/lab/admin/calculate` | `{from,to,competitionIds:[],unitProfit:5,mode:"HISTORICAL"}` oppure `OPERATIONAL`; storico fino a oggi, operativo dal tempo reale per sette giorni |
| `POST /api/lab/admin/campaign` | `{download:{from,to,competitionIds:[],bookmakers:[...],refresh:false},unitProfit:5}`; campagna storica sequenziale, risposta job `campaign`; fasi `results`, `reconcile`, `quotes`, `reconcile`, `validate`, `calculate` |
| `POST /api/lab/admin/reset-legacy` | Richiede backup verificato, restituisce numero righe archiviate |

Tutte le scritture `/admin` richiedono `X-MasterBet-Admin: 1`. Serve anche la sessione, tranne login e logout; logout revoca quella presente. Origine browser locale e corrispondente all'Host, nessuna policy CORS aperta. Errori ProblemDetail: 400 richiesta, 401 accesso, 403 origine/intestazione, 404 risorsa, 409 processi incompatibili, 429 tentativi accesso; errori interni senza dettagli privati. Nessuna chiave API restituita al browser.

Stati job: `RUNNING`, `SUCCEEDED`, `PARTIAL`, `FAILED`, `INTERRUPTED`. La copertura parziale non viene nascosta. Nessuna modalità mock o comando `build:mock`: stub solo nei test aggiornati. I vecchi endpoint scenario/betting/advance rispondono 410 per default (`masterbet.legacy-api-enabled=false` implicito); il profilo test li abilita per regressione. Non attivare quel flag nell'uso ordinario.

`Calcio` raw in `.local/data/calcio`, `OddsPapi` in `.local/data/raw`; revisioni immutabili e hash verificati. Primo fischio finale sconosciuto: disponibilità prudenziale kickoff +24 ore, esposta nelle proposte. Metodo, soglie, deroga quote e limiti in [docs/HISTORICAL_LAB_METHOD.md](docs/HISTORICAL_LAB_METHOD.md).

## Esperimento norvegese precedente — comandi di archivio

Le sezioni seguenti documentano l'implementazione precedente conservata. Non usare il suo import/job per la campagna multicampionato attuale; date, limiti cinque e contratti sono storici.

Da `backend`, dopo `package`, con il database disponibile:

```powershell
java -jar target/masterbet-backend-0.1.0.jar --import-scenario --server.port=0
```

Il comando importa lo scenario iniziale al 29 aprile: scarica solo le risposte assenti in cache, verifica il dataset, addestra i due modelli (campionato e calendario esteso), calcola le probabilità e pubblica in un'unica transazione. Esce con `MASTERBET_IMPORT_OK`. Usare il job web descritto sotto per avanzare, non ripetere l'importazione iniziale per creare nuovi step. La semplice apertura della pagina non chiama OddsPapi.

Prima importazione da zero: circa 151 richieste fatturate, più 8 richieste storiche attualmente non conteggiate dal piano, secondo le regole del provider verificate. I dati mancanti o anomali possono richiedere indagini aggiuntive. Il client verifica il piano gratuito e conserva una riserva configurata di 1 richiesta (`masterbet.api-reserve`); non tenta nuovi account o piani a pagamento. La precedente verifica locale dello step 8 era **241/250**; dopo la correzione e l'avanzamento 9 → 10 del 18 ottobre la chiave configurata risulta **1/250**, 249 richieste residue. Il contatore appartiene alla chiave corrente, non alla cache del progetto. Le regole del provider e la quota possono cambiare se la chiave viene usata altrove.

Le risposte originali sono in `.local/data/raw`, con richiesta priva di chiave, istante di acquisizione e SHA-256. Non cancellare questa cartella per riprovare: gli errori si analizzano sui dati originali. I controlli impediscono la pubblicazione di un dataset incompleto; uno snapshot precedente rimane consultabile.

La copertura delle quote è distinta dall'integrità del dataset: non si richiedono famiglie di mercati complete per ogni evento. Famiglie/esiti non pubblicati al taglio o sospesi restano assenti, anche se disponibili più tardi. I target restano nel calendario; le quote valide degli altri mercati o bookmaker rimangono consultabili. Il job registra gli esiti disponibili per target e calcola le schedine solo sulle quote Unibet idonee presenti. Una partita senza tali quote viene esclusa dalle schedine; una copertura insufficiente può produrre meno di cinque proposte, fino a zero, senza impedire l'aggiornamento di risultati e modello. Prezzi invalidi, duplicati, quote future o estranee ai target continuano a essere rifiutati. Non cancellare la cache né cambiare chiave per un mercato assente.

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

L'export è consentito soltanto per il database locale documentato e comprende snapshot storici, proposte attive, consultazioni degli esiti e archivi delle revisioni monetarie (schema V4 necessario). L'audit dati verifica hash, punteggi, periodi, prezzi/timestamp, esclusione del futuro dai dati del modello, esiti mancanti/sospesi, statistiche e API. I nuovi risultati sono confrontati con i referti in `.local/data/nff`. Se è presente `.local/audit/nff-results.json`, verifica anche i 47 referti della validazione iniziale; altrimenti dichiara quel riscontro `NOT_RUN`. L'audit schedine verifica la politica `net-profit-unit-v3`: probabilità dai pesi, importi, capitali di apertura, bilanci, ROI e drawdown delle due varianti; confronta inoltre gli esiti con NFF e le selezioni con gli originali archiviati. Rapporti in `.local/audit/report.json` e `betting-report.json`.

Le verifiche obbligatorie prima della pubblicazione sono `mvnw test`, `npm run build`, `npm test -- --watch=false`, più gli audit reali quando cambiano acquisizione o calcoli. Nessuna modalità mock applicativa è presente: gli stub sono limitati ai test e i contratti GET/POST reali sono condivisi con i modelli TypeScript.

## Avanzamento e schedine salvate

Aprire «Schedine e avanzamento», scegliere l'utile netto obiettivo per ogni schedina (valore dell'unità) per il prossimo step, poi premere «Avanza uno step e calcola le schedine». Non c'è un limite di spesa per step; resta il capitale disponibile. Il capitale iniziale non si modifica dopo il primo salvataggio. Le impostazioni non riscrivono le schedine correnti.

I riquadri delle proposte mostrano cinque indicatori: puntata virtuale, quota reale, probabilità stimata, ritorno lordo e Delta (EV). Il delta è il valore atteso già calcolato dal backend (`probabilità × quota − 1`), in percentuale con segno positivo esplicito. Nelle doppie la quota aggregata è «Quota totale simulata», con quote reali visibili sulle singole selezioni. Partite, esiti, linee, periodi, kickoff e occhio restano disponibili; utile obiettivo, utile netto, motivazione e aggiornamento della quota non sono ripetuti nei riquadri. La semplificazione non cambia API, selezione, calcoli, dati salvati o bilancio e non richiede un avanzamento reale.

Contratto di avvio: `POST /api/advance-job`, risposta 202 con stato del job. Corpo JSON:

```json
{"expectedScenarioId":"eliteserien-2026-round8","config":{"unitValue":5,"initialBankrollUnits":100}}
```

Questo esempio avanza **oltre** lo step 8: non eseguirlo per una semplice verifica di salute. Polling `GET /api/advance-job` ogni 1,5 secondi; un ID scenario non corrente restituisce 409. Lo stato è persistente; i job con proprietario terminato sono marcati falliti alla lettura e possono essere ripetuti. La cache e le schedine già congelate sono conservate in caso di errore, mentre la pubblicazione del nuovo snapshot e delle nuove schedine è transazionale. Non correggere manualmente lo stato di un job con processo ancora attivo.

Durante l'avvio della richiesta e lo stato `RUNNING`, l'interfaccia mostra una barra animata al centro dello schermo, con la fase comunicata dal job. È indeterminata perché il contratto non fornisce una percentuale. Si riattiva anche aprendo la pagina mentre il job è in corso; scompare alla fine o se la connessione si interrompe, lasciando visibile il messaggio di stato/errore. I test HTTP isolati verificano questi casi senza far avanzare lo scenario reale né consumare chiamate API.

La tabella «Bilancio virtuale» mostra solo schedine vinte/perse già concluse al taglio, escludendo puntate in attesa e proposte future. La select «Filtra schedine per step» e «Azzera filtro» operano localmente sui dati già ricevuti da `GET /api/betting`, per il modello selezionato. Il piè di tabella mostra puntato, ritorno e netto dei soli record visualizzati: senza filtro somma tutte le righe mostrate, con filtro solo quelle dello step scelto. La somma usa centesimi interi; netto = ritorno − puntato, anche negativo. In assenza di righe visibili i tre totali sono zero. Se lo step selezionato non è più disponibile dopo l'aggiornamento dei dati o il cambio modello, il filtro torna a «Tutti gli step». Il riepilogo capitale/ROI precedente alla tabella resta globale. Proposte correnti e consultazione degli esiti rimangono nelle rispettive sezioni; nessun contratto API o dato persistito è modificato.

Il pianificatore usa finestre di 7 giorni e salta settimane senza eventi. Prima dei download OddsPapi verifica il costo delle richieste calendario non in cache. Le nuove quote storiche sono scaricate prima dei nuovi risultati; questi ultimi provengono da NFF. I calendari di contesto sono richiesti in intervalli inferiori a 10 giorni, filtrati per le squadre pertinenti. Lo step 7 → 8 ha richiesto due chiamate fatturate (calendario stagione e contesto), sei richieste quote storiche non conteggiate e referti NFF gratuiti. Gli step futuri dipendono dalla copertura e dalla quota residua.

La riconciliazione dei calendari verifica Eliteserien (`tournamentId=20`), stagione 2026 (`seasonId=138092`), squadre casa/trasferta e data locale Europe/Oslo. Una differenza di orario fino a due ore è ammessa soltanto con identità univoca; due candidati restano un errore, anche quando uno ha l'orario esatto. La finestra delle nuove partite segue NFF, poi mantiene l'ID OddsPapi per scaricare le quote. Le copie del nuovo scenario riportano l'orario ufficiale; cache grezza e snapshot salvati non sono riscritti. Gli orari riconciliati sono registrati nel log con entrambi gli ID e timestamp.

I nuovi risultati con orari prudenziali usano NFF +24 ore (`NFF_CONSERVATIVE_24H_BOUND`); una fine reale affidabile resta tale. I target hanno `endedAt=null` e `timingBasis=NFF_SCHEDULE`. Lo storico già presente conserva i propri dati ed è verificato mediante l'identità NFF univoca, senza richiedere un'identica stringa dell'orario del provider. L'occhio sulle schedine legacy non riscrive le selezioni e verifica il referto all'orario ufficiale, aspettando almeno 24 ore dopo entrambi gli orari.

`OddsPapiHttpTransport` gestisce richieste seriali, distanziate di almeno 5,2 secondi per `historical-odds`, 2,2 per `fixtures` e 1,2 per gli altri endpoint, misurati dalla fine della risposta precedente. Su HTTP 429 temporanei riconosciuti rispetta il massimo tra `retryMs` nel JSON e `Retry-After` nell'header; fino a tre tentativi, attesa massima 30 secondi. Una richiesta di attesa maggiore viene segnalata senza ritentare prima del termine imposto dal provider. Errori di rete e 502/503/504 hanno ripetizioni limitate solo su account e quote storiche gratuiti: una richiesta fatturabile dall'esito incerto non viene duplicata automaticamente. `REQUEST_LIMIT_EXCEEDED`, 401 e errori TLS interrompono con un messaggio distinto; nessun controllo dei certificati è disabilitato. La cache già completa viene riutilizzata dopo un'interruzione. Formato dei limiti temporanei e quota descritti nella [documentazione del provider](https://oddspapi.io/blog/javascript-odds-api-nodejs/) e nella [guida alle richieste](https://oddspapi.io/us/docs/requests-and-quota).

Il cambio della chiave in `backend/.env.local` richiede il riavvio del backend. La cache è indipendente dalla chiave e resta disponibile; non cancellarla per cambiare account. Le chiavi non vengono inserite nei log, nei messaggi del job o negli URL restituiti al browser. I contratti HTTP di MasterBet restano invariati e non è introdotta una modalità mock applicativa.

Verifica reale della correzione: avanzamento 9 → 10 completato al 20 maggio 2026 ore 12 UTC, con 71 risultati, 11 target, 2.930 quote e cinque schedine per modello. Backup PostgreSQL e payload precedenti in `.local/audit/before-nff-reconciliation`, esclusi da Git. Entrambi gli audit ordinari sono passati; il controllo aggiuntivo in `.local/audit/nff-reconciliation-report.json` confronta tutti i target con il calendario originale NFF e verifica che snapshot, giornate, esiti e revisioni precedenti siano rimasti invariati. Non eseguire ulteriori avanzamenti per una semplice verifica di salute.

Verifica successiva sulla copertura quote: ripreso e completato lo step 10 → 11 al 27 maggio 2026 ore 12 UTC, 82 risultati, 7 target, 2.260 quote e cinque schedine per modello. L'errore `Famiglia di quote incompleta: id1000002067015210 totals/p2` era causato da un requisito di copertura troppo rigido: Brann–Sarpsborg conserva 174 quote valide di altri bookmaker, mentre le quote Unibet e il totale del secondo tempo non erano pubblicati al taglio. Il nuovo calcolo usa gli altri sei eventi idonei. Prova eseguita con `--masterbet.odds-api-key=` e cache completa, senza nuove richieste OddsPapi; `.env.local` invariato. Backup in `.local/audit/before-partial-odds`, due audit ordinari PASS e confronto preservazione in `.local/audit/partial-odds-report.json`: quattro snapshot/giornate precedenti, 17 esiti e tutte le revisioni invariati. I 101 test backend e i 36 test frontend, insieme alla build frontend, sono passati. Nessuna modifica ai contratti HTTP o ai mock di test; nessuna modalità mock applicativa introdotta.

Il job salva automaticamente fino a cinque schedine per modello. Quattro singole e una doppia richiedono sei partite idonee; con cinque partite vengono proposte cinque singole. Per ciascuna `stake = unitValue / (price - 1)`, usando il prodotto delle quote per la doppia. Puntata arrotondata HALF_UP al centesimo con minimo 0,01 €; ritorno lordo HALF_UP e netto ottenuto sottraendo la puntata. L'utile effettivo può differire dall'obiettivo per i centesimi. `units=1` rappresenta un'unità di utile obiettivo, non di puntata. Una proposta che non rientra nel capitale disponibile viene omessa senza ridimensionarla o reintegrare il capitale. `maxRoundUnits` non è più emesso né usato; viene ignorato durante la lettura degli archivi e delle richieste legacy.

`POST /api/betting/save` con `{"expectedScenarioId":"..."}` salva la prima anteprima. È idempotente; una proposta già presente viene restituita senza riscriverla, anche se legacy. La revisione monetaria esplicita descritta sotto è separata e conserva le stesse selezioni.

L'occhio invia `POST /api/betting/{scenarioId}/{variant}/{slipId}/reveal`, dove `variant` è `cups` o `league`. Prima del download blocca le scelte e verifica l'identità NFF della partita. Conserva risultato, fonte, esito e importi nella sola tabella `betting_reveal`; né training né liquidazione al taglio leggono questa tabella. Il referto viene richiesto solo dopo kickoff +24 ore rispetto al presente reale. Un risultato mancante produce attesa o errore esplicito, mai un punteggio sostitutivo. La liquidazione simulata avviene al successivo avanzamento che ammette il risultato nel passato.

I referti HTML originali sono archiviati in `.local/data/nff`, con hash e data di acquisizione. La cache può contenere risultati successivi al taglio aperti con l'occhio: questo è previsto, ma devono restare esclusi dallo snapshot del modello. La consultazione salvata viene recuperata riaprendo l'occhio dopo un riavvio. La versione corrente del parser è specifica per NFF Eliteserien 2026; se HTML, calendario o identità non coincidono, interrompe l'operazione.

## Ricalcolo monetario delle schedine esistenti

Applicato il 18 ottobre 2026 agli step 7 e 8 con autorizzazione esplicita. Non è un'operazione ordinaria di avvio: il server applica solo la migrazione strutturale V4; il ricalcolo dei dati richiede il flag dedicato. Prima dell'operazione arrestare MasterBet, verificare la destinazione locale e creare un backup PostgreSQL. Da `backend`, dopo build e test:

```powershell
java -jar target/masterbet-backend-0.1.0.jar --reprice-bets --server.port=0 --masterbet.odds-api-key=
```

Il comando non va combinato con `--import-scenario`. Ricalcola tutte le giornate legacy con il valore dell'unità salvato in ciascuna, in ordine cronologico, per entrambe le varianti. Aggiorna solo puntate, proiezioni monetarie, capitali di apertura e importi degli esiti; conserva numero delle schedine, selezioni, quote, probabilità, modello, hash, taglio e date di salvataggio/consultazione. Il salvataggio dei referti riallinea gli importi alla proposta attiva anche quando un download era iniziato prima del ricalcolo.

La transazione archivia i payload originali attivi nelle due tabelle V4 e mantiene immutabile `betting_round`; aggiorna `betting_proposal` e `betting_reveal`. Job in corso, snapshot incompatibile o capitale insufficiente fanno fallire l'intera operazione. Rifiuta anche uno storico che mescola la politica nuova con quella legacy, per non lasciare capitali di apertura incoerenti: applicare la revisione prima di creare nuovi step con il codice aggiornato. Ripetere il comando su uno storico già migrato è idempotente (`MASTERBET_REPRICE_OK rounds=0`). Non scarica dati, non addestra modelli, non avanza lo step e non legge gli esiti dell'occhio per calcolare il capitale al taglio.

Verifica locale: `MASTERBET_REPRICE_OK rounds=2`; originali di due giornate e sette esiti archiviati. Backup e confronto prima/dopo in `.local/audit/before-profit-unit`, escluso da Git. Dopo il riavvio eseguire export e i due audit. Snapshot completi e stato del job sono rimasti invariati allo step 8. Il confronto retrospettivo degli importi non è una nuova validazione predittiva.
