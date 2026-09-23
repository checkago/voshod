# ВОСХОД

Jmix 3 portal for payment requests. Locales: `ru`, `en`. Default development login: `admin` / `admin`.

## Run locally

Java 21. From the project root:

```bash
./gradlew bootRun
```

The UI is at <http://localhost:8080>.

Copy `application-local.properties` next to `build.gradle` (the file is gitignored) and put 1C OData settings there. See `.env.example` for the same keys used in Docker.

**Do not commit** `application-local.properties` or `.env`. Remove `ui.login.defaultUsername` and `ui.login.defaultPassword` before a public production deploy.

## Docker

```bash
cp .env.example .env
# fill ONEC_WEB_* in .env
docker compose up --build
```

The image is a production Vaadin JAR. HSQLDB and file storage live in the `voshod-data` volume.

## Jmix

- [Documentation](https://docs.jmix.io)
- [Setup](https://docs.jmix.io/jmix/setup.html)
