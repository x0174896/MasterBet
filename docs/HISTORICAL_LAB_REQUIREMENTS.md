# Laboratorio multicampionato — requisiti autorizzati

Autorizzazione del 19 ottobre 2026: sviluppare tutti i requisiti concordati, verificare,
pubblicare su develop e integrare su main. Nessuna operazione di produzione.

Questo documento tiene traccia dell'ambito; PROJECT_FEATURES.md descrive solo ciò che
è effettivamente implementato e verificato.

1. Censire tutti i campionati comuni Calcio.com/OddsPapi, con copertura verificabile.
2. Definire preparazione, prima scommessa e fine in base ai dati, riferimento 2026.
3. Importare HT/FT e calendario da Calcio; SH=FT−HT, rinvii identificati dalla data
   effettiva e dalla stessa identità, nessun punteggio mancante trasformato in zero.
4. Riconciliare eventi e squadre senza abbinamenti ambigui.
5. Almeno SETTE incontri precedenti conclusi di campionato per entrambe le squadre,
   nella stessa stagione. Coppe escluse dal conteggio.
6. Riaddestramento globale cronologico a ogni finestra settimanale, statistiche per
   campionato/stagione, due modalità confrontabili.
7. Risultati futuri esclusi da input, normalizzazione e pattern. Deroga concordata
   SOLO alle quote: ultimo aggiornamento prepartita, anche successivo al taglio
   della settimana. Regola fissa prima degli esiti; sospensioni non aggirate.
8. Una singola migliore per ogni incontro idoneo, nessuna multipla o limite cinque.
   Breve descrizione deterministica facoltativa, basata solo sui dati consentiti.
9. Unità=utile netto obiettivo, puntata=unità/(quota−1); bilanci separati per modalità.
10. Pagine/tab separati: scommesse, andamento, campionati/statistiche, modello/pattern,
    admin. Checkbox include SOLO i gol/risultati di coppa nelle statistiche. Entrambe
    le modalità includono calendario, riposo, frequenza e trasferte delle coppe.
11. Backup e azzeramento delle vecchie schedine e conteggi derivati; sorgenti conservate.
12. Test automatici backend/frontend, anti-leakage, quote, rinvii, checkbox e grafico.
13. Admin riservata: catalogo/copertura, due downloader indipendenti e calcolo separato,
    avanzamento persistente, errori, ripresa e configurazione del periodo/bookmaker.
14. DB storico riutilizzabile, originali con hash, versioni e riferimenti delle proposte.
15. Consultazione SOLO dalla prima proposta fino alla fine; esiti futuri con occhio
    spoiler, senza aggiornare saldo/statistiche. Cambio data e checkbox senza ricalcolo.
16. Risultati definitivi acquisiti una volta salvo correzioni; quote aggiornabili più
    spesso. Calcolo offline, nessun download implicito.
17. Ricerca automatica pattern su dati precedenti; supporto, frequenza/base, verifica
    cronologica fuori campione; nessun pattern futuro retroattivo.
18. Testing storico distinto dal funzionamento operativo, che non usa il rewind.
19. Campagna completa: download, preparazione, tutte le settimane fino alla copertura
    finale, entrambe le modalità, proposte persistite e nessuna scelta a posteriori.
20. Grafico del NETTO da ZERO: un punto per singola conclusa, anche simultanea; ordine
    stabile, valori negativi, tooltip, filtro al tempo selezionato, spoiler ininfluente.
21. Stessa stagione per tutti i paesi: 2025 vale per 2025/26, non per un annuale 2026.
    Coppe archiviate separatamente; dati incompleti segnalati, mai inventati.

## Sequenza di implementazione

- [x] Letture preliminari; develop pulito e pull completato.
- [x] Verifica pubblica Calcio: catalogo RSC con stagioni, schedule-rounds e schedule
      con roundId, /api/fixture/<id> con HT/FT e starting_at aggiornato.
- [x] Archivio normalizzato, migrazioni e revisioni con hash.
- [x] Catalogo e downloader indipendenti con ripresa e limiti.
- [x] Modello, pattern per campionato/stagione, singole e simulazione offline a due modalità.
- [x] API, accesso amministrativo e consultazione con spoiler.
- [x] Frontend separato in cinque pagine e grafico del netto.
- [x] Test automatici, collegamenti reali alle fonti, backup verificato e reset legacy.
- [ ] Campagna massiva reale conclusa: download ancora in corso, calcolo automatico successivo.
- [x] Documentazione e controlli di rilascio; esito di commit, push develop, merge e push main riportato nella consegna.
