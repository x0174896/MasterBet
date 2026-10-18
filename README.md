# MasterBet

Laboratorio locale per ricostruire un campionato a una data passata, consultare quote realmente offerte allora e confrontarle con probabilità statistiche. Riprende l'idea del programma originale; il modello è nuovo e non pretende di riprodurne esattamente i suggerimenti.

Prima versione: **Eliteserien 2026**, taglio **29 aprile 2026, ore 12:00 UTC** (14:00 in Italia). Sono disponibili 47 partite concluse e 1.998 esiti quotati per gli 8 incontri della settima giornata, dal 1 al 4 maggio. Include gol per tempo, risultati, classifica, calendario delle coppe e modello addestrato su un campione 2025.

- Frontend Angular 22 e Bootstrap: `frontend/`.
- Backend Java 25 e Spring Boot: `backend/`.
- PostgreSQL locale dedicato; dati e chiavi esclusi da Git.
- Downloader separati per calendario, risultati, catalogo mercati e quote storiche; interfacce sostituibili.

Doppio clic su **`dev-local.bat`** per aprire due console con i log: frontend su http://127.0.0.1:4200 e backend su http://127.0.0.1:8080. Chiudendo una console si arresta soltanto il relativo servizio. Prerequisiti, arresto e verifiche in [setup.md](setup.md). Funzionalità e limiti in [PROJECT_FEATURES.md](PROJECT_FEATURES.md). Metodo e controlli sui dati in [docs/DATA_AND_MODEL.md](docs/DATA_AND_MODEL.md).

L'applicazione mostra un esperimento su dati storici. Non esegue scommesse e non dimostra una redditività. L'avanzamento alla giornata successiva e la liquidazione dei pronostici non fanno parte di questa prima versione.
