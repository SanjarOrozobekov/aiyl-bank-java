package aiylbank.service.impl;

import aiylbank.dto.request.TransactionRequest;
import aiylbank.dto.response.TransactionResponse;
import aiylbank.entity.Account;
import aiylbank.entity.Transaction;
import aiylbank.enums.AccountStatus;
import aiylbank.enums.TransactionStatus;
import aiylbank.repo.AccountRepo;
import aiylbank.repo.TransactionRepo;
import aiylbank.service.serviceImpl.TransactionServiceImpl;
import aiylbank.exceptions.TransactionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceImplTest {
    @Mock private AccountRepo accountRepo;
    @Mock private TransactionRepo transactionRepo;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private Account sender;
    private Account receiver;
    private TransactionRequest request;

    @BeforeEach
    void setUp() {
        sender = Account.builder()
                .id(1L)
                .accountNumber("111")
                .balance(new BigDecimal("1000.00"))
                .status(AccountStatus.ACTIVE)
                .build();
        receiver = Account.builder()
                .id(2L)
                .accountNumber("222")
                .balance(new BigDecimal("500.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        request = new TransactionRequest("111","222",new BigDecimal("100.00"),"unique-key-123");
    }
    @Test
    @DisplayName("Успешный перевод: балансы обновлены, статус SUCCESS")
    void transaction_Success() {
        when(transactionRepo.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.empty());
        when(accountRepo.findByAccountNumber("111")).thenReturn(Optional.of(sender));
        when(accountRepo.findByAccountNumber("222")).thenReturn(Optional.of(receiver));
        when(transactionRepo.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionResponse response = transactionService.transaction(request);

        assertNotNull(response);
        assertEquals(TransactionStatus.SUCCESS, response.status());
        assertEquals(new BigDecimal("900.00"), sender.getBalance());
        assertEquals(new BigDecimal("600.00"), receiver.getBalance());

        verify(transactionRepo, atLeastOnce()).save(any(Transaction.class));
    }
    @Test
    @DisplayName("Идемпотентность: повторный запрос возвращает существующую транзакцию")
    void transaction_Idempotency_ReturnExisting(){
        Transaction existingTransaction = Transaction.builder()
                .id(99L)
                .fromAccount(sender)
                .toAccount(receiver)
                .amount(request.amount())
                .status(TransactionStatus.SUCCESS)
                .build();
        when(transactionRepo.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.of(existingTransaction));

        TransactionResponse response = transactionService.transaction(request);

        assertEquals(99L, response.id());
        assertEquals(TransactionStatus.SUCCESS, response.status());

        verify(accountRepo,never()).findByAccountNumber(anyString());
    }

    @Test
    @DisplayName("Ошибка: недостаточно средств, статус транзакции FAILED")
    void transaction_InsufficientFunds_ShouldSaveFailedStatus(){
        TransactionRequest largeRequest = new TransactionRequest("111","222",new BigDecimal("5000.00"),"key-failed");
        when(accountRepo.findByAccountNumber("111")).thenReturn(Optional.of(sender));
        when(accountRepo.findByAccountNumber("222")).thenReturn(Optional.of(receiver));

        assertThrows(TransactionException.class, () -> transactionService.transaction(largeRequest));

        verify(transactionRepo).save(argThat(t -> t.getStatus() == TransactionStatus.FAILED));
    }
}
