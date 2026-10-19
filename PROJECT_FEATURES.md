# Inventario funzionale MasterBet

Ultimo aggiornamento: 19 ottobre 2026 — correzione archivi quote voluminosi, conflitti e ripresa; campagna dati reale in corso.

## Funzionalità implementate

- Catalogo Calcio.com incrociato con OddsPapi per paese e competizione, senza tetto numerico. Corrispondenze ambigue escluse. Verificati 63 campionati comuni nel catalogo, 62 con stagione comprendente il 2026: il catalogo non certifica le quote di ogni incontro.
- Parser dell'API pubblica utilizzata da Calcio.com: giornata per ID, identità stabile, data effettiva `starting_at`, HT/FT regolamentari e SH per differenza. Gestiti finali con solo `2H` cumulativo e supplementari senza includerli nei gol dei 90 minuti. Dati mancanti sconosciuti, mai zero inventato.
- Tre processi indipendenti: risultati, quote e calcolo offline; catalogo come operazione preliminare distinta. Risultati definitivi riutilizzati, calendari ricontrollati per rinvii/recuperi, aggiornamento forzato per correzioni. Quote acquisibili prima dei risultati, con riconciliazione successiva degli ID.
- Coppe delle squadre coinvolte acquisite anche per la preparazione precedente al test. Nessuna proposta sulle coppe. Competizioni senza stagione pertinente escluse dal download richiesto.
- Amministrazione riservata: prima password scelta localmente, PBKDF2, sessione HttpOnly/SameSite, controllo origine e intestazione anti-CSRF. Periodo, campionati, bookmaker, aggiornamento, modalità e utile obiettivo configurabili. Job, avanzamento/errori e checkpoint persistenti; ripresa dopo interruzione/PID riutilizzato.
- Download risultati e quote concorrenti, avvio serializzato nel DB, nessun doppio job dello stesso tipo. Il calcolo è escluso mentre cambiano le sorgenti. Aprire le pagine/cambiare filtri non chiama le fonti esterne.
- Archivi OddsPapi con risposta JSON incapsulata leggibili fino a 128 milioni di caratteri per stringa dell'involucro, senza allentare i limiti del payload né la verifica dell'hash. Corretto il limite predefinito di 20 milioni che bloccava la rilettura di storici validi già scaricati.
- Storici con aggiornamenti discordanti conservati integralmente e uniti senza duplicazioni. Conflitti in-play non alterano i prezzi prepartita; se l'ultimo aggiornamento utilizzabile è ambiguo, solo quell'esito è indisponibile, senza scegliere prezzi arbitrari o recuperare quello precedente. Alias verificato Leyton Orient London / Leyton Orient.
- Checkpoint quote `IMPORTED` scritto solo dopo tutti i salvataggi dell'incontro. Una risposta soltanto `DOWNLOADED` viene riprocessata dalla cache; incontri conclusi completamente importati e assenze già verificate vengono riutilizzati, salvo aggiornamento forzato.
- Sette risultati precedenti per entrambe le squadre nello stesso campionato e stagione. Il 2025 prepara una stagione 2025/26, non un annuale 2026. Le coppe non soddisfano la soglia.
- Riaddestramento Poisson globale ogni sette giorni: statistiche per campionato/stagione, medie degli ultimi sette precedenti, interazioni attacco/difesa, calendario, riposo e trasferte. Almeno 25 righe globali idonee, validazione cronologica 80/20 e MAE sui quattro conteggi di gol. Normalizzazione, pesi e riuso dei coefficienti dipendono solo dal prefisso consentito.
- Checkbox: senza gol di coppa (32 variabili) oppure con indicatori separati degli ultimi 90 giorni (42). Calendario delle coppe, riposo, frequenza e trasferte in entrambe. Modelli, proposte e saldi separati e persistiti.
- Pattern per campionato/stagione: supporto, frequenza/base, lift e verifica cronologica. Condizioni sui precedenti di campionato e, solo se abilitati, sui gol di coppa. Quattro relazioni principali per popolazione; penalizzazione esplorativa delle ricerche multiple, non p-value calibrato o prova di causalità.
- Una migliore singola per incontro idoneo/modalità, senza multiple o limite cinque. Conservati i criteri EV almeno 5%, probabilità almeno 50%, quota al massimo 5. Nessuna selezione forzata; esclusioni motivate nello step. Supportati 1X2, entrambe segnano, totali/team totals su mezze linee, risultati esatti e primo tempo/finale.
- Quote storiche: ultimo aggiornamento strettamente prepartita, anche dopo il taglio settimanale secondo la deroga richiesta. Nessun ripiego dopo sospensione e nessuna quota in-play. In operativo si usano soltanto prezzi già disponibili nell'istante reale.
- Proposte immutabili: quota, probabilità, Delta/EV, puntata, spiegazione dai precedenti, input congelati, versione e hash. Parametri del modello salvati per step/modalità, tempi reali e simulati distinti.
- Unità come utile netto obiettivo: puntata = unità/(quota−1), centesimi e nessun recupero perdite. Netto da zero, saldi separati, filtro campionato. Grafico con un punto per singola liquidabile anche simultanea, ordine stabile e navigazione da tastiera.
- Cinque pagine Angular lazy: Scommesse, Andamento, Campionati e statistiche, Modello e pattern, Amministrazione; filtri condivisi. Statistiche complete: risultati, punti, forma, HT/SH/FT e casa/trasferta, sempre per competizione/stagione.
- Consultazione dalla prima proposta effettiva della modalità/campionato all'ultimo esito. Proposte future già calcolate visibili in attesa, occhio spoiler separato e senza effetti su modello/statistiche/saldo/grafico. Nessun ricalcolo cambiando data o checkbox.
- Modalità operativa separata: istante reale, sette giorni successivi, niente rewind; acquisizione calendari/quote fino a 14 giorni avanti. Nessun invio di puntate o deploy pubblico.
- Comando campagna: due job indipendenti di download, attesa del loro esito, calcolo di tutte le finestre e modalità. Errori bloccanti interrompono il flusso, lacune esplicite. Reset legacy subordinato a backup locale verificato e archivio delle righe precedenti.
- Flyway V1–V9, raw privati con hash e revisioni. Backup verificato realmente il 19 ottobre; 45 righe delle vecchie proposte/esiti/revisioni archiviate e rimosse. Partite, risultati, snapshot, modelli precedenti e raw conservati.

