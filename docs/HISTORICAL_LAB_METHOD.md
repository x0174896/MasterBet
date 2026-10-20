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

Gli originali con prezzi/stati discordanti allo stesso timestamp restano conservati. L'unione delle versioni deduplica solo aggiornamenti equivalenti; non sceglie una variante del conflitto. Conta esclusivamente l'ultimo timestamp ammesso al taglio: se ambiguo, quell'esito è indisponibile; se il conflitto è successivo al kickoff, non invalida le quote prepartita. Un aggiornamento successivo univoco può rendere di nuovo disponibile l'esito, senza modificare la sua disponibilità a un taglio precedente.

Pattern con condizioni predefinite cercati separatamente per campionato/stagione: supporto, frequenza/base, lift, verifica sull'ultimo 20% cronologico e penalizzazione esplorativa delle molte ricerche. Non sono p-value calibrati o prove causali. Quattro relazioni principali per popolazione; le condizioni entrano nelle variabili del modello senza riscrivere proposte passate.

Navigazione dalla prima proposta effettiva della modalità/campionato. Tutte le proposte future precomputate sono visibili con la settimana di generazione; gli esiti non ancora utilizzabili restano in attesa. Lo spoiler non modifica modello/statistiche/saldo. Grafico da zero, un punto per singola liquidabile, ordine di disponibilità e ID stabile, solo netto.

Operativo separato: tempo reale, prossimi sette giorni, nessun rewind/invio puntate. Il successivo download indipendente dei risultati permette di liquidare gli incontri conclusi.

## Ricerca adattiva iniziale e confronto delle campagne

La strategia `BASELINE` conserva il metodo originale. Gli esperimenti ereditano periodo, competizioni e utile obiettivo da una campagna storica originale conclusa, creando nuovi modelli/step/proposte senza download o riconciliazione delle sorgenti. `CALIBRATED` verifica soltanto la calibrazione, `SIGNALS` aggiunge il controllo dei gruppi statistici, `FILTERED` aggiunge i filtri sulle categorie, `ADAPTIVE` li combina. Queste strategie sperimentali sono limitate alla modalità storica.

Per ogni finestra e modalità coppe, le previsioni originali già concluse sono combinate con le nuove previsioni grezze di controllo, deduplicate per incontro con precedenza al controllo nuovo. Questi controlli conservano la migliore scelta grezza anche quando la politica finale si astiene, e non contribuiscono al saldo. Risultati disponibili al taglio o successivamente sono esclusi dall'apprendimento adattivo; le proposte e i controlli attuali non sono liquidati per decidere la loro stessa selezione.

La calibrazione è un'intercetta logistica con regolarizzazione 10 e offset limitato a ±1,5. Con almeno 100 osservazioni si usa il primo 70% cronologico per stimarla e il resto per controllare Brier e log loss; gli esiti simultanei restano nello stesso lato. Almeno 60 casi di preparazione e 30 di verifica; accettazione con miglioramento Brier almeno 0,001 e log loss inferiore. Si conserva l'offset preparato sul primo blocco senza rifarlo sul suffisso di verifica. In assenza di conferma si conserva la probabilità grezza. Il controllo riguarda la popolazione delle scelte grezze pregresse, non la distribuzione completa di tutti i mercati: restano necessari i criteri grezzi originali oltre a quelli successivi alla calibrazione.

L'audit dei segnali confronta la devianza Poisson del modello di verifica con quella ottenuta sostituendo alle medie del suo training un gruppo di input: medie recenti/interazioni, calendario, condizioni dell'ultima partita, gol di coppa quando presenti. Il suffisso è successivo alla disponibilità di tutti gli esiti di quel training; almeno 40 casi e miglioramento almeno 1% in entrambe le metà. Si sceglie al massimo un gruppo, poi si riaddestra sul prefisso completo azzerando quelle colonne. È una verifica tramite soppressione del segnale, non un confronto esaustivo di modelli alternativi. Le medie del campionato sono sempre conservate; i gruppi non confermati restano disponibili.

I filtri osservano categorie di mercato/periodo/esito, campionato/stagione, fasce EV grezzo (5–15%, 15–30%, almeno 30%), quote (sotto 1,5; 1,5–2,5; almeno 2,5), carico del calendario, condizioni del precedente e prezzo osservato oltre 24 ore prima del kickoff. Nessun bookmaker o campionato è escluso per nome. Servono almeno 200 osservazioni complessive e sei settimane; il confronto è diviso per settimane in due blocchi, con almeno 30 casi della condizione e 30 casi di confronto per blocco. ROI monetario negativo e inferiore di almeno due punti al confronto in entrambi i blocchi, più limite superiore esplorativo negativo del ritorno a puntata costante (media + 2,58 errori standard raggruppati per settimana). Il controllo sul ritorno a puntata costante aggiunge una verifica distinta dalla regola di puntata a utile obiettivo. Si ricalcola la decisione a ogni finestra. Sono euristiche esplorative, senza p-value calibrati o correzione formale delle molte ricerche.

