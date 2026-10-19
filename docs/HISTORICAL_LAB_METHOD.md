# Metodo del laboratorio multicampionato

## Fonti e identità

Il catalogo RSC pubblico `www.calcio.com/statistiche` fornisce ID, slug, paese e stagioni. Endpoint usati dal sito: `api.calcio.com/api/schedule-rounds`, `api.calcio.com/api/schedule` con `roundId`, `www.calcio.com/api/fixture/<id>`. Nessuna esecuzione degli script della pagina.

Calcio e OddsPapi hanno ID diversi. Si incrociano paese/competizione e poi casa/ospite/orario entro due ore, senza similarità arbitraria. Ambiguità escluse; quote acquisite per prime riconciliate successivamente. CONCACAF League è una coppa anche se il catalogo la classifica diversamente.

La data `starting_at` prevale su slug e giornata. Verificato il recupero Sarpsborg–Bodø/Glimt dal 14 marzo al 15 aprile 2026, HT 1–0 e FT 1–1. Identità/giornata originali conservate. Nei payload `2H` è cumulativo: può sostituire FT se assente nei finali ordinari. Nei supplementari si usa soltanto il cumulativo regolamentare, mai ET/PEN. Dati mancanti non diventano zeri.

Fine reale usata quando disponibile/coerente nel calendario OddsPapi; altrimenti kickoff +24 ore, limite prudenziale esplicito che può ritardare gli esiti. Gli originali e le revisioni con hash documentano le correzioni; metadati ricevuti oggi non certificano quando fossero noti allora.

## Preparazione e modello

Sette incontri conclusi per entrambe le squadre nello stesso campionato/stagione; coppe escluse dal conteggio. Il 2025 conta per 2025/26, non per un annuale 2026. Si importano anche le coppe precedenti necessarie al contesto.

Finestre consecutive di sette giorni, dalle 00:00 UTC della data iniziale. Ogni riga di training ricostruisce gli input dai risultati utilizzabili prima del proprio kickoff; lo step ammette soltanto righe concluse entro il taglio. La selezione riceve probabilità/input e quote, senza il risultato del target.

Quattro Poisson regolarizzate stimano gol casa/ospite per tempo. Medie degli ultimi sette precedenti, attacco/difesa e medie del campionato restano nella stagione pertinente. Riposo/frequenza/trasferte comprendono le coppe in entrambe le modalità; solo quella abilitata aggiunge gli indicatori separati dei gol di coppa degli ultimi 90 giorni.

Almeno 25 righe globali idonee. Il primo 80% cronologico (minimo 20 righe) addestra il modello di verifica; il resto misura la MAE. Poi il modello delle proposte usa tutto il prefisso consentito. Normalizzazione e pesi non vedono il futuro. Il riuso dei coefficienti per accelerare la validazione è ammesso soltanto se tutti i vecchi dati di training sono nel nuovo prefisso di training.

Selezione conservata dal laboratorio precedente: probabilità almeno 50%, EV almeno 5%, quota al massimo 5. Miglior EV, spareggio stabile per bookmaker/mercato/esito, una singola per incontro idoneo/modalità. Nessun limite cinque o proposta forzata; esclusioni motivate nello step. Puntata = utile obiettivo/(quota−1), centesimi, nessun recupero perdite.

Proposte congelate con input, quota, probabilità, versione/hash e motivazione; pesi nel relativo step. Test: modificare risultati futuri non cambia modelli precedenti e le probabilità si ricostruiscono dai dati salvati.

## Quote, pattern e consultazione

Storico: ultimo aggiornamento strettamente prepartita, anche dopo il taglio settimanale, secondo la deroga richiesta. Ultimo stato inattivo significa esito escluso, senza ripiego su quote vecchie. Operativo: prezzo già esistente nell'istante reale. Esclusi in-play e mercati non modellabili come eventi binari senza rimborso.

Pattern con condizioni predefinite cercati separatamente per campionato/stagione: supporto, frequenza/base, lift, verifica sull'ultimo 20% cronologico e penalizzazione esplorativa delle molte ricerche. Non sono p-value calibrati o prove causali. Quattro relazioni principali per popolazione; le condizioni entrano nelle variabili del modello senza riscrivere proposte passate.

Navigazione dalla prima proposta effettiva della modalità/campionato. Tutte le proposte future precomputate sono visibili con la settimana di generazione; gli esiti non ancora utilizzabili restano in attesa. Lo spoiler non modifica modello/statistiche/saldo. Grafico da zero, un punto per singola liquidabile, ordine di disponibilità e ID stabile, solo netto.

Operativo separato: tempo reale, prossimi sette giorni, nessun rewind/invio puntate. Il successivo download indipendente dei risultati permette di liquidare gli incontri conclusi.

## Campagna e accesso

Due job distinti importano tutti i campionati comuni della stagione pertinente; terminati i download, il comando di campagna calcola entrambe le modalità e tutte le settimane. I 404 delle quote sono checkpoint per evento/bookmaker, ricontrollabili con aggiornamento forzato. Credenziali errate, quota esaurita e problemi persistenti interrompono il flusso senza aggiramenti.

[Calendari OddsPapi](https://oddspapi.io/en/docs/get-fixtures): intervalli estesi per torneo. [Quote storiche](https://oddspapi.io/en/docs/get-historical-odds): storico da gennaio 2026, attesa minima cinque secondi. Non garantiscono dati per ogni incontro: lacune riscontrate realmente. Nessun piano a pagamento, nuovo account o download dei risultati gol OddsPapi è avviato dal laboratorio.
