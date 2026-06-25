# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Build the FS IS accelerator artifacts with Maven
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-11 AS builder

WORKDIR /build

# Dependency layer: root + accelerator poms
COPY pom.xml .
COPY financial-services-accelerator/pom.xml financial-services-accelerator/
COPY financial-services-accelerator/accelerators/pom.xml financial-services-accelerator/accelerators/
COPY financial-services-accelerator/accelerators/fs-is/ financial-services-accelerator/accelerators/fs-is/
COPY financial-services-accelerator/components/ financial-services-accelerator/components/
COPY financial-services-accelerator/internal-webapps/ financial-services-accelerator/internal-webapps/

# Build IS accelerator + all internal web apps (consent, events, auth, demo)
RUN mvn clean install \
    -pl financial-services-accelerator/accelerators/fs-is \
    -am -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Runtime image based on WSO2 Identity Server
# ─────────────────────────────────────────────────────────────────────────────
FROM wso2/wso2is:7.1.0

ARG ACCELERATOR_VERSION=4.1.2-SNAPSHOT
ENV WSO2_IS_HOME=/home/wso2carbon/wso2is-7.1.0
ENV FS_ACC_HOME=/home/wso2carbon/fs-is-accelerator

USER root

# Copy built accelerator zip
COPY --from=builder /build/financial-services-accelerator/accelerators/fs-is/target/wso2-fsiam-accelerator-*.zip /tmp/fs-is-accelerator.zip

# Copy internal web apps (WAR files) that deploy inside IS
COPY --from=builder /build/financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.consent.mgt.endpoint/target/*.war \
    ${WSO2_IS_HOME}/repository/deployment/server/webapps/api#financial-services#consent#v1.war
COPY --from=builder /build/financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.event.notifications.endpoint/target/*.war \
    ${WSO2_IS_HOME}/repository/deployment/server/webapps/api#financial-services#event-notifications#v1.war
COPY --from=builder /build/financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.authentication.endpoint/target/*.war \
    ${WSO2_IS_HOME}/repository/deployment/server/webapps/authenticationendpoint.war
COPY --from=builder /build/financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.demo.backend/target/*.war \
    ${WSO2_IS_HOME}/repository/deployment/server/webapps/demo-backend.war

# Extract accelerator and run merge
RUN mkdir -p ${FS_ACC_HOME} && \
    unzip -q /tmp/fs-is-accelerator.zip -d ${FS_ACC_HOME} && \
    rm /tmp/fs-is-accelerator.zip && \
    chmod +x ${FS_ACC_HOME}/bin/merge.sh && \
    bash ${FS_ACC_HOME}/bin/merge.sh ${WSO2_IS_HOME}

# Expose WSO2 IS ports
# 9443 – HTTPS (management console / REST APIs / OIDC)
# 9763 – HTTP
EXPOSE 9443 9763

USER wso2carbon

HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
    CMD curl -sk https://localhost:9443/carbon/admin/login.jsp | grep -q "WSO2" || exit 1

ENTRYPOINT ["sh", "-c", "${WSO2_IS_HOME}/bin/wso2server.sh"]
