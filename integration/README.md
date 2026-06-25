# WSO2 Financial Services Accelerator — Integration Layer

## Descripción general

Esta carpeta contiene la infraestructura de contenedores para desplegar **todos los servicios** del WSO2 Financial Services Accelerator (v4.1.2) de forma orquestada con Docker Compose.

---

## Estructura

```
integration/
├── docker/
│   ├── fs-apim/                     # WSO2 API Manager + FS APIM Accelerator
│   │   └── Dockerfile
│   ├── fs-is/                       # WSO2 Identity Server + FS IS Accelerator
│   │   └── Dockerfile
│   ├── consent-mgt-endpoint/        # REST API – Gestión de Consentimientos
│   │   └── Dockerfile
│   ├── event-notifications-endpoint/ # REST API – Notificaciones de Eventos
│   │   └── Dockerfile
│   ├── authentication-endpoint/     # Endpoint de Autenticación (web app)
│   │   └── Dockerfile
│   ├── demo-backend/                # Mock Bank Backend APIs
│   │   └── Dockerfile
│   └── self-care-portal/            # React SPA – Portal de autogestión
│       ├── Dockerfile
│       └── nginx.conf
├── init-scripts/
│   └── 01-create-databases.sql      # Creación de bases de datos MySQL
├── docker-compose.yml               # Orquestación completa
├── .env.example                     # Variables de entorno (plantilla)
└── README.md                        # Esta documentación
```

---

## Aplicaciones incluidas

| Servicio | Imagen base | Puerto(s) expuesto(s) | Descripción |
|---|---|---|---|
| `mysql` | `mysql:8.0` | `3306` | Base de datos compartida |
| `fs-is` | `wso2/wso2is:7.1.0` | `9443`, `9763` | Identity Server con acelerador FS IS |
| `fs-apim` | `wso2/wso2am:4.3.0` | `9444`, `8243`, `8280` | API Manager con acelerador FS APIM |
| `consent-mgt-endpoint` | `tomcat:9.0-jre11` | `8081` | API REST de gestión de consentimientos |
| `event-notifications-endpoint` | `tomcat:9.0-jre11` | `8082` | API REST de notificaciones de eventos |
| `authentication-endpoint` | `tomcat:9.0-jre11` | `8083` | Endpoint de autenticación (UI) |
| `demo-backend` | `tomcat:9.0-jre11` | `9090` | Mock APIs del banco demo |
| `self-care-portal` | `nginx:1.27-alpine` | `3000` | Portal React de autogestión de consentimientos |

---

## Prerrequisitos

| Herramienta | Versión mínima |
|---|---|
| Docker | 24.x |
| Docker Compose | v2.x (`docker compose` sin guion) |
| Java / Maven | Solo necesario si se construyen las imágenes localmente (ver más abajo) |

