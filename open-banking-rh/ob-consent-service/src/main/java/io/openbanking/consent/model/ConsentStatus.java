package io.openbanking.consent.model;

/**
 * Consent lifecycle states — mirrors the WSO2 FS Accelerator ConsentCoreServiceConstants
 * but as a plain Java enum, no Carbon/OSGi dependency.
 */
public enum ConsentStatus {

    /** Consent created but not yet authorised by the customer. */
    AWAITING_AUTHORISATION,

    /** Customer has approved the consent — TPP can now access resources. */
    AUTHORISED,

    /** Customer rejected the consent during the authorisation flow. */
    REJECTED,

    /** Consent was revoked by the customer or by the bank admin. */
    REVOKED,

    /** Consent passed its expirationTimestamp without being renewed. */
    EXPIRED
}
