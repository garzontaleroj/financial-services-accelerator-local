# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Build the accelerator artifacts with Maven
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-11 AS builder

WORKDIR /build

# Copy only the files needed for dependency resolution first (better layer caching)
COPY pom.xml .
COPY financial-services-accelerator/pom.xml financial-services-accelerator/
COPY financial-services-accelerator/accelerators/pom.xml financial-services-accelerator/accelerators/
COPY financial-services-accelerator/accelerators/fs-apim/ financial-services-accelerator/accelerators/fs-apim/
COPY financial-services-accelerator/components/ financial-services-accelerator/components/
COPY financial-services-accelerator/common-mediation-policies/ financial-services-accelerator/common-mediation-policies/

# Build only the APIM accelerator module (skip tests to speed up the image build)
RUN mvn clean install -pl financial-services-accelerator/accelerators/fs-apim \
    -am -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Runtime image based on WSO2 API Manager
# ─────────────────────────────────────────────────────────────────────────────
FROM wso2/wso2am:4.3.0

ARG ACCELERATOR_VERSION=4.1.2-SNAPSHOT
ENV WSO2_APIM_HOME=/home/wso2carbon/wso2am-4.3.0
ENV FS_ACC_HOME=/home/wso2carbon/fs-apim-accelerator

USER root

# Copy built accelerator zip from the builder stage
COPY --from=builder /build/financial-services-accelerator/accelerators/fs-apim/target/wso2-fsam-accelerator-*.zip /tmp/fs-apim-accelerator.zip

# Extract the accelerator and run merge
RUN mkdir -p ${FS_ACC_HOME} && \
    unzip -q /tmp/fs-apim-accelerator.zip -d ${FS_ACC_HOME} && \
    rm /tmp/fs-apim-accelerator.zip && \
    chmod +x ${FS_ACC_HOME}/bin/merge.sh && \
    bash ${FS_ACC_HOME}/bin/merge.sh ${WSO2_APIM_HOME}

# Expose WSO2 APIM ports
# 9443 – HTTPS management console / REST APIs
# 8243 – HTTPS Gateway
# 8280 – HTTP Gateway
# 9763 – HTTP management console
EXPOSE 9443 8243 8280 9763

USER wso2carbon

HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
    CMD curl -sk https://localhost:9443/carbon/admin/login.jsp | grep -q "WSO2" || exit 1

ENTRYPOINT ["sh", "-c", "${WSO2_APIM_HOME}/bin/wso2server.sh"]
