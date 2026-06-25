# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Build the Demo Backend WAR + collect runtime dependencies
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy pom files first for better layer caching
COPY pom.xml .
COPY financial-services-accelerator/pom.xml financial-services-accelerator/
COPY financial-services-accelerator/components/ financial-services-accelerator/components/
COPY financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.demo.backend/ \
     financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.demo.backend/

# Build the common module first, then the demo-backend WAR
RUN mvn clean package \
    -pl financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.demo.backend \
    -am -DskipTests -q

# Collect all compile+runtime dependencies (excludes 'provided' and 'test' scopes)
RUN mvn dependency:copy-dependencies \
    -pl financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.demo.backend \
    -DoutputDirectory=/tmp/deps \
    -DexcludeScope=provided \
    -q

# The original WAR excludes WEB-INF/lib (designed for Carbon/OSGi).
# For standalone Tomcat we unpack it, inject the collected JARs, and repack.
RUN WAR=$(ls /build/financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.demo.backend/target/*.war | head -1) && \
    mkdir -p /tmp/warexpand/WEB-INF/lib && \
    unzip -q "${WAR}" -d /tmp/warexpand && \
    cp /tmp/deps/*.jar /tmp/warexpand/WEB-INF/lib/ && \
    cd /tmp/warexpand && \
    jar -cf /tmp/demo-backend.war .

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Deploy the self-contained WAR in Tomcat
# ─────────────────────────────────────────────────────────────────────────────
FROM tomcat:9.0-jre17-temurin

LABEL maintainer="WSO2 Financial Services Accelerator" \
      description="Financial Services Demo Bank Backend (mock APIs)" \
      version="4.1.2"

ENV CATALINA_HOME=/usr/local/tomcat

# Remove unused default Tomcat webapps
RUN rm -rf ${CATALINA_HOME}/webapps/ROOT \
    ${CATALINA_HOME}/webapps/examples \
    ${CATALINA_HOME}/webapps/docs

# Deploy the self-contained WAR
COPY --from=builder /tmp/demo-backend.war ${CATALINA_HOME}/webapps/demo-backend.war

EXPOSE 8080

HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/demo-backend/ || exit 1

CMD ["catalina.sh", "run"]
