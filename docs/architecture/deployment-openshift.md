# Guía de Despliegue — OpenShift

> **WSO2 Financial Services Accelerator 4.0.0**  
> Guía específica para Red Hat OpenShift Container Platform (OCP) 4.x

---

## Prerrequisitos

| Requisito | Versión Mínima |
|---|---|
| OpenShift Container Platform | 4.12+ |
| `oc` CLI | 4.12+ |
| Helm | 3.12+ |
| WSO2 API Manager | 4.3.0 |
| WSO2 Identity Server | 7.0.0 |
| MySQL / PostgreSQL | 8.0 / 14+ |

---

## 1. Preparación del Clúster OpenShift

### 1.1 Crear Proyectos (Namespaces)

```bash
# Crear proyectos OpenShift
oc new-project fs-identity --display-name="FS Identity Server"
oc new-project fs-apim     --display-name="FS API Manager"
oc new-project fs-portal   --display-name="FS Self-Care Portal"
oc new-project fs-database --display-name="FS Databases"

# Etiquetar para seguridad
oc label namespace fs-identity app.kubernetes.io/part-of=financial-services
oc label namespace fs-apim     app.kubernetes.io/part-of=financial-services
```

### 1.2 Security Context Constraints (SCC)

OpenShift requiere SCC específicas para WSO2 (ejecuta como UID 802):

```bash
# Crear SCC personalizada para WSO2
cat <<EOF | oc apply -f -
apiVersion: security.openshift.io/v1
kind: SecurityContextConstraints
metadata:
  name: wso2-scc
allowPrivilegeEscalation: false
allowPrivilegedContainer: false
allowedCapabilities: []
defaultAddCapabilities: []
fsGroup:
  type: MustRunAs
  ranges:
    - min: 802
      max: 802
readOnlyRootFilesystem: false
requiredDropCapabilities:
  - ALL
runAsUser:
  type: MustRunAsRange
  uidRangeMin: 802
  uidRangeMax: 802
seLinuxContext:
  type: MustRunAs
supplementalGroups:
  type: RunAsAny
volumes:
  - configMap
  - secret
  - persistentVolumeClaim
  - emptyDir
EOF

# Asignar SCC a ServiceAccounts
oc create serviceaccount wso2-sa -n fs-identity
oc create serviceaccount wso2-sa -n fs-apim
oc adm policy add-scc-to-user wso2-scc -z wso2-sa -n fs-identity
oc adm policy add-scc-to-user wso2-scc -z wso2-sa -n fs-apim
```

### 1.3 Crear Secrets para Keystores

```bash
# Crear secret con los keystores de WSO2 IS
oc create secret generic is-keystores \
  --from-file=wso2carbon.jks=/path/to/wso2carbon.jks \
  --from-file=client-truststore.jks=/path/to/client-truststore.jks \
  -n fs-identity

# Crear secret con credenciales de base de datos
oc create secret generic db-credentials \
  --from-literal=db-url="jdbc:mysql://fs-database.fs-database.svc.cluster.local:3306/WSO2FS_DB?useSSL=true" \
  --from-literal=db-username="wso2fsuser" \
  --from-literal=db-password="<YOUR_SECURE_PASSWORD>" \
  -n fs-identity
```

---

## 2. Despliegue de Base de Datos

### 2.1 MySQL StatefulSet

```bash
# Aplicar manifiestos de base de datos
oc apply -f - <<EOF
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-pvc
  namespace: fs-database
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 50Gi
  storageClassName: gp2  # Ajustar según StorageClass disponible
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mysql-fs
  namespace: fs-database
spec:
  serviceName: mysql-fs
  replicas: 1
  selector:
    matchLabels:
      app: mysql-fs
  template:
    metadata:
      labels:
        app: mysql-fs
    spec:
      containers:
        - name: mysql
          image: mysql:8.0
          env:
            - name: MYSQL_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: mysql-root-secret
                  key: password
            - name: MYSQL_DATABASE
              value: WSO2FS_DB
          ports:
            - containerPort: 3306
          volumeMounts:
            - name: mysql-data
              mountPath: /var/lib/mysql
            - name: mysql-init
              mountPath: /docker-entrypoint-initdb.d
      volumes:
        - name: mysql-data
          persistentVolumeClaim:
            claimName: mysql-pvc
        - name: mysql-init
          configMap:
            name: mysql-init-scripts
---
apiVersion: v1
kind: Service
metadata:
  name: fs-database
  namespace: fs-database
spec:
  selector:
    app: mysql-fs
  ports:
    - port: 3306
      targetPort: 3306
  clusterIP: None  # Headless service para StatefulSet
EOF
```

