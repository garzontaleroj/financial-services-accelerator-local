package io.openbanking.consent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openbanking.consent.model.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /consents.
 * Replaces the body parsed by WSO2 ConsentManageEndpoint.handlePost().
 */
public class ConsentRequest {

    @NotNull(message = "consentType is required")
    @JsonProperty("consentType")
    public ConsentType consentType;

    /**
     * Full consent resource as a JSON object (e.g. Open Banking UK Data object).
     * Stored verbatim in ConsentEntity.receipt.
     */
    @NotBlank(message = "receipt must not be empty")
    @JsonProperty("receipt")
    public String receipt;

    /**
     * Optional validity period in seconds.
     * If null, the consent does not expire automatically.
     */
    @JsonProperty("validityPeriod")
    public Long validityPeriod;
}
