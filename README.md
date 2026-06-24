# Boilerplate Spring Boot + Angular

[![CI](https://github.com/Aizerith/BoilerPlate-Spring-Angular/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Aizerith/BoilerPlate-Spring-Angular/actions/workflows/ci.yml)

Boilerplate full stack avec auth JWT, roles, Flyway, PostgreSQL, UI Angular moderne, Docker et tests automatises.

## Stack
- Spring Boot 4
- Angular 21
- PostgreSQL 16
- MinIO (stockage objet prive)
- Flyway
- Spring Security + JWT
- Tailwind CSS + DaisyUI
- Vitest
- Playwright
- Docker Compose
- Swagger / OpenAPI

## Fonctionnalites deja en place
- auth JWT complete: login, refresh, logout, `me`
- roles `ADMIN`, `MANAGER`, `USER`
- CRUD `users` cote admin
- CRUD metier `projects`
- CRUD metier `tasks` avec assignation
- gestion de fichiers avec URLs presignees MinIO
- permissions metier simples par role
- migrations Flyway versionnees
- Swagger UI
- tests backend, frontend et E2E
- lancement full stack via Docker
- forgot password + mails de dev

## Structure
- [backend](C:\Users\skyry\IdeaProjects\BoilerPlate-Spring-Angular\backend) : API Spring Boot
- [frontend](C:\Users\skyry\IdeaProjects\BoilerPlate-Spring-Angular\frontend) : application Angular
- [docker-compose.yml](C:\Users\skyry\IdeaProjects\BoilerPlate-Spring-Angular\docker-compose.yml) : lancement full stack
- [DOCKER.md](C:\Users\skyry\IdeaProjects\BoilerPlate-Spring-Angular\DOCKER.md) : guide Docker
- [I18N.md](C:\Users\skyry\IdeaProjects\BoilerPlate-Spring-Angular\I18N.md) : guide de traduction frontend

## Comptes de demo
En profil `dev`, les comptes suivants sont seeds automatiquement :

- `admin@local.dev` / `Admin123!`
- `manager@local.dev` / `Manager123!`

## Prerequis
- Java 21 pour le backend
- Node.js 22.12+ ou 24.x pour le frontend Angular 21
- Docker Desktop si tu veux lancer PostgreSQL, Mailpit ou toute la stack en conteneurs

## Lancer en local

### 1. Base PostgreSQL
Le plus simple :

```powershell
docker compose up postgres
```

Si tu veux aussi les emails locaux dans Mailpit :

```powershell
docker compose up postgres mailpit
```

Si tu veux aussi le stockage objet pour les uploads directs :

```powershell
docker compose up postgres mailpit minio
```

### 2. Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Le backend demarre sur :

- API : [http://localhost:8080/api](http://localhost:8080/api)
- Swagger UI : [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

Le module fichiers attend aussi MinIO sur :

- API objet : [http://localhost:9000](http://localhost:9000)
- Console MinIO : [http://localhost:9001](http://localhost:9001)

### 3. Frontend

```powershell
cd frontend
npm ci
npm start
```

Le frontend demarre sur :

- [http://localhost:4200](http://localhost:4200)

## Lancer avec Docker

```powershell
docker compose up --build
```

Services exposes :

- frontend : [http://localhost:4200](http://localhost:4200)
- backend : [http://localhost:8080/api](http://localhost:8080/api)
- swagger : [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- mailpit : [http://localhost:8025](http://localhost:8025)
- minio api : [http://localhost:9000](http://localhost:9000)
- minio console : [http://localhost:9001](http://localhost:9001)

Pour arreter :

```powershell
docker compose down
```

## Tests

### Backend

```powershell
cd backend
.\mvnw.cmd test
```

### Frontend

```powershell
cd frontend
npm test -- --watch=false
```

### Playwright E2E
Prerequis :
- backend en profil `dev`
- frontend sur `http://localhost:4200`

```powershell
cd frontend
npm run e2e
```

Autres commandes utiles :

```powershell
npm run e2e:ui
npm run e2e:headed
```

## Swagger / OpenAPI

- UI : [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- spec JSON : [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

## CI
Une pipeline GitHub Actions est fournie dans [.github/workflows/ci.yml](C:\Users\skyry\IdeaProjects\BoilerPlate-Spring-Angular\.github\workflows\ci.yml).

Badge de statut :
- [CI GitHub Actions](https://github.com/Aizerith/BoilerPlate-Spring-Angular/actions/workflows/ci.yml)

Elle execute :
- tests backend
- tests frontend
- tests E2E Playwright

## Notes utiles
- le frontend en dev appelle le backend sur `http://localhost:8080/api`
- le frontend Docker appelle `/api` via un proxy Nginx
- la page `/files` utilise un bucket MinIO prive et des URLs PUT/GET presignees
- le backend parle a MinIO via `STORAGE_ENDPOINT`, tandis que les URLs signees exposent `STORAGE_PUBLIC_ENDPOINT`
- Flyway applique automatiquement les migrations au demarrage du backend
- le frontend supporte actuellement `fr` et `en` via Transloco
- les emails de dev sont visibles soit dans l app via `/dev-inbox`, soit dans Mailpit sur `http://localhost:8025`
- evite Node 18: Angular 21 ne s execute pas correctement avec cette version

## Checklist production
- Remplacer tous les secrets par des variables d environnement ou un gestionnaire de secrets.
- Utiliser un `JWT_SECRET` long, aleatoire et different par environnement.
- Garder `spring.jpa.open-in-view=false` et couvrir les relations lazy par quelques tests d integration cibles.
- Activer une origine CORS stricte via `CORS_ALLOWED_ORIGIN`.
- Configurer `RATE_LIMIT_MAX_REQUESTS` et `RATE_LIMIT_WINDOW_SECONDS` pour les endpoints d auth publics.
- Surveiller le header `X-Request-Id` dans les logs pour correlier frontend, backend et proxy.
- Brancher `/api/health` au healthcheck de l orchestrateur ou du reverse proxy.
- Prevoir un stockage de broker partage si les WebSockets doivent fonctionner sur plusieurs instances backend.
