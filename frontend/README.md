# Interfaccia MasterBet

Angular 22.2.3, componenti standalone, signals, TypeScript strict e Bootstrap 5.3.8 con tema SCSS. Interfaccia in italiano, orari visualizzati in UTC.

```powershell
npm ci
npm start
npm run build
npm test -- --watch=false
```

`npm start` ascolta su `127.0.0.1:4200` e inoltra `/api/**` al backend locale sulla porta 8080. Per avviare entrambi con un doppio clic usare `dev-local.bat` nella radice: apre due console con i log e chiudere la console frontend termina Node e i suoi figli senza fermare il backend. Il backend e lo scenario importato sono necessari. Non c'è una modalità mock applicativa.

`scenario-api` gestisce HTTP; `scenario-state` caricamenti, errori e annullamento delle richieste precedenti; `quote-table`, `statistics-panel` e `model-panel` presentano dati tipizzati. `betting-panel` gestisce schedine, parametri, salvataggio e polling del job unico; `slip-result` mostra l'occhio e gli esiti verificati. Il cambio modello annulla la richiesta di esito precedente. L'ordinamento EV è numerico, prima della paginazione, con valori mancanti in fondo. I mercati non modellati restano consultabili senza probabilità inventate.

`betting-history` presenta il bilancio dei soli record conclusi al taglio (`WON`/`LOST`, importi disponibili), escludendo le puntate in attesa anche dalle opzioni di filtro. Filtro per step, opzione «Tutti gli step» e reset non effettuano richieste HTTP. I tre totali in fondo (puntato, ritorno, netto) sono ricalcolati sui soli record visualizzati del modello selezionato, anche senza filtro, sommando centesimi interi. Il netto può essere negativo; assenza di record significa tre totali a zero. Il riepilogo capitale/ROI resta globale e le proposte correnti sono consultabili sopra il bilancio.

La pagina legge `GET /api/betting` e `GET /api/advance-job`; il pulsante di avanzamento invia una sola `POST /api/advance-job`, con `config: {unitValue, initialBankrollUnits}`. Una unità rappresenta l'utile netto obiettivo per ogni schedina vincente; le puntate sono calcolate dal backend come unità / (quota − 1), arrotondate ai centesimi. Il campo limite per step è rimosso. I parametri valgono dal prossimo avanzamento, mentre il capitale disponibile continua a limitare le proposte.

Ogni riquadro proposta mostra soltanto cinque indicatori: puntata virtuale, quota reale, probabilità stimata, ritorno lordo e Delta (EV). Il delta è `expectedReturn` dell'API, ossia probabilità × quota − 1, formattato in percentuale con `+` per valori positivi. Per le doppie la quota complessiva è «Quota totale simulata» e ciascuna selezione mantiene la propria quota reale. Partite, esiti, linee, periodi e kickoff identificano le giocate; utile obiettivo, utile netto, motivazione e data di aggiornamento della quota sono rimossi dai riquadri. L'occhio e il bilancio conservano le proprie informazioni. Nessun contratto o calcolo backend cambia.

Il job mostra una barra animata centrale già durante la richiesta di avvio e per lo stato `RUNNING`, con la fase restituita dal backend. Nessuna percentuale fittizia: la barra è indeterminata. La pagina riprende l'indicatore per job già attivi e lo chiude a fine job o su errore di connessione. Il salvataggio delle proposte non usa questa barra. Contratti API e stub HTTP esistenti restano compatibili; test dedicati coprono fasi, completamento, fallimento, ripresa e connessione interrotta, senza eseguire un avanzamento reale.

Le proposte sono persistite automaticamente dal job; `POST /api/betting/save` salva la prima anteprima e non riscrive proposte già presenti. L'occhio invia `POST /api/betting/{scenarioId}/{variant}/{slipId}/reveal`, con `variant=cups|league`, e mostra gli importi restituiti dal backend, inclusi quelli ricalcolati. Risultati consultati e bilancio al taglio sono distinti; nessun download viene avviato solo aprendo la pagina. Gli stub dei test condividono i modelli TypeScript del contratto reale aggiornato; non ci sono scenari mock applicativi da migrare.

La guida completa è in [setup.md](../setup.md). Il contenuto di `dist/` è generato e ignorato. Non inserire la chiave OddsPapi nel frontend.
