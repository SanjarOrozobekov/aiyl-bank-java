package aiylbank.service;

import aiylbank.entity.Account;

import java.math.BigDecimal;

public interface AccountService {
    Account findByAccountNumber(String accountNumber);

    void validateForTransfer(Account fromAccount, Account toAccount, BigDecimal amount);

    void applyTransfer(Account fromAccount, Account toAccount, BigDecimal amount);
}
