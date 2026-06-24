# Playwright E2E

Les tests Playwright valident l interface 2DayMovie avec des appels API et stockage mockes.
Ils n ont besoin que du serveur Angular.

## Variable utile

- `PLAYWRIGHT_BASE_URL`

## Commandes

```powershell
npm run e2e
```

```powershell
npm run e2e:ui
```

## Couverture

- creation d un salon prive avec upload mocke
- affichage du lien partage et du PIN genere
- ouverture d un salon avec PIN
- affichage des reglages de sous-titres
