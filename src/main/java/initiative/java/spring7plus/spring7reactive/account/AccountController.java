package initiative.java.spring7plus.spring7reactive.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * WebFlux REST controller: methods return Mono/Flux instead of blocking values.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Returning {@code Flux<Account>} lets WebFlux consume a finite result set,
     * buffer it into the normal JSON array response, and later reuse the same
     * signature for true streaming (SSE/NDJSON) without refactoring the controller.
     */
    @GetMapping
    public Flux<Account> getAllAccounts() {
        // Returning Flux allows streaming/pagination/backpressure-aware consumption later.
        return accountService.getAllAccounts();
    }

    /**
     * Exposes a reactive single-account lookup while keeping the 404 handling in the service layer.
     */
    @GetMapping("/{accountId}")
    public Mono<Account> getAccount(@PathVariable UUID accountId) {
        return accountService.getAccount(accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Account> createAccount(@RequestBody Mono<CreateAccountRequest> request) {
        // Request body can be consumed reactively as Mono<T> (useful for large/streaming bodies).
        return request.flatMap(req -> accountService.createAccount(req.name(), req.currencyCode(), req.initialBalance()));
    }

    /**
     * Records are a concise way to model request/response DTOs (Java 16+).
     */
    public record CreateAccountRequest(String name, String currencyCode, java.math.BigDecimal initialBalance) {
    }
}
