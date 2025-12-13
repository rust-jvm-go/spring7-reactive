package initiative.java.spring7plus.spring7reactive.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Spring Data (R2DBC) maps this type to the "account" table.
 */
@Table("account")
public class Account {

    // @Id marks the primary key column for Spring Data.
    @Id
    private UUID id;

    private String name;

    @Column("currency_code")
    private String currencyCode;

    // Use BigDecimal for money to avoid floating point rounding errors.
    private BigDecimal balance;

    // Prefer java.time types for timestamps (timezone-safe and immutable).
    @Column("created_at")
    private Instant createdAt;
}
