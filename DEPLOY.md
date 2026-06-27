# Deploying VBGone to the dockermacmini

VBGone follows the gregochr fleet convention: **GitHub Actions builds the images
and pushes them to GHCR; the Mini pulls and runs them** (it does not build from
source). State is in-memory, so there is no database to provision.

```
GitHub Actions ──build──▶ ghcr.io/gregochr/vbgone-frontend:latest
                          ghcr.io/gregochr/vbgone-backend:latest
                                      │ pull
                          dockermacmini ── docker compose up (docker-compose.prod.yml)
                                      ├── vbgone-frontend   :8086 → nginx :3000
                                      ├── vbgone-backend    (internal :8080)
                                      └── vbgone-dotnet-runner  (.NET 10 SDK sidecar)
```

## 1. Build & publish the images (CI)

The workflow [`.github/workflows/release-images.yml`](.github/workflows/release-images.yml)
builds + pushes both images on every push to `main` (and via **Run workflow** /
`workflow_dispatch`). After it succeeds, confirm the packages exist under
`github.com/gregochr?tab=packages`.

> First publish only: the new GHCR packages default to **private**. Either make
> `vbgone-frontend` and `vbgone-backend` **public** (Package → Settings →
> visibility), or ensure the Mini can pull them (step 2).

## 2. One-time: GHCR access on the Mini

The Mini already runs other `ghcr.io/gregochr/*` images, so it's likely already
authenticated. If a `pull` returns `denied`/`unauthorized`, log in once:

```bash
# On the Mini. PAT needs read:packages.
echo "$GHCR_PAT" | docker login ghcr.io -u gregochr --password-stdin
```

## 3. Drop the compose + env on the Mini

From your MacBook (repo root):

```bash
ssh dockermac 'mkdir -p ~/vbgone'
scp docker-compose.prod.yml dockermac:~/vbgone/
```

Then create `~/vbgone/.env` on the Mini (never commit it):

```bash
# ~/vbgone/.env
ANTHROPIC_API_KEY=sk-ant-...          # required
GITHUB_TOKEN=                          # optional — only for raising PRs (Step 6)
GITHUB_MODELS_TOKEN=                    # optional — only for the Copilot provider
```

## 4. Launch

```bash
cd ~/vbgone
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
docker compose -p vbgone ps
docker compose -p vbgone logs -f backend   # wait for "Started VbGoneApplication"
```

Reach it (LAN / Tailscale only — no public route yet):
- Tailscale: `http://100.76.73.16:8086`
- LAN: `http://192.168.0.102:8086`

> Port 8086 is currently free on the Mini. If it clashes later, change the
> `frontend` host port in `docker-compose.prod.yml`.

## 5. Update to a new build

```bash
cd ~/vbgone
docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

## 6. Stop / clean

```bash
docker compose -p vbgone down        # stop (keeps the generated-code volume)
docker compose -p vbgone down -v     # also wipe the workspace volume
```

## Security — before any public exposure

This stack mounts `/var/run/docker.sock` into the backend (required for the
ProcessBuilder build flow), which grants it **root over every container on the
Mini**. It is therefore bound to LAN/Tailscale only. Before adding a Cloudflare
Tunnel hostname:

- Confirm **Bucket4j** rate-limiting is active on `/api/migrate/*` (every request
  spends Anthropic tokens).
- Put **Cloudflare Access** (Google/GitHub OAuth) in front, per the Phase 3 plan.
- Longer term, isolate the build runner (rootless / dedicated build host) so a
  compromised backend can't reach the rest of the fleet.
