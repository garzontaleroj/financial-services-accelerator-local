package io.openbanking.events.api;

import io.openbanking.events.model.EventSubscriptionEntity;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Map;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.inject.Inject;

/**
 * Event Subscription endpoint — Quarkus rewrite of WSO2 EventSubscriptionEndpoint.
 * Path: /api/fs/events/v1.0/subscription
 */
@Path("/api/fs/events/v1.0/subscription")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Event Subscriptions", description = "TPP webhook subscriptions for Open Banking events")
public class EventSubscriptionResource {

    @Inject JsonWebToken jwt;

    @POST
    @Transactional
    @Operation(summary = "Register a new event subscription")
    public Response createSubscription(Map<String, Object> request) {

        var sub = new EventSubscriptionEntity();
        sub.clientId    = jwt.getClaim("azp");
        sub.callbackUrl = (String) request.get("CallbackUrl");
        sub.eventTypes  = request.getOrDefault("EventTypes", "[]").toString();
        sub.version     = (String) request.getOrDefault("Version", "v4.0");
        sub.persist();

        return Response.created(URI.create("/api/fs/events/v1.0/subscription/" + sub.subscriptionId))
                .entity(Map.of(
                    "Data", Map.of(
                        "SubscriptionId", sub.subscriptionId,
                        "CallbackUrl",    sub.callbackUrl != null ? sub.callbackUrl : "",
                        "EventTypes",     sub.eventTypes,
                        "Version",        sub.version
                    )
                )).build();
    }

    @GET
    @Path("/{subscriptionId}")
    @Operation(summary = "Get an event subscription")
    public Response getSubscription(@PathParam("subscriptionId") String subscriptionId) {
        return EventSubscriptionEntity.findBySubscriptionId(subscriptionId)
                .map(s -> Response.ok(Map.of("Data", Map.of(
                    "SubscriptionId", s.subscriptionId,
                    "CallbackUrl",    s.callbackUrl != null ? s.callbackUrl : "",
                    "EventTypes",     s.eventTypes,
                    "Version",        s.version
                ))).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @PUT
    @Path("/{subscriptionId}")
    @Transactional
    @Operation(summary = "Update an event subscription")
    public Response updateSubscription(@PathParam("subscriptionId") String subscriptionId,
                                       Map<String, Object> request) {
        return EventSubscriptionEntity.findBySubscriptionId(subscriptionId)
                .map(s -> {
                    s.callbackUrl = (String) request.getOrDefault("CallbackUrl", s.callbackUrl);
                    s.eventTypes  = request.getOrDefault("EventTypes", s.eventTypes).toString();
                    return Response.ok(Map.of("Data", Map.of(
                        "SubscriptionId", s.subscriptionId,
                        "CallbackUrl",    s.callbackUrl,
                        "EventTypes",     s.eventTypes
                    ))).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{subscriptionId}")
    @Transactional
    @Operation(summary = "Delete an event subscription")
    public Response deleteSubscription(@PathParam("subscriptionId") String subscriptionId) {
        long deleted = EventSubscriptionEntity.delete("subscriptionId", subscriptionId);
        return deleted > 0 ? Response.noContent().build()
                           : Response.status(Response.Status.NOT_FOUND).build();
    }
}
