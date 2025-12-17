package initiative.java.spring7plus.spring7reactive.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import initiative.java.spring7plus.spring7reactive.fakebank.FakeBankService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * WebFlux REST controller: methods return Mono/Flux instead of blocking values.
 */
@RestController
@RequestMapping("/api/accounts/{accountId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final FakeBankService fakeBankService;

    public TransactionController(TransactionService transactionService, FakeBankService fakeBankService) {
        this.transactionService = transactionService;
        this.fakeBankService = fakeBankService;
    }

    @GetMapping
    public Flux<Transaction> getTransactions(@PathVariable UUID accountId) {
        // Returning Flux allows streaming/pagination/backpressure-aware consumption later.
        return transactionService.getTransactionsForAccount(accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Transaction> createTransaction(
            @PathVariable UUID accountId,
            @RequestBody Mono<CreateTransactionRequest> request) {
        // Request body can be consumed reactively as Mono<T> (useful for large/streaming bodies).
        return request.flatMap(req -> transactionService.createTransaction(
                accountId,
                req.type(),
                req.amount(),
                req.currencyCode(),
                req.description(),
                req.occurredAt()));
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public Flux<Transaction> generateTransactions(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "50") int count) {
        // Demonstrates a "bulk write" reactive flow: Flux<Transaction> from saveAll(...).
        return fakeBankService.generateAndSave(accountId, count);
    }

    /**
     * Records are a concise way to model request/response DTOs (Java 16+).
     */
    public record CreateTransactionRequest(
            TransactionType type,
            BigDecimal amount,
            String currencyCode,
            String description,
            Instant occurredAt) {
    }
}