### 2.2 Inicializar Esquemas de Base de Datos

```bash
# Ejecutar scripts de creación de tablas (desde el acelerador)
# Los scripts están en:
# financial-services-accelerator/accelerators/fs-is/carbon-home/dbscripts/financial-services/

# Copiar scripts al pod de MySQL
oc cp ./financial-services-accelerator/accelerators/fs-is/carbon-home/dbscripts/financial-services/ \
  fs-database/mysql-fs-0:/tmp/dbscripts

# Ejecutar scripts en MySQL
oc exec -it mysql-fs-0 -n fs-database -- \
  mysql -u root -p WSO2FS_DB < /tmp/dbscripts/consent/mysql.sql

oc exec -it mysql-fs-0 -n fs-database -- \
  mysql -u root -p WSO2FS_DB < /tmp/dbscripts/event-notifications/mysql.sql
```

---

## 3. Despliegue de WSO2 Identity Server con FS Accelerator

### 3.1 Construir Imagen con Acelerador

```dockerfile
# Dockerfile para IS con FS Accelerator
FROM wso2/wso2is:7.0.0

# Copiar artefactos del acelerador FS IS
COPY wso2-fsiam-accelerator-4.0.0/dropins/*.jar \
     ${WSO2_SERVER_HOME}/repository/components/dropins/

COPY wso2-fsiam-accelerator-4.0.0/webapps/*.war \
     ${WSO2_SERVER_HOME}/repository/deployment/server/webapps/

COPY wso2-fsiam-accelerator-4.0.0/conf/ \
     ${WSO2_SERVER_HOME}/repository/conf/

# Config financiera (se sobreescribe vía ConfigMap en K8s)
USER wso2carbon
```

```bash
# Build y push de imagen
docker build -t registry.company.com/wso2/wso2is-fs:7.0.0-4.0.0 .
docker push registry.company.com/wso2/wso2is-fs:7.0.0-4.0.0
```

### 3.2 Deployment de Identity Server en OpenShift

```bash
oc apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wso2-identity-server
  namespace: fs-identity
spec:
  replicas: 2
  selector:
    matchLabels:
      app: wso2-identity-server
  template:
    metadata:
      labels:
        app: wso2-identity-server
    spec:
      serviceAccountName: wso2-sa
      securityContext:
        runAsUser: 802
        runAsGroup: 802
        fsGroup: 802
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchLabels:
                  app: wso2-identity-server
              topologyKey: kubernetes.io/hostname
      containers:
        - name: wso2is
          image: registry.company.com/wso2/wso2is-fs:7.0.0-4.0.0
          ports:
            - containerPort: 9443
            - containerPort: 9446
          resources:
            requests:
              cpu: "1000m"
              memory: "2Gi"
            limits:
              cpu: "2000m"
              memory: "4Gi"
          env:
            - name: JAVA_OPTS
              value: "-Xms2g -Xmx3g -XX:+UseG1GC -Dfile.encoding=UTF8"
          volumeMounts:
            - name: fs-conf
              mountPath: /home/wso2carbon/wso2-config-volume/repository/conf/financial-services.xml
              subPath: financial-services.xml
            - name: keystores
              mountPath: /home/wso2carbon/wso2-config-volume/repository/resources/security
              readOnly: true
          livenessProbe:
            tcpSocket:
              port: 9443
            initialDelaySeconds: 150
            periodSeconds: 30
          readinessProbe:
            httpGet:
              path: /carbon/
              port: 9443
              scheme: HTTPS
            initialDelaySeconds: 120
            periodSeconds: 20
      volumes:
        - name: fs-conf
          configMap:
            name: is-financial-services-conf
        - name: keystores
          secret:
            secretName: is-keystores
EOF
```

### 3.3 Route de OpenShift para Identity Server

```bash
oc apply -f - <<EOF
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: identity-server
  namespace: fs-identity
spec:
  host: identity.bank.com
  port:
    targetPort: 9443
  to:
    kind: Service
    name: identity-server
  tls:
    termination: reencrypt
    insecureEdgeTerminationPolicy: Redirect
    # destinationCACertificate: <WSO2 IS CA cert>
EOF
```

---

## 4. Despliegue de WSO2 API Manager con FS Accelerator

