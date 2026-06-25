# Índice de Documentación — Arquitectura de Referencia

> **WSO2 Financial Services Accelerator 4.0.0**  
> Arquitectura de Referencia para Despliegue en Contenedores

---

## Documentos de Arquitectura

| Documento | Descripción |
|---|---|
| [Arquitectura de Referencia](./reference-architecture.md) | Arquitectura completa con diagramas lógicos, de componentes, flujos, datos y manifiestos K8s |
| [Despliegue en OpenShift](./deployment-openshift.md) | Guía paso a paso para Red Hat OpenShift Container Platform 4.x |
| [Despliegue en AWS EKS](./deployment-aws-eks.md) | Guía paso a paso para Amazon Elastic Kubernetes Service |

---

## Resumen de Componentes

```
WSO2 Financial Services Accelerator
│
├── 🔐 Plano de Identidad (WSO2 IS + fs-is)
│   ├── OAuth2 / OIDC Authorization Server
│   ├── Dynamic Client Registration (DCR)
│   ├── Consent Management API        → /api/fs/consent/v1
│   ├── Event Notifications API       → /api/fs/event-notifications/v1
│   ├── Authentication Webapp         → /authenticationendpoint
│   └── Self-Care Portal (React)      → portal.bank.com
│
├── 🌐 Plano de API (WSO2 APIM + fs-apim)
│   ├── API Gateway                   → api.bank.com:443
│   ├── Consent Enforcement Policy
│   ├── mTLS Enforcement Policy
│   ├── DCR Policy
│   ├── Dynamic Endpoint Policy
│   └── Key Manager Connector
│
└── 💾 Persistencia
    ├── WSO2FS_DB     → Consentimientos + Event Notifications
    ├── WSO2IS_DB     → Identidades + Tokens OAuth2
    ├── WSO2AM_DB     → APIs + Suscripciones
    └── WSO2SHARED_DB → Gobernanza compartida
```

---

## Plataformas Soportadas

| Plataforma | Guía | Estado |
|---|---|---|
| Kubernetes 1.26+ | [reference-architecture.md](./reference-architecture.md#13-manifiestos-kubernetes-de-referencia) | ✅ Completa |
| Red Hat OpenShift 4.x | [deployment-openshift.md](./deployment-openshift.md) | ✅ Completa |
| AWS EKS | [deployment-aws-eks.md](./deployment-aws-eks.md) | ✅ Completa |
| Azure AKS | En desarrollo | 🔄 Pendiente |
| GCP GKE | En desarrollo | 🔄 Pendiente |
