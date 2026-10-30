# Backend configuration

The backend requires these environment variables at startup:

| Variable | Purpose |
| --- | --- |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth app client secret |
| `TOKEN_ENCRYPTOR_PASSWORD` | Password used to encrypt stored GitHub access tokens |
| `TOKEN_ENCRYPTOR_SALT` | Hex-encoded salt used by Spring's text encryptor |

Set these in your local environment or deployment secret manager. Do not commit
their values. Generate a unique, strong encryption password and salt per
deployment, and keep them stable: changing either value makes previously stored
GitHub tokens undecryptable. Because the former fallback values were committed,
rotate the GitHub OAuth client secret and replace the encryption credentials
before deploying this version.
