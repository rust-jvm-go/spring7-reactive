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
@Table("account")
public class Account {

    @Id
    private UUID id;

    private String name;

    @Column("currency_code")
    private String currencyCode;

    private BigDecimal balance;

    @Column("created_at")
    private Instant createdAt;
}
