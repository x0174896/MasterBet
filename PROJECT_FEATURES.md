# Inventario funzionale MasterBet

Ultimo aggiornamento: 18 ottobre 2026 — avanzamento, cinque schedine salvate ed esiti consultabili.

## Funzionalità implementate

Applicazione locale Angular 22 / Bootstrap e Java 25 / Spring Boot 4.1, PostgreSQL 18. Frontend in `frontend`, backend in `backend`. Riprende l'idea del vecchio MasterBet: gol fatti/subiti per tempo e casa/trasferta, probabilità stimate e quote reali. Il modello è nuovo, non una riproduzione letterale del vecchio algoritmo.

### Scenari storici e job di avanzamento

- Scenario iniziale conservato: Eliteserien 2026, taglio 29 aprile ore 12 UTC, 47 incontri conclusi, 8 target della settima giornata, 1.998 quote.
- Avanzamento reale eseguito allo step 8: taglio **6 maggio 2026 ore 12 UTC**, 57 incontri conclusi, 6 target fra 8 e 10 maggio, **1.787 quote**. Due incontri dell'ottava giornata rinviati al 20 maggio restano fuori dalla finestra; nessuna partita o quota inventata.
- Un solo job dall'interfaccia: congela le scelte precedenti, verifica budget API/cache, scarica calendario, quote storiche e nuovi risultati, valida dati, riaddestra, calcola suggerimenti e schedine, pubblica tutto atomicamente. Download partite, risultati e quote restano componenti distinti.
- Finestra settimanale ancorata al taglio; salta settimane vuote senza usare risultati futuri. Gli step sono finestre temporali, non sempre corrispondono ai numeri ufficiali di giornata. Copertura limitata a Eliteserien 2026.
- Stato persistente del job, blocco dei doppi avvii, rifiuto di richieste da uno scenario superato. Dopo interruzione del processo il job orfano viene riconosciuto e si può riprovare riutilizzando la cache. Uno step fallito non sostituisce lo scenario precedente.

### Integrità e fonti

- Quote originali Unibet, Pinnacle e bet365: ultimo aggiornamento attivo e valido non successivo al taglio. Nessuna interpolazione, né recupero di una quota vecchia dopo sospensione. I mercati non modellati restano consultabili senza probabilità inventate.
- Adapter distinti per calendario OddsPapi, risultati OddsPapi dell'importazione iniziale, quote storiche, catalogo mercati, calendario e referti NFF. Gli avanzamenti e l'occhio usano i referti ufficiali NFF per HT/FT senza consumare chiamate OddsPapi.
- Archivio privato delle risposte originali con hash SHA-256, richiesta senza chiave e data di acquisizione. Controlli di identità, kickoff, periodi, gol, timestamp, completezza delle famiglie di mercati e dati futuri.
- Secondo tempo = risultato dei 90 minuti meno primo tempo. Supplementari/rigori distinti. Quando gli orari del provider sono incoerenti, disponibilità prudenziale del risultato a kickoff +24 ore, dichiarata come tale.
- Calendario generale filtrato per le squadre interessate prima di validare le altre proprietà: un evento estraneo malformato non blocca MasterBet; dati errati delle squadre pertinenti restano errori.

### Modello e statistiche

- Classifica, punti, forma, statistiche casa/trasferta e gol HT/SH/FT, con elenco dei risultati al taglio.
- Quattro regressioni di Poisson regolarizzate. Variante campionato e variante con riposo, coppe, incontri e trasferte recenti; amichevoli escluse e competizioni classificate esplicitamente.
- Ogni avanzamento riaddestra sui dati precedenti al nuovo taglio, con separazione cronologica 80/20 e normalizzazione stimata soltanto sul training. Pesi e rapporti persistiti.
- Step 8: 80 partite del 2025 + 57 del 2026, **109 training / 28 validazione**. Brier 1 X 2: base **0,609880**, campionato **0,724720**, coppe **0,711693**. Entrambi i modelli sono peggiori della base in questa validazione; non è dimostrato un vantaggio predittivo.
- Probabilità per 1 X 2, entrambe segnano, totali/squadra con linee senza rimborso, risultati esatti e HT/FT riconosciuti. Linee asiatiche con rimborso o mezze vincite escluse dai suggerimenti.

### Schedine, salvataggio e risultati

- Obiettivo **cinque schedine per step e per variante**: quattro singole da 0,4 unità più una doppia da 0,25, su sei incontri distinti; con cinque soli incontri idonei, cinque singole da 0,4. Meno proposte se quote o capitale non bastano, senza forzare scommesse.
- Selezione Unibet con EV teorico almeno 5%, probabilità stimata della singola almeno 50%, quota singola massimo 5; ordinamento per EV e una sola esposizione per partita. Il valore della quota multipla è un prodotto simulato, non una multipla confermata dal bookmaker.
- Unità configurabile (default 5 €), capitale iniziale (100 unità), limite massimo 2 unità/step, capitale disponibile e somme impegnate. Puntate fisse senza recupero delle perdite o reintegri automatici. Le modifiche ai parametri valgono per il prossimo step.
- Step 8 salvato: cinque schedine per entrambe le varianti, quattro puntate da 2 € e una da 1,25 €, totale 9,25 €. Scelte, quote, modello, hash e taglio conservati nel database e recuperati dopo ricaricamento/riavvio.
- Proposta originaria conservata immutabile. La revisione a cinque dello step 8 è separata; lo step 7 resta con le quattro schedine originali. Non si riscrivono le scelte già valutate o dopo la consultazione degli esiti.
- **Occhio** su ogni schedina corrente e nello storico: scarica/verifica il referto NFF, mostra risultato esatto HT/SH/FT, esito di ogni selezione e della schedina, puntata, ritorno lordo e profitto/perdita. Esito persistito e riutilizzato; dati mancanti non diventano pareggi o perdite.
- L'occhio salva gli esiti in un archivio separato: non modifica snapshot, addestramento, statistiche o bilancio al taglio. La liquidazione simulata avviene con l'avanzamento, quando i risultati entrano nel passato consentito.
- Bilanci distinti campionato/coppe: profitto, ROI, capitale, disponibilità, drawdown misurato a fine step, profitti singole/doppie e Brier binario delle selezioni risolte. Importi decimali; ritorno lordo arrotondato al centesimo prima di sottrarre la puntata.

