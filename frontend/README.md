# Interfaccia MasterBet

Angular 22.2.3, componenti standalone, signals, TypeScript strict e Bootstrap 5.3.8 con tema SCSS. Interfaccia in italiano, orari visualizzati in UTC.

```powershell
npm ci
npm start
npm run build
npm test -- --watch=false
```

`npm start` ascolta su `127.0.0.1:4201` e inoltra `/api/**` al backend locale sulla porta 8081. Il backend e lo scenario importato sono necessari. Non c'è una modalità mock applicativa.

`scenario-api` gestisce HTTP; `scenario-state` caricamenti, errori e annullamento delle richieste precedenti; `quote-table`, `statistics-panel` e `model-panel` presentano dati tipizzati. I mercati non modellati restano consultabili senza probabilità inventate. Soglie, bookmaker e timestamp sono sempre distinti dal valore stimato dal modello.

La guida completa è in [setup.md](../setup.md). Il contenuto di `dist/` è generato e ignorato. Non inserire la chiave OddsPapi nel frontend.
