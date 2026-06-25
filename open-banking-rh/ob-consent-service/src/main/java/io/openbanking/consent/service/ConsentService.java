package io.openbanking.consent.service;

import io.openbanking.consent.dto.ConsentRequest;
import io.openbanking.consent.model.ConsentEntity;
import io.openbanking.consent.model.ConsentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Business logic for consent lifecycle management.
 *
 * Replaces WSO2 ConsentManageHandler + ConsentCoreService.
 * No Carbon, no OSGi — pure CDI bean.
 */
@ApplicationScoped
public class ConsentService {

    // ─── Create ──────────────────────────────────────────────────────────────

    @Transactional
    public ConsentEntity createConsent(String clientId, ConsentRequest request) {
        var consent = new ConsentEntity();
        consent.clientId        = clientId;
        consent.consentType     = request.consentType;
        consent.receipt         = request.receipt;
        consent.status          = ConsentStatus.AWAITING_AUTHORISATION;

        if (request.validityPeriod != null && request.validityPeriod > 0) {
            consent.expirationTimestamp = Instant.now().plusSeconds(request.validityPeriod);
        }

        consent.persist();
        return consent;
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    public ConsentEntity getConsent(String consentId) {
        return ConsentEntity.findByConsentId(consentId)
                .orElseThrow(() -> new NotFoundException("Consent not found: " + consentId));
    }

    public List<ConsentEntity> searchConsents(
            String clientId, String userId, String consentType, String status) {
        return ConsentEntity.search(clientId, userId, consentType, status);
    }

    // ─── Authorise ────────────────────────────────────────────────────────────

    /**
     * Called by Keycloak's authentication flow / Self-Care Portal after the
     * customer completes SCA and approves the consent.
     *
     * Replaces WSO2 ConsentAuthorizeEndpoint.handlePost()
     */
    @Transactional
    public ConsentEntity authoriseConsent(String consentId, String userId,
                                          Map<String, String> attributes) {
        var consent = getConsent(consentId);
        assertTransition(consent.status, ConsentStatus.AWAITING_AUTHORISATION);

        consent.status = ConsentStatus.AUTHORISED;
        consent.userId = userId;

        if (attributes != null && !attributes.isEmpty()) {
            consent.consentAttributes = new com.fasterxml.jackson.databind.ObjectMapper()
                    .valueToTree(attributes).toString();
        }
        return consent;
    }

    // ─── Reject ───────────────────────────────────────────────────────────────

    @Transactional
    public ConsentEntity rejectConsent(String consentId) {
        var consent = getConsent(consentId);
        assertTransition(consent.status, ConsentStatus.AWAITING_AUTHORISATION);
        consent.status = ConsentStatus.REJECTED;
        return consent;
    }

    // ─── Revoke ───────────────────────────────────────────────────────────────

    /**
     * Customer-initiated revocation via Self-Care Portal or API.
     * Replaces WSO2 ConsentManageEndpoint.handleDelete()
     */
    @Transactional
    public ConsentEntity revokeConsent(String consentId, String requesterId) {
        var consent = getConsent(consentId);
        if (consent.status == ConsentStatus.REVOKED
                || consent.status == ConsentStatus.EXPIRED) {
            throw new BadRequestException("Consent is already " + consent.status);
        }
        consent.status = ConsentStatus.REVOKED;
        return consent;
    }

    // ─── Validate (called by API Gateway / 3scale policy) ────────────────────

    /**
     * Lightweight validation called on every API request from a TPP.
     * Replaces WSO2 ConsentValidationEndpoint.
     */
    public boolean validateConsent(String consentId, String clientId) {
        return ConsentEntity.findByConsentId(consentId)
                .map(c -> c.clientId.equals(clientId)
                        && c.status == ConsentStatus.AUTHORISED
                        && (c.expirationTimestamp == null
                            || c.expirationTimestamp.isAfter(Instant.now())))
                .orElse(false);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void assertTransition(ConsentStatus current, ConsentStatus expected) {
        if (current != expected) {
            throw new BadRequestException(
                    "Invalid transition: consent is in state " + current
                    + ", expected " + expected);
        }
    }
}
