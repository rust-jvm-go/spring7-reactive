package initiative.java.spring7plus.spring7reactive.transaction;

import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import initiative.java.spring7plus.spring7reactive.fakebank.FakeBankService;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * WebFlux slice test for TransactionController streaming endpoints.
 * 
 * This test class demonstrates focused testing of specific controller functionality:
 * - Uses @WebFluxTest to load only the web layer for the target controller
 * - Tests Server-Sent Events (SSE) streaming capabilities
 * - Verifies error propagation from service layer to HTTP responses
 * - Uses StepVerifier for reactive stream testing and cancellation
 * - Mocks service dependencies to isolate controller behavior
 * 
 * Slice testing benefits:
 * - Fast execution (~0.3s) - minimal Spring context
 * - No database or external dependencies required
 * - Focused testing of HTTP contracts and streaming behavior
 * - Ideal for CI/CD pipelines and rapid development cycles
 */
@WebFluxTest(TransactionController.class)
class TransactionControllerSliceTest {

    // WebTestClient automatically configured by @WebFluxTest for HTTP testing
    @Autowired
    WebTestClient webTestClient;

    // Mock service dependencies injected into the controller
    @MockitoBean
    TransactionService transactionService;

    @MockitoBean
    FakeBankService fakeBankService;

    @Test
    void streamEndpointEmitsTransactionsAndCancels() {
        // Arrange: Create test data and mock service response
        UUID accountId = UUID.randomUUID();
        Transaction firstTx = fixture(accountId, UUID.fromString("11111111-2222-3333-4444-555555555555"), "Paycheck");
        Transaction secondTx = fixture(accountId, UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"), "Groceries");

        // Create an infinite stream that emits two transactions then never completes
        // This simulates a real-world streaming scenario where client controls cancellation
        Flux<Transaction> stream = Flux.just(firstTx, secondTx).concatWith(Flux.never());
        when(transactionService.streamTransactions(accountId)).thenReturn(stream);

        // Act: Perform HTTP GET request for Server-Sent Events stream
        var result = webTestClient.get()
                .uri("/api/accounts/{id}/transactions/stream", accountId)  // SSE endpoint
                .accept(MediaType.TEXT_EVENT_STREAM)  // Request SSE content type
                .exchange()
                .expectStatus().isOk()  // Verify HTTP 200 status
                .returnResult(Transaction.class);  // Get reactive stream for manual testing

        // Assert: Verify stream behavior using StepVerifier
        StepVerifier.create(result.getResponseBody())
                .expectNext(firstTx)  // First transaction received
                .expectNext(secondTx)  // Second transaction received
                .thenCancel()  // Simulate client disconnecting (important for resource cleanup)
                .verify();  // Verify the stream behavior
    }

    @Test
    void getTransactionsPropagates404() {
        // Arrange: Create test data and mock service error response
        UUID accountId = UUID.randomUUID();
        
        // Mock service to return 404 error (account not found scenario)
        when(transactionService.getTransactionsForAccount(accountId))
                .thenReturn(Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")));

        // Act & Assert: Verify error propagation from service to HTTP response
        webTestClient.get()
                .uri("/api/accounts/{id}/transactions", accountId)  // Standard REST endpoint
                .exchange()
                .expectStatus().isNotFound();  // Verify HTTP 404 status is properly propagated
    }

    /**
     * Factory method to create test Transaction fixtures for streaming tests.
     * 
     * Uses consistent test data to ensure predictable streaming behavior.
     * Fixed timestamps help with testing ordering and consistency.
     * 
     * @param accountId Account ID the transaction belongs to
     * @param txId Unique transaction identifier
     * @param description Human-readable transaction description
     * @return Test transaction with realistic properties for streaming
     */
    private static Transaction fixture(UUID accountId, UUID txId, String description) {
        return Transaction.builder()
                .id(txId)
                .newEntity(false)  // Represents existing entity in stream
                .accountId(accountId)
                .type(TransactionType.INCOME)
                .amount(new BigDecimal("10.00"))  // Consistent amount for testing
                .currencyCode("USD")
                .description(description)
                .occurredAt(Instant.parse("2025-01-01T00:00:00Z"))  // Fixed timestamp
                .createdAt(Instant.parse("2025-01-01T00:00:10Z"))  // Fixed creation time
                .build();
    }
}
