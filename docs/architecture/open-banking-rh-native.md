# Propuesta: Open Banking Accelerator — Red Hat Native

> **Estado:** Propuesta arquitectónica  
> **Versión:** 1.0.0  
> **Fecha:** 2026-06-25  
> **Autor:** Juan Pablo Garzon  

---

## Resumen ejecutivo

Esta propuesta describe una arquitectura **100% Red Hat** para Open Banking que mantiene
**compatibilidad de API** con el WSO2 Financial Services Accelerator pero elimina toda
dependencia de WSO2 (Carbon, OSGi, APIM, IS).

Los componentes WSO2 son reemplazados por Quarkus microservices, Keycloak y 3scale,
todos productos del ecosistema Red Hat/open-source.

---

## 1. Tabla de equivalencias WSO2 → Red Hat

| Componente WSO2 | Tecnología | Reemplazo Red Hat | Tecnología |
|---|---|---|---|
| WSO2 API Manager | Java/Carbon/OSGi WAR | **Red Hat 3scale** | Operador OCP |
| WSO2 Identity Server | Java/Carbon/OSGi WAR | **Red Hat SSO (Keycloak)** | Operador OCP |
| `consent.mgt.endpoint` | JAX-RS (CXF) + Carbon | **ob-consent-service** | Quarkus 3.8 |
| `consent.mgt.service` | OSGi bundle | Integrado en ob-consent-service | Quarkus CDI |
| `consent.mgt.dao` | OSGi bundle + JDBC manual | Integrado en ob-consent-service | Hibernate Panache |
| `event.notifications.endpoint` | JAX-RS (CXF) + Carbon | **ob-event-notifications** | Quarkus 3.8 |
| `event.notifications.service` | OSGi bundle | Integrado en ob-event-notifications | Quarkus CDI |
| `authentication.endpoint` | JSP/WAR | Keycloak custom theme + SPI | Keycloak |
| `demo.backend` | JAX-RS (CXF) WAR | **ob-bank-backend** | Quarkus 3.8 |
| `self-care-portal` | React + nginx (sin cambios) | Mismo React app | nginx |
| Mediation policies (Synapse) | XML/Java Synapse | **Camel K routes** | Camel K Operator |
| APIM internal DB | MySQL + WSO2 schemas | MySQL (schemas propios) | MySQL Operator |
| Kafka events (OSGi) | Carbon event bus | **AMQ Streams** | Strimzi Operator |

---

## 2. Arquitectura de la propuesta

