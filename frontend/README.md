# Interfaccia MasterBet

Angular 22.2.3, standalone, signals e Bootstrap 5.3.8/SCSS. Sei pagine lazy: `/campagne`, `/scommesse`, `/andamento`, `/campionati`, `/modello`, `/admin`. Orari nell'ora locale, API in UTC.

```powershell
npm ci
npm start
npm run build
npm test -- --watch=false
```

Frontend `127.0.0.1:4200`, proxy `/api/**` verso backend 8080. `dev-local.bat` apre le due console documentate in [setup.md](../setup.md). Nessuna modalità mock applicativa.

`lab-api` contiene HTTP/DTO; `lab-state` condivide campagna, tempo, campionato e modalità, annullando richieste superate. Selettore con nomi, strategia e stati; scelta conservata in localStorage quando disponibile. Cambiare campagna mantiene l'istante simulato, limitato dal backend al suo periodo. `lab-shell` gestisce filtri/navigazione; le sei pagine separano i contenuti. I componenti precedenti sono conservati per regressione, non instradati nell'app corrente.

`/campagne` legge `GET /api/lab/campaigns?competition=0&cursor=...`: saldo, rendimento, puntato, discesa dal picco e qualità delle probabilità per entrambe le modalità coppe. Per default mostra esplicitamente i risultati finali; togliendo la checkbox confronta allo stesso istante simulato. Campagne incomplete visibili con stato ma non selezionabili come concluse. `/modello` espone anche decisioni sui gruppi di statistiche, verifica della calibrazione, categorie controllate e candidati scartati.

Gli step con `learning.markets` mostrano verifiche specifiche di mercato/periodo/esito e fascia di probabilità, con supporti per incontri distinti, date separate di training e verifica, condizioni limitate al mercato e controlli diagnostici delle tre politiche sulle sole giocate già concluse. I confronti con/senza alternative usano lo stesso modello, non creano campagne. I vecchi step senza questo campo mantengono la vista precedente. Contratti GET invariati con campo opzionale aggiuntivo e stub aggiornati.

Le proposte mostrano esplicitamente anche linea e periodo della quota, distinguendo per esempio Under 1,5 del primo tempo da quello della partita intera. Campi già presenti nelle risposte reali, descritti nel DTO frontend senza introdurre nuove API.

Gli step `conditional-markets-v2` espongono anche `learning.markets.replacements`: supporti, settimane, ROI dei due blocchi e limiti inferiori del rendimento e del confronto abbinato alla prima scelta. Le alternative non promosse restano nei controlli di apprendimento, non nel saldo. Le campagne precedenti senza il campo restano leggibili.

L'admin crea esperimenti con `POST /api/lab/admin/experiments`, corpo `{referenceRunId,name,strategy}`; strategie `CALIBRATED`, `SIGNALS`, `FILTERED`, `ADAPTIVE`. Periodo, campionati e utile ereditati dal riferimento originale; nessun download. Gli esperimenti non sostituiscono le campagne precedenti. Sessione e intestazione anti-CSRF richieste come per gli altri comandi.

Un ricalcolo esplicitamente autorizzato nello stesso ID è disponibile da CLI (`--lab-action=recalculate --lab-run=...`, istruzioni in `setup.md`), solo per esperimenti storici completi. La versione precedente resta visibile fino alla pubblicazione atomica; nessuna campagna aggiuntiva. Dopo il completamento premere «Aggiorna archivio» per leggere i nuovi dati nello stesso elemento del selettore. La campagna originale non può essere sostituita da questo comando.

Data e checkbox leggono `GET /api/lab/workspace`, senza download/addestramento. Proposte future in attesa anche dopo spoiler. Grafico del netto con punto iniziale zero e un punto per singola liquidabile, valori negativi, tooltip e pulsanti da tastiera. Paginazione a 50 proposte, nessun limite al totale.

Il pulsante «Fine» accanto alla data usa l'istante esatto dell'ultimo esito, incluse le frazioni di secondo che il campo visualizzato al minuto non rappresenta. Disponibile solo nello storico, senza ricalcoli; evita di omettere l'ultima giocata scegliendo manualmente il minuto finale.

Admin con sessione e `X-MasterBet-Admin: 1` per scritture; prima password scelta localmente dall'utente, almeno 12 caratteri. Endpoint distinti per risultati, quote e calcolo, job persistenti e polling ogni cinque secondi solo dopo accesso. Nessuna chiamata diretta dal browser alle fonti né chiave nel frontend.

Il pulsante «Avvia campagna automatica» usa `POST /api/lab/admin/campaign` con `{ download, unitProfit }`: risultati → abbinamenti → quote → abbinamenti finali → controlli → calcolo. I job delle fasi sono visibili nell'elenco; durante la campagna i comandi incompatibili sono disabilitati. La ripetizione della stessa configurazione riusa archivi e checkpoint; gli errori tecnici fermano le fasi successive. Restano disponibili i download indipendenti.

Stub HTTP di test allineati a `/api/lab`; navigazione, checkbox, annullamento richieste, spoiler, grafico e admin coperti. Nessun `build:mock`. `dist/`, cache e dati privati ignorati da Git.
