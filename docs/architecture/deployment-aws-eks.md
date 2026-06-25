# Guía de Despliegue — AWS EKS

> **WSO2 Financial Services Accelerator 4.0.0**  
> Guía específica para Amazon Elastic Kubernetes Service (EKS)

---

## Prerrequisitos

| Herramienta | Versión |
|---|---|
| AWS CLI | 2.x |
| eksctl | 0.180+ |
| kubectl | 1.28+ |
| Helm | 3.12+ |
| Terraform (opcional) | 1.6+ |

---

## 1. Crear Clúster EKS

### 1.1 Con eksctl

```bash
# cluster-config.yaml
cat <<EOF > cluster-config.yaml
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig

metadata:
  name: financial-services-cluster
  region: us-east-1
  version: "1.29"

vpc:
  cidr: "10.0.0.0/16"
  nat:
    gateway: HighlyAvailable

managedNodeGroups:
  - name: wso2-nodes
    instanceType: m5.2xlarge
    minSize: 2
    maxSize: 10
    desiredCapacity: 4
    availabilityZones:
      - us-east-1a
      - us-east-1b
    labels:
      workload: wso2
    iam:
      withAddonPolicies:
        ebs: true
        efs: true
        albIngress: true
        cloudWatch: true
        autoScaler: true

addons:
  - name: vpc-cni
  - name: coredns
  - name: kube-proxy
  - name: aws-ebs-csi-driver

cloudWatch:
  clusterLogging:
    enable: ["api", "audit", "authenticator", "controllerManager", "scheduler"]
EOF

eksctl create cluster -f cluster-config.yaml
```

### 1.2 Instalar AWS Load Balancer Controller

```bash
# Crear IAM policy para ALB Controller
curl -O https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.7.0/docs/install/iam_policy.json

aws iam create-policy \
  --policy-name AWSLoadBalancerControllerIAMPolicy \
  --policy-document file://iam_policy.json

# OIDC provider
eksctl utils associate-iam-oidc-provider \
  --region us-east-1 \
  --cluster financial-services-cluster \
  --approve

# Service Account
eksctl create iamserviceaccount \
  --cluster=financial-services-cluster \
  --namespace=kube-system \
  --name=aws-load-balancer-controller \
  --role-name "AmazonEKSLoadBalancerControllerRole" \
  --attach-policy-arn=arn:aws:iam::<ACCOUNT_ID>:policy/AWSLoadBalancerControllerIAMPolicy \
  --approve

# Instalar con Helm
helm repo add eks https://aws.github.io/eks-charts
helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=financial-services-cluster \
  --set serviceAccount.create=false \
  --set serviceAccount.name=aws-load-balancer-controller
```

---

## 2. Infraestructura de Datos — Amazon RDS

### 2.1 Crear RDS MySQL Multi-AZ con Terraform

```hcl
# rds-mysql.tf
resource "aws_db_subnet_group" "fs_db_subnet_group" {
  name       = "fs-db-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name        = "Financial Services DB Subnet Group"
    Environment = var.environment
  }
}

resource "aws_security_group" "fs_rds_sg" {
  name        = "fs-rds-sg"
  description = "Security group for FS RDS MySQL"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.eks_node_sg.id]
  }

  tags = {
    Name = "fs-rds-sg"
  }
}

resource "aws_db_instance" "fs_mysql" {
  identifier        = "financial-services-db"
  engine            = "mysql"
  engine_version    = "8.0"
  instance_class    = "db.r6g.xlarge"
  allocated_storage = 100
  storage_type      = "gp3"
  storage_encrypted = true
  kms_key_id        = aws_kms_key.fs_rds_key.arn

  db_name  = "WSO2FS_DB"
  username = "wso2admin"
  password = var.db_password  # Usar Secrets Manager en producción

  multi_az               = true
  db_subnet_group_name   = aws_db_subnet_group.fs_db_subnet_group.name
  vpc_security_group_ids = [aws_security_group.fs_rds_sg.id]

  backup_retention_period   = 7
  backup_window             = "03:00-04:00"
  maintenance_window        = "sun:04:00-sun:05:00"
  auto_minor_version_upgrade = true
  deletion_protection       = true

  enabled_cloudwatch_logs_exports = ["general", "slowquery", "error"]

  tags = {
    Name        = "fs-mysql"
    Environment = var.environment
  }
}
```

### 2.2 ElastiCache Redis para Caché

```hcl
# elasticache-redis.tf
resource "aws_elasticache_subnet_group" "fs_redis_subnet" {
  name       = "fs-redis-subnet-group"
  subnet_ids = var.private_subnet_ids
}

resource "aws_elasticache_replication_group" "fs_redis" {
  replication_group_id = "fs-redis-cluster"
  description          = "FS Accelerator Redis Cache"

  node_type            = "cache.r6g.large"
  num_cache_clusters   = 2
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.fs_redis_subnet.name
  security_group_ids = [aws_security_group.fs_redis_sg.id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = var.redis_auth_token

  automatic_failover_enabled = true

  tags = {
    Name = "fs-redis"
  }
}
```

---

