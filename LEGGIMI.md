# Istruzioni riutilizzabili per un nuovo progetto

Preparato il 18 ottobre 2026. Il pacchetto contiene regole generali e modelli vuoti: non include codice, dati, segreti, infrastruttura o funzionalità di StyleFinder.

## File da copiare nella radice del nuovo repository

| File | Scopo |
|---|---|
| `AGENTS.md` | Istruzioni principali: autorizzazione, Git, Java/Spring, Angular se previsto, test e documentazione. |
| `environment.md` | Modello locale da compilare per il PC e l'ambiente reali; da escludere da Git. |
| `PROJECT_FEATURES.md` | Inventario da inizializzare in base al codice realmente presente. |
| `setup.md` | Guida da compilare con versioni, configurazione e comandi verificati. |
| `CLAUDE.md` | Rinvio alle stesse regole per Claude Code, se utilizzato. |
| `MOCK_API_STUDIO.md` | Modello facoltativo, utile soltanto se il progetto prevede una modalità mock. |

Il nome del file principale è **`AGENTS.md`**, al plurale. È il documento che raccoglie le regole di sviluppo; gli altri file forniscono il contesto variabile del nuovo progetto. Questo `LEGGIMI.md` serve per la consegna e non è necessario copiarlo.

## Prima attivazione

1. Copiare i file utili nella radice del nuovo progetto senza sovrascrivere documenti esistenti senza verificarli.
2. Compilare `environment.md`, censire il progetto in `PROJECT_FEATURES.md` e completare `setup.md`, oppure autorizzare esplicitamente l'agente a farlo con il prompt seguente.
3. Escludere `environment.md` e i file di segreti dal versionamento. Gli altri documenti possono essere versionati nel nuovo progetto.
4. Prima dello sviluppo di una funzionalità, descriverla e autorizzarla esplicitamente con «procedi» o «procedere».

Prompt per la sola inizializzazione documentale:

> Leggi AGENTS.md e procedi esclusivamente con l'inizializzazione di environment.md, PROJECT_FEATURES.md e setup.md per questo progetto. Verifica lo stato reale e chiedimi i dati che non puoi ricavare. Non sviluppare funzionalità, non installare strumenti, non modificare database e non eseguire commit, push o merge.

## Autorizzazioni previste

- Un semplice «ok» non autorizza lo sviluppo: occorre «procedi» o «procedere», riferito al lavoro concordato.
- Dopo l'autorizzazione allo sviluppo, il flusso ordinario prevede verifiche, commit e push su `develop`, come nelle regole di origine. È possibile limitarlo scrivendo, per esempio, «procedi soltanto in locale, senza commit né push».
- Il merge e la push su `main` richiedono una richiesta esplicita separata, oppure possono essere inclusi esplicitamente nell'autorizzazione iniziale.
- «Dimmi se ti è chiaro» richiede soltanto una spiegazione e non autorizza alcuna operazione.

Le sezioni Angular e mock si applicano soltanto se questi componenti fanno parte dello stack concordato. Le versioni tecniche devono essere scelte e verificate per il nuovo progetto.
