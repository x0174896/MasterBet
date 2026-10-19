# Interfaccia MasterBet

Angular 22.2.3, standalone, signals e Bootstrap 5.3.8/SCSS. Cinque pagine lazy: `/scommesse`, `/andamento`, `/campionati`, `/modello`, `/admin`. Orari nell'ora locale, API in UTC.

```powershell
npm ci
npm start
npm run build
npm test -- --watch=false
```

Frontend `127.0.0.1:4200`, proxy `/api/**` verso backend 8080. `dev-local.bat` apre le due console documentate in [setup.md](../setup.md). Nessuna modalità mock applicativa.

`lab-api` contiene HTTP/DTO; `lab-state` condivide elaborazione, tempo, campionato e modalità, annullando richieste superate. `lab-shell` gestisce filtri/navigazione; le cinque pagine separano i contenuti. I componenti precedenti sono conservati per regressione, non instradati nell'app corrente.

Data e checkbox leggono `GET /api/lab/workspace`, senza download/addestramento. Proposte future in attesa anche dopo spoiler. Grafico del netto con punto iniziale zero e un punto per singola liquidabile, valori negativi, tooltip e pulsanti da tastiera. Paginazione a 50 proposte, nessun limite al totale.

Admin con sessione e `X-MasterBet-Admin: 1` per scritture; prima password scelta localmente dall'utente, almeno 12 caratteri. Endpoint distinti per risultati, quote e calcolo, job persistenti e polling ogni cinque secondi solo dopo accesso. Nessuna chiamata diretta dal browser alle fonti né chiave nel frontend.

Stub HTTP di test allineati a `/api/lab`; navigazione, checkbox, annullamento richieste, spoiler, grafico e admin coperti. Nessun `build:mock`. `dist/`, cache e dati privati ignorati da Git.
