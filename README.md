# MasterBet

Laboratorio locale per ricostruire un campionato a una data passata, consultare quote realmente offerte allora e confrontarle con probabilità statistiche. Riprende l'idea del programma originale; il modello è nuovo e non pretende di riprodurne esattamente i suggerimenti.

Scenario corrente verificato: **Eliteserien 2026**, taglio **6 maggio 2026, ore 12:00 UTC** (14:00 in Italia), step 8. Sono disponibili 57 partite concluse, 1.787 quote per 6 incontri e cinque schedine salvate per ciascun modello. Lo scenario iniziale al 29 aprile resta conservato. Include gol per tempo, classifica, calendario delle coppe e riaddestramento su partite precedenti al taglio.

- Frontend Angular 22 e Bootstrap: `frontend/`.
- Backend Java 25 e Spring Boot: `backend/`.
- PostgreSQL locale dedicato; dati e chiavi esclusi da Git.
- Downloader separati per calendario, risultati, catalogo mercati e quote storiche; interfacce sostituibili.

Doppio clic su **`dev-local.bat`** per aprire due console con i log: frontend su http://127.0.0.1:4200 e backend su http://127.0.0.1:8080. Chiudendo una console si arresta soltanto il relativo servizio. Prerequisiti, arresto e verifiche in [setup.md](setup.md). Funzionalità e limiti in [PROJECT_FEATURES.md](PROJECT_FEATURES.md). Metodo e controlli sui dati in [docs/DATA_AND_MODEL.md](docs/DATA_AND_MODEL.md).

Un unico job avanza lo scenario, scarica risultati e quote, riaddestra, liquida le scelte precedenti e propone nuove schedine. L'occhio mostra i risultati esatti e la vincita/perdita ipotetica, salvandoli separatamente dai dati del modello. L'applicazione non esegue scommesse e non dimostra una redditività: nella validazione corrente entrambi i modelli sono peggiori della base statistica.
