package initiative.java.spring7plus.spring7reactive.account;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountRepository accountRepository;

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public Flux<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Account> createAccount(@RequestBody Mono<CreateAccountRequest> request) {
        return request
                .map(req -> Account.builder()
                        .id(UUID.randomUUID())
                        .name(req.name())
                        .currencyCode(req.currencyCode())
                        .balance(req.initialBalance())
                        .createdAt(Instant.now())
                        .build())
                .flatMap(accountRepository::save);
    }

    public record CreateAccountRequest(String name, String currencyCode, java.math.BigDecimal initialBalance) {
    }
}
