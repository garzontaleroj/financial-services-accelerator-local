package io.openbanking.consent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openbanking.consent.model.ConsentEntity;
import io.openbanking.consent.model.ConsentStatus;
import io.openbanking.consent.model.ConsentType;
import java.time.Instant;

/** Response DTO for all consent read operations. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsentResponse {

    @JsonProperty("consentId")
    public String consentId;

    @JsonProperty("clientId")
    public String clientId;

    @JsonProperty("userId")
    public String userId;

    @JsonProperty("consentType")
    public ConsentType consentType;

    @JsonProperty("status")
    public ConsentStatus status;

    @JsonProperty("receipt")
    public Object receipt;  // returned as raw JSON node

    @JsonProperty("createdTimestamp")
    public Instant createdTimestamp;

    @JsonProperty("updatedTimestamp")
    public Instant updatedTimestamp;

    @JsonProperty("expirationTimestamp")
    public Instant expirationTimestamp;

    /** Factory method — maps entity to response DTO. */
    public static ConsentResponse from(ConsentEntity e) {
        var dto = new ConsentResponse();
        dto.consentId          = e.consentId;
        dto.clientId           = e.clientId;
        dto.userId             = e.userId;
        dto.consentType        = e.consentType;
        dto.status             = e.status;
        dto.receipt            = e.receipt;          // Jackson will serialize the raw JSON string
        dto.createdTimestamp   = e.createdTimestamp;
        dto.updatedTimestamp   = e.updatedTimestamp;
        dto.expirationTimestamp = e.expirationTimestamp;
        return dto;
    }
}
