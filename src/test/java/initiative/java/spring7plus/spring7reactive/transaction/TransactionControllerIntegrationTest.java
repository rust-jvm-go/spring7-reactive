package initiative.java.spring7plus.spring7reactive.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import initiative.java.spring7plus.spring7reactive.account.AccountRepository;
import initiative.java.spring7plus.spring7reactive.fakebank.FakeBankService;
import initiative.java.spring7plus.spring7reactive.transaction.TransactionController.CreateTransactionRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * WebFlux slice test for TransactionController.
 * persistence so HTTP behavior remains deterministic and doesn’t require YugabyteDB.
 */
@WebFluxTest(TransactionController.class)
class TransactionControllerIntegrationTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    WebTestClient webTestClient;

    @MockitoBean
    AccountRepository accountRepository;

    @MockitoBean
    TransactionRepository transactionRepository;

    @MockitoBean
    TransactionService transactionService;

    @MockitoBean
    FakeBankService fakeBankService;

    @Test
    void getTransactionsReturnsSortedFlux() {
        Transaction newer = transaction(UUID.fromString("11111111-2222-3333-4444-555555555555"),
                Instant.parse("2025-01-02T00:00:00Z"),
                new BigDecimal("125.50"),
                "Newer entry");
        Transaction older = transaction(UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"),
                Instant.parse("2024-12-31T23:00:00Z"),
                new BigDecimal("25.00"),
                "Older entry");

        when(transactionService.getTransactionsForAccount(ACCOUNT_ID))
                .thenReturn(Flux.just(newer, older));

        webTestClient.get()
                .uri("/api/accounts/{id}/transactions", ACCOUNT_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Transaction.class)
                .value(list -> {
                    assertThat(list).containsExactly(newer, older);
                    assertThat(list).isSortedAccordingTo(Comparator.comparing(Transaction::getOccurredAt).reversed());
                });
    }

    @Test
    void createTransactionPersistsAndReturnsCreatedPayload() {
        Transaction savedTransaction = transaction(UUID.randomUUID(), Instant.parse("2025-01-01T10:15:30Z"), new BigDecimal("500.00"), "Initial deposit");
        when(transactionService.createTransaction(eq(ACCOUNT_ID), eq(TransactionType.INCOME), eq(new BigDecimal("500.00")), eq("USD"), eq("Initial deposit"), any(Instant.class)))
                .thenReturn(Mono.just(savedTransaction));

        var request = new CreateTransactionRequest(
                TransactionType.INCOME,
                new BigDecimal("500.00"),
                "USD",
                "Initial deposit",
                Instant.parse("2025-01-01T10:15:30Z"));

        webTestClient.post()
                .uri("/api/accounts/{id}/transactions", ACCOUNT_ID)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Transaction.class)
                .value(saved -> {
                    assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID);
                    assertThat(saved.getType()).isEqualTo(TransactionType.INCOME);
                    assertThat(saved.getDescription()).isEqualTo("Initial deposit");
                });

        verify(transactionService).createTransaction(eq(ACCOUNT_ID), eq(TransactionType.INCOME), eq(new BigDecimal("500.00")), eq("USD"), eq("Initial deposit"), any(Instant.class));
    }

    @Test
    void generateTransactionsDelegatesToFakeBankService() {
        Transaction generated = transaction(UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"),
                Instant.parse("2025-01-05T08:00:00Z"),
                new BigDecimal("42.00"),
                "Bulk entry");

        when(fakeBankService.generateAndSave(ACCOUNT_ID, 3)).thenReturn(Flux.just(generated));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/accounts/{id}/transactions/generate")
                        .queryParam("count", 3)
                        .build(ACCOUNT_ID))
                .exchange()
                .expectStatus().isCreated()
                .expectBodyList(Transaction.class)
                .value(list -> assertThat(list).containsExactly(generated));

        verify(fakeBankService).generateAndSave(eq(ACCOUNT_ID), eq(3));
    }

    private static Transaction transaction(UUID id, Instant occurredAt, BigDecimal amount, String description) {
        return Transaction.builder()
                .id(id)
                .newEntity(false)
                .accountId(ACCOUNT_ID)
                .type(TransactionType.INCOME)
                .amount(amount)
                .currencyCode("USD")
                .description(description)
                .occurredAt(occurredAt)
                .createdAt(occurredAt.plusSeconds(60))
                .build();
    }
}
