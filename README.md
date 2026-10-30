# DevPilot

DevPilot connects GitHub repositories to a private code assistant. It indexes source code with Gemini embeddings in PostgreSQL/pgvector, then streams retrieval-grounded answers with file citations through a Next.js interface.

## What it does

- Sign in with GitHub OAuth and sync repositories available to the account.
- Index eligible source files, store embeddings in PostgreSQL with pgvector, and track indexing status.
- Ask questions about an indexed repository and receive streamed answers with source citations.
- Keep repository data, chat sessions, and messages in PostgreSQL; encrypt stored GitHub access tokens.

## Architecture

```text
Next.js client ── session-authenticated REST + SSE ── Spring Boot API
                                                       ├── GitHub OAuth/API
                                                       ├── Gemini chat + embeddings
                                                       └── PostgreSQL + pgvector
```

The frontend lives in `client/`; the Spring Boot service lives in `backend/`. Docker Compose at the repository root starts the PostgreSQL/pgvector service for local development.

## Requirements

- Java 21 or newer
- Node.js and npm
- Docker with Docker Compose
- A GitHub OAuth app
- A Gemini API key for indexing and AI chat

## Local setup

### 1. Start PostgreSQL with pgvector

From the repository root:

```bash
docker compose up -d postgres
```

The database is exposed on `localhost:5433`. The local Compose configuration uses the development database `devpilot` with user `postgres` and password `postgres`; do not reuse these credentials in a deployed environment.

### 2. Configure and start the backend

Set the following environment variables in the shell where the backend runs:

| Variable | Required | Purpose |
| --- | --- | --- |
| `GITHUB_CLIENT_SECRET` | Yes | GitHub OAuth app secret. The client ID defaults to the development app ID; override it with `GITHUB_CLIENT_ID` when needed. |
| `TOKEN_ENCRYPTOR_PASSWORD` | Yes | Strong secret used to encrypt stored GitHub access tokens. |
| `TOKEN_ENCRYPTOR_SALT` | Yes | Hex-encoded salt for Spring's text encryptor. |
| `GEMINI_API_KEY` | For AI features | Gemini API key used for chat and embeddings. |
| `GOOGLE_CLOUD_PROJECT` | If required by your Google setup | Google Cloud project for embeddings. |
| `FRONTEND_URL` | No | Frontend origin; defaults to `http://localhost:3000`. |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated allowed frontend origins; defaults to `http://localhost:3000`. |

Keep the token-encryption password and salt stable for the lifetime of the stored tokens. Changing them makes existing encrypted GitHub tokens unreadable. Never commit secret values. For production, also configure the database through `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`.

Run the backend:

```bash
cd backend
./mvnw spring-boot:run
```

The GitHub OAuth callback URL for local development is:

```text
http://localhost:8080/login/oauth2/code/github
```

### 3. Start the frontend

In a second terminal:

```bash
cd client
npm ci
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). The API URL defaults to `http://localhost:8080`; set `NEXT_PUBLIC_API_BASE_URL` if the backend uses a different address.

## Verification

From `client/`:

```bash
npm run lint
npm run build
```

From `backend/`:

```bash
./mvnw test
```

Backend tests that load the application context require PostgreSQL to be running and reachable using the configured datasource settings.

## Security and operations

- Configure OAuth, encryption, Gemini, and database credentials through environment variables or a secret manager.
- Use unique, strong token-encryption credentials per deployment and back them up securely.
- Keep `CORS_ALLOWED_ORIGINS` limited to the deployed frontend origins.
- Indexing is limited to 2,000 eligible files and 50 MiB of eligible source per repository; individual files are limited to 100 KiB.
- Chat message content is limited to 8,000 characters.

## Current limitations

- GitHub repository sync and indexing depend on the authenticated user's GitHub access and API limits.
- AI chat and indexing require a working Gemini configuration.
- The app currently uses Hibernate schema auto-update for local convenience; use a managed schema-migration process for production.
