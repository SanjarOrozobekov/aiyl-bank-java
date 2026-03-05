package aiylbank.entity;

import aiylbank.enums.AccountStatus;
import aiylbank.exceptions.TransactionException;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "accounts",
        indexes = @Index(name = "idx_account_number", columnList = "accountNumber"))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "account_gen")
    @SequenceGenerator(name = "account_gen", sequenceName = "account_seq", allocationSize = 1)
    Long id;

    @Column(unique = true, nullable = false)
    String accountNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    BigDecimal balance;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    AccountStatus status;

    @Column(nullable = false)
    LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public void decrease(BigDecimal amount) {
        if (amount.signum() <= 0) throw new TransactionException("Amount must be positive");
        if (balance.compareTo(amount) < 0) throw new TransactionException("Insufficient balance");
        balance = balance.subtract(amount);
    }

    public void increase(BigDecimal amount) {
        if (amount.signum() <= 0) throw new TransactionException("Amount must be positive");
        balance = balance.add(amount);
    }
}