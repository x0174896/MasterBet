# Ambiente locale del progetto

**Stato: MODELLO DA COMPILARE.** Non descrive ancora un ambiente verificato. Completare manualmente oppure autorizzare esplicitamente l'agente con «procedi con la configurazione della documentazione iniziale» prima delle altre attività operative.

Questo file è locale e deve essere escluso da Git. Non inserirvi password, token, chiavi API o altri segreti.

## Workstation

- Nome del progetto: DA COMPILARE.
- Sistema operativo e shell: DA VERIFICARE.
- Percorso assoluto del repository: DA COMPILARE.
- Fuso orario: DA COMPILARE.
- Branch di sviluppo: `develop`; branch di rilascio: `main` (verificarne l'esistenza).

## Strumenti verificati

- JDK / Java / JAVA_HOME: DA VERIFICARE, se previsto.
- Maven e wrapper: DA VERIFICARE, se previsti.
- Node.js e npm: DA VERIFICARE, se previsti.
- Database, client ed eventuali estensioni: DA VERIFICARE, se previsti.
- Altri strumenti necessari al progetto: DA DOCUMENTARE solo quando adottati.

## Servizi e isolamento

- Ambiente e database dedicati al progetto: DA COMPILARE.
- Schema e strumento di migrazione: DA COMPILARE, se previsti.
- Riferimento alla configurazione privata delle credenziali: DA COMPILARE (solo percorso o meccanismo, mai i valori).
- Porte locali previste e servizi preesistenti da conservare: DA VERIFICARE.
- Risorse e database di test utilizzabili: DA CONCORDARE.
- Eventuali particolarità di rete, certificati o proxy: DA DOCUMENTARE senza ridurre le verifiche di sicurezza.

## Manutenzione

- Registrare soltanto dati verificati su questa workstation.
- Non copiare configurazioni, autorizzazioni o dati infrastrutturali di altri progetti.
- Le autorizzazioni operative devono arrivare dall'utente nella conversazione; la compilazione di questo file non autorizza sviluppi o operazioni remote.
- Sostituire lo stato iniziale con «Ambiente verificato il ...» soltanto dopo i controlli effettivi.
