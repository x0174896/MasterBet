# Guida della modalità mock

**Stato: MODELLO FACOLTATIVO, NESSUNA IMPLEMENTAZIONE ATTESTATA.** La presenza di questo file non significa che esistano interceptor, endpoint simulati, pannelli o comandi mock. Compilarlo soltanto se la modalità mock fa parte del progetto autorizzato.

Verifica MasterBet del 20 ottobre 2026: nessuna modalità mock applicativa o comando `build:mock` presente. I soli stub HTTP dei test Angular sono aggiornati per `GET /api/lab/campaigns`, `POST /api/lab/admin/experiments` e i campi opzionali delle campagne/decisioni adattive, inclusi `learning.markets`, `learning.markets.replacements`, verifiche per mercato/fascia e confronti diagnostici. Linea e periodo delle quote già restituiti dalle API sono descritti anche nei DTO frontend. Nessun endpoint modificato per il ricalcolo nello stesso ID, disponibile solo da CLI. Contratto reale e comandi in `setup.md` e `frontend/README.md`.

## Scopo e avvio

Descrivere quali flussi si possono provare senza backend e quali restano esclusi. Indicare i comandi realmente configurati, i prerequisiti e come riconoscere visivamente la modalità mock.

## Contratto API

Per ogni API simulata documentare endpoint, metodo, autenticazione, parametri, richiesta, risposta, codici di stato e formato degli errori. Allineare queste informazioni al backend o distinguere chiaramente un contratto ancora proposto.

## Scenari e ricette

Descrivere con parole semplici l'obiettivo, i passi e il risultato osservabile degli scenari esistenti: successo, dati vuoti, errori, attese e autenticazione, quando pertinenti. Non inventare controlli o pulsanti non implementati.

## Dati e limiti

Spiegare quali dati sono fittizi, dove sono conservati e come vengono ripristinati. Usare dati di prova; non riutilizzare segreti o dati personali di produzione.

## Manutenzione

Aggiornare contratto, implementazione mock, fixture, scenari, test e guide pertinenti nello stesso sviluppo quando cambiano le API. Mantenere distinto il funzionamento simulato da quello reale.