## 3. AWS Secrets Manager — Gestión de Secretos

### 3.1 Crear Secretos en AWS

```bash
# Credenciales de base de datos
aws secretsmanager create-secret \
  --name "financial-services/db-credentials" \
  --description "WSO2 FS Database Credentials" \
  --secret-string '{
    "username": "wso2admin",
    "password": "<SECURE_PASSWORD>",
    "host": "<RDS_ENDPOINT>",
    "port": "3306",
    "dbname": "WSO2FS_DB"
  }'

# Keystores WSO2
aws secretsmanager create-secret \
  --name "financial-services/is-keystores" \
  --description "WSO2 IS Keystores" \
  --secret-binary fileb://wso2carbon.jks
```

### 3.2 External Secrets Operator

```bash
# Instalar External Secrets Operator
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets \
  -n external-secrets-system \
  --create-namespace

# Crear IAM role para acceso a Secrets Manager
eksctl create iamserviceaccount \
  --name external-secrets-sa \
  --namespace fs-identity \
  --cluster financial-services-cluster \
  --attach-policy-arn arn:aws:iam::aws:policy/SecretsManagerReadWrite \
  --approve
```

```yaml
# external-secret-db.yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: db-credentials
  namespace: fs-identity
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: aws-secrets-manager
    kind: ClusterSecretStore
  target:
    name: db-credentials
    creationPolicy: Owner
  data:
    - secretKey: db-url
      remoteRef:
        key: financial-services/db-credentials
        property: host
    - secretKey: db-username
      remoteRef:
        key: financial-services/db-credentials
        property: username
    - secretKey: db-password
      remoteRef:
        key: financial-services/db-credentials
        property: password
```

---

## 4. Certificados TLS con AWS ACM + cert-manager

### 4.1 cert-manager con ACM Integration

```bash
# Instalar cert-manager
helm repo add jetstack https://charts.jetstack.io
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set installCRDs=true

# Instalar AWS PCA Issuer (si se usa ACM Private CA)
helm repo add awspca https://cert-manager.github.io/aws-privateca-issuer
helm install aws-pca-issuer awspca/aws-privateca-issuer \
  --namespace cert-manager
```

```yaml
# certificate-identity.yaml
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: identity-bank-tls
  namespace: fs-identity
spec:
  secretName: identity-bank-tls
  issuerRef:
    name: letsencrypt-prod
    kind: ClusterIssuer
  dnsNames:
    - identity.bank.com
    - "*.bank.com"
  duration: 2160h  # 90 días
  renewBefore: 360h
```

---

## 5. Ingress con AWS ALB

```yaml
# ingress-alb-identity.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: fs-identity-ingress
  namespace: fs-identity
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/certificate-arn: "arn:aws:acm:us-east-1:<ACCOUNT>:certificate/<CERT_ID>"
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
    alb.ingress.kubernetes.io/backend-protocol: HTTPS
    alb.ingress.kubernetes.io/healthcheck-path: /carbon/
    alb.ingress.kubernetes.io/healthcheck-protocol: HTTPS
    alb.ingress.kubernetes.io/ssl-policy: ELBSecurityPolicy-TLS13-1-2-2021-06
    # WAF
    alb.ingress.kubernetes.io/wafv2-acl-arn: "arn:aws:wafv2:us-east-1:<ACCOUNT>:regional/webacl/fs-waf/<ID>"
spec:
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
# Para APIM Gateway con mTLS: usar NLB en lugar de ALB
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: fs-apim-ingress
  namespace: fs-apim
  annotations:
    kubernetes.io/ingress.class: alb
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
    # mTLS: pasar certificado al backend WSO2
    alb.ingress.kubernetes.io/mutual-authentication: >
      '[{"port": 443, "mode": "passthrough"}]'
    alb.ingress.kubernetes.io/certificate-arn: "arn:aws:acm:us-east-1:<ACCOUNT>:certificate/<CERT_ID>"
    alb.ingress.kubernetes.io/listen-ports: '[{"HTTPS":443}]'
spec:
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

## 6. Observabilidad con CloudWatch y Prometheus

### 6.1 CloudWatch Container Insights

```bash
# Instalar CloudWatch agent
ClusterName=financial-services-cluster
RegionName=us-east-1
FluentBitHttpPort='2020'
FluentBitReadFromHead='Off'

curl https://raw.githubusercontent.com/aws-samples/amazon-cloudwatch-container-insights/latest/k8s-deployment-manifest-templates/deployment-mode/daemonset/container-insights-monitoring/quickstart/cwagent-fluent-bit-quickstart.yaml \
  | sed "s/{{cluster_name}}/${ClusterName}/;s/{{region_name}}/${RegionName}/;s/{{http_server_toggle}}/"On"/;s/{{http_server_port}}/${FluentBitHttpPort}/;s/{{read_from_head}}/${FluentBitReadFromHead}/;s/{{read_from_tail}}/$([[ ${FluentBitReadFromHead} = 'On' ]] && echo 'Off' || echo 'On')/" \
  | kubectl apply -f -