### 4.1 Deployment del Gateway

```bash
oc apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wso2-apim-gateway
  namespace: fs-apim
spec:
  replicas: 2
  selector:
    matchLabels:
      app: wso2-apim-gateway
  template:
    metadata:
      labels:
        app: wso2-apim-gateway
    spec:
      serviceAccountName: wso2-sa
      securityContext:
        runAsUser: 802
        runAsGroup: 802
        fsGroup: 802
      containers:
        - name: wso2apim
          image: registry.company.com/wso2/wso2am-fs:4.3.0-4.0.0
          ports:
            - containerPort: 8243
              name: https-gateway
            - containerPort: 8280
              name: http-gateway
            - containerPort: 9443
              name: https-cp
          resources:
            requests:
              cpu: "1000m"
              memory: "2Gi"
            limits:
              cpu: "2000m"
              memory: "4Gi"
          env:
            - name: JAVA_OPTS
              value: "-Xms2g -Xmx3g -XX:+UseG1GC"
          volumeMounts:
            - name: fs-conf-apim
              mountPath: /home/wso2carbon/wso2-config-volume/repository/conf/financial-services.xml
              subPath: financial-services.xml
            - name: keystores-apim
              mountPath: /home/wso2carbon/wso2-config-volume/repository/resources/security
              readOnly: true
      volumes:
        - name: fs-conf-apim
          configMap:
            name: apim-financial-services-conf
        - name: keystores-apim
          secret:
            secretName: apim-keystores
EOF
```

### 4.2 Route para APIM Gateway (passthrough mTLS)

```bash
oc apply -f - <<EOF
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: apim-gateway
  namespace: fs-apim
spec:
  host: api.bank.com
  port:
    targetPort: 8243
  to:
    kind: Service
    name: apim-gateway
  tls:
    termination: passthrough   # mTLS requiere passthrough
    insecureEdgeTerminationPolicy: Redirect
EOF
```

---

## 5. Despliegue del Self-Care Portal

```bash
oc apply -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: self-care-portal
  namespace: fs-portal
spec:
  replicas: 2
  selector:
    matchLabels:
      app: self-care-portal
  template:
    metadata:
      labels:
        app: self-care-portal
    spec:
      containers:
        - name: self-care-portal
          image: registry.company.com/fs/self-care-portal:4.0.0
          ports:
            - containerPort: 3000
          env:
            - name: REACT_APP_IS_URL
              value: "https://identity.bank.com"
            - name: REACT_APP_CONSENT_API_URL
              value: "https://identity.bank.com/api/fs/consent/v1"
          resources:
            requests:
              cpu: "200m"
              memory: "256Mi"
            limits:
              cpu: "500m"
              memory: "512Mi"
---
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: self-care-portal
  namespace: fs-portal
spec:
  host: portal.bank.com
  port:
    targetPort: 3000
  to:
    kind: Service
    name: self-care-portal
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
EOF
```

---

## 6. Verificación del Despliegue

```bash
# Verificar estado de pods
oc get pods -n fs-identity
oc get pods -n fs-apim
oc get pods -n fs-portal

# Verificar endpoints
oc get routes -n fs-identity
oc get routes -n fs-apim
oc get routes -n fs-portal

# Health check Identity Server
curl -k https://identity.bank.com/carbon/

# Health check APIM Gateway
curl -k https://api.bank.com/

# Test DCR endpoint
curl -k -X GET https://identity.bank.com/keymanager-operations/dcr/register \
  --cert /path/to/tpp-cert.pem \
  --key /path/to/tpp-key.pem

# Verificar logs
oc logs -f deployment/wso2-identity-server -n fs-identity
oc logs -f deployment/wso2-apim-gateway -n fs-apim
```

---

## 7. Operaciones Day-2

### Escalado Manual

```bash
oc scale deployment wso2-identity-server --replicas=4 -n fs-identity
oc scale deployment wso2-apim-gateway --replicas=4 -n fs-apim
```

### Actualización Rolling

```bash
oc set image deployment/wso2-identity-server \
  wso2is=registry.company.com/wso2/wso2is-fs:7.0.0-4.1.0 \
  -n fs-identity
```

### Backup de Base de Datos

```bash
# Backup de WSO2FS_DB
oc exec mysql-fs-0 -n fs-database -- \
  mysqldump -u root -p WSO2FS_DB > backup-$(date +%Y%m%d).sql
```
