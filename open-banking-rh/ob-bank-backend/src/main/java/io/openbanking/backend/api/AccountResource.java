package io.openbanking.backend.api;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Mock Accounts API — Quarkus rewrite of WSO2 AccountService.
 *
 * Retains the same response structure as the WSO2 demo backend.
 * Path: /api/fs/backend/v1.0/accounts
 *
 * In production, replace the static data with calls to the real core banking system.
 */
@Path("/api/fs/backend/v1.0")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Accounts", description = "Open Banking UK — Account & Transaction Information")
public class AccountResource {

    // ─── GET /accounts ────────────────────────────────────────────────────────

    @GET
    @Path("/accounts")
    @Operation(summary = "Get list of authorised accounts")
    public Response getAccounts(
            @HeaderParam("x-fapi-interaction-id") String interactionId,
            @HeaderParam("Account-Request-Information") String accountRequestInfo) {

        var accounts = List.of(
            buildAccount("30080012343456", "Bills",       "SortCodeAccountNumber", "Mr Kevin"),
            buildAccount("30080098763459", "Savings",     "SortCodeAccountNumber", "Mr Kevin"),
            buildAccount("30080055500001", "Joint Bills", "SortCodeAccountNumber", "Mr Kevin & Ms Ana")
        );

        return Response.ok(Map.of(
            "Data",  Map.of("Account", accounts),
            "Links", Map.of("Self", "https://api.alphabank.com/open-banking/v4.0/accounts"),
            "Meta",  Map.of("TotalPages", 1)
        )).header("x-fapi-interaction-id", resolveInteractionId(interactionId))
          .build();
    }

    // ─── GET /accounts/{accountId} ────────────────────────────────────────────

    @GET
    @Path("/accounts/{accountId}")
    @Operation(summary = "Get a specific account by ID")
    public Response getAccount(@PathParam("accountId") String accountId,
                               @HeaderParam("x-fapi-interaction-id") String interactionId) {

        var account = buildAccount(accountId, "Bills", "SortCodeAccountNumber", "Mr Kevin");
        return Response.ok(Map.of(
            "Data",  Map.of("Account", List.of(account)),
            "Links", Map.of("Self", "https://api.alphabank.com/open-banking/v4.0/accounts/" + accountId),
            "Meta",  Map.of("TotalPages", 1)
        )).header("x-fapi-interaction-id", resolveInteractionId(interactionId))
          .build();
    }

    // ─── GET /accounts/{accountId}/balances ───────────────────────────────────

    @GET
    @Path("/accounts/{accountId}/balances")
    @Operation(summary = "Get account balances")
    public Response getBalances(@PathParam("accountId") String accountId,
                                @HeaderParam("x-fapi-interaction-id") String interactionId) {

        var balance = Map.of(
            "AccountId",        accountId,
            "Amount",           Map.of("Amount", "1230.00", "Currency", "GBP"),
            "CreditDebitIndicator", "Credit",
            "Type",             "ClosingAvailable",
            "DateTime",         "2020-04-16T06:06:06+00:00",
            "CreditLine",       List.of(Map.of("Included", true,
                                               "Amount",   Map.of("Amount", "2000.00", "Currency", "GBP"),
                                               "Type",     "Pre-Agreed"))
        );

        return Response.ok(Map.of(
            "Data",  Map.of("Balance", List.of(balance)),
            "Links", Map.of("Self", "https://api.alphabank.com/open-banking/v4.0/accounts/" + accountId + "/balances"),
            "Meta",  Map.of("TotalPages", 1)
        )).header("x-fapi-interaction-id", resolveInteractionId(interactionId))
          .build();
    }

    // ─── GET /accounts/{accountId}/transactions ───────────────────────────────

    @GET
    @Path("/accounts/{accountId}/transactions")
    @Operation(summary = "Get account transactions")
    public Response getTransactions(@PathParam("accountId") String accountId,
                                    @HeaderParam("x-fapi-interaction-id") String interactionId) {

        var transactions = List.of(
            buildTransaction(accountId, "500.00", "Credit",  "Direct Deposit - Salary"),
            buildTransaction(accountId, "150.00", "Debit",   "Amazon Purchase"),
            buildTransaction(accountId, "75.00",  "Debit",   "Utility Bill"),
            buildTransaction(accountId, "200.00", "Credit",  "Refund - Return")
        );

        return Response.ok(Map.of(
            "Data",  Map.of("Transaction", transactions),
            "Links", Map.of("Self", "https://api.alphabank.com/open-banking/v4.0/accounts/" + accountId + "/transactions"),
            "Meta",  Map.of("TotalPages", 1, "FirstAvailableDateTime", "2020-01-01T06:06:06+00:00")
        )).header("x-fapi-interaction-id", resolveInteractionId(interactionId))
          .build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildAccount(String accountId, String nickname,
                                             String schemeName, String name) {
        return Map.of(
            "AccountId",              accountId,
            "Status",                 "Enabled",
            "StatusUpdateDateTime",   "2020-04-16T06:06:06+00:00",
            "Currency",               "GBP",
            "AccountType",            "Personal",
            "AccountSubType",         "CurrentAccount",
            "Nickname",               nickname,
            "OpeningDate",            "2020-01-16T06:06:06+00:00",
            "Account",                List.of(Map.of(
                "SchemeName",             schemeName,
                "Identification",         accountId,
                "Name",                   name,
                "SecondaryIdentification", "00021"
            ))
        );
    }

    private Map<String, Object> buildTransaction(String accountId, String amount,
                                                 String indicator, String reference) {
        return Map.of(
            "AccountId",             accountId,
            "TransactionId",         UUID.randomUUID().toString(),
            "CreditDebitIndicator",  indicator,
            "Status",                "Booked",
            "BookingDateTime",       "2020-04-16T06:06:06+00:00",
            "Amount",                Map.of("Amount", amount, "Currency", "GBP"),
            "TransactionInformation", reference
        );
    }

    private String resolveInteractionId(String incoming) {
        return (incoming != null && !incoming.isBlank()) ? incoming : UUID.randomUUID().toString();
    }
}
