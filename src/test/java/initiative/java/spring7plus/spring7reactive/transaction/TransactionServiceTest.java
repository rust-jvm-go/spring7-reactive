package initiative.java.spring7plus.spring7reactive.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import initiative.java.spring7plus.spring7reactive.account.AccountRepository;
import initiative.java.spring7plus.spring7reactive.transaction.TransactionController.CreateTransactionRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pure unit tests for TransactionService.
 * 
 * This test class demonstrates best practices for unit testing in reactive Spring Boot applications:
 * - Uses @ExtendWith(MockitoExtension.class) to avoid loading Spring context (fast execution)
 * - Tests business logic in isolation with mocked dependencies
 * - Uses StepVerifier for reactive stream testing
 * - Verifies both happy path and error scenarios
 * - Validates repository interactions and argument capturing
 * - No database or Spring context required
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    // Test account ID used consistently across all test methods
    private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    // Mock dependencies - these will be injected into the service under test
    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    // Service under test - mocks will be automatically injected into this instance
    @InjectMocks
    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        // Common setup can go here if needed
        // Currently empty as we use specific mocks per test for clarity
    }

    @Test
    void getTransactionsForAccountReturnsTransactionsWhenAccountExists() {
        // Arrange: Create test transactions with different timestamps
        Transaction tx1 = createTransaction(UUID.randomUUID(), Instant.now(), BigDecimal.valueOf(100.00), "Test 1");
        Transaction tx2 = createTransaction(UUID.randomUUID(), Instant.now().minusSeconds(60), BigDecimal.valueOf(50.00), "Test 2");

        // Mock the repository responses
        when(accountRepository.existsById(ACCOUNT_ID)).thenReturn(Mono.just(true));
        when(transactionRepository.findAllByAccountIdOrderByOccurredAtDesc(ACCOUNT_ID))
                .thenReturn(Flux.just(tx1, tx2));

        // Act: Call the service method
        Flux<Transaction> result = transactionService.getTransactionsForAccount(ACCOUNT_ID);

        // Assert: Verify the reactive stream emits expected values in order
        StepVerifier.create(result)
                .expectNext(tx1)  // First transaction (newer)
                .expectNext(tx2)  // Second transaction (older)
                .verifyComplete(); // Stream completes successfully

        // Verify repository interactions
        verify(accountRepository).existsById(ACCOUNT_ID);
        verify(transactionRepository).findAllByAccountIdOrderByOccurredAtDesc(ACCOUNT_ID);
    }

    @Test
    void getTransactionsForAccountReturns404WhenAccountNotFound() {
        // Arrange: Mock account not found
        when(accountRepository.existsById(ACCOUNT_ID)).thenReturn(Mono.just(false));

        // Act: Call the service method
        Flux<Transaction> result = transactionService.getTransactionsForAccount(ACCOUNT_ID);

        // Assert: Verify the reactive stream emits 404 error
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> 
                    throwable instanceof org.springframework.web.server.ResponseStatusException &&
                    ((org.springframework.web.server.ResponseStatusException) throwable).getStatusCode() == 
                    org.springframework.http.HttpStatus.NOT_FOUND)
                .verify();

        // Verify only account existence check was performed
        verify(accountRepository).existsById(ACCOUNT_ID);
        // Repository query should not be called when account doesn't exist (fail-fast pattern)
        verify(transactionRepository, never()).findAllByAccountIdOrderByOccurredAtDesc(ACCOUNT_ID);
    }

    @Test
    void createTransactionPersistsAndReturnsTransactionWhenAccountExists() {
        // Arrange: Create request and expected response
        CreateTransactionRequest request = new CreateTransactionRequest(
                TransactionType.INCOME,
                new BigDecimal("500.00"),
                "USD",
                "Test deposit",
                Instant.now());

        Transaction savedTransaction = createTransaction(UUID.randomUUID(), request.occurredAt(), request.amount(), request.description());

        // Mock repository responses
        when(accountRepository.existsById(ACCOUNT_ID)).thenReturn(Mono.just(true));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(Mono.just(savedTransaction));

        // Act: Call the service method with individual parameters (matching service signature)
        Mono<Transaction> result = transactionService.createTransaction(ACCOUNT_ID, request.type(), request.amount(), request.currencyCode(), request.description(), request.occurredAt());

        // Assert: Verify the reactive stream returns expected transaction
        StepVerifier.create(result)
                .expectNextMatches(tx -> 
                    tx.getAccountId().equals(ACCOUNT_ID) &&
                    tx.getType().equals(request.type()) &&
                    tx.getAmount().equals(request.amount()) &&
                    tx.getDescription().equals(request.description()))
                .verifyComplete();

        // Verify repository interaction and capture the persisted entity
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        Transaction persisted = captor.getValue();
        
        // Assert the persisted transaction has correct properties
        assertThat(persisted.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(persisted.getType()).isEqualTo(request.type());
        assertThat(persisted.getAmount()).isEqualTo(request.amount());
        assertThat(persisted.getDescription()).isEqualTo(request.description());
        assertThat(persisted.isNew()).isTrue(); // Important for R2DBC to treat as INSERT
    }

    @Test
    void createTransactionReturns404WhenAccountNotFound() {
        // Arrange: Create request and mock account not found
        CreateTransactionRequest request = new CreateTransactionRequest(
                TransactionType.INCOME,
                new BigDecimal("500.00"),
                "USD",
                "Test deposit",
                Instant.now());

        when(accountRepository.existsById(ACCOUNT_ID)).thenReturn(Mono.just(false));

        // Act: Call the service method
        Mono<Transaction> result = transactionService.createTransaction(ACCOUNT_ID, request.type(), request.amount(), request.currencyCode(), request.description(), request.occurredAt());

        // Assert: Verify the reactive stream emits 404 error
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> 
                    throwable instanceof org.springframework.web.server.ResponseStatusException &&
                    ((org.springframework.web.server.ResponseStatusException) throwable).getStatusCode() == 
                    org.springframework.http.HttpStatus.NOT_FOUND)
                .verify();

        // Verify only account existence check was performed
        verify(accountRepository).existsById(ACCOUNT_ID);
        // Repository save should not be called when account doesn't exist (fail-fast pattern)
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    /**
     * Factory method to create test Transaction instances.
     * 
     * @param id Unique identifier for the transaction
     * @param occurredAt When the transaction occurred
     * @param amount Transaction amount
     * @param description Transaction description
     * @return Test transaction with realistic properties
     */
    private static Transaction createTransaction(UUID id, Instant occurredAt, BigDecimal amount, String description) {
        return Transaction.builder()
                .id(id)
                .newEntity(true)  // Critical for R2DBC INSERT operations
                .accountId(ACCOUNT_ID)
                .type(TransactionType.INCOME)
                .amount(amount)
                .currencyCode("USD")
                .description(description)
                .occurredAt(occurredAt)
                .createdAt(occurredAt.plusSeconds(60))  // Simulate creation shortly after occurrence
                .build();
    }
}
