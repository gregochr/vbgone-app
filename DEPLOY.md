# Deploying VBGone to the dockermacmini

VBGone follows the gregochr fleet convention: **tag a release → GitHub Actions
builds the images and pushes them to GHCR → the self-hosted runner on the Mini
pulls and restarts the stack.** State is in-memory, so there is no database.

```
./release.sh 1.1.0  ──tag v1.1.0──▶  .github/workflows/deploy.yml
   build-images (github-hosted) ──push──▶ ghcr.io/gregochr/vbgone-{frontend,backend}:{1.1.0,latest}
   deploy ([self-hosted, dockermacmini]) ──▶ cd ~/services/vbgone && docker compose pull && up -d
                                              ├── vbgone-frontend       :8086 → nginx :3000
                                              ├── vbgone-backend         (internal :8080)
                                              ├── vbgone-dotnet-runner   (.NET 10 SDK sidecar — C# builds)
                                              └── vbgone-jdk-maven-runner (Maven/JDK 21 sidecar — Java builds)
```

## Normal release flow

From the repo root on your MacBook, with `main` clean and pushed:

```bash
./release.sh 1.1.0
```

That tags `v1.1.0`, pushes it, and the `Deploy` workflow does the rest. Watch it
at https://github.com/gregochr/vbgone-app/actions. (`v1.0.0` already exists, so
the first redesign release is `1.1.0`.)

The script refuses to release from a dirty tree, off `main`, when `main` has
drifted from `origin/main`, or onto an existing tag (`FORCE_BRANCH=1` /
`FORCE_SYNC=1` to override).

## One-time setup on the Mini

The `deploy` job just `cd`s to a fixed dir and runs `docker compose` — it does
**not** copy the compose or secrets. Seed them once:

```bash
# self-hosted runner must be registered with the `dockermacmini` label
# (already true — wainwrights/goldenhour use it).

ssh dockermac 'mkdir -p ~/services/vbgone'
scp docker-compose.yml dockermac:~/services/vbgone/

ssh dockermac 'cat > ~/services/vbgone/.env' <<'EOF'
ANTHROPIC_API_KEY=sk-ant-...           # required
GITHUB_TOKEN=                           # optional — raising PRs (Step 6)
GITHUB_MODELS_TOKEN=                     # optional — the Copilot provider
EOF
```

> If you ever change `docker-compose.yml` in the repo, re-`scp` it to
> `~/services/vbgone/` — the deploy job runs the box's copy, not the repo's.

> **Java target — one-time per-box step.** The Java build path runs `mvn test`
> in a new `vbgone-jdk-maven-runner` sidecar (`maven:3.9-eclipse-temurin-21`).
> Because the deploy job only `docker compose pull && up -d`s the box's copy of
> the compose, the updated `docker-compose.yml` (new sidecar + maven base image)
> must be re-`scp`'d to `~/services/vbgone/docker-compose.yml` and pulled/started
> **before** the first Java release:
> ```bash
> scp docker-compose.yml dockermac:~/services/vbgone/
> ssh dockermac 'cd ~/services/vbgone && docker compose pull && docker compose up -d'
> ```
> Otherwise the backend's `docker exec vbgone-jdk-maven-runner …` will fail
> because the container won't exist on the box.

GHCR pull access: the new packages default to **private**; the Mini already runs
other `ghcr.io/gregochr/*` images so it's authenticated, but if a pull is denied,
make `vbgone-frontend`/`vbgone-backend` public or `docker login ghcr.io` once.

## Reaching it

LAN / Tailscale only for now (no public route):
- Tailscale: `http://100.76.73.16:8086`
- LAN: `http://192.168.0.102:8086`

## Local development

Build-from-source compose for your laptop (not used on the box):

```bash
docker compose -f docker-compose.dev.yml --env-file .env up --build
```

## Manual ops on the box

```bash
cd ~/services/vbgone
docker compose ps
docker compose logs -f backend     # "Started VbGoneApplication"
docker compose down                # stop (keeps the generated-code volume)
docker compose down -v             # also wipe the workspace volume
```

## Security — before any public exposure

The backend mounts `/var/run/docker.sock` (required by the ProcessBuilder build
flow), granting it **root over every container on the Mini**. It is therefore
LAN/Tailscale-only. Before adding a Cloudflare Tunnel hostname:

- Confirm **Bucket4j** rate-limiting on `/api/migrate/*` (every request spends
  Anthropic tokens).
- Put **Cloudflare Access** (Google/GitHub OAuth) in front (Phase 3).
- Longer term, isolate the build runner so a compromised backend can't reach the
  rest of the fleet.