```
┌────────────────────────────────────────────────────────────────────────┐
│                        OCP — Red Hat Native                            │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  INGRESS (OCP Routes — TLS termination)                          │  │
│  └────────┬─────────────────┬───────────────────────────────────────┘  │
│           │                 │                                          │
│    ┌──────▼──────┐   ┌──────▼──────┐                                  │
│    │   3scale    │   │   Keycloak  │  ← OAuth2/OIDC/DCR/SCA           │
│    │  API Mgmt   │   │  (RH SSO)   │                                  │
│    └──────┬──────┘   └──────┬──────┘                                  │
│           │                 │                                          │
│    ┌──────▼─────────────────▼──────────────────────────────────────┐  │
│    │              Quarkus Microservices                             │  │
│    │                                                                │  │
│    │  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────┐  │  │
│    │  │ ob-consent-svc  │  │ ob-event-notif.  │  │ ob-bank-back │  │  │
│    │  │  :8081          │  │  :8082           │  │  :8083       │  │  │
│    │  │                 │  │                  │  │              │  │  │
│    │  │  /api/fs/consent│  │  /api/fs/events  │  │  /api/fs/    │  │  │
│    │  │  ├ manage       │  │  ├ subscription  │  │  backend/    │  │  │
│    │  │  ├ admin        │  │  └ poll          │  │  ├ accounts  │  │  │
│    │  │  └ validate     │  │                  │  │  ├ payments  │  │  │
│    │  └────────┬────────┘  └────────┬─────────┘  │  ├ fundsConf│  │  │
│    │           │                    │             │  └ vrps     │  │  │
│    │           ▼                    ▼             └──────────────┘  │  │
│    │    ┌──────────────────────────────────┐                        │  │
│    │    │   AMQ Streams (Kafka)            │                        │  │
│    │    │   Topic: ob-consent-events       │                        │  │
│    │    └──────────────────────────────────┘                        │  │
│    └────────────────────────┬───────────────────────────────────────┘  │
│                             │                                          │
│                    ┌────────▼────────┐                                 │
│                    │  MySQL Cluster  │                                 │
│                    │  ob_consent     │                                 │
│                    │  ob_events      │                                 │
│                    └─────────────────┘                                 │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │  Self-Care Portal (React + nginx) — sin cambios                  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Decisiones de diseño del fork Quarkus

### 3.1 ¿Por qué Quarkus?

| Criterio | WSO2 Carbon | Quarkus |
|---|---|---|
| **Startup** | 60–120 s | 0.5–2 s |
| **Memoria** | ~512 MB–1 GB por proceso | ~50–100 MB por proceso |
| **Dependencias** | OSGi + Carbon (100+ JARs) | JARs mínimos (~40 MB imagen) |
| **Native build** | No | Sí (GraalVM — imagen < 15 MB) |
| **Dev mode** | Ciclo largo | Hot reload instantáneo |
| **Operabilidad** | Gestión manual | Kubernetes-native (health, metrics built-in) |
| **Vendor lock-in** | Muy alto (WSO2 Carbon) | Bajo (Jakarta EE estándar) |

### 3.2 Estructura del fork

```
open-banking-rh/
├── pom.xml                          ← Quarkus BOM parent (v3.8.x LTS)
│
├── ob-consent-service/              ← REEMPLAZA 3 módulos WSO2
│   ├── api/
│   │   ├── ConsentResource.java     ← WSO2 ConsentManageEndpoint
│   │   └── ConsentAdminResource.java← WSO2 ConsentAdminEndpoint
│   ├── service/
│   │   └── ConsentService.java      ← WSO2 ConsentCoreService (sin Carbon)
│   ├── model/
│   │   ├── ConsentEntity.java       ← Panache (WSO2 ConsentCore DAO)
│   │   ├── ConsentStatus.java
│   │   └── ConsentType.java
│   ├── events/
│   │   └── ConsentEventPublisher.java ← AMQ Streams (reemplaza Carbon bus)
│   └── db/migration/V1__init.sql    ← Flyway (WSO2 MySQL scripts)
│
├── ob-bank-backend/                 ← REEMPLAZA demo.backend WAR
│   └── api/
│       ├── AccountResource.java     ← WSO2 AccountService (misma respuesta JSON)
│       ├── PaymentResource.java     ← WSO2 PaymentService
│       ├── FundsConfirmationResource.java
│       └── VrpResource.java
│
└── ob-event-notifications/          ← REEMPLAZA 2 módulos WSO2
    ├── api/
    │   └── EventSubscriptionResource.java ← WSO2 EventSubscriptionEndpoint
    ├── kafka/
    │   └── ConsentEventConsumer.java ← Consume AMQ Streams, persiste SETs
    └── model/
        ├── EventSubscriptionEntity.java
        └── EventNotificationEntity.java
