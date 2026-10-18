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
| `SERVER_PORT` | Opzionale, default 8081 |
| `MASTERBET_DATA_DIR` | Opzionale, default `../.local/data` dalla cartella backend |

Database previsto: `masterbet_local`; ruolo: `masterbet_app`; host: `127.0.0.1:5432`. La password non è documentata nel repository. `environment.md` è locale, ignorato e non contiene segreti.

## PostgreSQL

Su questa workstation database e ruolo sono già stati creati e verificati. Non ripetere il bootstrap. Il ruolo non è superuser, non può creare database o ruoli ed è limitato da regole HBA specifiche all'accesso a MasterBet da localhost. Le regole degli altri utenti sono preservate.

Per una nuova installazione, un amministratore crea database e ruolo dedicati. `scripts/bootstrap-postgres.ps1` conserva la procedura speciale usata, con accesso amministrativo temporaneo su localhost, ripristino immediato dell'autenticazione originale, backup locale e controlli contro risorse preesistenti. Richiede autorizzazione esplicita e il parametro `-ApproveTemporaryLocalAdminAccess`; non è una procedura ordinaria di avvio né un reset password. Non riavvia il servizio.

Flyway applica `backend/src/main/resources/db/migration/`. Schema `public`, tabelle per partite, risultati, modelli e snapshot; vincoli sui gol HT/FT. Gli istanti sono stringhe ISO-8601 UTC e i payload JSON conservano il dettaglio normalizzato. I test usano esclusivamente H2 in memoria e non le credenziali locali. Nessuna estensione PostgreSQL richiesta.

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

Il comando scarica solo le risposte assenti in cache, verifica il dataset, addestra i due modelli (campionato e calendario esteso), calcola le probabilità e pubblica in un'unica transazione. Esce con `MASTERBET_IMPORT_OK`. Non è una chiamata avviabile dall'interfaccia web. La consultazione non chiama OddsPapi.

Prima importazione da zero: circa 151 richieste fatturate, più 8 richieste storiche attualmente non conteggiate dal piano, secondo le regole del provider verificate. I dati mancanti o anomali possono richiedere indagini aggiuntive. Il client verifica il piano gratuito e conserva almeno 10 richieste; non tenta nuovi account o piani a pagamento. Alla verifica locale del 18 ottobre il contatore era 239/250. La quota può cambiare se la chiave viene usata altrove.

Le risposte originali sono in `.local/data/raw`, con richiesta priva di chiave, istante di acquisizione e SHA-256. Non cancellare questa cartella per riprovare: gli errori si analizzano sui dati originali. I controlli impediscono la pubblicazione di un dataset incompleto; uno snapshot precedente rimane consultabile.

Ripetizione verificata senza accesso API, con chiave deliberatamente vuota:

```powershell
java -jar target/masterbet-backend-0.1.0.jar --import-scenario --server.port=0 --masterbet.odds-api-key=
```

Lo stesso archivio produce lo stesso hash dei dati. Modelli e snapshot sono salvati nel database. Una nuova importazione sostituisce lo scenario con lo stesso ID dopo i controlli, senza creare una seconda simulazione.

## Avvio locale

Dalla radice, dopo build backend e installazione frontend:

```powershell
.\scripts\start-local.ps1 -JavaHome $env:JAVA_HOME
```

Su questo PC si può aggiungere `-UseWindowsTrustStore`. Il comando avvia processi nascosti, rifiuta porte occupate e registra PID, percorso e data di avvio in `.local`. Non esegue importazioni.

- Interfaccia: http://127.0.0.1:4201
- Salute HTTP: http://127.0.0.1:8081/api/health
- Scenario: `GET /api/scenario`
- Quote: `GET /api/matches/{fixtureId}/quotes`

Angular inoltra `/api/**` al backend tramite `proxy.conf.json`. Per avvio in terminali separati: `java -jar target/masterbet-backend-0.1.0.jar` da `backend`, `npm start` da `frontend`. Entrambi ascoltano solo localhost. I servizi preesistenti sulle porte 8080 e 4200 non vengono toccati. Non è predisposta un'esposizione pubblica o un'autenticazione multiutente.

Arresto dei soli processi avviati dal launcher:

```powershell
.\scripts\stop-local.ps1
```

Prima dell'arresto vengono verificati eseguibile, comando e data di avvio, per evitare di terminare un PID riutilizzato. Log separati `.local/backend.out.log`, `.local/backend.err.log`, `.local/frontend.out.log`, `.local/frontend.err.log`. In caso di errore di avvio controllare questi file.

## Audit indipendente

Dalla radice, con backend avviato e Python 3 disponibile (solo libreria standard):

```powershell
.\scripts\export-snapshot.ps1 -PostgresHome 'C:\Program Files\PostgreSQL\18'
python -B scripts/audit-data.py
```

L'export è consentito soltanto per il database locale documentato. L'audit verifica hash delle risposte, punteggi, periodi, prezzi e timestamp, esclusione di dati futuri, esiti mancanti/sospesi, statistiche ricostruite e corrispondenza delle API. Se è presente `.local/audit/nff-results.json`, verifica anche i 47 referti ufficiali acquisiti durante la validazione iniziale; altrimenti dichiara quel riscontro `NOT_RUN`. Il rapporto resta locale in `.local/audit/report.json`.

Le verifiche obbligatorie prima della pubblicazione sono `mvnw test`, `npm run build`, `npm test -- --watch=false`, più l'audit reale quando cambiano acquisizione o calcoli dei dati. Nessuna modalità mock applicativa è presente: gli stub sono limitati ai test e il contratto GET reale è condiviso con i modelli TypeScript.
