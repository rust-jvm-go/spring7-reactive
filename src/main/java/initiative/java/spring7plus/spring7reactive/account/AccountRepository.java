package initiative.java.spring7plus.spring7reactive.account;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

/**
 * ReactiveCrudRepository provides non-blocking CRUD operations backed by R2DBC.
 */
public interface AccountRepository extends ReactiveCrudRepository<Account, UUID> {

    // Derived query method: Spring Data parses the name and generates the query reactively.
    Flux<Account> findByCurrencyCode(String currencyCode);
}
