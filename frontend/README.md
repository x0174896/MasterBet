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

La pagina legge `GET /api/betting` e `GET /api/advance-job`; il pulsante di avanzamento invia una sola `POST /api/advance-job`, con `config: {unitValue, initialBankrollUnits}`. Una unità rappresenta l'utile netto obiettivo per ogni schedina vincente; le puntate sono calcolate dal backend come unità / (quota − 1), arrotondate ai centesimi. Obiettivo, puntata, ritorno lordo e netto effettivo sono distinti; il campo limite per step è rimosso. I parametri valgono dal prossimo avanzamento, mentre il capitale disponibile continua a limitare le proposte.

Le proposte sono persistite automaticamente dal job; `POST /api/betting/save` salva la prima anteprima e non riscrive proposte già presenti. L'occhio invia `POST /api/betting/{scenarioId}/{variant}/{slipId}/reveal`, con `variant=cups|league`, e mostra gli importi restituiti dal backend, inclusi quelli ricalcolati. Risultati consultati e bilancio al taglio sono distinti; nessun download viene avviato solo aprendo la pagina. Gli stub dei test condividono i modelli TypeScript del contratto reale aggiornato; non ci sono scenari mock applicativi da migrare.

La guida completa è in [setup.md](../setup.md). Il contenuto di `dist/` è generato e ignorato. Non inserire la chiave OddsPapi nel frontend.
