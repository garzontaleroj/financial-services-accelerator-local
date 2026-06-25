package io.openbanking.consent.api;

import io.openbanking.consent.dto.ConsentRequest;
import io.openbanking.consent.dto.ConsentResponse;
import io.openbanking.consent.service.ConsentService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import java.net.URI;
import java.util.Map;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Consent Manage endpoint — CRUD for consent objects.
 *
 * API-compatible replacement for WSO2 ConsentManageEndpoint.
 * Authenticated via Keycloak JWT (quarkus-oidc).
 * The TPP's client_id is extracted from the JWT "azp" (authorized party) claim.
 *
 * Base path: /api/fs/consent/v1.0
 */
@Path("/api/fs/consent/v1.0/consents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Consent Management", description = "Create, read and revoke Open Banking consents")
public class ConsentResource {

    @Inject
    ConsentService consentService;

    @Inject
    JsonWebToken jwt;

    // ─── POST /consents ───────────────────────────────────────────────────────

    @POST
    @Operation(summary = "Create a new consent",
               description = "Called by a TPP to initiate a consent. Returns AWAITING_AUTHORISATION.")
    @APIResponse(responseCode = "201", description = "Consent created")
    @APIResponse(responseCode = "400", description = "Invalid request body")
    @APIResponse(responseCode = "401", description = "Missing or invalid JWT")
    public Response createConsent(
            @Valid ConsentRequest request,
            @Context SecurityContext ctx) {

        String clientId = resolveClientId();
        var entity = consentService.createConsent(clientId, request);
        var response = ConsentResponse.from(entity);

        return Response.created(URI.create("/api/fs/consent/v1.0/consents/" + entity.consentId))
                .entity(response)
                .build();
    }

    // ─── GET /consents/{consentId} ────────────────────────────────────────────

    @GET
    @Path("/{consentId}")
    @Operation(summary = "Retrieve a consent by ID")
    @APIResponse(responseCode = "200", description = "Consent found")
    @APIResponse(responseCode = "404", description = "Consent not found")
    public ConsentResponse getConsent(
            @Parameter(description = "Consent ID (UUID)") @PathParam("consentId") String consentId) {

        return ConsentResponse.from(consentService.getConsent(consentId));
    }

    // ─── DELETE /consents/{consentId} ─────────────────────────────────────────

    @DELETE
    @Path("/{consentId}")
    @Operation(summary = "Revoke a consent",
               description = "Customer or TPP can revoke an AUTHORISED or AWAITING consent.")
    @APIResponse(responseCode = "204", description = "Consent revoked")
    @APIResponse(responseCode = "400", description = "Consent already revoked or expired")
    @APIResponse(responseCode = "404", description = "Consent not found")
    public Response revokeConsent(@PathParam("consentId") String consentId) {
        consentService.revokeConsent(consentId, resolveClientId());
        return Response.noContent().build();
    }

    // ─── PUT /consents/{consentId}/authorise ──────────────────────────────────

    @PUT
    @Path("/{consentId}/authorise")
    @Operation(summary = "Authorise a consent",
               description = "Called by the bank's auth flow after customer SCA is complete.")
    @APIResponse(responseCode = "200", description = "Consent authorised")
    public ConsentResponse authoriseConsent(
            @PathParam("consentId") String consentId,
            Map<String, String> attributes) {

        String userId = jwt.getSubject();
        return ConsentResponse.from(consentService.authoriseConsent(consentId, userId, attributes));
    }

    // ─── PUT /consents/{consentId}/reject ─────────────────────────────────────

    @PUT
    @Path("/{consentId}/reject")
    @Operation(summary = "Reject a consent during authorisation")
    @APIResponse(responseCode = "200", description = "Consent rejected")
    public ConsentResponse rejectConsent(@PathParam("consentId") String consentId) {
        return ConsentResponse.from(consentService.rejectConsent(consentId));
    }

    // ─── GET /consents/{consentId}/validate ───────────────────────────────────

    @GET
    @Path("/{consentId}/validate")
    @Operation(summary = "Validate consent (called by API Gateway on every request)",
               description = "Returns 200 if consent is AUTHORISED and not expired, 403 otherwise.")
    public Response validateConsent(@PathParam("consentId") String consentId) {
        boolean valid = consentService.validateConsent(consentId, resolveClientId());
        return valid
                ? Response.ok(Map.of("valid", true, "consentId", consentId)).build()
                : Response.status(Response.Status.FORBIDDEN)
                          .entity(Map.of("valid", false, "reason", "Consent invalid or expired"))
                          .build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Extracts the TPP client_id from the Keycloak JWT.
     * "azp" = authorized party claim (set by Keycloak for client credentials flow).
     */
    private String resolveClientId() {
        // Try azp (client credentials / code flow via Keycloak)
        String azp = jwt.getClaim("azp");
        if (azp != null && !azp.isBlank()) return azp;
        // Fallback: use sub
        return jwt.getSubject();
    }
}
