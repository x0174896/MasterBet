# Inventario funzionale MasterBet

Ultimo aggiornamento: 18 ottobre 2026.

## Prima versione implementata

Applicazione locale Angular 22 e Java 25 / Spring Boot 4.1, con PostgreSQL 18. Frontend in `frontend`, backend in `backend`. Riprende l'idea del vecchio MasterBet: usare i gol realizzati e subiti, distinti per tempo e casa/trasferta, per confrontare probabilità stimate e quote reali. Non riproduce alla lettera il vecchio algoritmo e non riutilizza il tutorial PhoneCat come interfaccia.

### Scenario storico reale

- Eliteserien 2026, taglio al 29 aprile 2026 ore 12:00 UTC, dopo la sesta giornata di calendario.
- 47 partite concluse, 16 squadre e 8 eventi della settima giornata, dal 1° al 4 maggio. Le squadre hanno da 5 a 7 partite per effetto di anticipi e rinvii: non si forza artificialmente un calendario uniforme.
- 1.998 quote reali disponibili al taglio, provenienti da Unibet, Pinnacle e bet365. Selezione dell'ultimo aggiornamento non futuro, purché attivo e valido. Nessuna quota sintetica o recupero di prezzi precedenti dopo una sospensione.
- Mercati completi per tutti gli 8 eventi: 1 X 2 finale, entrambe segnano, totali gol finali, primo tempo e secondo tempo, combinazione primo tempo/finale. Altri mercati originali sono consultabili, anche quando non modellati.
- 1.093 esiti con probabilità calcolata; 164 segnalazioni con valore atteso teorico almeno 5% nel dataset verificato. Questi numeri descrivono lo snapshot, non risultati economici osservati.
- Risultati futuri della settima giornata non acquisiti. Nessun avanzamento alla giornata successiva in questa versione.

### Acquisizione e integrità

- Adapter distinti per calendario/partite (`OddsPapiFixtureDownloader`), risultati (`OddsPapiScoreDownloader`), quote (`OddsPapiHistoricalOddsDownloader`) e catalogo mercati. Interfacce separate permettono di aggiungere altri provider senza un'unica classe onnisciente.
- Archivio locale delle risposte originali con SHA-256, richiesta senza chiave e data di acquisizione. Cache riutilizzabile; ripetizione completa verificata con chiave API disabilitata.
- Controllo del piano gratuito e riserva di almeno 10 richieste. Nessun download dalla consultazione web e nessun acquisto automatico.
- Validazione obbligatoria di identità, periodo, punteggi, completezza dei mercati, timestamp e assenza di dati futuri. Pubblicazione atomica dello scenario in PostgreSQL dopo i controlli.
- Gestione esplicita dei tempi incoerenti del provider: conservazione del calcio d'inizio programmato e, quando necessario, limite prudenziale di disponibilità del risultato a +24 ore. Non è presentato come orario effettivo di fine partita.
- Gol del secondo tempo calcolati come finale nei 90 minuti meno primo tempo. Per le coppe non si confondono supplementari, rigori e risultato dei 90 minuti.

### Statistiche e modello

- Classifica e statistiche casa/trasferta, punti, forma, gol fatti e subiti per primo tempo, secondo tempo e finale; elenco delle 47 partite con punteggi verificabili.
- Addestramento su 80 partite del campionato regolare 2025: 64 per apprendimento e 16 successive per validazione cronologica. Pesi, normalizzazione e rapporto salvati nel database.
- Quattro regressioni di Poisson per gol casa/trasferta nei due tempi, regolarizzazione e caratteristiche costruite usando solo dati precedenti alla previsione.
- Confronto fra base statistica, modello con solo campionato e modello con calendario esteso. Brier multicategoria sulla validazione: rispettivamente 0,528751, 0,427087 e 0,442050. Il modello con coppe non supera quello con solo campionato in questo piccolo campione.
- Riposo fra calci d'inizio, frequenza delle partite, trasferte recenti e coppe riconosciute nel calendario. Amichevoli escluse; serie minori distinte dalle coppe. Due risultati di coppa acquisiti separatamente.
- Probabilità per 1 X 2, entrambe segnano, totali gol su linee senza rimborso, totali squadra, risultati esatti e primo tempo/finale, quando riconosciuti. Linee asiatiche con rimborso o mezze vincite e mercati non supportati sono mostrati senza probabilità o suggerimento.

