package io.openbanking.consent.events;

import io.openbanking.consent.model.ConsentEntity;
import io.openbanking.consent.model.ConsentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import java.time.Instant;
import java.util.Map;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;

/**
 * Publishes consent lifecycle events to AMQ Streams (Kafka).
 *
 * Replaces the WSO2 Event Notifications trigger inside ConsentCoreService.
 * The topic "consent-events" maps to an AMQ Streams topic in OCP.
 */
@ApplicationScoped
public class ConsentEventPublisher {

    private static final Logger log = Logger.getLogger(ConsentEventPublisher.class);

    @Channel("consent-events")
    Emitter<Map<String, Object>> emitter;

    public void publishConsentCreated(ConsentEntity consent) {
        publish("CONSENT_CREATED", consent);
    }

    public void publishConsentAuthorised(ConsentEntity consent) {
        publish("CONSENT_AUTHORISED", consent);
    }

    public void publishConsentRevoked(ConsentEntity consent) {
        publish("CONSENT_REVOKED", consent);
    }

    public void publishConsentRejected(ConsentEntity consent) {
        publish("CONSENT_REJECTED", consent);
    }

    private void publish(String eventType, ConsentEntity consent) {
        var event = Map.<String, Object>of(
            "eventType",   eventType,
            "consentId",   consent.consentId,
            "clientId",    consent.clientId,
            "userId",      consent.userId != null ? consent.userId : "",
            "status",      consent.status.name(),
            "consentType", consent.consentType.name(),
            "timestamp",   Instant.now().toString()
        );
        emitter.send(event)
               .whenComplete((v, ex) -> {
                   if (ex != null) log.warnf("Failed to publish %s for consent %s: %s",
                           eventType, consent.consentId, ex.getMessage());
               });
    }
}
