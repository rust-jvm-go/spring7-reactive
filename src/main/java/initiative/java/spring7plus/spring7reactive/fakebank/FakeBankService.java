package initiative.java.spring7plus.spring7reactive.fakebank;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import initiative.java.spring7plus.spring7reactive.account.AccountRepository;
import initiative.java.spring7plus.spring7reactive.transaction.Transaction;
import initiative.java.spring7plus.spring7reactive.transaction.TransactionRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fake bank "use-case" service:
 * loads Account context, generates transactions, and persists them reactively.
 *
 * Demonstrates a typical WebFlux pattern:
 * Mono<Account> -> flatMapMany(...) -> Flux<Transaction>.
 */
@Service
public class FakeBankService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final FakeBankTransactionGenerator generator;

    public FakeBankService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            FakeBankTransactionGenerator generator) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.generator = generator;
    }

    public Flux<Transaction> generateAndSave(UUID accountId, int count) {
        if (count <= 0) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "count must be > 0"));
        }

        // Reactive composition: find Account (Mono) then produce a Flux of saved transactions.
        return accountRepository.findById(accountId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")))
                .flatMapMany(account -> {
                    var transactions = generator.generate(accountId, account.getCurrencyCode(), count);
                    // Spring Data R2DBC saveAll returns Flux<T> (non-blocking writes).
                    return transactionRepository.saveAll(transactions);
                });
    }
}
