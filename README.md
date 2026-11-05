# box-project

Baja Solar Supply — a Spring Boot (3.5, Java 17) backend for selling solar systems (panels, batteries, inverters, kits), with a quick-quote sizing wizard and a catalog admin panel.

## Running locally

There are two ways to run this, depending on what you're doing.

### 1. Full stack in Docker

Everything — the app plus Postgres and MailHog — runs containerized. Use this to test exactly what will be deployed, or when you don't have a local JDK/Postgres set up.

```bash
docker compose up --build
```

- App: http://localhost:8080
- MailHog UI: http://localhost:8025
- Remote debug port 5005 is exposed — attach IntelliJ's **AppFromDocker** run configuration (Run/Debug → AppFromDocker) to step through code running inside the container.

Rebuild (`--build`) whenever you change source code, since the jar is built inside the image.

### 2. App natively in IntelliJ, infra in Docker

For day-to-day development, running the app directly in IntelliJ is much faster than rebuilding a Docker image on every change — you get real breakpoints, instant restarts, and (with devtools, see below) automatic reload on recompile.

Start only the supporting services:

```bash
docker compose up -d postgres mailhog
```

Then run the **DemoBoxApplication** run configuration in IntelliJ (Run/Debug → DemoBoxApplication) as-is — no profile or environment variables needed. `application.yml`'s defaults already point at `localhost:5432` (Postgres) and `localhost:1025` (MailHog), which is exactly what those containers publish to your host.

**Faster reload:** this project includes `spring-boot-devtools` as a `developmentOnly` dependency, which auto-restarts the app whenever IntelliJ recompiles a changed class (it's excluded from the Docker image's jar, so this never affects anything deployed). For it to trigger automatically, make sure IntelliJ has:
- Settings → Build, Execution, Deployment → Compiler → **Build project automatically**
- Registry (`Ctrl+Shift+A` → "Registry…") → **compiler.automake.allow.when.app.running** enabled

Without those, you can still trigger a reload manually with Build → Recompile.

**Don't run both at once** — the containerized `app` service and IntelliJ's native run both bind port 8080. Stop `docker compose`'s `app` service (or just don't start it) while running natively, and vice versa. The `postgres`/`mailhog` containers are safe to leave running either way.

## Product photos (Cloudflare R2)

Photos uploaded from the admin ("Subir foto" next to any image field) are resized to at most 1600 px, saved as JPEG and stored in a Cloudflare R2 bucket (S3-compatible); the product/kit keeps the photo's public URL. Without R2 configured the button just reports it, and image links can still be pasted by hand.

Configure it with these variables — in production as real environment variables, and locally in a `.env` file at the project root (copy `.env.example`; `.env` is git-ignored). Both the IntelliJ run and `docker compose` read that `.env`:

| Variable | Value |
|---|---|
| `R2_ENDPOINT` | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` (R2 → bucket → Settings → S3 API, without the bucket name) |
| `R2_BUCKET` | `box-app-images` |
| `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` | from an R2 API token with *Object Read & Write* on that bucket — **never commit these** |
| `R2_PUBLIC_URL` | the bucket's public URL: the `https://pub-….r2.dev` development URL for now, a custom domain later |

Changing `R2_PUBLIC_URL` later (e.g. to your own domain) only affects new uploads; photos saved before keep the URL they were stored with.

## Default accounts

- A default admin account for the catalog admin panel (`/admin`) is seeded on startup if it doesn't already exist: `admin@bajasolarsupply.com` / `admin123` (see `admin.bootstrap.*` in `application.yml`). **Override `ADMIN_BOOTSTRAP_EMAIL`/`ADMIN_BOOTSTRAP_PASSWORD` before deploying anywhere this default would be a real risk.**
