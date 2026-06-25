package io.openbanking.consent.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA entity for Open Banking consents.
 *
 * Replaces WSO2's consent.mgt.dao ConsentCore table + ConsentMappingTable.
 * Uses Panache Active Record pattern — no separate Repository class needed.
 */
@Entity
@Table(
    name = "ob_consent",
    indexes = {
        @Index(name = "idx_consent_client",  columnList = "client_id"),
        @Index(name = "idx_consent_user",    columnList = "user_id"),
        @Index(name = "idx_consent_status",  columnList = "status"),
        @Index(name = "idx_consent_type",    columnList = "consent_type")
    }
)
public class ConsentEntity extends PanacheEntityBase {

    @Id
    @Column(name = "consent_id", length = 36, nullable = false, updatable = false)
    public String consentId;

    /** OAuth2 client_id of the TPP that requested this consent. */
    @Column(name = "client_id", length = 255, nullable = false)
    public String clientId;

    /** Subject (sub) of the authenticated customer. Set after authorisation. */
    @Column(name = "user_id", length = 255)
    public String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", length = 50, nullable = false)
    public ConsentType consentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    public ConsentStatus status;

    /**
     * Full consent request payload stored as JSON string.
     * Replaces WSO2 ConsentResource.getReceipt().
     */
    @Column(name = "receipt", columnDefinition = "TEXT", nullable = false)
    public String receipt;

    @Column(name = "created_timestamp", nullable = false, updatable = false)
    public Instant createdTimestamp;

    @Column(name = "updated_timestamp", nullable = false)
    public Instant updatedTimestamp;

    /** Null means the consent does not expire. */
    @Column(name = "expiration_timestamp")
    public Instant expirationTimestamp;

    /** Free-form JSON for implementation-specific attributes (e.g. permissions list). */
    @Column(name = "consent_attributes", columnDefinition = "TEXT")
    public String consentAttributes;

    @PrePersist
    void onCreate() {
        if (consentId == null) {
            consentId = UUID.randomUUID().toString();
        }
        createdTimestamp = Instant.now();
        updatedTimestamp = createdTimestamp;
        if (status == null) {
            status = ConsentStatus.AWAITING_AUTHORISATION;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedTimestamp = Instant.now();
    }

    // ─── Panache query methods ────────────────────────────────────────────────

    public static Optional<ConsentEntity> findByConsentId(String consentId) {
        return find("consentId", consentId).firstResultOptional();
    }

    public static List<ConsentEntity> findByClientId(String clientId) {
        return list("clientId", clientId);
    }

    public static List<ConsentEntity> findByUserId(String userId) {
        return list("userId", userId);
    }

    public static List<ConsentEntity> findByClientAndStatus(String clientId, ConsentStatus status) {
        return list("clientId = ?1 and status = ?2", clientId, status);
    }

    public static List<ConsentEntity> search(
            String clientId, String userId, String consentType, String status) {

        StringBuilder query = new StringBuilder("1=1");
        var params = new java.util.LinkedHashMap<String, Object>();

        if (clientId != null && !clientId.isBlank()) {
            query.append(" and clientId = :clientId");
            params.put("clientId", clientId);
        }
        if (userId != null && !userId.isBlank()) {
            query.append(" and userId = :userId");
            params.put("userId", userId);
        }
        if (consentType != null && !consentType.isBlank()) {
            query.append(" and consentType = :consentType");
            params.put("consentType", ConsentType.valueOf(consentType));
        }
        if (status != null && !status.isBlank()) {
            query.append(" and status = :status");
            params.put("status", ConsentStatus.valueOf(status));
        }
        return list(query.toString(), io.quarkus.panache.common.Parameters.from(params));
    }
}
