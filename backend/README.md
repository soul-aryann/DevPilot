# Backend configuration

The backend requires these environment variables at startup:

| Variable | Purpose |
| --- | --- |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth app client secret |
| `TOKEN_ENCRYPTOR_PASSWORD` | Password used to encrypt stored GitHub access tokens |
| `TOKEN_ENCRYPTOR_SALT` | Hex-encoded salt used by Spring's text encryptor |
| `GEMINI_API_KEY` | Gemini API key used for chat and embeddings |

Set these in the same shell that starts the backend, or in your deployment
secret manager. For a local development shell, generate encryption credentials
once and keep them stable:

```sh
export TOKEN_ENCRYPTOR_PASSWORD="$(openssl rand -base64 32)"
export TOKEN_ENCRYPTOR_SALT="$(openssl rand -hex 16)"
```

Then export `GITHUB_CLIENT_SECRET` and `GEMINI_API_KEY` in that shell before
starting the app. Embeddings use the Gemini Developer API key, so no Google
Cloud project ID or Vertex AI credentials are needed. Do not commit these
values. Save the generated encryption credentials somewhere secure and reuse
them on later runs: changing either value makes previously stored GitHub tokens
undecryptable. Because the former fallback values were committed, rotate the
GitHub OAuth client secret and replace the encryption credentials before
deploying this version.