La selezione applica la calibrazione e le esclusioni a ciascun candidato prima di scegliere la migliore singola ammessa. Un'alternativa può quindi sostituire la vecchia proposta; se nessuna supera i criteri, si registra l'astensione con motivazione. I confronti ottenuti eliminando soltanto vecchie giocate non predeterminano il risultato del nuovo calcolo.

Modelli, correzione, supporti, gruppi soppressi, categorie e contatori degli scarti sono salvati nello step; previsioni grezze in una tabella separata. Gli esiti delle campagne sono congelati per preservare il saldo da future correzioni dell'archivio. Il confronto `/campagne` mostra saldo, ROI sul puntato, volume, discesa dal picco e qualità delle probabilità; esiti finali e taglio simulato sono esplicitamente distinti. Anche con il rispetto automatico dei prefissi temporali, le ipotesi sono state sviluppate osservando questo storico: il confronto resta retrospettivo, non una conferma indipendente di rendimento futuro.

## Calibrazione condizionale e mercati alternativi, versione 3

La correzione iniziale era appresa sulle prime scelte grezze ma applicata anche ad altri mercati: una popolazione diversa. La versione 3 conserva, prima della selezione finale, tutte le quote che superano probabilità grezza 50%, EV grezzo 5% e quota massima 5. Ogni record per incontro/modalità contiene input e previsioni di tutte le linee/bookmaker/esiti idonei, senza risultato. Anche una partita senza proposta finale contribuisce ai controlli dopo la conclusione; nessuna promozione a posteriori di una previsione mai prodotta.

Le previsioni vengono raggruppate per tipo di mercato, periodo ed esito. Per ogni gruppo si stima una regressione logistica su `logit(probabilità grezza)`, con intercetta limitata a ±3, pendenza tra 0 e 2 e penalizzazione 10 verso un modello globale. Il globale usa solo il medesimo prefisso di training. Ogni incontro ha peso totale uno nel calibratore, indipendentemente dal numero di quote; supporti minimi contati su incontri distinti: 100 complessivi, 60 nel training, 30 nella verifica.

Il taglio circa 70/30 viene scelto fra settimane di previsione, conservando insieme i simultanei. Il training ammette soltanto previsioni precedenti al confine e risultati disponibili strettamente prima del confine. Le previsioni di verifica partono dal confine: nessun risultato del training è successivo alla loro generazione. La correzione deve migliorare Brier di almeno 0,001 e log loss; in caso contrario le probabilità grezze possono essere usate soltanto nelle fasce verificate.

Il controllo è distinto per probabilità grezza 50–60, 60–70, 70–80, 80–100% e comprende il sottoinsieme che supera probabilità/EV dopo la correzione. Servono 15 incontri di verifica nella fascia e 10 nel sottoinsieme. La sovrastima media non deve superare 0,02 più due errori standard dei residui, aggregati per incontro. Input oltre il minimo/massimo di training esteso di 0,025 non sono supportati. Sono soglie esplorative predefinite: non sono un intervallo di rendimento garantito né una correzione formale della selezione tra molti mercati.

Le regole adattive precedenti sono valutate all'interno di ciascun mercato, su un solo rappresentante per partita scelto per EV grezzo. Un calendario congestionato o una stagione negativa possono quindi escludere quel mercato senza vietare le alternative. Restano i supporti, i due blocchi cronologici e il controllo settimanale descritti sopra. La politica sceglie il miglior EV fra tutte le quote confermate; nessuna quota è imposta per completare il calendario.

Per ogni incontro sono conservate anche le scelte senza filtri e senza sostituzioni. Il confronto diagnostico nella pagina Modello liquida solo quelle concluse prima del taglio e non guida retroattivamente la selezione. Isola gli effetti delle regole usando lo stesso modello, non equivale a tre campagne indipendenti o a una prova futura.

Il ricalcolo richiesto dall'utente sostituisce il solo esperimento autorizzato nello stesso ID, mantenendo nome, configurazione e data di creazione. Tutti gli step vengono preparati prima della pubblicazione atomica; la campagna originale e le sorgenti non sono riscritte e non viene creata un'altra campagna di backup. Il comando dedicato e i vincoli sono in `setup.md`. Le campagne già salvate con i metodi precedenti conservano il loro comportamento e i loro esiti.

