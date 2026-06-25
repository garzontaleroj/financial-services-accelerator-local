package io.openbanking.consent.api;

import io.openbanking.consent.dto.ConsentResponse;
import io.openbanking.consent.service.ConsentService;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Consent Admin endpoint — bank-internal search and revocation.
 *
 * Replaces WSO2 ConsentAdminEndpoint.
 * Requires "ob-admin" Keycloak role (mapped via quarkus.security.roles-mapping).
 *
 * Base path: /api/fs/consent/v1.0/admin
 */
@Path("/api/fs/consent/v1.0/admin/consents")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@RolesAllowed("ob-admin")
@Tag(name = "Consent Admin", description = "Bank-internal consent search and administration")
public class ConsentAdminResource {

    @Inject
    ConsentService consentService;

    @GET
    @Operation(summary = "Search consents",
               description = "All query params are optional and combinable.")
    public List<ConsentResponse> searchConsents(
            @QueryParam("clientId")    String clientId,
            @QueryParam("userId")      String userId,
            @QueryParam("consentType") String consentType,
            @QueryParam("status")      String status) {

        return consentService.searchConsents(clientId, userId, consentType, status)
                             .stream()
                             .map(ConsentResponse::from)
                             .toList();
    }

    @GET
    @Path("/{consentId}")
    @Operation(summary = "Get a specific consent (admin view)")
    public ConsentResponse getConsent(@PathParam("consentId") String consentId) {
        return ConsentResponse.from(consentService.getConsent(consentId));
    }

    @DELETE
    @Path("/{consentId}")
    @Operation(summary = "Revoke a consent (bank-initiated)")
    public Response revokeConsent(@PathParam("consentId") String consentId) {
        consentService.revokeConsent(consentId, "bank-admin");
        return Response.noContent().build();
    }
}