## Dati reali e verifiche

Campagna 1 gennaio–18 ottobre 2026 avviata, non ancora conclusa. Già verificati oltre duemila risultati e acquisiti oltre quarantamila esiti quotati; contatori correnti nell'amministrazione. Diverse risposte di gennaio sono `404 / No historical odds found`; verificato un incontro di maggio con storico di tutti e tre i bookmaker. Catalogo, incontri riconciliati e copertura effettiva restano conteggi distinti. Nessuna conclusione statistica della campagna completa ancora disponibile.

Verifica riuscita dopo le correzioni: 139 test JUnit 5/H2, 46 test Angular/Vitest e build frontend di produzione. Coperti anche rilettura/aggiornamento di archivi oltre 20 MB, conflitti prepartita/in-play, deduplicazione in persistenza, alias Leyton Orient e checkpoint dopo errori di salvataggio. Restano coperti parser/rinvii, soglia sette, campionati/stagioni, coppe, sospensioni, download indipendenti, concorrenza/ripresa, accesso e origine, futuro escluso, probabilità ricostruite dagli input congelati, spoiler e grafico. Nessun mock applicativo da compilare; contratti HTTP invariati.

Audit reale del 19 ottobre sulle 69 risposte già segnalate (59 archivi oltre il limite di lettura e 10 incontri con conflitti): rilettura/hash, fusione idempotente e selezione strettamente prepartita verificati su 88.945 serie, zero errori, senza nuove richieste API o scritture nel database. Dei 39 abbinamenti mancanti esaminati in League One, 34 dipendono dall'alias Leyton Orient; gli altri cinque sono eventi `Cancelled` nel calendario OddsPapi, correttamente esclusi anziché associati a un altro incontro.

Browser reale: pagine distinte, catalogo dal DB, stato senza elaborazioni complete, amministrazione riservata e navigazione su schermo stretto. Grafico popolato/esiti verificati automaticamente; campagna completa reale ancora non consultabile.

## Limiti

- Fonti modificabili e lacune reali: quote mancanti non ricostruite. Alias verificati e scarto massimo due ore; ambiguità o date non concordanti richiedono verifica, senza abbinamenti approssimativi.
- Calcio non espone il fischio finale. Si usa `trueEndTime` OddsPapi se coerente, altrimenti kickoff +24 ore: limite prudenziale dichiarato, non misurazione, che può ritardare statistiche/liquidazione. Metadati acquisiti oggi non certificano quando le modifiche fossero note allora.
- La deroga ai prezzi successivi al taglio differisce da una simulazione rigorosa dei prezzi disponibili allora. Lo spoiler può influenzare scelte manuali, pur restando escluso dal calcolo.
- Pattern esplorativi e quattro Poisson con indipendenza, probabilità non calibrate estesamente: nessun miglioramento o rendimento garantito. Includere le coppe può cambiare le proposte senza migliorarle.
- Storico senza HT/FT escluso. Annullamenti, rimborsi e regole particolari non liquidati come perdite. In operativo sono proposti solo incontri futuri confermati; gli esiti richiedono il download indipendente dei risultati.
- Download ripresi da cache/checkpoint. Calcolo interrotto conservato e ripetibile come nuova elaborazione completa, non riavviato automaticamente dopo reboot. La campagna attiva prosegue finché rimane in esecuzione il suo processo.
- Nessun mock applicativo. API precedenti disabilitate di default (HTTP 410) per impedire vecchie multiple e aggiramento dell'admin; codice precedente conservato per regressione, abilitato nel profilo test.
- Dati, credenziali, backup e cache non versionati. Nessun database remoto, produzione o deploy modificato.

## Registro delle modifiche

- 19 ottobre 2026: corretti archivi quote validi oltre il limite di lettura, fusione degli aggiornamenti discordanti, disponibilità prudenziale degli esiti ambigui, alias Leyton Orient e checkpoint distinto fra download e importazione completa. Cache e risultati precedenti riutilizzati; nessuna modifica al contratto API o ai mock applicativi, assenti.
- 19 ottobre 2026: laboratorio multicampionato, import separati, sette precedenti, modalità coppe, riaddestramento/pattern, singole, amministrazione, versioni, consultazione e grafico netto. Backup/reset eseguiti; campagna reale in corso. Inventario aggiornato distinguendo l'archivio norvegese dal comportamento corrente.
- 18 ottobre 2026: esperimento Eliteserien fino allo step 11, selezione limitata a cinque schedine e API precedenti; dati conservati come archivio.
