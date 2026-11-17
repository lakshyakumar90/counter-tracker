# Secrets and Source Control

- Release signing values are read from `PACE_RELEASE_*` environment variables as documented in [`build-and-signing.md`](../build-and-signing.md). A partial signing configuration fails; with no values, release artifacts are unsigned.
- `.gitignore` excludes Gradle/build output, local SDK configuration, `.env*`, keystores, and common private-key formats. Keep any local `.env.example` free of real values.
- GitHub Actions runs Gitleaks over repository history on push and pull request. The Gitleaks action does not require a license for a personal user account; an organization-owned repository must configure its `GITLEAKS_LICENSE` repository/organization secret.
- Before a commit, inspect `git status --short` and the staged diff. Never include passwords, access tokens, service-role keys, keystores, recovery codes, raw location tracks, or Play Console identity materials.
- If a secret is committed, revoke/rotate it immediately and remove it from repository history; deleting the visible file alone does not invalidate the credential.