```

### 3.3 Compatibilidad de API

Todos los Quarkus services exponen las **mismas rutas y contratos de respuesta**
que el WSO2 accelerator. El Self-Care Portal y cualquier TPP existente
pueden conectarse sin cambios en el cliente.

| WSO2 URL | Quarkus URL | Servicio |
|---|---|---|
| `/consent/services/manage/*` | `/api/fs/consent/v1.0/consents/*` | ob-consent-service |
| `/consent/services/admin/*` | `/api/fs/consent/v1.0/admin/consents/*` | ob-consent-service |
| `/event-notifications/services/subscription/*` | `/api/fs/events/v1.0/subscription/*` | ob-event-notifications |
| `/api/fs/backend/...` | `/api/fs/backend/v1.0/...` | ob-bank-backend |

### 3.4 Autenticación: Keycloak en lugar de WSO2 IS

```
WSO2:
  TPP ──► WSO2 APIM Gateway ──► WSO2 IS (token validation) ──► Carbon services

RH-native:
  TPP ──► 3scale API Gateway ──► Keycloak (token introspection) ──► Quarkus services
                                       │
                              quarkus-oidc validates JWT
                              extracts client_id from "azp" claim
```

**Keycloak reemplaza WSO2 IS para:**
- OAuth2 Authorization Code Flow + PKCE
- Dynamic Client Registration (DCR) — nativo en Keycloak 21+
- SCA (Strong Customer Authentication) — via Keycloak Authentication Flows customizados
- JWKS endpoint — nativo en Keycloak
- User federation — LDAP/AD connector nativo

### 3.5 Gateway: 3scale en lugar de WSO2 APIM

```
WSO2 APIM policies (Synapse XML):
  mTLS enforcement      ──► Camel K route / Istio mTLS policy
  consent enforcement   ──► Quarkus HTTP filter + ob-consent-service validate endpoint
  DCR mediator          ──► Keycloak DCR endpoint
```

---

## 4. Flujo de consentimiento — comparación

### WSO2 (actual)
```
TPP → APIM Gateway (Synapse) → ConsentManageEndpoint (Carbon WAR)
                                      → ConsentCoreService (OSGi bundle)
                                            → ConsentDAO (OSGi JDBC)
                                                  → MySQL
```

### Red Hat Native (propuesta)
```
TPP → 3scale Gateway → ob-consent-service (Quarkus)
                              → ConsentService (CDI bean)
                                    → ConsentEntity.persist() (Panache/Hibernate)
                                          → MySQL
                              → ConsentEventPublisher (Kafka)
                                    → AMQ Streams → ob-event-notifications
```

**Reducción de capas:** de 5 capas con OSGi a 2 capas con CDI.

---

## 5. Despliegue OCP de los servicios Quarkus

```yaml
# ob-consent-service — Deployment OCP
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ob-consent-service
  namespace: fs-ob-rh
spec:
  replicas: 2
  selector:
    matchLabels:
      app: ob-consent-service
  template:
    spec:
      containers:
      - name: ob-consent-service
        image: quay.io/openbanking/ob-consent-service:1.0.0
        ports:
        - containerPort: 8081
        env:
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: ob-mysql-secret
              key: host
        - name: DB_PASS
          valueFrom:
            secretKeyRef:
              name: ob-mysql-secret
              key: password
        - name: KEYCLOAK_HOST
          value: "keycloak.apps.cluster.example.com"
        - name: KAFKA_BOOTSTRAP
          value: "amq-streams-cluster-kafka-bootstrap.fs-messaging:9092"
        resources:
          requests:
            memory: "64Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"    # vs ~512MB WSO2
            cpu: "500m"
        readinessProbe:
          httpGet:
            path: /q/health/ready
            port: 8081
        livenessProbe:
          httpGet:
            path: /q/health/live
            port: 8081
```

---

## 6. Ventajas de la propuesta

| Aspecto | WSO2 FS Accelerator | Red Hat Native (Quarkus) |
|---|---|---|
| **Vendors** | WSO2 + Red Hat | Solo Red Hat |
| **Licencias** | WSO2 (comercial) + RH | Solo RH subscription |
| **Memoria por servicio** | ~512 MB–1 GB | ~64–256 MB |
| **Tiempo de arranque** | 60–120 s | 1–3 s |
| **Native image** | No | Sí (GraalVM) |
| **Developer experience** | OSGi + Carbon lifecycle | CDI + Quarkus dev mode |
| **Observabilidad** | Requiere config extra | Built-in (Prometheus, health) |
| **OpenAPI** | Swagger separado | Built-in SmallRye OpenAPI |
| **Pruebas** | Difícil (OSGi) | Fácil (Quarkus test + H2) |
| **Escalado en OCP** | HPA limitado por estado | HPA nativo, stateless |

---

## 7. Roadmap de migración

```
Fase 1 — Scaffolding (Semanas 1–2)
  ├── ✅ ob-consent-service: estructura base + DB schema
  ├── ✅ ob-bank-backend: todas las APIs mock
  └── ✅ ob-event-notifications: subscriptions + Kafka consumer

Fase 2 — Integración con Keycloak (Semanas 3–4)
  ├── Configurar Realm "openbanking" en Keycloak
  ├── Implementar DCR endpoint en Keycloak (native support)
  ├── Customizar Keycloak Authentication Flow para SCA
  └── Integrar quarkus-oidc en los 3 servicios

Fase 3 — Integración con 3scale (Semanas 5–6)
  ├── Publicar las APIs Quarkus en 3scale
  ├── Configurar policies: rate limiting, consent enforcement
  └── Migrar Synapse mediators a Camel K routes

Fase 4 — Self-Care Portal adaptado (Semana 7)
  ├── Actualizar SERVER_URL del portal para apuntar a Keycloak
  └── Adaptar llamadas API a las nuevas URLs de Quarkus

Fase 5 — Pruebas E2E y go-live (Semanas 8–10)
  ├── Pruebas de integración TPP simulado ↔ Keycloak ↔ ob-consent-service
  ├── Pruebas de performance (JMeter): comparar con WSO2 baseline
  └── Despliegue en OCP producción
```

---

*Para contribuir: crear rama `feature/ob-rh-native` y abrir PR contra `main`.*
