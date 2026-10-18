# Dati, cronologia e interpretazione del modello

Verifica iniziale: 18 ottobre 2026. I dati sono reali e conservati nell'archivio privato locale. Questa guida documenta le scelte che incidono sulla riproducibilità; `PROJECT_FEATURES.md` elenca lo stato funzionale.

## Scenario iniziale congelato

Il taglio è **2026-04-29T12:00:00Z**, dopo la sesta giornata di calendario dell'Eliteserien. Le 47 partite effettivamente concluse comprendono anticipi e rinvii: le 16 squadre hanno fra 5 e 7 presenze. Si calcola la situazione conoscibile al taglio, non una classifica ricostruita retroattivamente imponendo sei incontri a ciascuno.

Gli 8 eventi previsti appartengono alla settima giornata, dal 1° al 4 maggio. I loro risultati sono esclusi da questo snapshot; entrano nel passato consentito soltanto al successivo avanzamento. Per ogni combinazione di evento, bookmaker, mercato ed esito, si sceglie l'ultimo aggiornamento con timestamp non successivo al taglio. Se quell'aggiornamento è inattivo o ha prezzo non valido, l'esito non viene pubblicato; non si torna a un prezzo precedente. Aggiornamenti simultanei contraddittori fanno fallire la selezione.

Lo snapshot verificato comprende 1.998 prezzi: Unibet 1.373, Pinnacle 433, bet365 192. La selezione tratta correttamente anche serie che iniziano dopo il taglio e aggiornamenti attivi con prezzo zero presenti nel payload: questi ultimi non sono quote utilizzabili. Il prezzo è conservato come decimale, senza ricostruzioni o interpolazioni. Il timestamp mostrato è quello dell'ultimo aggiornamento, non necessariamente l'istante del taglio.

Tutti gli eventi hanno le famiglie richieste: 1 X 2 finale, entrambe segnano, totali gol finali, primo e secondo tempo, primo tempo/finale. I mercati originali restano consultabili anche quando il modello non sa valutarli. Una quota per una linea 1,25 o 2,0 non viene trattata come una semplice scommessa binaria senza rimborso.

Hash SHA-256 del dataset normalizzato verificato:

```text
497c4664ae3f0a00d3300258d2765527e51823bf7ba6fdd718c8e5b4e9ed1951
```

## Punteggi e orari

Il primo tempo proviene dal periodo `p1`. Il finale è il risultato nei 90 minuti. Il secondo tempo è la differenza finale meno primo tempo, con controllo di non negatività. Nelle competizioni di campionato regolare è ammesso il campo `result` quando `fulltime` manca, solo se non risultano supplementari o rigori; quando entrambi esistono devono concordare. Nelle coppe `fulltime` è obbligatorio: non si ricava il punteggio dei 90 minuti da un risultato comprensivo di supplementari. L'assenza di dati sui supplementari è rappresentata come sconosciuta, non automaticamente come assenza di supplementari.

Il provider presenta alcuni `trueStartTime` e `trueEndTime` incoerenti con il calcio d'inizio programmato. Il confronto con i referti NFF conferma gli orari programmati delle 47 partite. `FixtureTiming` usa la fine effettiva solo quando l'inizio effettivo è entro 15 minuti dall'orario previsto e la durata è coerente. Negli altri casi adotta il limite conservativo **calcio d'inizio +24 ore** per decidere quando il risultato possa essere usato. Quel valore non è una misurazione della fine effettiva. Il campo `timingBasis` distingue i casi; il calcio d'inizio originale resta invariato. Questo può escludere prudentemente informazioni che in realtà erano già disponibili.

