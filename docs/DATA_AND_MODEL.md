# Dati, cronologia e interpretazione del modello

Verifica iniziale: 18 ottobre 2026. I dati sono reali e conservati nell'archivio privato locale. Questa guida documenta le scelte che incidono sulla riproducibilità; `PROJECT_FEATURES.md` elenca lo stato funzionale.

## Scenario congelato

Il taglio è **2026-04-29T12:00:00Z**, dopo la sesta giornata di calendario dell'Eliteserien. Le 47 partite effettivamente concluse comprendono anticipi e rinvii: le 16 squadre hanno fra 5 e 7 presenze. Si calcola la situazione conoscibile al taglio, non una classifica ricostruita retroattivamente imponendo sei incontri a ciascuno.

Gli 8 eventi previsti appartengono alla settima giornata, dal 1° al 4 maggio. Non si acquisiscono i loro risultati. Per ogni combinazione di evento, bookmaker, mercato ed esito, si sceglie l'ultimo aggiornamento con timestamp non successivo al taglio. Se quell'aggiornamento è inattivo o ha prezzo non valido, l'esito non viene pubblicato; non si torna a un prezzo precedente. Aggiornamenti simultanei contraddittori fanno fallire la selezione.

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

1.093 esiti hanno probabilità calcolata. La segnalazione usa `probabilità × quota − 1 ≥ 0,05` e produce 164 segnali. Il valore atteso è teorico, sensibile alla calibrazione e alle ipotesi del modello. Esiti della stessa partita e bookmaker diversi non sono scommesse indipendenti. Non sono implementati selezione di portafoglio, gestione del capitale, liquidazione o ROI storico.

## Verifiche ripetibili e provenienza

`scripts/audit-data.py` rilegge indipendentemente archivio, export del database e API. Controlla gli hash di 163 risposte originali, i 129 punteggi utilizzati (80 training/validazione, 47 scenario, 2 coppe), tutte le quote con timestamp e mercato, completezza della selezione, statistiche ricostruite e assenza di risultati dei target. Verifica anche la corrispondenza fra le API degli 8 eventi e lo snapshot. L'audit non consuma richieste OddsPapi.

Tutti i 47 primi tempi, finali e calci d'inizio del campionato 2026 sono stati riscontrati con i referti ufficiali NFF, senza differenze. Le 80 partite storiche sono controllate contro i payload originali, ma non tutte contro una seconda fonte. Le verifiche esterne locali sono in `.local/audit/nff-results.json`; se il file manca, l'audit dichiara esplicitamente il controllo NFF non eseguito.

- [Calendario ufficiale NFF Eliteserien 2026](https://www.fotball.no/fotballdata/turnering/hjem/?fiksId=206092&underside=kamper).
- [Esempio di referto NFF con punteggio del primo tempo](https://www.fotball.no/fotballdata/kamp/?fiksId=8986151).
- [Documentazione OddsPapi sui risultati](https://oddspapi.io/en/docs/get-scores).
- [Quote e conteggio richieste OddsPapi](https://oddspapi.io/us/docs/requests-and-quota).

Il contatore verificato il 18 ottobre 2026 è 239/250: restavano 11 richieste. La ripetizione completa da cache con chiave vuota ha prodotto lo stesso hash. Non è necessario un altro account per consultare o riaddestrare questa prima versione sulla cache esistente.
