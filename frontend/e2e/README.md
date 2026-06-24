# Playwright E2E

## Prerequis

- backend demarre en profil `dev`
- frontend demarre sur `http://localhost:4200`

Par defaut, les tests utilisent :

- email : `admin@local.dev`
- mot de passe : `Admin123!`
- assigne par defaut : `Manager Local (MANAGER)`

Tu peux surcharger ces valeurs avec :

- `PLAYWRIGHT_BASE_URL`
- `PLAYWRIGHT_AUTH_EMAIL`
- `PLAYWRIGHT_AUTH_PASSWORD`
- `PLAYWRIGHT_ASSIGNEE_LABEL`

## Commandes

```powershell
npm run e2e
```

```powershell
npm run e2e:ui
```

## Couverture initiale

- redirection vers login sur route protegee
- connexion UI
- creation d un projet
- creation d une tache avec assignation

## Convention de stabilite

- privilegier `data-testid` pour les actions critiques et les champs de formulaire
- eviter de cibler les boutons par leur texte quand ce texte depend de la langue active
- garder les assertions texte pour le contenu metier cree pendant le test, par exemple un nom de projet ou de tache genere dynamiquement
