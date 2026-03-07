package aiylbank.service.impl;

import aiylbank.entity.Account;
import aiylbank.enums.AccountStatus;
import aiylbank.exceptions.EntityNotFoundException;
import aiylbank.exceptions.TransactionException;
import aiylbank.repo.AccountRepo;
import aiylbank.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepo accountRepo;

    @Override
    public Account findByAccountNumber(String accountNumber) {
        return accountRepo.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountNumber));
    }

    @Override
    public void validateForTransfer(Account fromAccount, Account toAccount, BigDecimal amount) {
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new TransactionException("Перевод самому себе запрещен");
        }

        if (fromAccount.getStatus() != AccountStatus.ACTIVE || toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new TransactionException("Оба счета должны быть ACTIVE");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TransactionException("Сумма должна быть больше нуля");
        }

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new TransactionException("Недостаточно средств на счете");
        }
    }

    @Override
    public void applyTransfer(Account fromAccount, Account toAccount, BigDecimal amount) {
        fromAccount.decrease(amount);
        toAccount.increase(amount);
    }
}
