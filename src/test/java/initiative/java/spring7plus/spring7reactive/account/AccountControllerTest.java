package initiative.java.spring7plus.spring7reactive.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import initiative.java.spring7plus.spring7reactive.account.AccountController.CreateAccountRequest;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * WebFlux slice test for AccountController.
 * 
 * This test class demonstrates comprehensive testing of REST endpoints using WebTestClient:
 * - Uses @WebFluxTest to load only the web layer for the target controller
 * - Tests all CRUD operations (GET all, GET by ID, POST create)
 * - Verifies HTTP status codes, response bodies, and content types
 * - Tests reactive request body handling with Mono<Request>
 * - Mocks service dependencies to isolate controller behavior
 * - Validates error propagation (404 scenarios)
 * 
 * Slice testing benefits:
 * - Fast execution (~0.2s) - minimal Spring context, no database
 * - Focused testing of HTTP contracts and controller logic
 * - Ideal for API contract validation and CI/CD pipelines
 */
@WebFluxTest(AccountController.class)
class AccountControllerTest {

    @Autowired
    WebTestClient webTestClient;

    @MockitoBean
    AccountService accountService;

    private static final UUID ACCOUNT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Instant FIXED_CREATED_AT = Instant.parse("2026-01-12T00:00:00Z"); // Deterministic timestamp keeps JSON assertions stable.

    /**
     * Ensures GET /api/accounts returns the mocked Flux and that WebTestClient deserializes it correctly by
     * verifying status, list size, and representative fields.
     */
    @Test
    void getAllAccountsReturnsFluxOfAccounts() {
        // Arrange: Create test accounts with different properties
        Account account1 = createAccount(UUID.fromString("11111111-2222-3333-4444-555555555555"), 
                "Savings Account", "USD", new BigDecimal("1000.00"));
        Account account2 = createAccount(UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"), 
                "Checking Account", "EUR", new BigDecimal("500.00"));

        when(accountService.getAllAccounts()).thenReturn(Flux.just(account1, account2));

        // Act & Assert: Verify HTTP response and account list
        // This demonstrates DUAL VERIFICATION PATTERN - two complementary verification types:
        
        // 1. HTTP RESPONSE VERIFICATION (Black-box testing): Validates what the client receives
        webTestClient.get()
                .uri("/api/accounts")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Account.class)
                .value(accounts -> {
                    // Validates API contract: correct data structure and values
                    assertThat(accounts).hasSize(2);
                    assertThat(accounts).containsExactly(account1, account2);
                    assertThat(accounts.get(0).getName()).isEqualTo("Savings Account");
                    assertThat(accounts.get(1).getCurrencyCode()).isEqualTo("EUR");
                });

        // 2. MOCK INTERACTION VERIFICATION (White-box testing): Validates internal behavior
        // Confirms controller properly delegates to service layer (architectural integrity)
        verify(accountService).getAllAccounts();
        
