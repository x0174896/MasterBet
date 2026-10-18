# Guida di predisposizione e avvio

**Stato: MODELLO DA COMPILARE.** Le sezioni seguenti non attestano che il progetto, i componenti o i comandi siano già configurati.

## 1. Stack e prerequisiti

Indicare le versioni effettivamente adottate di Java/JDK, Spring Boot, Maven, Node.js/npm, Angular, Bootstrap e database, limitatamente ai componenti presenti. Documentare strumenti aggiuntivi soltanto se necessari. Non ereditare automaticamente le versioni di altri progetti.

## 2. Repository e documentazione

Documentare repository e procedura di clone, verifica dei branch `develop` e `main`, configurazione del remoto e compilazione di `environment.md` locale. Le operazioni di inizializzazione richiedono l'autorizzazione prevista da `AGENTS.md`.

Escludere da Git `environment.md` e i file privati dei segreti. Predisporre esempi privi di valori reali, se necessari. Documentare Git LFS soltanto se effettivamente adottato.

## 3. Configurazione

Elencare i nomi delle variabili, il loro scopo, l'obbligatorietà, gli eventuali valori predefiniti non sensibili e il meccanismo di caricamento. Separare sviluppo, test e produzione. Non inserire segreti.

## 4. Database e migrazioni

Documentare database e ruolo dedicati, schema, estensioni necessarie, strumento di migrazione, procedura per ambiente vuoto e configurazione dei test. Distinguere operazioni locali da operazioni remote che richiedono autorizzazione specifica.

## 5. Installazione delle dipendenze

Inserire soltanto comandi verificati per il progetto reale. Documentare l'uso del wrapper Maven e del lockfile npm, se presenti. Non disattivare TLS per aggirare problemi di rete o certificati.

## 6. Build e test

Comandi di riferimento per la struttura `backend` / `frontend`, da confermare durante l'inizializzazione:

| Componente | Cartella | Comando di riferimento |
|---|---|---|
| Backend Maven, Windows | `backend` | `.\mvnw.cmd test` |
| Backend Maven, macOS/Linux | `backend` | `./mvnw test` |
| Build frontend Angular | `frontend` | `npm run build` |
| Test frontend | `frontend` | `npm test -- --watch=false` |
| Build mock, solo se configurata | `frontend` | `npm run build:mock` |

Documentare gli equivalenti reali se lo stack differisce, i test di integrazione, gli eventuali controlli facoltativi e i requisiti di isolamento. Nessun comando della tabella è stato verificato per il nuovo progetto.

## 7. Avvio e arresto

Documentare comandi reali, profili, porte, URL locali, endpoint di salute e modalità di arresto dei soli processi avviati per il progetto. Descrivere i launcher soltanto se esistono.

## 8. Mock e altri client

Se esiste una modalità mock, descrivere avvio, separazione dal backend reale e collegamento a `MOCK_API_STUDIO.md`. Se viene introdotto un client mobile o un altro componente, aggiungere prerequisiti, build, test e avvio nello stesso sviluppo.

## 9. Problemi frequenti

Registrare problemi riprodotti e soluzioni verificate, senza riportare segreti o modificare configurazioni globali di altre applicazioni.
