package initiative.java.spring7plus.spring7reactive.transaction;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import initiative.java.spring7plus.spring7reactive.account.AccountRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service layer owns the use-case logic so controllers do not talk to repositories directly.
 * In WebFlux apps, service methods typically return Mono/Flux to keep the call chain non-blocking.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository, AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    public Flux<Transaction> getTransactionsForAccount(UUID accountId) {
        // Fail fast with a 404 if the account does not exist.
        return accountRepository.existsById(accountId)
                .flatMapMany(exists -> {
                    if (!exists) {
                        return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
                    }

                    return transactionRepository.findAllByAccountIdOrderByOccurredAtDesc(accountId);
                });
    }

    public Mono<Transaction> createTransaction(UUID accountId, TransactionType type, BigDecimal amount, String currencyCode,
            String description, Instant occurredAt) {
        // Keep account existence validation in the service layer to centralize business rules.
        return accountRepository.existsById(accountId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
                    }

                    Transaction transaction = Transaction.builder()
                            .id(UUID.randomUUID())
                            .newEntity(true)
                            .accountId(accountId)
                            .type(type)
                            .amount(amount)
                            .currencyCode(currencyCode)
                            .description(description)
                            .occurredAt(occurredAt != null ? occurredAt : Instant.now())
                            .createdAt(Instant.now())
                            .build();

                    return transactionRepository.save(transaction);
                });
    }

    /**
     * Emits a live ledger feed by polling the latest transactions on a fixed cadence.
     * <p>
     * R2DBC lacks tailable cursors today, so we approximate streaming by:
     * <ol>
     *     <li>Validating the account exists (404 otherwise).</li>
     *     <li>Setting up {@link Flux#interval(Duration)} to poll every second.</li>
     *     <li>Querying the most recent rows and deduplicating by {@link Transaction#getId()}.</li>
     * </ol>
     * Clients (UI dashboards, CLIs) consume this as Server-Sent Events.
     */
    public Flux<Transaction> streamTransactions(UUID accountId) {
        return accountRepository.existsById(accountId)
                .flatMapMany(exists -> {
                    if (!exists) {
                        return Flux.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
                    }

                    return Flux.interval(Duration.ofSeconds(1))
                            .flatMap(tick -> transactionRepository.findAllByAccountIdOrderByOccurredAtDesc(accountId).take(20))
                            .distinctUntilChanged(Transaction::getId);
                });
    }
}
