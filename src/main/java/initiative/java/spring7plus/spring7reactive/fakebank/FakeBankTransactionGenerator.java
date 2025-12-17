package initiative.java.spring7plus.spring7reactive.fakebank;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import initiative.java.spring7plus.spring7reactive.transaction.Transaction;
import initiative.java.spring7plus.spring7reactive.transaction.TransactionType;

/**
 * Generates realistic-ish transactions for demo/testing.
 *
 * Designed as a pure generator (no I/O). The "Reactive" part belongs in the service layer
 * where we persist and compose with Mono/Flux.
 */
@Component
public class FakeBankTransactionGenerator {

    private static final List<String> DESCRIPTIONS = List.of(
            "Coffee",
            "Groceries",
            "Salary",
            "Rent",
            "Subscription",
            "Taxi",
            "Restaurant",
            "Book"
    );

    public List<Transaction> generate(UUID accountId, String currencyCode, int count) {
        var now = Instant.now();
        var rnd = ThreadLocalRandom.current();

        // Java 8 functional style: generate N elements with IntStream and map to Transactions.
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    var type = rnd.nextBoolean() ? TransactionType.EXPENSE : TransactionType.INCOME;
                    var amount = randomAmount(rnd, type);

                    // Use java.time for timestamps; here we spread "occurredAt" over ~30 days.
                    var occurredAt = now.minus(Duration.ofHours(rnd.nextInt(1, 24 * 30)));

                    return Transaction.builder()
                            .id(UUID.randomUUID())
                            .newEntity(true)
                            .accountId(accountId)
                            .type(type)
                            .amount(amount)
                            .currencyCode(currencyCode)
                            .description(DESCRIPTIONS.get(rnd.nextInt(DESCRIPTIONS.size())))
                            .occurredAt(occurredAt)
                            .createdAt(now)
                            .build();
                })
                .toList();
    }

    private static BigDecimal randomAmount(ThreadLocalRandom rnd, TransactionType type) {
        // Keep amounts positive; the TransactionType indicates INCOME vs EXPENSE.
        double raw = (type == TransactionType.EXPENSE)
                ? rnd.nextDouble(3.0, 120.0)
                : rnd.nextDouble(200.0, 5000.0);

        // BigDecimal for money; force 2 decimals for currency-like values.
        return BigDecimal.valueOf(raw).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
