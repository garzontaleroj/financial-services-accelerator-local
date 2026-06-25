package io.openbanking.events.kafka;

import io.openbanking.events.model.EventNotificationEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Map;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consumes consent lifecycle events from AMQ Streams (Kafka topic: ob-consent-events)
 * and persists them as EventNotificationEntity for TPP polling.
 *
 * Replaces WSO2 EventNotificationProducerServiceImpl.
 */
@ApplicationScoped
public class ConsentEventConsumer {

    private static final Logger log = Logger.getLogger(ConsentEventConsumer.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Incoming("consent-events-in")
    @Transactional
    public void onConsentEvent(Map<String, Object> event) {
        try {
            String clientId   = (String) event.get("clientId");
            String consentId  = (String) event.get("consentId");
            String eventType  = (String) event.get("eventType");

            if (clientId == null || eventType == null) return;

            // Build a minimal SET (Security Event Token) payload — RFC 8417
            var setPayload = Map.of(
                "iss",  "https://openbanking.example.com",
                "iat",  System.currentTimeMillis() / 1000,
                "jti",  java.util.UUID.randomUUID().toString(),
                "sub",  consentId,
                "aud",  clientId,
                "txn",  consentId,
                "toe",  System.currentTimeMillis() / 1000,
                "events", Map.of(
                    "urn:uk:org:openbanking:events:" + toEventUrn(eventType),
                    Map.of("subject", Map.of(
                        "subject_type", "http://openbanking.org.uk/rid_http://openbanking.org.uk/rty",
                        "http://openbanking.org.uk/rid", consentId,
                        "http://openbanking.org.uk/rty", "domestic-payment"
                    ))
                )
            );

            var notification = new EventNotificationEntity();
            notification.clientId   = clientId;
            notification.setPayload = mapper.writeValueAsString(setPayload);
            notification.persist();

            log.infof("Persisted event notification for client %s, event %s", clientId, eventType);

        } catch (Exception e) {
            log.errorf("Failed to process consent event: %s", e.getMessage());
        }
    }

    private String toEventUrn(String eventType) {
        return switch (eventType) {
            case "CONSENT_AUTHORISED" -> "consent-authorization-accepted";
            case "CONSENT_REJECTED"   -> "consent-authorization-revoked";
            case "CONSENT_REVOKED"    -> "resource-update";
            default                   -> "resource-update";
        };
    }
}
