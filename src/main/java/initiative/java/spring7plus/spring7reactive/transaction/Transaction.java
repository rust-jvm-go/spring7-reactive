package initiative.java.spring7plus.spring7reactive.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ledger entry tied to an account that captures incoming or outgoing cash flow.
 * <p>
 * Each transaction stores the account reference, business type (income/expense),
 * money amount + currency, optional human-readable memo, and timestamps for when
 * the activity happened vs when it was recorded in the system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("account_transaction")
public class Transaction implements Persistable<UUID> {

    // @Id marks the primary key column for Spring Data.
    @Id
    private UUID id;

    @Transient
    private boolean newEntity;

    @Column("account_id")
    private UUID accountId;

    private TransactionType type;

    // Use BigDecimal for money to avoid floating point rounding errors.
    private BigDecimal amount;

    @Column("currency_code")
    private String currencyCode;

    private String description;

    // Prefer java.time types for timestamps (timezone-safe and immutable).
    @Column("occurred_at")
    private Instant occurredAt;

    @Column("created_at")
    private Instant createdAt;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