Il dataset di addestramento considera solo la stagione regolare 2025, identificata dal provider come stagione 127337. I playoff presenti nello stesso torneo non vengono aggiunti al campionato. I gol osservati sul campo non sono automaticamente sostituiti dai risultati amministrativi: Vålerenga–Kristiansund del 22 novembre 2025 è 3–3 sul campo (HT 0–1), mentre una decisione successiva NFF assegna il 3–0. Il modello usa i gol giocati; non pretende di ricostruire quella classifica amministrativa. [Decisione ufficiale NFF](https://www.fotball.no/lov-og-reglement/beslutninger-fra-utvalg/2025/valerenga---kristiansund-settes-til-3-0/).

## Addestramento e prevenzione delle informazioni future

Le 80 partite selezionate del campionato 2025 sono ordinate cronologicamente. Le prime 64 costituiscono il training (13 settembre–9 novembre); le successive 16 la validazione (21–30 novembre). Normalizzazione e pesi sono stimati solo sul training. Cambiare i gol della validazione non deve cambiare i pesi: un test automatico verifica questa proprietà.

Il modello stima quattro intensità: gol di casa e trasferta nel primo e nel secondo tempo. Si applicano regressioni di Poisson regolarizzate, addestrate deterministicamente. Le caratteristiche statistiche usano le ultime cinque partite di campionato disponibili prima della previsione e una prior fissa per campioni insufficienti. Le probabilità di eventi composti derivano dalle quattro distribuzioni; l'indipendenza dei gol è un'ipotesi semplificatrice.

Due modelli condividono la stessa separazione temporale: solo campionato e campionato con contesto del calendario. Il secondo considera riposo fra calci d'inizio, numero di incontri ufficiali e coppe recenti, trasferte. La classificazione delle competizioni è esplicita: le amichevoli sono escluse e una serie minore non diventa una coppa. Il calendario contiene 400 eventi di 19 squadre del campione; non sono tutti incontri di coppa o tutti delle 16 squadre della stagione 2026.

Il contesto contiene soltanto partite concluse al taglio. Non include una proiezione del carico per gli incontri programmati fra il taglio e il calcio d'inizio previsto. Non misura minuti dei singoli giocatori, trasferimenti geografici, recupero fisiologico o infortuni. Inoltre i metadati ricevuti oggi dal provider non sono un archivio delle modifiche al calendario effettivamente note nel passato.

## Risultati del confronto

| Metodo | Brier 1 X 2, somma sui tre esiti, 16 partite |
|---|---:|
| Base statistica | 0,528751 |
| Modello con solo campionato | 0,427087 |
| Modello con calendario e coppe | 0,442050 |

Più basso è meglio. Il modello con coppe supera la base in questo campione, ma non quello con solo campionato. Il campione è insufficiente per una conclusione robusta sul vantaggio delle coppe. Lo scenario usa il modello con coppe come scelta esplicita del primo esperimento, senza selezione basata sui risultati futuri della settima giornata.

1.093 esiti hanno probabilità calcolata. La segnalazione usa `probabilità × quota − 1 ≥ 0,05` e produce 164 segnali. Il valore atteso è teorico, sensibile alla calibrazione e alle ipotesi del modello. Esiti della stessa partita e bookmaker diversi non sono scommesse indipendenti. Questi conteggi descrivono lo scenario iniziale; selezione delle schedine, capitale, liquidazione e ROI sono ora implementati come descritto sotto.

## Verifiche ripetibili e provenienza

`scripts/audit-data.py` rilegge indipendentemente archivio, export del database e API. Controlla gli hash di 163 risposte originali, i 129 punteggi utilizzati (80 training/validazione, 47 scenario, 2 coppe), tutte le quote con timestamp e mercato, completezza della selezione, statistiche ricostruite e esclusione dei risultati dei target dai dati del modello. Verifica anche la corrispondenza fra le API degli 8 eventi e lo snapshot. L'audit non consuma richieste OddsPapi.

Tutti i 47 primi tempi, finali e calci d'inizio del campionato 2026 sono stati riscontrati con i referti ufficiali NFF, senza differenze. Le 80 partite storiche sono controllate contro i payload originali, ma non tutte contro una seconda fonte. Le verifiche esterne locali sono in `.local/audit/nff-results.json`; se il file manca, l'audit dichiara esplicitamente il controllo NFF non eseguito.

- [Calendario ufficiale NFF Eliteserien 2026](https://www.fotball.no/fotballdata/turnering/hjem/?fiksId=206092&underside=kamper).
- [Esempio di referto NFF con punteggio del primo tempo](https://www.fotball.no/fotballdata/kamp/?fiksId=8986151).
- [Documentazione OddsPapi sui risultati](https://oddspapi.io/en/docs/get-scores).
- [Quote e conteggio richieste OddsPapi](https://oddspapi.io/us/docs/requests-and-quota).

Prima dell’avanzamento il contatore era 239/250; dopo lo step 8 è stato verificato a 241/250 (9 residue). La ripetizione completa da cache con chiave vuota ha prodotto lo stesso hash. Non è necessario un altro account per consultare o riaddestrare questa prima versione sulla cache esistente.


## Avanzamento verificato: step 8

Il taglio successivo è **6 maggio 2026 ore 12 UTC**. Le partite concluse diventano 57: le otto del precedente gruppo di target più due anticipi del 29–30 aprile. I target dell’8–10 maggio sono sei: due incontri dell’ottava giornata ufficiale sono rinviati al 20 maggio. Non si forza il calendario a otto incontri per finestra. Gli step settimanali possono divergere dai numeri ufficiali di giornata e saltare settimane vuote.

Le 1.787 quote disponibili sono state ricalcolate dalle serie originali con lo stesso criterio temporale. I dieci nuovi HT/FT sono verificati nei referti NFF, identificati per squadre e kickoff; il parser legge solo la prima scheda della partita, mai incontri correlati. Il calendario NFF viene usato per identità/orari e non per importare automaticamente tutti i risultati presenti nella pagina.

Hash verificato dello snapshot dello step 8:

```text
4a2b7ee44f017acb948101f7f3ba6418924121fa05c4924b8b233d005e031d9a
```

Il training viene ricostruito usando le 80 partite del 2025 e le 57 del campionato 2026, con separazione cronologica 80/20: 109 training (13 settembre 2025–12 aprile 2026), 28 validazione (15 aprile–4 maggio 2026). I risultati della validazione non contribuiscono ai pesi; possono essere usati come storia nota per previsioni successive al loro completamento.

| Metodo | Brier 1 X 2, 28 partite |
|---|---:|
| Base statistica | 0,609880 |
| Solo campionato | 0,724720 |
| Calendario e coppe | 0,711693 |

Entrambi i modelli sono **peggiori della base** in questa validazione. Il modello con coppe migliora leggermente quello senza, ma questo non dimostra redditività né una buona calibrazione. Le schedine sono un esperimento riproducibile, non suggerimenti con vincita garantita.

## Scelte congelate, capitale ed occhio

Il job salva le selezioni prima di scaricare nuovi risultati. Ogni schedina conserva quote originali, probabilità, modello, hash e taglio; i bilanci sono indipendenti per le due varianti. La politica corrente punta a cinque proposte, entro due unità: quattro singole da 0,4 u e una doppia da 0,25 u su sei partite distinte, oppure cinque singole se sono idonee solo cinque partite. Criteri della singola: Unibet, EV almeno 5%, probabilità almeno 50%, quota massimo 5, ordinamento EV; non si aumentano i rischi né si ripete una partita per raggiungere cinque.

Lo step 7 mantiene le quattro scelte originali (tre singole da 0,5 u e una doppia da 0,25 u), congelate prima di acquisirne i risultati. Profitto liquidato: **+0,51 €** su 8,75 € (ROI circa 5,8%) per entrambe le varianti in questo singolo step. Nessuna conclusione statistica di redditività è possibile. La proposta iniziale dello step 8 è conservata; la revisione a cinque, richiesta prima di consultarne gli esiti, è salvata separatamente. Con unità 5 € impegna **9,25 €**; capitale al taglio 500,51 €, disponibile 491,26 €.

L’icona dell’occhio consulta e conserva il referto reale in un archivio separato. Mostra HT, SH e FT nei 90 minuti, esito di ciascuna selezione, ritorno e netto. Non aggiorna la storia ammessa al modello né liquida il capitale della simulazione; questo avviene soltanto con un avanzamento che rende il risultato conoscibile al taglio. La consultazione blocca revisioni retroattive della proposta, anche se il download del referto fallisce. Rimborsi, annullamenti e regole particolari del bookmaker non sono implementati: il risultato è una liquidazione matematica dei mercati supportati sui gol sul campo.

La presenza di referti futuri nella cache è quindi intenzionale dopo un clic sull’occhio. L’invariante verificata è la loro **esclusione dai dati del modello**, non la loro assenza da tutto il disco. L’utente che apre l’occhio conosce il futuro: decisioni manuali prese dopo quella consultazione non sono più un test cieco.

L’audit indipendente corrente verifica 171 risposte OddsPapi, 139 punteggi usati, 1.787 quote e le 22 selezioni dei due modelli nei due step. Ricostruisce le probabilità dai pesi e dalle caratteristiche e controlla gli importi decimali. Verificate anche due consultazioni NFF, una singola vinta e una doppia persa, senza variazione dell’hash dello snapshot. La massima perdita dal picco è misurata a fine step, non lungo la sequenza intragiornaliera delle partite. Il Brier binario del portafoglio riguarda soltanto le selezioni risolte, non tutti i mercati.
