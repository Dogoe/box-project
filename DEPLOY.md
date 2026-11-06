# Publicar Baja Solar Supply (Render + Neon)

Guía para poner el sitio en línea en **bajasolarsupply.com**. Para probar todo es gratis:

| Pieza | Servicio | Plan |
|---|---|---|
| La app (Java) | [Render](https://render.com) | Free (se duerme tras 15 min sin visitas; la siguiente visita tarda ~1 min en despertar) |
| Base de datos | [Neon](https://neon.tech) Postgres | Free (0.5 GB; se puede subir de plan sin migrar) |
| Fotos | Cloudflare R2 (`box-app-images`) | Free (10 GB) |
| Dominio y DNS | Cloudflare | — |
| Correo | Brevo (envío) + Cloudflare Email Routing (recepción) | Free |

> **Nunca subas contraseñas o llaves a git.** Todas van como variables de entorno en Render
> (o en tu `.env` local, que git ignora). `.env.example` tiene la lista completa.

---

## 1. Base de datos en Neon

1. Crea una cuenta en neon.tech → **New project**.
   - Name: `baja-solar-supply`
   - Postgres version: la que sugiera (16 o 17)
   - Region: **AWS US West 2 (Oregon)** (la misma zona que Render, para que sea rápido)
2. En el proyecto: **Connect** (o *Connection string*).
   - Desactiva **Connection pooling** (usa el host *directo*, sin `-pooler` en el nombre).
   - Copia la cadena; se ve así:
     ```
     postgresql://neondb_owner:CONTRASEÑA@ep-algo-123456.us-west-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require
     ```
3. Sepárala en tres variables (las vas a pegar en Render):

   | Variable | Valor |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://ep-algo-123456.us-west-2.aws.neon.tech/neondb?sslmode=require` |
   | `DB_USERNAME` | `neondb_owner` |
   | `DB_PASSWORD` | la contraseña (lo que va entre `:` y `@`) |

   Fíjate: empieza con `jdbc:`, **no** lleva usuario ni contraseña, y solo deja `?sslmode=require`.

Las tablas se crean solas en el primer arranque. La base está vacía: los productos y kits los
das de alta desde el admin.

## 2. La app en Render

1. Crea una cuenta en render.com y conecta tu GitHub (repositorio `Dogoe/box-project`).
2. **New → Blueprint** → elige el repositorio. Render lee `render.yaml` y te muestra el servicio
   `baja-solar-supply` (Docker, plan Free, Oregon, rama `main`).
3. Te pide los valores marcados como secretos:

   | Variable | Qué poner |
   |---|---|
   | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Los del paso 1 |
   | `ADMIN_BOOTSTRAP_EMAIL` | El correo con el que entrarás al admin, p. ej. `admin@bajasolarsupply.com` |
   | `ADMIN_BOOTSTRAP_PASSWORD` | Una contraseña de **12+ caracteres** (guárdala en tu gestor de contraseñas) |
   | `R2_ENDPOINT` | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` (el mismo de tu `.env`) |
   | `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | Los de tu token de R2 (mismos de tu `.env`) |
   | `R2_PUBLIC_URL` | `https://pub-xxxx.r2.dev` por ahora |
   | `MAIL_USERNAME` | el *Login* de Brevo → SMTP & API → SMTP (`xxxx@smtp-brevo.com`) |
   | `MAIL_PASSWORD` | la SMTP key de Brevo |

   Ya vienen puestos: `SPRING_PROFILES_ACTIVE=prod`, `JWT_SECRET` (Render lo genera al azar),
   `R2_BUCKET=box-app-images` y el correo con Brevo (`MAIL_ENABLED=true`, `MAIL_HOST`,
   `MAIL_PORT`, `MAIL_FROM`). Para probar sin correos, cambia `MAIL_ENABLED` a `false`.
4. **Apply**. El primer build tarda unos 5–8 min. Cuando diga *Live*, abre
   `https://baja-solar-supply.onrender.com` (Render te muestra la dirección exacta).
5. Entra a `/admin` con el correo y contraseña del paso 3.

### Si no arranca

Revisa **Logs** en Render. La app se niega a arrancar con valores inseguros y dice cuál es:

- `Production configuration is not safe: - JWT_SECRET ...` / `ADMIN_BOOTSTRAP_PASSWORD ...` /
  `PUBLIC_BASE_URL ...` → corrige esa variable.
- `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${DB_URL}` → falta `DB_URL`.
- `Could not resolve placeholder 'X'` → falta la variable `X`.
- `password authentication failed` / `Connection refused` → revisa los datos de Neon
  (`DB_URL` debe empezar con `jdbc:postgresql://`).

### Importante sobre el admin

- La cuenta admin se crea **solo la primera vez** (si ese correo no existe). Cambiar después
  `ADMIN_BOOTSTRAP_PASSWORD` **no** cambia la contraseña del admin existente.
- `/auth/login` solo acepta cuentas admin; los clientes entran por **Mi cuenta**.

## 3. Conectar bajasolarsupply.com

1. Render → servicio → **Settings → Custom Domains → Add**: agrega `bajasolarsupply.com` y
   `www.bajasolarsupply.com`. Render te dice qué registros DNS crear.
2. Cloudflare → `bajasolarsupply.com` → **DNS → Records**: crea los que pidió Render
   (normalmente un `CNAME` de `www` y otro para la raíz `@` hacia `baja-solar-supply.onrender.com`).
   - Ponlos en **DNS only** (nube gris) hasta que Render marque el dominio como *Verified* y emita
     el certificado; después puedes dejarlos así.
3. Cuando abra con https, en Render → **Environment** agrega
   `PUBLIC_BASE_URL=https://bajasolarsupply.com` (se usa en los links de los correos). Guarda:
   Render vuelve a desplegar.

### Fotos con tu dominio (opcional)

Cloudflare → R2 → `box-app-images` → **Settings → Custom Domains → Connect domain**:
`imagenes.bajasolarsupply.com`. Luego cambia `R2_PUBLIC_URL=https://imagenes.bajasolarsupply.com`
en Render. (Las fotos ya subidas guardan la dirección vieja r2.dev, que sigue funcionando.)

## 4. Correo

Son dos cosas distintas:

- **Recibir** en `pedidos@bajasolarsupply.com` → **Cloudflare Email Routing** (reenvía a tu Gmail).
- **Enviar** desde la app (avisos de pedido, verificar cuenta, recuperar contraseña) → necesita un
  servidor SMTP. Cloudflare Email Routing **no envía**. Usa **Brevo** (300 correos/día gratis).

### 4a. Recibir (Cloudflare Email Routing)

Cloudflare → `bajasolarsupply.com` → **Email → Email Routing** → *Get started*:
1. *Custom address*: `pedidos` → *Destination*: tu Gmail. Cloudflare te manda un correo para
   verificar el destino.
2. Acepta que agregue los registros MX y SPF.
3. Repite para `admin@` si quieres.

### 4b. Enviar (Brevo)

1. Crea una cuenta en brevo.com.
2. **Senders, Domains & Dedicated IPs → Domains → Add a domain**: `bajasolarsupply.com`.
   Brevo te da registros (código `brevo-code`, DKIM y DMARC). Agrégalos en Cloudflare → DNS
   (o usa el botón de conexión automática con Cloudflare si te lo ofrece). Espera a que diga
   *Authenticated*.
   - Ya tienes un SPF de Cloudflare (`v=spf1 include:_spf.mx.cloudflare.net ~all`). Si Brevo
     pide SPF, **no crees un segundo** registro SPF: edita el existente, por ejemplo
     `v=spf1 include:_spf.mx.cloudflare.net include:spf.brevo.com ~all`.
3. **Senders → Add a sender**: `Baja Solar Supply <pedidos@bajasolarsupply.com>`.
4. **SMTP & API → SMTP → Generate a new SMTP key**.
5. Render ya trae `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` y `MAIL_ENABLED=true` (de `render.yaml`)
   y al crear el servicio te pide:

   | Variable | Valor |
   |---|---|
   | `MAIL_USERNAME` | el *SMTP login* que muestra Brevo (algo como `8a1b2c001@smtp-brevo.com`, no tu correo) |
   | `MAIL_PASSWORD` | la SMTP key |

6. En el admin, en *Correo para avisos de pedidos nuevos*, pon el correo que recibe los avisos
   (p. ej. `pedidos@bajasolarsupply.com`).

## 5. Lista de prueba

- [ ] `https://…/actuator/health` responde `{"status":"UP"}`
- [ ] La página de inicio abre (y `/` redirige a `/home`)
- [ ] Entras a `/admin`, das de alta un producto con foto (sube a R2) y un kit
- [ ] El kit aparece en `/kits`, se agrega al carrito y el pedido se crea
- [ ] El pedido aparece en el admin → Pedidos
- [ ] Creas una cuenta de cliente en **Mi cuenta**
- [ ] (Con correo activado) llega el aviso del pedido y el correo de verificación

## 6. Cuando ya no sea prueba

- Render **Starter** (~7 USD/mes): no se duerme, sin espera de 1 minuto.
- Neon **Launch**: más espacio y cómputo; mismo `DB_URL`, sin migrar.
- Neon guarda historial para restaurar (*Branches / Restore*); revisa cuántos días incluye tu plan.

## Desarrollo local (sin cambios)

`gradle bootRun` o IntelliJ siguen igual: sin perfil `prod` usa Postgres local `dbbox` y el admin
`admin@bajasolarsupply.com` / `admin123`. `docker compose up` también levanta la app con el puerto
de depuración 5005.
