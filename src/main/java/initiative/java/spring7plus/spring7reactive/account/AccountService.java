package initiative.java.spring7plus.spring7reactive.account;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service layer owns the use-case logic so controllers do not talk to repositories directly.
 * In WebFlux apps, service methods typically return Mono/Flux to keep the call chain non-blocking.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Flux<Account> getAllAccounts() {
        // Flux<T> represents 0..N elements over time.
        return accountRepository.findAll();
    }

    public Mono<Account> createAccount(String name, String currencyCode, java.math.BigDecimal initialBalance) {
        // Centralizes account initialization defaults (id, createdAt) in one place.
        // Mono<T> represents 0..1 element (here: the saved Account).
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .name(name)
                .currencyCode(currencyCode)
                .balance(initialBalance)
                .createdAt(Instant.now())
                .build();

        return accountRepository.save(account);
    }
}