        // WHY BOTH ARE ESSENTIAL:
        // - HTTP verifier ensures API contract is fulfilled (what clients get)
        // - Mock verifier ensures controller does its job correctly (how it works)
        // - Together they provide complete coverage of both external and internal contracts
    }

    /**
     * Verifies GET /api/accounts/{id} maps a Mono<Account> into a 200 JSON payload with expected field values.
     */
    @Test
    void getAccountReturnsAccountWhenExists() {
        // Arrange: Create test account
        Account account = createAccount(ACCOUNT_ID, "Main Account", "USD", new BigDecimal("2500.00"));
        
        when(accountService.getAccount(ACCOUNT_ID)).thenReturn(Mono.just(account));

        // Act & Assert: Verify HTTP response and single account
        webTestClient.get()
                .uri("/api/accounts/{accountId}", ACCOUNT_ID)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Account.class)
                .value(response -> {
                    assertThat(response.getId()).isEqualTo(ACCOUNT_ID);
                    assertThat(response.getName()).isEqualTo("Main Account");
                    assertThat(response.getBalance()).isEqualTo(new BigDecimal("2500.00"));
                    assertThat(response.getCurrencyCode()).isEqualTo("USD");
                });

        verify(accountService).getAccount(ACCOUNT_ID);
    }

    /**
     * Confirms controller propagates service-level 404 by asserting WebTestClient observes HTTP 404 status.
     */
    @Test
    void getAccountReturns404WhenNotFound() {
        // Arrange: Mock service to return 404 error (account not found scenario)
        when(accountService.getAccount(ACCOUNT_ID))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")));

        // Act & Assert: Verify 404 status for missing account
        webTestClient.get()
                .uri("/api/accounts/{accountId}", ACCOUNT_ID)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();

        verify(accountService).getAccount(ACCOUNT_ID);
    }

    /**
     * Validates POST /api/accounts returns 201 and matches the JSON contract via jsonPath assertions.
     */
    @Test
    void createAccountReturns201AndMatchesJsonContract() {
        // Shows how a slice test can validate both service integration and the serialized HTTP contract.
        CreateAccountRequest request = new CreateAccountRequest(
                "Investment Account",
                "USD",
                new BigDecimal("10000.00"));

        Account createdAccount = Account.builder()
                .id(UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff"))
                .newEntity(false)
                .name(request.name())
                .currencyCode(request.currencyCode())
                .balance(request.initialBalance())
                .createdAt(FIXED_CREATED_AT)
                .build();

        when(accountService.createAccount(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(createdAccount));

        webTestClient.post()
                .uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody() // JSON-path checks protect the HTTP contract even if the Account entity evolves.
                .jsonPath("$.id").isEqualTo(createdAccount.getId().toString())
                .jsonPath("$.name").isEqualTo(request.name())
                .jsonPath("$.currencyCode").isEqualTo(request.currencyCode())
                .jsonPath("$.balance").isEqualTo(request.initialBalance().doubleValue())
                .jsonPath("$.createdAt").isEqualTo(FIXED_CREATED_AT.toString());

        verify(accountService).createAccount(request.name(), request.currencyCode(), request.initialBalance());
    }

    /**
     * Property-based variant: regardless of which Accounts the service emits, the GET endpoint must echo them 1:1.
     * Using jqwik here shows juniors how to express "for all lists" invariants without hand-writing dozens of samples.
     */
    @Property(tries = 25)
    void getAllAccountsEchoesServiceResults(@ForAll("accountLists") List<Account> accountsFromService) {
        AccountService serviceStub = mock(AccountService.class);
        when(serviceStub.getAllAccounts()).thenReturn(Flux.fromIterable(accountsFromService));

        WebTestClient client = WebTestClient.bindToController(new AccountController(serviceStub)).build();

        client.get()
                .uri("/api/accounts")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Account.class)
                .value(response -> assertThat(response).containsExactlyElementsOf(accountsFromService));
    }

    /**
     * Guards the error path by forcing AccountService to emit BAD_REQUEST and ensuring the controller surfaces it.
     */
    @Test
    void createAccountReturnsBadRequestWhenServiceValidatesInput() {
        // Highlights how controller slice tests capture reactive error propagation.
        CreateAccountRequest request = new CreateAccountRequest(
                "",
                "USD",
                new BigDecimal("0.00"));

        when(accountService.createAccount(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency not supported")));

        webTestClient.post()
                .uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest(); // Confirms WebFlux propagates service-level validation failures.

        verify(accountService).createAccount(request.name(), request.currencyCode(), request.initialBalance());
    }

    /**
     * Factory method to create test Account instances.
     * 
     * @param id Unique identifier for the account
     * @param name Account display name
     * @param currencyCode ISO currency code
     * @param balance Initial account balance
     * @return Test account with realistic properties
     */
    private static Account createAccount(UUID id, String name, String currencyCode, BigDecimal balance) {
        return Account.builder()
                .id(id)
                .newEntity(false)  // Represents existing entity
                .name(name)
                .currencyCode(currencyCode)
                .balance(balance)
                .createdAt(FIXED_CREATED_AT) // Reuse the fixed instant so each invocation yields identical serialized payloads.
                .build();
    }

    /**
     * jqwik generator for realistic Account lists (<=5 elements keeps property runs fast for WebTestClient).
     */
    @Provide
    Arbitrary<List<Account>> accountLists() {
        return accountArbitrary().list().ofMaxSize(5);
    }

    private Arbitrary<Account> accountArbitrary() {
        Arbitrary<UUID> ids = Arbitraries.create(UUID::randomUUID);
        Arbitrary<String> names = Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofMinLength(3)
                .ofMaxLength(20);
        Arbitrary<String> currencies = Arbitraries.of("USD", "EUR", "JPY", "GBP");
        Arbitrary<BigDecimal> balances = Arbitraries.longs().between(0, 1_000_000)
                .map(cents -> BigDecimal.valueOf(cents, 2));

        return Combinators.combine(ids, names, currencies, balances)
                .as((id, name, currency, balance) -> Account.builder()
                        .id(id)
                        .newEntity(false)
                        .name(name)
                        .currencyCode(currency)
                        .balance(balance)
                        .createdAt(FIXED_CREATED_AT)
                        .build());
    }
}
