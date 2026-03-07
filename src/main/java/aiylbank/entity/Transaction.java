package aiylbank.entity;

import aiylbank.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transaction_gen")
    @SequenceGenerator(name = "transaction_gen", sequenceName = "transaction_seq", allocationSize = 1)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_account_id", nullable = false)
    Account fromAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", nullable = false)
    Account toAccount;

    @Column(nullable = false, precision = 10, scale = 2)
    BigDecimal amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    TransactionStatus status;

    @Column(nullable = false)
    LocalDateTime createdAt;

    @Column(unique = true)
    String idempotencyKey;

    private String reason;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
