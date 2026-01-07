package initiative.java.spring7plus.spring7reactive.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

import initiative.java.spring7plus.spring7reactive.account.AccountController.CreateAccountRequest;
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

    @Test
    void getAllAccountsReturnsFluxOfAccounts() {
        // Arrange: Create test accounts with different properties
        Account account1 = createAccount(UUID.fromString("11111111-2222-3333-4444-555555555555"), 
                "Savings Account", "USD", new BigDecimal("1000.00"));
        Account account2 = createAccount(UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa"), 
                "Checking Account", "EUR", new BigDecimal("500.00"));

        when(accountService.getAllAccounts()).thenReturn(Flux.just(account1, account2));

        // Act & Assert: Verify HTTP response and account list
        webTestClient.get()
                .uri("/api/accounts")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Account.class)
                .value(accounts -> {
                    assertThat(accounts).hasSize(2);
                    assertThat(accounts).containsExactly(account1, account2);
                    assertThat(accounts.get(0).getName()).isEqualTo("Savings Account");
                    assertThat(accounts.get(1).getCurrencyCode()).isEqualTo("EUR");
                });

        verify(accountService).getAllAccounts();
    }

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

    @Test
    void createAccountPersistsAndReturnsCreatedAccount() {
        // Arrange: Create request and expected response
        CreateAccountRequest request = new CreateAccountRequest(
                "Investment Account", 
                "USD", 
                new BigDecimal("10000.00"));

        Account createdAccount = createAccount(UUID.randomUUID(), 
                request.name(), request.currencyCode(), request.initialBalance());

        when(accountService.createAccount(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(createdAccount));

        // Act & Assert: Verify creation endpoint
        webTestClient.post()
                .uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Account.class)
                .value(response -> {
                    assertThat(response.getName()).isEqualTo(request.name());
                    assertThat(response.getCurrencyCode()).isEqualTo(request.currencyCode());
                    assertThat(response.getBalance()).isEqualTo(request.initialBalance());
                    assertThat(response.getId()).isNotNull();
                    assertThat(response.getCreatedAt()).isNotNull();
                });

        // Verify service was called with correct parameters
        verify(accountService).createAccount(request.name(), request.currencyCode(), request.initialBalance());
    }

    @Test
    void createAccountHandlesReactiveRequestBody() {
        // Arrange: Test reactive request body processing
        CreateAccountRequest request = new CreateAccountRequest(
                "Reactive Test Account", 
                "JPY", 
                new BigDecimal("75000.00"));

        Account createdAccount = createAccount(UUID.randomUUID(), 
                request.name(), request.currencyCode(), request.initialBalance());

        when(accountService.createAccount(any(String.class), any(String.class), any(BigDecimal.class)))
                .thenReturn(Mono.just(createdAccount));

        // Act & Assert: Verify reactive body consumption works correctly
        webTestClient.post()
                .uri("/api/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), CreateAccountRequest.class)  // Reactive body
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Account.class)
                .value(response -> {
                    assertThat(response.getName()).isEqualTo("Reactive Test Account");
                    assertThat(response.getCurrencyCode()).isEqualTo("JPY");
                });

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
                .createdAt(Instant.now().minusSeconds(86400))  // Created yesterday
                .build();
    }
}
