package io.openbanking.consent.model;

/** Open Banking resource types that can be covered by a consent. */
public enum ConsentType {
    ACCOUNT_ACCESS,
    DOMESTIC_PAYMENT,
    DOMESTIC_SCHEDULED_PAYMENT,
    DOMESTIC_STANDING_ORDER,
    INTERNATIONAL_PAYMENT,
    FUNDS_CONFIRMATION,
    DOMESTIC_VRP
}