## Promozione delle sostituzioni, versione 4

La calibrazione per mercato ha migliorato alcune probabilità ma non il saldo delle alternative: il ricalcolo intermedio è rimasto negativo. La versione 4 non considera sufficiente un EV stimato alto per sostituire la prima scelta. In ogni settimana conserva la migliore alternativa per famiglia dopo calibrazione e condizioni, prima del nuovo controllo di promozione. Il candidato e la quota sono scelti senza il risultato, anche quando non vengono giocati; gli esiti entrano nei controlli soltanto dopo la loro disponibilità. Non si ricostruisce a posteriori l'alternativa vincente usando una calibrazione addestrata sul suo stesso esito.

Per famiglia servono almeno 30 incontri distinti in sei settimane di previsione e almeno dieci casi in ciascuna metà cronologica. Entrambi i blocchi devono avere ROI positivo. Si calcolano inoltre il ROI complessivo e la differenza netta per incontro rispetto alla prima scelta grezza sugli stessi incontri; i limiti inferiori `stima − 2 errori standard`, aggregando i residui per settimana di previsione, devono essere positivi per entrambi. La differenza usa la stessa unità di utile obiettivo e le puntate delle due quote, non un numero diverso di incontri. Sono controlli esplorativi, non p-value calibrati o una garanzia sul futuro.

Una sostituzione priva di conferma resta nei controlli, senza essere pubblicata come giocata. Se la prima scelta supera calibrazione e condizioni resta disponibile; altrimenti si attende. Le alternative possono essere ammesse in una finestra successiva senza una blacklist permanente. La prova su questo storico non ha ammesso alcuna famiglia alternativa: il saldo finale positivo deriva dalle prime scelte rimaste, con meno volume, non dimostra che le alternative siano tutte perdenti.

Quando modelli e previsioni grezze sono già congelati, il ricalcolo riusa questi dati e ripercorre soltanto calibrazione, apprendimento e selezione per ogni settimana. Modelli dei gol, input, quote e risultati restano identici; non si ripete un addestramento dei gol con dati invariati né la scansione di milioni di quote. La versione corrente contiene 587 singole/+99,78 euro senza coppe e 557/+14,71 euro con coppe. Anche queste scelte metodologiche sono state sviluppate sullo stesso storico già esaminato: nessuna validazione futura indipendente e probabilità ancora sovrastimate.

## Campagna e accesso

I download singoli restano indipendenti. La campagna li coordina in sei fasi sequenziali: risultati, abbinamenti, completamento quote, riconciliazione finale, controlli, calcolo. Le fasi di importazione completate si riutilizzano per la stessa configurazione; i controlli si ripetono. Errori tecnici impediscono il calcolo. I 404 delle quote sono checkpoint per evento/bookmaker, ricontrollabili con aggiornamento forzato. Credenziali errate, quota esaurita e problemi persistenti interrompono il flusso senza aggiramenti.

Le corrispondenze sconosciute possono essere ricavate solo da almeno due incontri in date differenti con avversari diversi già riconosciuti, nello stesso campionato/stagione e con associazione uno-a-uno. Nessuna similarità dei nomi o risultato della partita partecipa alla decisione. Prove e versioni sono conservate, l'unione delle quote provvisorie è transazionale. Campionati privi di risultati HT/FT o di quote effettivamente abbinate sono esclusi anche dall'addestramento; incontri dubbi restano in archivio senza proposte forzate.

Gli alias verificati sono limitati al campionato di provenienza. Un nome provider senza suffisso femminile può essere riconosciuto nei tre campionati femminili già confermati dal catalogo, senza confonderlo con una prima squadra maschile o una riserva. Una nuova versione delle regole ripete la riconciliazione alla ripresa, mantenendo i checkpoint dei download; la riconciliazione finale resta automatica prima del calcolo.

Se la fonte non espone giornate numerate viene verificato il calendario senza `roundId`. Questo recupera la MLS 2026. Canadian Soccer League 2026 è vuota anche da questo percorso, quindi indisponibile per il test verificato; nessun risultato è ricostruito o sostituito.

[Calendari OddsPapi](https://oddspapi.io/en/docs/get-fixtures): intervalli estesi per torneo. [Quote storiche](https://oddspapi.io/en/docs/get-historical-odds): storico da gennaio 2026, attesa minima cinque secondi. Non garantiscono dati per ogni incontro: lacune riscontrate realmente. Nessun piano a pagamento, nuovo account o download dei risultati gol OddsPapi è avviato dal laboratorio.
