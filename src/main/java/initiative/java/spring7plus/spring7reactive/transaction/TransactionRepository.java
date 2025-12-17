package initiative.java.spring7plus.spring7reactive.transaction;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

/**
 * ReactiveCrudRepository provides non-blocking CRUD operations backed by R2DBC.
 */
public interface TransactionRepository extends ReactiveCrudRepository<Transaction, UUID> {

    // Explicit query keeps ordering logic close to the repository and stays fully reactive.
    @Query("SELECT * FROM account_transaction WHERE account_id = :accountId ORDER BY occurred_at DESC")
    Flux<Transaction> findAllByAccountIdOrderByOccurredAtDesc(UUID accountId);
}