### Interfaccia, API e ambiente

- Viste schedine, quote, statistiche, modello. Filtri bookmaker/periodo/mercato, paginazione, ordinamento numerico EV crescente/decrescente prima della paginazione, valori mancanti in fondo. Linea, periodo, prezzo e timestamp visibili.
- API di lettura preesistenti mantenute. Aggiunte API schedine, stato job, avvio avanzamento, salvataggio e consultazione esiti; contratto in `setup.md`. Nessuna chiave inviata al browser.
- Nessuna modalità mock applicativa: stub soltanto nei test, aggiornati per i nuovi contratti. `MOCK_API_STUDIO.md` non descrive una funzionalità attiva.
- Migrazioni Flyway V1–V3; database locale dedicato e ruolo non amministrativo. Backup locale prima delle migrazioni operative. Configurazione privata in `backend/.env.local`; segreti, cache, dati e `environment.md` esclusi da Git.
- `dev-local.bat` apre due console indipendenti con log, backend 8080/frontend 4200. Chiudere una console termina soltanto il relativo albero di processi; log backend anche in `.local/backend.log`. Launcher rifiuta porte occupate e non arresta altre applicazioni.

## Verifiche eseguite

- 70 test JUnit 5, inclusi persistenza/API su H2 isolato, selezione, budget, liquidazione per periodo, importi, salvataggi immutabili, isolamento dei risultati, errori dei job e finestre temporali.
- 22 test Angular/Vitest e build di produzione, inclusi ordinamento EV, job, salvataggio, linee/periodi e apertura esiti/errori/cambio modello.
- Avanzamento reale 7 → 8, ripresa da cache dopo un errore su un evento estraneo, addestramento e pubblicazione PostgreSQL.
- Audit indipendente di 171 risposte OddsPapi, 139 punteggi usati, tutte le 1.787 quote e statistiche/API delle 16 squadre. Riscontro NFF dei 47 risultati originari e dei 10 nuovi referti.
- Audit indipendente delle 22 selezioni dei due step e delle due varianti: probabilità ricalcolate da pesi/caratteristiche, prezzi, puntate, ritorni e bilanci. Verificati anche due esiti consultati (una singola vinta e una doppia persa) contro i referti NFF, con hash dello snapshot invariato.
- Browser reale: cinque proposte, consultazione dei punteggi e del netto, riapertura dell'esito salvato, ordinamento EV in entrambe le direzioni.
- Contatore OddsPapi verificato il 18 ottobre: **241/250**, 9 richieste residue. Questo avanzamento ha usato 2 richieste fatturate. Consultazione delle schedine salvate ed esiti NFF non consumano richieste OddsPapi.
- Test delle due console e della loro terminazione indipendente già verificati nello sviluppo del launcher, invariato in questa modifica.

## Limiti

- Nessuna vincita garantita: pochi dati, probabilità non calibrate estesamente, modelli Poisson con ipotesi di indipendenza. Il profitto liquidato dello step 7 (+0,51 €) è un singolo esperimento, non prova di redditività.
- La consultazione dell'occhio rivela il futuro all'utente: eventuali decisioni manuali successive non costituiscono più un test cieco. I calcoli automatici restano separati dal futuro.
- La liquidazione rappresenta le regole matematiche dei mercati supportati sui risultati sul campo nei 90 minuti. Non certifica accettazione della multipla, fiscalità o regole particolari del bookmaker; annullamenti/rimborsi non implementati.
- Il contesto coppe comprende solo incontri conclusi al taglio, non il carico programmato fra taglio e target. Nessun dato su formazioni, infortuni, minuti o distanze di viaggio.
- Quote storiche al taglio, ma calendari e metadati ricevuti oggi: non sono un archivio certificato delle modifiche note allora. Punteggi sul campo distinti da decisioni amministrative successive.
- Nessuna scelta libera di stagione/campionato, aggiornamento live, invio di puntate, autenticazione multiutente o deploy pubblico. Avanzamenti subordinati a quota gratuita, copertura e controlli: nessun nuovo account o piano a pagamento automatico.
- L'archivio locale non è versionato; un clone richiede importazione o trasferimento privato della cache. Fonti, HTML NFF e limiti del piano possono cambiare.

## Registro delle modifiche

- 18 ottobre 2026: job unico di avanzamento, riaddestramento, bilanci, EV ordinabile, cinque schedine persistite ed occhio con referti verificati e archivio separato. Avanzato e verificato lo step 8; originali conservati.
- 18 ottobre 2026: due console indipendenti, log visibili, chiusura degli alberi di processi, arresto selettivo e test isolato del ciclo di vita.
- 18 ottobre 2026: `dev-local.bat`, porte e proxy 8080/4200, audit e istruzioni di avvio.
- 18 ottobre 2026: prima versione storica con database, downloader separati, statistiche, addestramento, interfaccia e controlli di integrità.
