package initiative.java.spring7plus.spring7reactive.account;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

public interface AccountRepository extends ReactiveCrudRepository<Account, UUID> {

    Flux<Account> findByCurrencyCode(String currencyCode);
}
