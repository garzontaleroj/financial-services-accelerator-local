package io.openbanking.backend.api;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Mock Funds Confirmation API — Quarkus rewrite of WSO2 FundsConfirmationService.
 * Path: /api/fs/backend/v1.0/funds-confirmations
 */
@Path("/api/fs/backend/v1.0/funds-confirmations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Funds Confirmation", description = "Open Banking UK — Confirmation of Funds")
public class FundsConfirmationResource {

    @POST
    @Operation(summary = "Check if funds are available in an account")
    public Response checkFunds(Map<String, Object> request) {

        // In production: call core banking to check actual balance
        var response = Map.of(
            "Data", Map.of(
                "FundsConfirmationId", UUID.randomUUID().toString(),
                "ConsentId",           request.getOrDefault("ConsentId", "UNKNOWN"),
                "CreationDateTime",    "2020-04-16T06:06:06+00:00",
                "Reference",           request.getOrDefault("Reference", ""),
                "FundsAvailable",      true
            ),
            "Links", Map.of("Self", "/api/fs/backend/v1.0/funds-confirmations"),
            "Meta",  Map.of()
        );
        return Response.ok(response).build();
    }
}
