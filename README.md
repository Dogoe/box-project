# box-project

SolarTech — a Spring Boot (3.5, Java 17) backend for selling solar systems (panels, batteries, inverters, kits), with a quick-quote sizing wizard and a catalog admin panel.

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

## Default accounts

- A default admin account for the catalog admin panel (`/admin`) is seeded on startup if it doesn't already exist: `admin@solartech.com` / `admin123` (see `admin.bootstrap.*` in `application.yml`). **Override `ADMIN_BOOTSTRAP_EMAIL`/`ADMIN_BOOTSTRAP_PASSWORD` before deploying anywhere this default would be a real risk.**
