# WSO2 Financial Services Accelerator — Arquitectura de Referencia

> **Versión:** 4.0.0  
> **Fecha:** 2026-06-25  
> **Plataformas objetivo:** OpenShift · Kubernetes · AWS (EKS)

---

## Tabla de Contenidos

1. [Visión General](#1-visión-general)
2. [Componentes del Acelerador](#2-componentes-del-acelerador)
3. [Diagrama de Arquitectura Lógica](#3-diagrama-de-arquitectura-lógica)
4. [Diagrama de Flujo de Petición (Request Flow)](#4-diagrama-de-flujo-de-petición-request-flow)
5. [Diagrama de Componentes — Capa de Gateway](#5-diagrama-de-componentes--capa-de-gateway)
6. [Diagrama de Componentes — Capa de Identidad y Consentimiento](#6-diagrama-de-componentes--capa-de-identidad-y-consentimiento)
7. [Arquitectura de Despliegue en Kubernetes / OpenShift](#7-arquitectura-de-despliegue-en-kubernetes--openshift)
8. [Arquitectura de Despliegue en AWS (EKS)](#8-arquitectura-de-despliegue-en-aws-eks)
9. [Modelo de Datos y Bases de Datos](#9-modelo-de-datos-y-bases-de-datos)
10. [Políticas de Mediación](#10-políticas-de-mediación)
11. [Consideraciones de Seguridad](#11-consideraciones-de-seguridad)
12. [Escalabilidad y Alta Disponibilidad](#12-escalabilidad-y-alta-disponibilidad)
13. [Manifiestos Kubernetes de Referencia](#13-manifiestos-kubernetes-de-referencia)

---

## 1. Visión General

El **WSO2 Financial Services Accelerator** es un conjunto de tecnologías que acelera la adopción de cumplimiento normativo para la banca abierta (Open Banking). Está construido sobre dos productos WSO2:

| Producto Base | Acelerador FS | Puerto por Defecto |
|---|---|---|
| WSO2 API Manager 4.x | `wso2-fsam-accelerator-4.0.0` | 8243 (Gateway), 9443 (Publisher/DevPortal) |
| WSO2 Identity Server 7.x | `wso2-fsiam-accelerator-4.0.0` | 9443, 9446 |

### Capacidades Principales

- **Dynamic Client Registration (DCR)** — Registro dinámico de clientes TPP con validación de JWT/SSA
- **Consent Management** — Ciclo de vida completo de consentimientos (creación, autorización, validación, revocación)
- **Event Notifications** — Notificaciones asíncronas de eventos financieros (SET/CAEP)
- **mTLS Enforcement** — Aplicación de certificados de transporte mutuos
- **Self-Care Portal** — Portal React para gestión de consentimientos por parte del usuario final

---

## 2. Componentes del Acelerador

### 2.1 Módulos Java (OSGi Bundles)

```
financial-services-accelerator/components/
├── org.wso2.financial.services.accelerator.common          # Utilidades comunes, JDBC, caché
├── org.wso2.financial.services.accelerator.consent.mgt.dao      # Capa de acceso a datos de consentimientos
├── org.wso2.financial.services.accelerator.consent.mgt.service  # Lógica de negocio de consentimientos
├── org.wso2.financial.services.accelerator.consent.mgt.extensions # Extensiones (handlers, validators, steps)
├── org.wso2.financial.services.accelerator.event.notifications.service # Servicio de notificaciones de eventos
├── org.wso2.financial.services.accelerator.gateway         # Ejecutores del gateway (request router, executors)
├── org.wso2.financial.services.accelerator.identity.extensions # Extensiones de identidad (claims, grants, DCR)
└── org.wso2.financial.services.accelerator.keymanager      # Integración con Key Manager de APIM
```

### 2.2 WebApps Internas (WAR)

```
financial-services-accelerator/internal-webapps/
├── org.wso2.financial.services.accelerator.authentication.endpoint  # Endpoint de autenticación/consentimiento
├── org.wso2.financial.services.accelerator.consent.mgt.endpoint     # API REST de gestión de consentimientos
├── org.wso2.financial.services.accelerator.demo.backend             # Backend de demostración (mock bancario)
└── org.wso2.financial.services.accelerator.event.notifications.endpoint # API REST de notificaciones de eventos
```

### 2.3 Políticas de Mediación (Synapse/API Gateway)

```
financial-services-accelerator/common-mediation-policies/
├── consent-enforcement/        # Valida consentimientos en cada llamada a la API
├── dynamic-client-registration/ # Maneja flujo DCR en el gateway
├── dynamic-endpoint/           # Enrutamiento dinámico de endpoints bancarios
└── mtls-enforcement/           # Valida certificados de transporte mTLS
```

### 2.4 Aplicaciones Frontend

```
financial-services-accelerator/react-apps/
└── self-care-portal/           # Portal React (Redux) — Gestión de consentimientos para usuarios finales
```

### 2.5 Aceleradores de Producto

```
financial-services-accelerator/accelerators/
├── fs-apim/   # Artefactos, configuración y scripts para WSO2 API Manager
└── fs-is/     # Artefactos, configuración y scripts para WSO2 Identity Server
```

---

## 3. Diagrama de Arquitectura Lógica

```mermaid
graph TB
    subgraph "Clientes / Consumidores"
        TPP[TPP / App de Terceros]
        PSU[Usuario Final / PSU]
        BANK[Sistema Core Bancario]
    end

    subgraph "DMZ / Ingress"
        WAF[WAF / DDoS Protection]
        LB[Load Balancer / Ingress Controller]
    end

    subgraph "Plano de API - WSO2 API Manager + FS Accelerator"
        direction TB
        GW[Gateway\nPort 8243/8280]
        PUB[Publisher Portal\nPort 9443]
        DEV[Developer Portal\nPort 9443]
        TM[Traffic Manager\nPort 9443]
        KM_APIM[Key Manager Connector\nfs-keymanager]

        subgraph "Mediation Policies"
            CE[Consent Enforcement Policy]
            DCR_POL[DCR Policy]
            DE[Dynamic Endpoint Policy]
            MTLS[mTLS Enforcement Policy]
        end

        GW --> CE
        GW --> DCR_POL
        GW --> DE
        GW --> MTLS
    end

    subgraph "Plano de Identidad - WSO2 Identity Server + FS Accelerator"
        direction TB
        IS[Identity Server\nPort 9443/9446]

        subgraph "FS IS Extensions"
            AUTH_EP[Authentication Endpoint\n/authenticationendpoint]
            CONS_EP[Consent Mgt Endpoint\n/api/fs/consent/v1]
            EVT_EP[Event Notifications Endpoint\n/api/fs/event-notifications/v1]
        end

        subgraph "FS IS Components"
            CONS_SVC[Consent Management Service]
            CONS_DAO[Consent DAO]
            EVT_SVC[Event Notifications Service]
            ID_EXT[Identity Extensions]
            DCR_EXT[DCR Application Updater]
        end

        IS --> AUTH_EP
        IS --> CONS_EP
        IS --> EVT_EP
        CONS_EP --> CONS_SVC
        CONS_SVC --> CONS_DAO
        EVT_EP --> EVT_SVC
        IS --> ID_EXT
        IS --> DCR_EXT
    end

    subgraph "Persistencia"
        FS_DB[(WSO2FS_DB\nConsentimientos + Eventos)]
        IS_DB[(WSO2IS_DB\nIdentidad + Sesiones)]
        APIM_DB[(WSO2AM_DB\nAPIs + Suscripciones)]
        SHARED_DB[(WSO2SHARED_DB\nGobernanza)]
    end

    subgraph "Frontend"
        SCP[Self-Care Portal\nReact + Redux]
    end

    subgraph "Backend Bancario"
        DEMO_BE[Demo Backend\nMock API]
        CORE_BE[Core Banking API]
    end

    TPP -->|HTTPS + mTLS| WAF
    PSU -->|HTTPS| WAF
    WAF --> LB
    LB -->|Port 8243| GW
    LB -->|Port 9443| IS
    LB -->|Port 443| SCP

    GW -->|Consent Validation| CONS_EP
    GW -->|Key Introspection| IS
    GW -->|API Calls| DEMO_BE
    GW -->|API Calls| CORE_BE

    SCP -->|OAuth2 / OIDC| IS
    SCP -->|Consent API| CONS_EP

    KM_APIM -->|Token Validation| IS

    CONS_DAO --> FS_DB
    EVT_SVC --> FS_DB
    IS --> IS_DB
    GW --> APIM_DB
    PUB --> SHARED_DB

    PSU -->|Consent Auth Flow| AUTH_EP
    AUTH_EP -->|Consent Persist| CONS_SVC

    BANK -->|Event Publishing| EVT_EP
```

---

## 4. Diagrama de Flujo de Petición (Request Flow)

### 4.1 Flujo de Autorización de Consentimiento (OAuth2 / OIDC)

```mermaid
sequenceDiagram
    participant TPP as TPP / Third Party App
    participant GW as API Gateway (APIM)
    participant IS as Identity Server
    participant PSU as Usuario Final (PSU)
    participant CONS as Consent Management
    participant DB as WSO2FS_DB

    TPP->>GW: POST /oauth2/token (DCR registration)
    GW->>IS: Forward DCR Request (JWT/SSA)
    IS->>IS: Validate SSA + JWT
    IS-->>GW: Client credentials (client_id, client_secret)
    GW-->>TPP: 201 Created (DCR Response)

    TPP->>IS: GET /authorize (PAR/RAR request)
    IS->>IS: Validate Request Object
    IS->>CONS: Create Consent (Draft)
    CONS->>DB: INSERT consent record
    DB-->>CONS: consent_id
    IS-->>TPP: Redirect to Auth endpoint

    TPP->>PSU: Redirect PSU to Authorization URL
    PSU->>IS: GET /authenticationendpoint/consent.do
    IS->>CONS: Retrieve Consent Details
    CONS->>DB: SELECT consent by id
    DB-->>CONS: consent details
    CONS-->>IS: Consent data
    IS-->>PSU: Display Consent Screen

    PSU->>IS: POST Authorize Consent
    IS->>CONS: Persist Consent (Authorized)
    CONS->>DB: UPDATE consent status = AUTHORIZED
    DB-->>CONS: OK
    IS-->>PSU: Redirect with authorization_code

    PSU->>TPP: authorization_code
    TPP->>IS: POST /oauth2/token (code exchange)
    IS->>IS: Validate code + generate tokens
    IS-->>TPP: access_token + refresh_token + id_token
```

### 4.2 Flujo de Llamada a API Protegida

```mermaid
sequenceDiagram
    participant TPP as TPP
    participant GW as API Gateway (APIM)
    participant CE as Consent Enforcement Policy
    participant IS as Identity Server
    participant CONS as Consent Validation Endpoint
    participant BANK as Backend Bancario

    TPP->>GW: GET /open-banking/v3.1/accounts\nAuthorization: Bearer <access_token>\n+ mTLS client cert

    GW->>GW: mTLS Enforcement Policy\n(Validate client certificate)

    GW->>IS: Introspect Token
    IS-->>GW: Token claims (sub, consent_id, scope)

    GW->>CE: Consent Enforcement Policy
    CE->>CONS: POST /api/fs/consent/validate\n{token_claims, request_path, method}
    CONS->>CONS: Validate consent status\n+ scope + bound certificate
    CONS-->>CE: {isValid: true, consentId: "xxx"}
    CE-->>GW: Consent Valid

    GW->>GW: Dynamic Endpoint Policy\n(Route to correct backend)
    GW->>BANK: Forward API Request
    BANK-->>GW: API Response

    GW-->>TPP: 200 OK + Response Body
```

---

## 5. Diagrama de Componentes — Capa de Gateway

```mermaid
graph LR
    subgraph "WSO2 API Manager Gateway"
        REQ[Inbound Request] --> SYNAPSE[Synapse Engine]

        subgraph "FS Gateway Executors"
            ROUTER[DefaultRequestRouter]
            EXEC1[DCR Executor]
            EXEC2[Consent Enforcement Executor]
            EXEC3[mTLS Executor]
            EXEC4[Custom Executor\nExtensible]
        end

        SYNAPSE --> ROUTER
        ROUTER --> EXEC1
        ROUTER --> EXEC2
        ROUTER --> EXEC3
        ROUTER --> EXEC4

        subgraph "Mediation Policies (Synapse XML)"
            MP1[consent-enforcement.xml]
            MP2[dynamic-client-registration.xml]
            MP3[dynamic-endpoint.xml]
            MP4[mtls-enforcement.xml]
        end

        EXEC2 --> MP1
        EXEC1 --> MP2
        MP3 --> BE_ROUTE[Backend Route]
        EXEC3 --> MP4
    end

    subgraph "Key Manager Extension"
        KM[fs-keymanager\nOSGi Bundle]
        KM --> IS_KM[IS Key Manager API\n/api/am/admin/v3/key-managers]
    end

    EXEC2 -->|Validate| CONSENT_API[/api/fs/consent/validate\nIS Consent Endpoint]
    EXEC1 -->|Register App| DCR_API[/keymanager-operations/dcr/register\nIS DCR Endpoint]
```

---

## 6. Diagrama de Componentes — Capa de Identidad y Consentimiento

```mermaid
graph TB
    subgraph "WSO2 Identity Server"
        subgraph "FS Identity Extensions"
            REQ_VAL[Request Object Validator\nFSDefaultRequestObjectValidator]
            RESP_HANDLER[Response Type Handler\nFSDefaultResponseTypeHandlerImpl]
            CLAIM_PROV[Claim Provider\nFSDefaultClaimProvider]
            GRANT_HANDLER[Grant Handler\nFSDefaultGrantHandler]
            INTRO_PROV[Introspection Data Provider\nFSDefaultIntrospectionDataProvider]
        end

        subgraph "DCR Extension"
            APP_UPDATER[Application Updater\nApplicationUpdaterImpl]
        end

        subgraph "Consent Authorization Flow"
            AUTH_SERVLET[FS Auth Servlet\nFSDefaultAuthServletImpl]
            RETRIEVE_STEP[DefaultConsentRetrievalStep]
            PERSIST_STEP[DefaultConsentPersistStep]
        end
    end

    subgraph "Consent Management Layer"
        subgraph "API Endpoints (WAR)"
            CONS_API[Consent Mgt Endpoint\n/api/fs/consent/v1]
            EVT_API[Event Notifications Endpoint\n/api/fs/event-notifications/v1]
            AUTH_WEB[Authentication Webapp\n/authenticationendpoint]
        end

        subgraph "Service Layer"
            CONS_SVC[ConsentCoreService]
            EVT_SVC[EventNotificationService]
        end

        subgraph "Extension Points"
            MANAGE_HANDLER[ConsentManageHandler\nDefaultConsentManageHandler]
            ADMIN_HANDLER[ConsentAdminHandler\nDefaultConsentAdminHandler]
            VALIDATOR[ConsentValidator\nDefaultConsentValidator]
            EVT_GEN[EventNotificationGenerator\nDefaultEventNotificationGenerator]
        end

        subgraph "DAO Layer"
            CONS_DAO[ConsentCoreDAO\nJDBC Implementation]
            EVT_DAO[EventNotificationDAO\nJDBC Implementation]
        end
    end

    subgraph "Persistencia"
        FS_DB[(WSO2FS_DB\nMySQL / PostgreSQL)]
    end

    subgraph "Self-Care Portal"
        SCP_FE[React Frontend\nRedux State Management]
        SCP_BE[Self-Care Backend\nSpring/JAX-RS]
    end

    AUTH_SERVLET --> RETRIEVE_STEP --> CONS_SVC
    AUTH_SERVLET --> PERSIST_STEP --> CONS_SVC
    CONS_API --> MANAGE_HANDLER --> CONS_SVC
    CONS_API --> ADMIN_HANDLER --> CONS_SVC
    CONS_API --> VALIDATOR
    EVT_API --> EVT_GEN --> EVT_SVC
    CONS_SVC --> CONS_DAO --> FS_DB
    EVT_SVC --> EVT_DAO --> FS_DB

    SCP_FE -->|OAuth2 OIDC| REQ_VAL
    SCP_FE -->|REST API| CONS_API

    REQ_VAL -.->|Validates| AUTH_WEB
    CLAIM_PROV -.->|Enriches tokens| GRANT_HANDLER
```

---

## 7. Arquitectura de Despliegue en Kubernetes / OpenShift

### 7.1 Diagrama de Infraestructura

```mermaid
graph TB
    subgraph "Cluster Kubernetes / OpenShift"
        subgraph "Namespace: ingress-system"
            ING[Ingress Controller\nnginx / HAProxy / OpenShift Router]
        end

        subgraph "Namespace: fs-apim"
            APIM_GW_POD[wso2-apim-gateway\nDeployment\nreplicas: 2+]
            APIM_CP_POD[wso2-apim-control-plane\nDeployment\nreplicas: 2]
            APIM_GW_SVC[Service: apim-gateway\nLoadBalancer 8243/8280]
            APIM_CP_SVC[Service: apim-control-plane\nClusterIP 9443]
            APIM_CM[ConfigMap: apim-financial-services-conf]
            APIM_SEC[Secret: apim-keystores]
        end

        subgraph "Namespace: fs-identity"
            IS_POD[wso2-identity-server\nDeployment\nreplicas: 2+]
            IS_SVC[Service: identity-server\nLoadBalancer 9443]
            IS_CONS_SVC[Service: consent-api\nClusterIP 9446]
            IS_CM[ConfigMap: is-financial-services-conf]
            IS_SEC[Secret: is-keystores + db-credentials]
        end

        subgraph "Namespace: fs-portal"
            SCP_POD[self-care-portal\nDeployment\nreplicas: 2]
            SCP_SVC[Service: self-care-portal\nClusterIP 443]
        end

        subgraph "Namespace: fs-database"
            DB_STS[mysql / postgresql\nStatefulSet]
            DB_SVC[Service: fs-database\nClusterIP 3306/5432]
            DB_PVC[PersistentVolumeClaim\n20Gi RWO]
        end

        subgraph "Namespace: fs-monitoring"
            PROM[Prometheus\nDeployment]
            GRAF[Grafana\nDeployment]
            LOKI[Loki / Elasticsearch\nDeployment]
        end
    end

    INTERNET((Internet)) --> ING
    ING -->|tpp.bank.com:443| APIM_GW_SVC
    ING -->|identity.bank.com:443| IS_SVC
    ING -->|portal.bank.com:443| SCP_SVC

    APIM_GW_POD --> APIM_GW_SVC
    APIM_CP_POD --> APIM_CP_SVC
    IS_POD --> IS_SVC
    IS_POD --> IS_CONS_SVC
    SCP_POD --> SCP_SVC

    APIM_GW_POD -->|JDBC| DB_SVC
    IS_POD -->|JDBC| DB_SVC

    APIM_GW_POD -->|Consent Validation| IS_CONS_SVC
    APIM_GW_POD -->|Key Introspection| IS_SVC
    SCP_POD -->|OIDC + Consent API| IS_SVC

    APIM_CM --> APIM_GW_POD
    APIM_SEC --> APIM_GW_POD
    IS_CM --> IS_POD
    IS_SEC --> IS_POD
    DB_PVC --> DB_STS
    DB_STS --> DB_SVC
```

### 7.2 Topología de Red y Puertos

| Servicio | Namespace | Puerto Interno | Puerto Externo | Protocolo |
|---|---|---|---|---|
| APIM Gateway | fs-apim | 8243 | 443 (via Ingress TLS) | HTTPS + mTLS |
| APIM Gateway (passthrough) | fs-apim | 8280 | 80 | HTTP |
| APIM Control Plane | fs-apim | 9443 | N/A (interno) | HTTPS |
| Identity Server | fs-identity | 9443 | 443 (via Ingress TLS) | HTTPS |
| Consent API | fs-identity | 9446 | N/A (interno) | HTTPS |
| Self-Care Portal | fs-portal | 443 | 443 (via Ingress TLS) | HTTPS |
| MySQL/PostgreSQL | fs-database | 3306/5432 | N/A | TCP |

---

## 8. Arquitectura de Despliegue en AWS (EKS)

```mermaid
graph TB
    subgraph "AWS Cloud"
        subgraph "VPC: 10.0.0.0/16"
            subgraph "Public Subnets (AZ-1a, AZ-1b)"
                ALB[Application Load Balancer\nAWS ALB Ingress Controller]
                NAT[NAT Gateway]
            end

            subgraph "Private Subnets (AZ-1a, AZ-1b)"
                subgraph "EKS Cluster"
                    subgraph "Node Group: wso2-nodes (m5.2xlarge)"
                        APIM_NODE[APIM Gateway Pods\n+ APIM Control Plane Pods]
                        IS_NODE[Identity Server Pods\n+ Self-Care Portal Pods]
                    end
                end
            end

            subgraph "Data Tier (Private Subnets)"
                RDS[(Amazon RDS\nMySQL 8.0 Multi-AZ\nWSO2FS_DB + WSO2IS_DB)]
                REDIS[Amazon ElastiCache\nRedis Cluster\nSession + Token Cache]
                EFS[Amazon EFS\nShared Config Storage]
            end

            subgraph "Security"
                ACM[AWS Certificate Manager\nTLS Certificates]
                KMS[AWS KMS\nKeystore Encryption]
                SECRETS[AWS Secrets Manager\nDB Credentials + Keystores]
                WAF_AWS[AWS WAF\n+ Shield Standard]
            end

            subgraph "Observabilidad"
                CW[CloudWatch\nLogs + Metrics]
                XRAY[AWS X-Ray\nDistributed Tracing]
            end
        end

        R53[Route 53\nDNS]
        CF[CloudFront\nSelf-Care Portal CDN]
    end

    USERS((TPP / PSU)) --> R53
    R53 --> WAF_AWS
    WAF_AWS --> ALB
    ALB -->|HTTPS 443| APIM_NODE
    ALB -->|HTTPS 443| IS_NODE

    CF -->|Origin| SCP_S3[S3 Bucket\nSelf-Care Portal Static]

    APIM_NODE -->|JDBC via VPC| RDS
    IS_NODE -->|JDBC via VPC| RDS
    APIM_NODE -->|Cache| REDIS
    IS_NODE -->|Cache| REDIS
    APIM_NODE -->|Shared Config| EFS
    IS_NODE -->|Shared Config| EFS

    APIM_NODE -->|Secrets| SECRETS
    IS_NODE -->|Secrets| SECRETS
    APIM_NODE -->|Logs| CW
    IS_NODE -->|Logs| CW

    ACM -->|TLS Termination| ALB
    KMS -->|Encrypt| SECRETS
```

### 8.1 Recursos AWS Recomendados

| Recurso | Tipo / SKU | Justificación |
|---|---|---|
| EKS Node Group (APIM) | `m5.2xlarge` (2+ nodos) | 8 vCPU, 32 GB RAM por nodo |
| EKS Node Group (IS) | `m5.xlarge` (2+ nodos) | 4 vCPU, 16 GB RAM por nodo |
| RDS MySQL | `db.r6g.xlarge` Multi-AZ | Alta disponibilidad para FS_DB |
| ElastiCache Redis | `cache.r6g.large` cluster | Token cache + sesiones |
| EFS | `General Purpose` | Configuraciones compartidas entre pods |
| ALB | Application Load Balancer | TLS termination + path routing |
| ACM | Wildcard Certificate | `*.bank.com` |

---

## 9. Modelo de Datos y Bases de Datos

```mermaid
erDiagram
    FS_CONSENT {
        string CONSENT_ID PK
        string RECEIPT
        string CLIENT_ID
        string CONSENT_TYPE
        int CURRENT_STATUS
        string APPLICANT_ID
        datetime CREATED_TIME
        datetime UPDATED_TIME
        int VALIDITY_PERIOD
        bool IS_RECURRING
        int RECURRING_FREQUENCY
    }

    FS_CONSENT_ATTRIBUTE {
        string CONSENT_ID FK
        string ATT_KEY
        string ATT_VALUE
    }

    FS_CONSENT_RESOURCE {
        string CONSENT_ID FK
        string RESOURCE_PATH
        string RESOURCE_OPERATION
    }

    FS_CONSENT_MAPPING {
        string MAPPING_ID PK
        string CONSENT_ID FK
        string ACCOUNT_ID
        string PERMISSION
        string MAPPING_STATUS
    }

    FS_CONSENT_AUTH_RESOURCE {
        string AUTH_ID PK
        string CONSENT_ID FK
        string AUTH_TYPE
        string USER_ID
        int AUTH_STATUS
        datetime UPDATED_TIME
    }

    FS_CONSENT_STATUS_AUDIT {
        int STATUS_AUDIT_ID PK
        string CONSENT_ID FK
        string CURRENT_STATUS
        datetime ACTION_TIME
        string ACTION_BY
        string REASON
    }

    NOTIFICATION {
        string NOTIFICATION_ID PK
        string CLIENT_ID
        string RESOURCE_ID
        string RESOURCE_TYPE
        string RESOURCE_ENDPOINT_PUBLISH_STATUS
        int PRIORITY
        datetime UPDATED_TIME
    }

    NOTIFICATION_EVENT {
        string EVENT_ID PK
        string NOTIFICATION_ID FK
        string EVENT_INFORMATION
    }

    NOTIFICATION_ERROR {
        string NOTIFICATION_ID PK_FK
        string ERROR_CODE
        string ERROR_DESCRIPTION
        string ERROR_URI
    }

    FS_CONSENT ||--o{ FS_CONSENT_ATTRIBUTE : "has"
    FS_CONSENT ||--o{ FS_CONSENT_RESOURCE : "has"
    FS_CONSENT ||--o{ FS_CONSENT_MAPPING : "maps to"
    FS_CONSENT ||--o{ FS_CONSENT_AUTH_RESOURCE : "authorizes"
    FS_CONSENT ||--o{ FS_CONSENT_STATUS_AUDIT : "audits"
    NOTIFICATION ||--o{ NOTIFICATION_EVENT : "contains"
    NOTIFICATION ||--o| NOTIFICATION_ERROR : "may have"
```

### Bases de Datos Requeridas

| Base de Datos | Propósito | Motor Recomendado |
|---|---|---|
| `WSO2FS_DB` | Consentimientos + Event Notifications | MySQL 8.0 / PostgreSQL 14+ |
| `WSO2IS_DB` | Identidades, sesiones, tokens OAuth2 | MySQL 8.0 / PostgreSQL 14+ |
| `WSO2AM_DB` | APIs, suscripciones, aplicaciones | MySQL 8.0 / PostgreSQL 14+ |
| `WSO2SHARED_DB` | Gobernanza compartida IS + APIM | MySQL 8.0 / PostgreSQL 14+ |

---

## 10. Políticas de Mediación

```mermaid
flowchart LR
    REQ[API Request\nTPP] --> GW[Gateway\nInbound Handler]

    GW --> MTLS_CHECK{mTLS Policy\nVerify Client Cert?}
    MTLS_CHECK -->|FAIL| ERR_401[401 Unauthorized\n- Invalid Certificate]
    MTLS_CHECK -->|PASS| TOKEN_CHECK{Token Introspection\nValid Bearer Token?}

    TOKEN_CHECK -->|FAIL| ERR_401
    TOKEN_CHECK -->|PASS| CONSENT_CHECK{Consent Enforcement\nConsent Valid?}

    CONSENT_CHECK -->|Check: POST /api/fs/consent/validate| CONS_API[Consent Validation\nEndpoint IS]
    CONS_API -->|isValid=false| ERR_403[403 Forbidden\n- Consent Invalid/Expired]
    CONS_API -->|isValid=true| DCR_CHECK{Is DCR\nRequest?}

    DCR_CHECK -->|Yes| DCR_POLICY[DCR Policy\n- Validate SSA JWT\n- Extract software_id\n- Register App in IS]
    DCR_CHECK -->|No| DYN_EP[Dynamic Endpoint Policy\n- Route to correct\nbanking backend]

    DCR_POLICY --> BE_RESP[Backend Response]
    DYN_EP --> BACKEND[Core Banking\nAPI Backend]
    BACKEND --> BE_RESP
    BE_RESP --> RESP[API Response\nto TPP]
```

---

## 11. Consideraciones de Seguridad

### 11.1 Modelo de Seguridad en Capas

```mermaid
graph TB
    subgraph "Capa 1 - Perímetro"
        WAF_L1[WAF + Rate Limiting\nOWASP Top 10 Protection]
        DDOS[DDoS Protection\nAWS Shield / Cloudflare]
    end

    subgraph "Capa 2 - Transporte"
        MTLS_L2[mTLS Mutuo\nCertificados de cliente TPP]
        TLS13[TLS 1.3\nCifrado en tránsito]
    end

    subgraph "Capa 3 - Identidad y Acceso"
        OAUTH[OAuth 2.0 + OIDC\nPKCE + PAR + RAR]
        DCR_SEC[DCR con SSA\nSoftware Statement Assertion]
        SCOPES[Fine-grained Scopes\nPor tipo de recurso]
    end

    subgraph "Capa 4 - Aplicación"
        CONS_VAL[Consent Validation\nEn cada petición]
        CERT_BIND[Certificate Binding\ncnf/x5t claim en tokens]
        IDEMPOTENCY[Idempotency Keys\nPrevención de replay]
    end

    subgraph "Capa 5 - Datos"
        ENCRYPT_REST[Cifrado en reposo\nAES-256 / AWS KMS]
        AUDIT_LOG[Audit Logging\nInmutable + Trazable]
        DB_TLS[DB TLS connections\nJDBC over SSL]
    end

    WAF_L1 --> MTLS_L2
    DDOS --> WAF_L1
    MTLS_L2 --> OAUTH
    TLS13 --> OAUTH
    OAUTH --> CONS_VAL
    DCR_SEC --> OAUTH
    SCOPES --> CONS_VAL
    CONS_VAL --> CERT_BIND
    CERT_BIND --> ENCRYPT_REST
    IDEMPOTENCY --> AUDIT_LOG
```

### 11.2 Checklist de Seguridad para Producción

| Ítem | Estado | Descripción |
|---|---|---|
| mTLS en Gateway | Obligatorio | Todos los TPPs deben presentar certificado de cliente |
| TLS 1.2+ mínimo | Obligatorio | Deshabilitar TLS 1.0/1.1 y SSLv3 |
| Rotación de keystores | Obligatorio | JKS/P12 en Kubernetes Secrets o HSM |
| Contraseñas en Secrets | Obligatorio | No hardcodear en ConfigMaps |
| Network Policies | Obligatorio | Microsegmentación entre namespaces |
| Pod Security Standards | Obligatorio | `restricted` en namespaces productivos |
| RBAC mínimo | Obligatorio | ServiceAccounts con permisos mínimos |
| Audit Logging IS | Obligatorio | Habilitar `audit.log` en IS |
| WAF Rules OWASP | Recomendado | Reglas para SQLi, XSS, injection |
| Secrets Manager | Recomendado | AWS Secrets Manager / HashiCorp Vault |

---

## 12. Escalabilidad y Alta Disponibilidad

### 12.1 Estrategia de Escalado

```mermaid
graph LR
    subgraph "APIM Gateway - Stateless"
        GW1[Gateway Pod 1]
        GW2[Gateway Pod 2]
        GW3[Gateway Pod N]
        HPA_GW[HorizontalPodAutoscaler\nmin:2 max:10\nCPU: 70%]
    end

    subgraph "Identity Server - Clustered"
        IS1[IS Pod 1\nHazelcast Node]
        IS2[IS Pod 2\nHazelcast Node]
        IS3[IS Pod N\nHazelcast Node]
        HPA_IS[HorizontalPodAutoscaler\nmin:2 max:6\nCPU: 70%]
        HC[Hazelcast Cluster\nSession Replication]
        IS1 <-->|Cluster| HC
        IS2 <-->|Cluster| HC
        IS3 <-->|Cluster| HC
    end

    subgraph "Database - HA"
        DB_PRIMARY[(Primary\nWrite)]
        DB_REPLICA[(Replica\nRead)]
        DB_PRIMARY -->|Replication| DB_REPLICA
    end

    LB_EXT[Load Balancer] --> GW1
    LB_EXT --> GW2
    LB_EXT --> GW3
    LB_IS[Load Balancer] --> IS1
    LB_IS --> IS2
    LB_IS --> IS3

    GW1 --> DB_PRIMARY
    IS1 --> DB_PRIMARY
    IS2 --> DB_REPLICA

    HPA_GW -.->|scales| GW1
    HPA_IS -.->|scales| IS1
```

### 12.2 Recursos por Pod (Sizing de Referencia)

| Componente | CPU Request | CPU Limit | Memory Request | Memory Limit | Min Replicas |
|---|---|---|---|---|---|
| APIM Gateway | 1000m | 2000m | 2Gi | 4Gi | 2 |
| APIM Control Plane | 1000m | 2000m | 2Gi | 4Gi | 2 |
| Identity Server | 1000m | 2000m | 2Gi | 4Gi | 2 |
| Self-Care Portal | 200m | 500m | 256Mi | 512Mi | 2 |
| MySQL (StatefulSet) | 1000m | 2000m | 2Gi | 4Gi | 1 (+ replica) |

---

## 13. Manifiestos Kubernetes de Referencia

> Los siguientes manifiestos son de referencia. Adaptar valores de imagen, namespace y configuración al entorno específico.

### 13.1 Namespace y ResourceQuota

```yaml
# namespace-fs.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: fs-identity
  labels:
    app.kubernetes.io/part-of: financial-services
    pod-security.kubernetes.io/enforce: restricted
---
apiVersion: v1
kind: Namespace
metadata:
  name: fs-apim
  labels:
    app.kubernetes.io/part-of: financial-services
    pod-security.kubernetes.io/enforce: restricted
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: fs-identity-quota
  namespace: fs-identity
spec:
  hard:
    requests.cpu: "4"
    requests.memory: 8Gi
    limits.cpu: "8"
    limits.memory: 16Gi
    pods: "20"
```

### 13.2 ConfigMap — Financial Services (Identity Server)

```yaml
# configmap-is-financial-services.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: is-financial-services-conf
  namespace: fs-identity
data:
  financial-services.xml: |
    <?xml version="1.0" encoding="UTF-8"?>
    <Server xmlns="http://wso2.org/projects/carbon/financial-services.xml">
        <JDBCPersistenceManager>
            <DataSource>
                <Name>jdbc/WSO2FS_DB</Name>
            </DataSource>
        </JDBCPersistenceManager>
        <Consent>
            <ManageHandler>
                org.wso2.financial.services.accelerator.consent.mgt.extensions.manage.impl.DefaultConsentManageHandler
            </ManageHandler>
            <AuthorizeSteps>
                <Retrieve>
                    <Step class="org.wso2.financial.services.accelerator.consent.mgt.extensions.authorize.impl.DefaultConsentRetrievalStep" priority="1"/>
                </Retrieve>
                <Persist>
                    <Step class="org.wso2.financial.services.accelerator.consent.mgt.extensions.authorize.impl.DefaultConsentPersistStep" priority="1"/>
                </Persist>
            </AuthorizeSteps>
            <Validation>
                <Validator>
                    org.wso2.financial.services.accelerator.consent.mgt.extensions.validate.impl.DefaultConsentValidator
                </Validator>
                <JWTPayloadValidation>true</JWTPayloadValidation>
            </Validation>
            <Portal>
                <Params>
                    <IdentityServerBaseUrl>https://identity.bank.com</IdentityServerBaseUrl>
                    <ApiManagerServerBaseUrl>https://api.bank.com</ApiManagerServerBaseUrl>
                </Params>
            </Portal>
        </Consent>
        <Identity>
            <Extensions>
                <RequestObjectValidator>
                    org.wso2.financial.services.accelerator.identity.extensions.auth.extensions.request.validator.DefaultFSRequestObjectValidator
                </RequestObjectValidator>
                <ClaimProvider>
                    org.wso2.financial.services.accelerator.identity.extensions.claims.FSDefaultClaimProvider
                </ClaimProvider>
                <GrantHandler>
                    org.wso2.financial.services.accelerator.identity.extensions.grant.type.handlers.FSDefaultGrantHandler
                </GrantHandler>
            </Extensions>
        </Identity>
        <EventNotifications>
            <NotificationGeneration>
                <NotificationGenerator>
                    org.wso2.financial.services.accelerator.event.notifications.service.DefaultEventNotificationGenerator
                </NotificationGenerator>
                <NumberOfSetsToReturn>5</NumberOfSetsToReturn>
            </NotificationGeneration>
        </EventNotifications>
    </Server>
```

### 13.3 Secret — Credenciales de Base de Datos

```yaml
# secret-db-credentials.yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
  namespace: fs-identity
type: Opaque
stringData:
  db-url: "jdbc:mysql://fs-database.fs-database.svc.cluster.local:3306/WSO2FS_DB?useSSL=true"
  db-username: "wso2fsuser"
  db-password: "<CHANGE_ME_USE_SEALED_SECRETS_OR_VAULT>"
```

### 13.4 Deployment — WSO2 Identity Server

```yaml
# deployment-identity-server.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wso2-identity-server
  namespace: fs-identity
  labels:
    app: wso2-identity-server
    app.kubernetes.io/component: identity
    app.kubernetes.io/part-of: financial-services
spec:
  replicas: 2
  selector:
    matchLabels:
      app: wso2-identity-server
  strategy:
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
    type: RollingUpdate
  template:
    metadata:
      labels:
        app: wso2-identity-server
    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchExpressions:
                  - key: app
                    operator: In
                    values:
                      - wso2-identity-server
              topologyKey: kubernetes.io/hostname
      containers:
        - name: wso2is
          image: wso2/wso2is:7.0.0  # Customizar con imagen que incluya FS accelerator
          imagePullPolicy: Always
          ports:
            - containerPort: 9443
              name: https
            - containerPort: 9446
              name: consent-api
          resources:
            requests:
              memory: "2Gi"
              cpu: "1000m"
            limits:
              memory: "4Gi"
              cpu: "2000m"
          env:
            - name: WSO2_FS_DB_URL
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: db-url
            - name: WSO2_FS_DB_USER
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: db-username
            - name: WSO2_FS_DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: db-password
          volumeMounts:
            - name: financial-services-conf
              mountPath: /home/wso2carbon/wso2-config-volume/repository/conf/financial-services.xml
              subPath: financial-services.xml
            - name: keystores
              mountPath: /home/wso2carbon/wso2-config-volume/repository/resources/security
              readOnly: true
          livenessProbe:
            exec:
              command:
                - /bin/sh
                - -c
                - nc -z localhost 9443
            initialDelaySeconds: 120
            periodSeconds: 30
            failureThreshold: 3
          readinessProbe:
            exec:
              command:
                - /bin/sh
                - -c
                - curl -k -s https://localhost:9443/carbon/
            initialDelaySeconds: 90
            periodSeconds: 15
            failureThreshold: 3
      volumes:
        - name: financial-services-conf
          configMap:
            name: is-financial-services-conf
        - name: keystores
          secret:
            secretName: is-keystores
      securityContext:
        runAsNonRoot: true
        runAsUser: 802
        fsGroup: 802
```

### 13.5 Service — Identity Server

```yaml
# service-identity-server.yaml
apiVersion: v1
kind: Service
metadata:
  name: identity-server
  namespace: fs-identity
  labels:
    app: wso2-identity-server
spec:
  type: ClusterIP
  selector:
    app: wso2-identity-server
  ports:
    - name: https
      port: 9443
      targetPort: 9443
    - name: consent-api
      port: 9446
      targetPort: 9446
---
apiVersion: v1
kind: Service
metadata:
  name: identity-server-lb
  namespace: fs-identity
  annotations:
    # AWS: service.beta.kubernetes.io/aws-load-balancer-type: "nlb"
    # OpenShift: route.openshift.io/termination: "passthrough"
spec:
  type: LoadBalancer
  selector:
    app: wso2-identity-server
  ports:
    - name: https
      port: 443
      targetPort: 9443
```

### 13.6 HorizontalPodAutoscaler

```yaml
# hpa-identity-server.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: wso2-identity-server-hpa
  namespace: fs-identity
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: wso2-identity-server
  minReplicas: 2
  maxReplicas: 6
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 75
```

### 13.7 NetworkPolicy — Microsegmentación

```yaml
# networkpolicy-fs-identity.yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: fs-identity-netpol
  namespace: fs-identity
spec:
  podSelector:
    matchLabels:
      app: wso2-identity-server
  policyTypes:
    - Ingress
    - Egress
  ingress:
    # Permitir desde Ingress Controller
    - from:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: ingress-system
      ports:
        - port: 9443
    # Permitir desde APIM Gateway (consent validation + token introspection)
    - from:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: fs-apim
      ports:
        - port: 9443
        - port: 9446
    # Permitir desde Self-Care Portal
    - from:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: fs-portal
      ports:
        - port: 9443
        - port: 9446
  egress:
    # Permitir a base de datos
    - to:
        - namespaceSelector:
            matchLabels:
              kubernetes.io/metadata.name: fs-database
      ports:
        - port: 3306
        - port: 5432
    # Permitir DNS
    - to:
        - namespaceSelector: {}
      ports:
        - port: 53
          protocol: UDP
```

### 13.8 Ingress — TLS Termination

```yaml
# ingress-financial-services.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: financial-services-ingress
  namespace: fs-identity
  annotations:
    nginx.ingress.kubernetes.io/backend-protocol: "HTTPS"
    nginx.ingress.kubernetes.io/ssl-passthrough: "true"
    # Para mTLS en APIM Gateway usar passthrough o configurar en APIM
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - identity.bank.com
      secretName: identity-bank-tls
  rules:
    - host: identity.bank.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: identity-server
                port:
                  number: 9443
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: apim-ingress
  namespace: fs-apim
  annotations:
    nginx.ingress.kubernetes.io/backend-protocol: "HTTPS"
    nginx.ingress.kubernetes.io/ssl-passthrough: "true"
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - api.bank.com
      secretName: api-bank-tls
  rules:
    - host: api.bank.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: apim-gateway
                port:
                  number: 8243
```

---

## Apéndice A — Variables de Entorno Clave

| Variable | Componente | Descripción |
|---|---|---|
| `WSO2_FS_DB_URL` | IS, APIM | JDBC URL para WSO2FS_DB |
| `WSO2_IS_DB_URL` | IS | JDBC URL para WSO2IS_DB |
| `WSO2_AM_DB_URL` | APIM | JDBC URL para WSO2AM_DB |
| `CARBON_HOME` | IS, APIM | Directorio raíz del servidor WSO2 |
| `JAVA_OPTS` | IS, APIM | `-Xms2g -Xmx4g -XX:+UseG1GC` |

## Apéndice B — Imágenes Docker Recomendadas

```
# Base Images (customizar con artefactos FS)
wso2/wso2am:4.3.0           -> + fs-apim accelerator artifacts
wso2/wso2is:7.0.0            -> + fs-is accelerator artifacts
mysql:8.0                    -> WSO2FS_DB, WSO2IS_DB, WSO2AM_DB
node:20-alpine               -> Self-Care Portal build
```

## Apéndice C — Puertos y Endpoints de Referencia

| Endpoint | Descripción |
|---|---|
| `https://identity.bank.com/oauth2/token` | Token endpoint (OAuth2) |
| `https://identity.bank.com/oauth2/authorize` | Authorization endpoint |
| `https://identity.bank.com/api/fs/consent/v1` | Consent Management API |
| `https://identity.bank.com/api/fs/event-notifications/v1` | Event Notifications API |
| `https://identity.bank.com/keymanager-operations/dcr/register` | DCR endpoint |
| `https://api.bank.com/open-banking/v3.1/` | Open Banking APIs (via APIM Gateway) |
| `https://portal.bank.com` | Self-Care Portal (React) |
