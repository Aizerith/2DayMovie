# Docker full stack

## Demarrage

```powershell
docker compose up --build
```

Applications disponibles :

- Frontend : http://localhost:4200
- Backend API : http://localhost:8080/api
- Swagger UI : http://localhost:8080/swagger-ui.html
- Mailpit : http://localhost:8025
- MinIO API : http://localhost:9000
- MinIO Console : http://localhost:9001

## Arret

```powershell
docker compose down
```

Pour supprimer aussi le volume Postgres :

```powershell
docker compose down -v
```

## Notes

- Le frontend Docker proxifie `/api` vers le service `backend`.
- Le backend tourne avec le profil `prod` dans Compose.
- Flyway applique automatiquement les migrations au demarrage.
- Le backend envoie aussi les emails vers `mailpit:1025`.
- Les emails restent consultables dans l app via la page admin `/dev-inbox`.
- Les uploads de la page `/files` passent directement du navigateur vers MinIO via des URLs presignees.
- Le backend utilise `STORAGE_ENDPOINT=http://minio:9000` en interne et signe des URLs publiques sur `http://localhost:9000`.
- Le bucket MinIO est cree automatiquement au premier upload si besoin.
