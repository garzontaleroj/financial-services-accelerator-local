# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 – Build the Demo Backend WAR
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-11 AS builder

WORKDIR /build

COPY pom.xml .
COPY financial-services-accelerator/pom.xml financial-services-accelerator/
COPY financial-services-accelerator/components/ financial-services-accelerator/components/
COPY financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.demo.backend/ \
     financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.demo.backend/

RUN mvn clean package \
    -pl financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.demo.backend \
    -am -DskipTests -q

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 – Deploy WAR in Tomcat
# ─────────────────────────────────────────────────────────────────────────────
FROM tomcat:9.0-jre11-temurin

LABEL maintainer="WSO2 Financial Services Accelerator" \
      description="Financial Services Demo Bank Backend (mock APIs)" \
      version="4.1.2"

ENV CATALINA_HOME=/usr/local/tomcat

RUN rm -rf ${CATALINA_HOME}/webapps/ROOT \
    ${CATALINA_HOME}/webapps/examples \
    ${CATALINA_HOME}/webapps/docs

COPY --from=builder /build/financial-services-accelerator/internal-webapps/org.wso2.financial.services.accelerator.demo.backend/target/*.war \
    ${CATALINA_HOME}/webapps/demo-backend.war

VOLUME ["/usr/local/tomcat/conf/fs"]

EXPOSE 8080

HEALTHCHECK --interval=20s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/demo-backend/ || exit 1

CMD ["catalina.sh", "run"]
