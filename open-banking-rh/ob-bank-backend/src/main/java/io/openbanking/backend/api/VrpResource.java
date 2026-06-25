package io.openbanking.backend.api;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Mock Variable Recurring Payments (VRP) API.
 * Quarkus rewrite of WSO2 VrpService.
 * Path: /api/fs/backend/v1.0/domestic-vrps
 */
@Path("/api/fs/backend/v1.0/domestic-vrps")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "VRP", description = "Open Banking UK — Variable Recurring Payments")
public class VrpResource {

    @POST
    @Operation(summary = "Create a domestic VRP payment")
    public Response createVrp(Map<String, Object> request) {
        String vrpId = UUID.randomUUID().toString();
        return Response.created(URI.create("/api/fs/backend/v1.0/domestic-vrps/" + vrpId))
                .entity(Map.of(
                    "Data", Map.of(
                        "DomesticVRPId",        vrpId,
                        "ConsentId",            request.getOrDefault("ConsentId", "UNKNOWN"),
                        "Status",               "AcceptedSettlementInProcess",
                        "CreationDateTime",     "2020-04-16T06:06:06+00:00"
                    ),
                    "Links", Map.of("Self", "/api/fs/backend/v1.0/domestic-vrps/" + vrpId),
                    "Meta",  Map.of()
                )).build();
    }

    @GET
    @Path("/{vrpId}")
    @Operation(summary = "Get VRP payment status")
    public Response getVrp(@PathParam("vrpId") String vrpId) {
        return Response.ok(Map.of(
            "Data", Map.of(
                "DomesticVRPId",      vrpId,
                "Status",             "AcceptedSettlementCompleted",
                "CreationDateTime",   "2020-04-16T06:06:06+00:00"
            ),
            "Links", Map.of("Self", "/api/fs/backend/v1.0/domestic-vrps/" + vrpId),
            "Meta",  Map.of()
        )).build();
    }

    @GET
    @Path("/{vrpId}/payment-details")
    @Operation(summary = "Get VRP payment details")
    public Response getVrpDetails(@PathParam("vrpId") String vrpId) {
        return Response.ok(Map.of(
            "Data", Map.of(
                "PaymentStatus", List.of(Map.of(
                    "PaymentTransactionId", UUID.randomUUID().toString(),
                    "Status",               "Accepted",
                    "StatusUpdateDateTime", "2020-04-16T06:06:06+00:00"
                ))
            )
        )).build();
    }
}
