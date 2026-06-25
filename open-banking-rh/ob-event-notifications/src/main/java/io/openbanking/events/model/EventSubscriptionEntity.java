package io.openbanking.events.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Replaces WSO2 EventSubscriptionDAO.
 * Stores TPP webhook subscriptions for Open Banking event types.
 */
@Entity
@Table(name = "ob_event_subscription",
       indexes = @Index(name = "idx_sub_client", columnList = "client_id"))
public class EventSubscriptionEntity extends PanacheEntityBase {

    @Id
    @Column(name = "subscription_id", length = 36)
    public String subscriptionId;

    @Column(name = "client_id", length = 255, nullable = false)
    public String clientId;

    /** Webhook URL where the bank will POST event notifications. */
    @Column(name = "callback_url", length = 1024)
    public String callbackUrl;

    /**
     * JSON array of event type strings the TPP wants to receive.
     * E.g.: ["urn:uk:org:openbanking:events:resource-update",
     *         "urn:uk:org:openbanking:events:consent-authorization-revoked"]
     */
    @Column(name = "event_types", columnDefinition = "TEXT")
    public String eventTypes;

    @Column(name = "version", length = "20")
    public String version = "v4.0";

    @Column(name = "created_timestamp", nullable = false, updatable = false)
    public Instant createdTimestamp;

    @PrePersist
    void onCreate() {
        if (subscriptionId == null) subscriptionId = UUID.randomUUID().toString();
        createdTimestamp = Instant.now();
    }

    public static Optional<EventSubscriptionEntity> findBySubscriptionId(String id) {
        return find("subscriptionId", id).firstResultOptional();
    }

    public static List<EventSubscriptionEntity> findByClientId(String clientId) {
        return list("clientId", clientId);
    }
}