### Interfaccia e API

- Interfaccia italiana Bootstrap responsive, navigazione da tastiera, caricamento/errori/riprova e tre viste: quote, statistiche, modello e coppe.
- Selezione partita; filtri bookmaker, periodo, mercato e segnalazioni; paginazione. Sono visibili linea del mercato, prezzo originale, timestamp UTC, probabilità base/modello e valore atteso teorico.
- Provenienza dei dati e limiti del modello visibili. Date del dataset e modello non vengono presentate come dati correnti.
- API locali in sola lettura: `GET /api/health`, `GET /api/scenario`, `GET /api/matches/{fixtureId}/quotes`. Errori senza dettagli sensibili; nessuna chiave restituita al browser.
- Nessuna modalità mock applicativa; gli stub sono esclusivamente nei test. `MOCK_API_STUDIO.md` resta un documento preesistente, non una funzionalità attiva.

### Ambiente operativo

- Database dedicato `masterbet_local`, ruolo `masterbet_app` senza privilegi amministrativi e accesso HBA limitato al database locale. Migrazioni Flyway versionate.
- Launcher e arresto PowerShell dei soli processi MasterBet, con verifica di percorso, comando e data di creazione del PID. Porte 8081 e 4201.
- Configurazione privata in `backend/.env.local`; dati, log, cache, ambiente locale e segreti esclusi da Git. Nessuna dipendenza dai servizi di altre applicazioni.
- Script di export e audit indipendente dei dati. Comandi e prerequisiti in `setup.md`.

## Verifiche eseguite

- 46 test JUnit 5, inclusi dominio, acquisizione, esclusione di dati futuri, persistenza transazionale e API Spring con H2 isolato.
- 13 test Angular/Vitest e build di produzione.
- Audit di 163 risposte originali, 129 punteggi usati e tutte le 1.998 quote, statistiche e risposte API degli 8 eventi.
- Riscontro esterno di tutti i 47 primi tempi, finali e orari del campionato 2026 contro i referti ufficiali NFF. Le 80 partite di addestramento sono confrontate con il payload originale del provider, non tutte con una seconda fonte indipendente.
- Importazione senza chiave API ripetibile sullo stesso archivio, con hash dati invariato.
- Verifica nel browser dell'applicazione reale collegata a PostgreSQL, dei filtri e delle viste statistiche/modello.

## Limiti e funzionalità assenti

- Un solo scenario storico fissato; selezione libera di stagione, aggiornamento live, avanzamento di giornata e liquidazione delle scommesse non implementati.
- Nessun ROI, capitale simulato o backtest economico sui risultati della settima giornata. Le segnalazioni sono confronti matematici, non prova di redditività.
- 16 partite di validazione sono poche; non è dimostrato un vantaggio dell'inclusione delle coppe. Gol modellati come Poisson indipendenti e assenza di calibrazione estesa.
- Il calendario del contesto include soltanto incontri conclusi al taglio: non proietta il carico degli impegni programmati fra taglio e evento futuro. Il riposo è misurato fra calci d'inizio, non come recupero fisiologico effettivo.
- Nessun dato su formazioni, infortuni, minuti dei giocatori o distanze di viaggio. Copertura del calendario subordinata al provider e classificazione esplicita delle competizioni note.
- Le quote sono storiche al taglio, ma il calendario e i metadati delle partite sono quelli restituiti oggi dal provider, non un archivio certificato delle modifiche note a quella data.
- I punteggi sul campo sono distinti da eventuali decisioni amministrative successive; vedere il caso Vålerenga–Kristiansund in `docs/DATA_AND_MODEL.md`.
- Nessuna esposizione pubblica, autenticazione multiutente o deploy automatico. L'applicazione è per uso locale.
- Archivio dati non versionato: un nuovo clone richiede importazione o trasferimento privato della cache. Disponibilità e limiti del piano gratuito possono cambiare.

## Registro delle modifiche

- 18 ottobre 2026: prima implementazione funzionante del simulatore storico, database, downloader separati, statistiche, addestramento, interfaccia e controlli di integrità. Sostituito l'inventario iniziale con lo stato verificabile della versione.