> **Acceso a imágenes WSO2:** Las imágenes `wso2/wso2am` y `wso2/wso2is` se distribuyen a través de [WSO2 Docker Hub](https://hub.docker.com/u/wso2). Para versiones con soporte oficial puede ser necesario una cuenta en https://wso2.com.

---

## Configuración inicial

### 1. Copiar y ajustar variables de entorno

```bash
cp integration/.env.example integration/.env
# Editar integration/.env con contraseñas y hostnames reales
```

> ⚠️ **Nunca** commitear el archivo `.env` con credenciales reales. Ya está incluido en `.gitignore`.

### 2. Variables clave

| Variable | Valor por defecto | Descripción |
|---|---|---|
| `DB_USER` | `wso2user` | Usuario MySQL del acelerador |
| `DB_PASS` | `wso2password` | Contraseña MySQL (**cambiar**) |
| `WSO2_ADMIN_PASS` | `admin` | Contraseña del admin de WSO2 (**cambiar**) |
| `PORTAL_PORT` | `3000` | Puerto local para el Self-Care Portal |
| `DEMO_BACKEND_PORT` | `9090` | Puerto local para el Demo Backend |

---

## Construcción de imágenes

Las imágenes se construyen con **multi-stage builds** directamente desde el código fuente del repositorio. El contexto de build es la **raíz del repositorio**.

```bash
# Desde la raíz del repositorio
docker compose -f integration/docker-compose.yml build
```

Para construir una sola imagen:

```bash
docker compose -f integration/docker-compose.yml build fs-is
docker compose -f integration/docker-compose.yml build self-care-portal
```

---

## Despliegue completo

```bash
# Iniciar todos los servicios en segundo plano
docker compose -f integration/docker-compose.yml --env-file integration/.env up -d

# Ver logs en tiempo real
docker compose -f integration/docker-compose.yml logs -f

# Detener y eliminar contenedores (conserva volúmenes)
docker compose -f integration/docker-compose.yml down

# Detener y eliminar también volúmenes (⚠️ borra datos)
docker compose -f integration/docker-compose.yml down -v
```

---

## Orden de arranque y dependencias

```
mysql ──► fs-is ──► fs-apim
                └──► authentication-endpoint
  └──────────────► consent-mgt-endpoint
  └──────────────► event-notifications-endpoint
                   demo-backend (sin dependencias)
       fs-is ──► self-care-portal
```

Los healthchecks de MySQL e IS controlan que los servicios dependientes no arranquen hasta que los anteriores estén listos.

---

## URLs de acceso (configuración por defecto)

| Servicio | URL |
|---|---|
| IS – Management Console | https://localhost:9443/carbon |
| IS – OIDC Discovery | https://localhost:9443/oauth2/token/.well-known/openid-configuration |
| APIM – Publisher Portal | https://localhost:9444/publisher |
| APIM – Developer Portal | https://localhost:9444/devportal |
| APIM – Admin Portal | https://localhost:9444/admin |
| APIM – Gateway HTTP | http://localhost:8280 |
| APIM – Gateway HTTPS | https://localhost:8243 |
| Consent Mgt Endpoint | http://localhost:8081/consent/ |
| Event Notifications | http://localhost:8082/event-notifications/ |
| Auth Endpoint | http://localhost:8083/authenticationendpoint/ |
| Demo Backend | http://localhost:9090/demo-backend/ |
| Self-Care Portal | http://localhost:3000 |

---

## Descripción de cada Dockerfile

### `docker/fs-apim/Dockerfile`
**Multi-stage build:**
1. **Stage `builder`** — usa `maven:3.9.6-eclipse-temurin-11` para compilar el módulo `fs-apim` junto con todos los componentes Java del acelerador.
2. **Stage runtime** — parte de `wso2/wso2am:4.3.0`, extrae el ZIP del acelerador construido y ejecuta `merge.sh` para superponer los artefactos sobre el servidor APIM.

### `docker/fs-is/Dockerfile`
**Multi-stage build:**
1. **Stage `builder`** — compila el módulo `fs-is` y todos los `internal-webapps` (consent, event-notifications, authentication, demo-backend).
2. **Stage runtime** — parte de `wso2/wso2is:7.1.0`, copia los WARs al directorio de despliegue del servidor IS y ejecuta `merge.sh`.

### `docker/consent-mgt-endpoint/Dockerfile`
Construye y despliega únicamente el WAR `org.wso2.financial.services.accelerator.consent.mgt.endpoint` en un contenedor **Tomcat 9** independiente. Útil para escalar el API de consentimientos de forma autónoma.

### `docker/event-notifications-endpoint/Dockerfile`
Construye y despliega el WAR `org.wso2.financial.services.accelerator.event.notifications.endpoint` en **Tomcat 9**. Permite despliegue independiente del sistema de notificaciones de eventos de Open Banking.

### `docker/authentication-endpoint/Dockerfile`
Construye y despliega el WAR `org.wso2.financial.services.accelerator.authentication.endpoint` en **Tomcat 9**. Contiene los flujos personalizados de autenticación para los flujos OAuth2/OIDC del acelerador.

### `docker/demo-backend/Dockerfile`
Construye y despliega el WAR `org.wso2.financial.services.accelerator.demo.backend` en **Tomcat 9**. Proporciona APIs mock del banco para pruebas de extremo a extremo sin necesidad de un core bancario real.

### `docker/self-care-portal/Dockerfile`
**Multi-stage build:**
1. **Stage `builder`** — usa `node:18-alpine` para instalar dependencias npm y ejecutar `npm run build` generando la carpeta `build/` de producción.
2. **Stage runtime** — copia el bundle estático en un contenedor **Nginx 1.27-alpine** ultra-ligero. El archivo `nginx.conf` configura el servidor HTTP con proxy hacia el IS para las llamadas a la API de consentimientos.

---

## Bases de datos

El script `init-scripts/01-create-databases.sql` crea automáticamente las siguientes bases de datos al primer arranque del contenedor MySQL:

| Base de datos | Uso |
|---|---|
| `apimgtdb` | API Manager – gestión de APIs |
| `am_configdb` | API Manager – configuración |
| `userdb` | User Store compartido |
| `identitydb` | Identity Server – datos de identidad |
| `fs_consentdb` | Acelerador FS – consentimientos |
| `fs_eventsdb` | Acelerador FS – notificaciones de eventos |

---

## Solución de problemas comunes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `fs-is` no arranca | MySQL no está listo | Verificar healthcheck: `docker inspect fs-mysql` |
| `Filename too long` en git | Rutas largas en Windows | `git config core.longpaths true` |
| Error `wso2/wso2am` not found | Imagen requiere login | `docker login` con cuenta WSO2 |
| Puerto `9443` ocupado | IS y APIM usan el mismo puerto interno | APIM se mapea a `9444` externamente |
| React build falla | Dependencias incompatibles | Asegurar `node:18` y usar `--legacy-peer-deps` |
| Self-Care Portal en blanco | La URL del IS no es accesible | Verificar la variable `IS_HOST` en `.env` |

---

## Notas de seguridad

- Cambiar **siempre** `DB_PASS` y `WSO2_ADMIN_PASS` antes de cualquier despliegue en entornos no locales.
- Las comunicaciones entre servicios ocurren dentro de la red privada Docker `fs-network`. Solo los puertos necesarios se exponen al host.
- Los certificados SSL de WSO2 son auto-firmados por defecto. Para producción, reemplazarlos con certificados válidos montándolos como volúmenes en los keystores del servidor.
- No incluir el archivo `.env` en el control de versiones.

---

## Referencia oficial

- [WSO2 Financial Services Accelerator Docs](https://ob.docs.wso2.com/)
- [WSO2 API Manager 4.3.0](https://apim.docs.wso2.com/en/4.3.0/)
- [WSO2 Identity Server 7.1.0](https://is.docs.wso2.com/en/7.1.0/)
- [WSO2 Docker Hub](https://hub.docker.com/u/wso2)
