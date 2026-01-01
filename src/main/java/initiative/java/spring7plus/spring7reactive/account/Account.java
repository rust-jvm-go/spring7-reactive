package initiative.java.spring7plus.spring7reactive.account;

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
 * Domain aggregate representing a budgeting account (cash wallet, credit card, savings goal, etc.).
 * <p>
 * Each account tracks the user's display name, home currency, current balance snapshot, and creation timestamp.
 * Transactions (income/expense) reference {@code account_id} to build the running ledger for this entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("account")
public class Account implements Persistable<UUID> {

    // @Id marks the primary key column for Spring Data.
    @Id
    private UUID id;

    // Marked @Transient so Spring Data does not persist this helper flag; AccountService sets it before saves,
    // allowing us to decide when Persistable#isNew returns true (prevents R2DBC from issuing UPDATEs for new rows).
    @Transient
    private boolean newEntity;

    private String name;

    @Column("currency_code")
    private String currencyCode;

    // Use BigDecimal for money to avoid floating point rounding errors.
    private BigDecimal balance;

    // Prefer java.time types for timestamps (timezone-safe and immutable).
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
