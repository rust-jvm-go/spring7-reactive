package initiative.java.spring7plus.spring7reactive.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Pure unit tests for AccountService.
 * 
 * This test class demonstrates best practices for unit testing reactive service layers:
 * - Uses @ExtendWith(MockitoExtension.class) to avoid loading Spring context (fast execution)
 * - Tests business logic in isolation with mocked dependencies
 * - Uses StepVerifier for reactive stream testing and verification
 * - Verifies both happy path and error scenarios
 * - Validates repository interactions and argument capturing
 * - Tests reactive operators like switchIfEmpty for error handlin* 
 * - No database or Spring context required
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Instant FIXED_CREATED_AT = Instant.parse("2026-01-12T00:00:00Z"); // Deterministic fixture keeps assertions stable.

    // Mock dependencies - these will be injected into the service under test
    @Mock
    private AccountRepository accountRepository;

    // Service under test - mocks will be automatically injected into this instance
    @InjectMocks
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        // Common setup can go here if needed
        // Currently empty as we use specific mocks per test for clarity
    }

    /**
     * Ensures {@link AccountService#getAllAccounts()} streams through the repository result
     * by arranging two accounts, mocking {@code findAll()}, and verifying via StepVerifier
     * that each one is emitted before the Flux completes.
     */
    @Test
    void getAllAccountsReturnsAllAccountsFromRepository() {
        // Arrange: Create test accounts with different properties
        Account account1 = createAccount(UUID.fromString("11111111-2222-3333-4444-555555555555"), 
                "Savings Account", "USD", new BigDecimal("1000.00"));
        Account account2 = createAccount(UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"), 
                "Checking Account", "EUR", new BigDecimal("500.00"));

        // Mock the repository response
        when(accountRepository.findAll()).thenReturn(Flux.just(account1, account2));

        // Act: Call the service method
        Flux<Account> result = accountService.getAllAccounts();

        // Assert: Verify the reactive stream emits expected values
        StepVerifier.create(result)
                .expectNext(account1)  // First account
                .expectNext(account2)  // Second account
                .verifyComplete(); // Stream completes successfully

        // Verify repository interaction
        verify(accountRepository).findAll();
    }

    /**
     * Verifies {@link AccountService#getAccount(UUID)} returns the matching entity when present
     * by stubbing {@code findById()} and asserting StepVerifier receives a single Account containing
     * the expected ID/name/currency before completing.
     */
    @Test
    void getAccountReturnsAccountWhenExists() {
        // Arrange: Create test account
        Account account = createAccount(ACCOUNT_ID, "Main Account", "USD", new BigDecimal("2500.00"));

        // Mock the repository response
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Mono.just(account));

        // Act: Call the service method
        Mono<Account> result = accountService.getAccount(ACCOUNT_ID);

        // Assert: Verify the reactive stream returns expected account
        StepVerifier.create(result)
                .expectNextMatches(foundAccount -> 
                    foundAccount.getId().equals(ACCOUNT_ID) &&
                    foundAccount.getName().equals("Main Account") &&
                    foundAccount.getBalance().equals(new BigDecimal("2500.00")) &&
                    foundAccount.getCurrencyCode().equals("USD"))
                .verifyComplete();

        // Verify repository interaction
        verify(accountRepository).findById(ACCOUNT_ID);
    }

    /**
     * Confirms the service surfaces a 404 when {@code findById()} is empty by wiring it to
     * {@link Mono#empty()}, executing the call, and asserting StepVerifier observes a ResponseStatusException.
     */
    @Test
    void getAccountReturns404WhenAccountNotFound() {
        // Arrange: Mock repository to return empty Mono (account not found)
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Mono.empty());

        // Act: Call the service method
        Mono<Account> result = accountService.getAccount(ACCOUNT_ID);

        // Assert: Verify the reactive stream emits 404 error
        StepVerifier.create(result)
                .expectErrorMatches(throwable -> 
                    throwable instanceof org.springframework.web.server.ResponseStatusException &&
                    ((org.springframework.web.server.ResponseStatusException) throwable).getStatusCode() == 
                    org.springframework.http.HttpStatus.NOT_FOUND &&
                    throwable.getMessage().contains("Account not found"))
                .verify();

        // Verify repository interaction
        verify(accountRepository).findById(ACCOUNT_ID);
    }

    /**
     * Covers the happy path for {@link AccountService#createAccount(String, String, java.math.BigDecimal)}
     * by mocking {@code save()}, using StepVerifier to assert the returned Account mirrors inputs,
     * then capturing the persisted entity to ensure INSERT-required fields (id/newEntity/createdAt) are set.
     */
    @Test
    void createAccountPersistsAndReturnsAccount() {
        // Arrange: Define creation parameters
        String name = "Investment Account";
        String currencyCode = "USD";
        BigDecimal initialBalance = new BigDecimal("10000.00");

        Account savedAccount = createAccount(UUID.randomUUID(), name, currencyCode, initialBalance);

        // Mock repository response
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(savedAccount));

        // Act: Call the service method
        Mono<Account> result = accountService.createAccount(name, currencyCode, initialBalance);

        // Assert: Verify the reactive stream returns expected account
        StepVerifier.create(result)
                .expectNextMatches(createdAccount -> 
                    createdAccount.getName().equals(name) &&
                    createdAccount.getCurrencyCode().equals(currencyCode) &&
                    createdAccount.getBalance().equals(initialBalance) &&
                    createdAccount.getId() != null &&
                    createdAccount.getCreatedAt() != null)
                .verifyComplete();

        // Verify repository interaction and capture the persisted entity
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account persisted = captor.getValue();
        
        // Assert the persisted account has correct properties
        assertThat(persisted.getName()).isEqualTo(name);
        assertThat(persisted.getCurrencyCode()).isEqualTo(currencyCode);
        assertThat(persisted.getBalance()).isEqualTo(initialBalance);
        assertThat(persisted.getId()).isNotNull();  // Should be generated
        assertThat(persisted.isNew()).isTrue();      // Important for R2DBC to treat as INSERT
        assertThat(persisted.getCreatedAt()).isNotNull();  // Should be set by service
    }

    /**
     * Exercises the generated ID/timestamp branch by mocking {@code save()} and then inspecting the
     * captured Account to confirm {@code isNew}=true and {@code createdAt} is within the reasonable clock window.
     */
    @Test
    void createAccountGeneratesIdAndTimestamp() {
        // Arrange: Exercise initialization defaults for ID/timestamp.
        String name = "Test Account";
        String currencyCode = "JPY";
        BigDecimal initialBalance = new BigDecimal("75000.00");

        Account savedAccount = createAccount(UUID.randomUUID(), name, currencyCode, initialBalance);

        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(savedAccount));

        // Act: Call the service method
        Mono<Account> result = accountService.createAccount(name, currencyCode, initialBalance);

        // Assert: Verify the reactive stream returns expected account (without timestamp check)
        StepVerifier.create(result)
                .expectNextMatches(account -> 
                    account.getName().equals(name) &&
                    account.getCurrencyCode().equals(currencyCode) &&
                    account.getBalance().equals(initialBalance) &&
                    account.getId() != null)
                .verifyComplete();

        // Verify and capture the persisted entity to check generated values
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account persisted = captor.getValue();
        
        // Assert generated values are properly set
        assertThat(persisted.getId()).isNotNull();
        assertThat(persisted.isNew()).isTrue();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getCreatedAt()).isBefore(Instant.now().plusSeconds(10));  // Within reasonable time
        assertThat(persisted.getCreatedAt()).isAfter(Instant.now().minusSeconds(10));  // Created recently
    }

    /**
     * Guards against repository failures hanging the reactive pipeline by forcing {@code save()} to error
     * and asserting StepVerifier relays the IllegalStateException to subscribers.
     */
    @Test
    void createAccountPropagatesRepositoryErrors() {
        // Regression guard: StepVerifier should capture repository failures without hanging.
        when(accountRepository.save(any(Account.class)))
                .thenReturn(Mono.error(new IllegalStateException("Repository unavailable")));

        StepVerifier.create(accountService.createAccount("Outage Test", "USD", BigDecimal.ONE))
                .expectErrorMatches(throwable -> throwable instanceof IllegalStateException &&
                        throwable.getMessage().contains("Repository unavailable"))
                .verify();

        verify(accountRepository).save(any(Account.class));
    }

    /**
     * Property-based variant: for any random account creation payload, the service should echo the input fields
     * (name/currency/balance) back to the caller even when UUID/timestamps are generated at runtime.
     */
    @Property(tries = 25)
    void createAccountEchoesInputsForAnyPayload(@ForAll("accountCreationParams") AccountCreationParams params) {
        AccountRepository repository = mock(AccountRepository.class);
        AccountService service = new AccountService(repository);

        when(repository.save(any(Account.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.createAccount(params.name(), params.currencyCode(), params.initialBalance()))
                .assertNext(account -> {
                    assertThat(account.getName()).isEqualTo(params.name());
                    assertThat(account.getCurrencyCode()).isEqualTo(params.currencyCode());
                    assertThat(account.getBalance()).isEqualTo(params.initialBalance());
                })
                .verifyComplete();
    }

    /**
     * Factory method to create test Account instances.
     * 
     * @param id Unique identifier for the account
     * @param name Account display name
     * @param currencyCode ISO currency code
     * @param balance Account balance
     * @return Test account with realistic properties
     */
    private static Account createAccount(UUID id, String name, String currencyCode, BigDecimal balance) {
        return Account.builder()
                .id(id)
                .newEntity(false)  // Represents existing entity for find operations
                .name(name)
                .currencyCode(currencyCode)
                .balance(balance)
                .createdAt(FIXED_CREATED_AT)  // Stable timestamp ensures reproducible assertions.
                .build();
    }

    /**
     * jqwik generator for createAccount property tests; focuses on reasonable strings/currencies so shrinking stays readable.
     */
    @Provide
    Arbitrary<AccountCreationParams> accountCreationParams() {
        Arbitrary<String> names = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofMinLength(3)
                .ofMaxLength(20);
        Arbitrary<String> currencies = Arbitraries.of("USD", "EUR", "JPY", "GBP");
        Arbitrary<BigDecimal> balances = Arbitraries.longs().between(0, 1_000_000)
                .map(cents -> BigDecimal.valueOf(cents, 2));

        return Combinators.combine(names, currencies, balances)
                .as(AccountCreationParams::new);
    }

    record AccountCreationParams(String name, String currencyCode, BigDecimal initialBalance) { }
}