```

### 6.2 Prometheus + Grafana

```bash
# Instalar kube-prometheus-stack
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set grafana.adminPassword=<SECURE_PASSWORD> \
  --set prometheus.prometheusSpec.retention=15d \
  --set prometheus.prometheusSpec.storageSpec.volumeClaimTemplate.spec.resources.requests.storage=50Gi
```

---

## 7. AWS WAF — Reglas para Open Banking

```hcl
# waf-rules.tf
resource "aws_wafv2_web_acl" "fs_waf" {
  name  = "financial-services-waf"
  scope = "REGIONAL"

  default_action {
    allow {}
  }

  # Regla 1: AWS Managed Rules - Core Rule Set
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 1
    override_action { none {} }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "AWSManagedRulesCommonRuleSet"
      sampled_requests_enabled   = true
    }
  }

  # Regla 2: Rate limiting para DCR endpoint
  rule {
    name     = "DCRRateLimit"
    priority = 2
    action { block {} }
    statement {
      rate_based_statement {
        limit              = 100   # 100 DCR requests por 5 min por IP
        aggregate_key_type = "IP"
        scope_down_statement {
          byte_match_statement {
            search_string         = "/keymanager-operations/dcr"
            field_to_match { uri_path {} }
            text_transformation { priority = 0 type = "NONE" }
            positional_constraint = "CONTAINS"
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "DCRRateLimit"
      sampled_requests_enabled   = true
    }
  }

  # Regla 3: AWS Managed Rules - Known Bad Inputs
  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 3
    override_action { none {} }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "AWSManagedRulesKnownBadInputsRuleSet"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "FSWebACL"
    sampled_requests_enabled   = true
  }
}
```

---

## 8. Pipeline CI/CD con AWS CodePipeline

```yaml
# buildspec-is.yml (AWS CodeBuild)
version: 0.2

phases:
  pre_build:
    commands:
      - echo Logging in to Amazon ECR...
      - aws ecr get-login-password --region $AWS_DEFAULT_REGION | docker login --username AWS --password-stdin $ECR_REGISTRY
      - IMAGE_TAG=$(echo $CODEBUILD_RESOLVED_SOURCE_VERSION | cut -c 1-7)

  build:
    commands:
      - echo Building FS IS image...
      - docker build -t $ECR_REGISTRY/wso2is-fs:$IMAGE_TAG -f Dockerfile.is .
      - docker tag $ECR_REGISTRY/wso2is-fs:$IMAGE_TAG $ECR_REGISTRY/wso2is-fs:latest

  post_build:
    commands:
      - docker push $ECR_REGISTRY/wso2is-fs:$IMAGE_TAG
      - docker push $ECR_REGISTRY/wso2is-fs:latest
      - printf '[{"name":"wso2is","imageUri":"%s"}]' $ECR_REGISTRY/wso2is-fs:$IMAGE_TAG > imagedefinitions.json
      - kubectl set image deployment/wso2-identity-server wso2is=$ECR_REGISTRY/wso2is-fs:$IMAGE_TAG -n fs-identity

artifacts:
  files: imagedefinitions.json
```

---

## 9. Verificación del Despliegue en EKS

```bash
# Verificar nodos
kubectl get nodes --show-labels

# Verificar pods en todos los namespaces FS
kubectl get pods -n fs-identity -o wide
kubectl get pods -n fs-apim -o wide
kubectl get pods -n fs-portal -o wide

# Verificar Ingress y ALB
kubectl get ingress -A
kubectl describe ingress fs-identity-ingress -n fs-identity

# Verificar RDS connectivity desde pod
kubectl run -it --rm debug \
  --image=mysql:8.0 \
  --namespace=fs-identity \
  -- mysql -h <RDS_ENDPOINT> -u wso2admin -p WSO2FS_DB

# Health checks
curl -k https://identity.bank.com/carbon/
curl -k https://api.bank.com/services/Version
curl https://portal.bank.com/

# Métricas en CloudWatch
aws cloudwatch get-metric-statistics \
  --namespace ContainerInsights \
  --metric-name pod_cpu_utilization \
  --dimensions Name=ClusterName,Value=financial-services-cluster \
  --start-time $(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S) \
  --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
  --period 300 \
  --statistics Average
```

---

## 10. Costos Estimados (us-east-1)

| Recurso | Tipo | Costo Estimado/mes |
|---|---|---|
| EKS Cluster | Control Plane | ~$73 |
| EC2 Nodes (4x m5.2xlarge) | Nodos EKS | ~$1,100 |
| RDS MySQL (db.r6g.xlarge Multi-AZ) | Base de datos | ~$460 |
| ElastiCache Redis (cache.r6g.large x2) | Caché | ~$200 |
| ALB (2 load balancers) | Networking | ~$50 |
| ACM Certificates | TLS | Gratis |
| CloudWatch Logs + Metrics | Observabilidad | ~$50 |
| AWS WAF | Seguridad | ~$30 |
| **Total estimado** | | **~$1,963/mes** |

> Costos aproximados. Usar [AWS Pricing Calculator](https://calculator.aws) para estimaciones precisas.
> Considerar Reserved Instances para reducir costos hasta 40%.
