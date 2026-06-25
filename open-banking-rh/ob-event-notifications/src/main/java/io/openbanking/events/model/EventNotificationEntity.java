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
import java.util.UUID;

/**
 * Persisted event notification — created when the bank generates an event
 * (consent revoked, payment completed, etc.) and awaiting TPP polling.
 *
 * Replaces WSO2 EventNotificationDAO + EventNotificationModel.
 */
@Entity
@Table(name = "ob_event_notification",
       indexes = {
           @Index(name = "idx_notif_client", columnList = "client_id"),
           @Index(name = "idx_notif_status", columnList = "status")
       })
public class EventNotificationEntity extends PanacheEntityBase {

    @Id
    @Column(name = "notification_id", length = 36)
    public String notificationId;

    @Column(name = "client_id", length = 255, nullable = false)
    public String clientId;

    /** JWT SET (Security Event Token) payload — RFC 8417. */
    @Column(name = "set_payload", columnDefinition = "TEXT", nullable = false)
    public String setPayload;

    /**
     * OPEN — awaiting TPP poll.
     * DELIVERED — TPP acknowledged (polled successfully).
     */
    @Column(name = "status", length = 20, nullable = false)
    public String status = "OPEN";

    @Column(name = "created_timestamp", nullable = false, updatable = false)
    public Instant createdTimestamp;

    @PrePersist
    void onCreate() {
        if (notificationId == null) notificationId = UUID.randomUUID().toString();
        createdTimestamp = Instant.now();
    }

    public static List<EventNotificationEntity> findOpenByClientId(String clientId, int limit) {
        return find("clientId = ?1 and status = 'OPEN'", clientId)
                .page(0, limit)
                .list();
    }
}
