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
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Mock Payment Initiation API — Quarkus rewrite of WSO2 PaymentService.
 * Path: /api/fs/backend/v1.0/payments
 */
@Path("/api/fs/backend/v1.0/payments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Payments", description = "Open Banking UK — Payment Initiation")
public class PaymentResource {

    // ─── POST /domestic-payments ──────────────────────────────────────────────

    @POST
    @Path("/domestic-payments")
    @Operation(summary = "Create a domestic payment")
    public Response createDomesticPayment(Map<String, Object> paymentRequest) {

        String paymentId = UUID.randomUUID().toString();
        var response = Map.of(
            "Data", Map.of(
                "DomesticPaymentId",      paymentId,
                "ConsentId",              getOrDefault(paymentRequest, "consentId", "UNKNOWN"),
                "Status",                 "AcceptedSettlementInProcess",
                "CreationDateTime",       "2020-04-16T06:06:06+00:00",
                "StatusUpdateDateTime",   "2020-04-16T06:06:06+00:00",
                "Initiation",             paymentRequest.getOrDefault("Data",
                    Map.of("InstructedAmount", Map.of("Amount", "0.00", "Currency", "GBP")))
            ),
            "Links", Map.of("Self", "/api/fs/backend/v1.0/payments/domestic-payments/" + paymentId),
            "Meta",  Map.of()
        );

        return Response.created(URI.create("/api/fs/backend/v1.0/payments/domestic-payments/" + paymentId))
                .entity(response)
                .build();
    }

    // ─── GET /domestic-payments/{paymentId} ───────────────────────────────────

    @GET
    @Path("/domestic-payments/{paymentId}")
    @Operation(summary = "Get status of a domestic payment")
    public Response getDomesticPayment(@PathParam("paymentId") String paymentId) {

        var response = Map.of(
            "Data", Map.of(
                "DomesticPaymentId",      paymentId,
                "Status",                 "AcceptedSettlementCompleted",
                "CreationDateTime",       "2020-04-16T06:06:06+00:00",
                "StatusUpdateDateTime",   "2020-04-16T06:06:10+00:00"
            ),
            "Links", Map.of("Self", "/api/fs/backend/v1.0/payments/domestic-payments/" + paymentId),
            "Meta",  Map.of()
        );
        return Response.ok(response).build();
    }

    @SuppressWarnings("unchecked")
    private String getOrDefault(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }
}
