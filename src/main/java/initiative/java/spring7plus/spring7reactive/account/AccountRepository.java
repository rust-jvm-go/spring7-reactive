package initiative.java.spring7plus.spring7reactive.account;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

/**
 * ReactiveCrudRepository provides non-blocking CRUD primitives:
 * <ul>
 *     <li>{@code save(..)} / {@code saveAll(..)} for create/update</li>
 *     <li>{@code findById(..)}, {@code findAll(..)}, {@code existsById(..)}</li>
 *     <li>{@code deleteById(..)}, {@code delete(..)}, {@code deleteAll(..)}</li>
 *     <li>{@code count()}</li>
 * </ul>
 * so the interface only needs to declare extra derived queries such as {@code findByCurrencyCode}.
 */
public interface AccountRepository extends ReactiveCrudRepository<Account, UUID> {

    // Derived query method: Spring Data parses the name and generates the query reactively.
    Flux<Account> findByCurrencyCode(String currencyCode);
}
