package aiylbank.service.impl;

import aiylbank.dto.request.TransactionRequest;
import aiylbank.dto.response.TransactionResponse;
import aiylbank.entity.Account;
import aiylbank.entity.Transaction;
import aiylbank.enums.AccountStatus;
import aiylbank.enums.TransactionStatus;
import aiylbank.exceptions.TransactionException;
import aiylbank.mapper.TransactionMapper;
import aiylbank.repo.TransactionRepo;
import aiylbank.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private AccountService accountService;

    @Mock
    private TransactionRepo transactionRepo;

    @Mock
    private TransactionMapper transactionMapper;

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

        request = new TransactionRequest("111", "222", new BigDecimal("100.00"), "unique-key-123");
    }

    @Test
    @DisplayName("Успешный перевод: SUCCESS и сохранение транзакции")
    void transaction_Success() {
        when(transactionRepo.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.empty());
        when(accountService.findByAccountNumber("111")).thenReturn(sender);
        when(accountService.findByAccountNumber("222")).thenReturn(receiver);

        when(transactionRepo.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepo.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doNothing().when(accountService).validateForTransfer(sender, receiver, request.amount());
        doNothing().when(accountService).applyTransfer(sender, receiver, request.amount());

        when(transactionMapper.toResponse(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction t = invocation.getArgument(0);
                    return TransactionResponse.builder()
                            .id(t.getId())
                            .fromAccountNumber(t.getFromAccount().getAccountNumber())
                            .toAccountNumber(t.getToAccount().getAccountNumber())
                            .amount(t.getAmount())
                            .status(t.getStatus())
                            .reason(t.getReason())
                            .createdAt(LocalDateTime.now())
                            .build();
                });

        TransactionResponse response = transactionService.transaction(request);

        assertNotNull(response);
        assertEquals(TransactionStatus.SUCCESS, response.status());

        verify(transactionRepo).saveAndFlush(any(Transaction.class));
        verify(accountService).validateForTransfer(sender, receiver, request.amount());
        verify(accountService).applyTransfer(sender, receiver, request.amount());
        verify(transactionRepo).save(argThat(t -> t.getStatus() == TransactionStatus.SUCCESS));
        verify(transactionMapper, atLeastOnce()).toResponse(any(Transaction.class));
    }

    @Test
    @DisplayName("Ошибка валидации: сохраняется FAILED с reason")
    void transaction_ValidationError_ShouldSaveFailed() {
        when(transactionRepo.findByIdempotencyKey(request.idempotencyKey())).thenReturn(Optional.empty());
        when(accountService.findByAccountNumber("111")).thenReturn(sender);
        when(accountService.findByAccountNumber("222")).thenReturn(receiver);

        when(transactionRepo.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepo.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        doThrow(new TransactionException("Недостаточно средств на счете"))
                .when(accountService).validateForTransfer(sender, receiver, request.amount());

        when(transactionMapper.toResponse(any(Transaction.class)))
                .thenAnswer(invocation -> {
                    Transaction t = invocation.getArgument(0);
                    return TransactionResponse.builder()
                            .status(t.getStatus())
                            .reason(t.getReason())
                            .build();
                });

        TransactionResponse response = transactionService.transaction(request);

        assertEquals(TransactionStatus.FAILED, response.status());
        assertEquals("Недостаточно средств на счете", response.reason());

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepo, atLeastOnce()).save(captor.capture());
        Transaction saved = captor.getValue();
        assertEquals(TransactionStatus.FAILED, saved.getStatus());
        assertEquals("Недостаточно средств на счете", saved.getReason());

        verify(accountService, never()).applyTransfer(any(), any(), any());
    }

    @Test
    @DisplayName("Гонка по idempotencyKey: при unique-конфликте возвращается существующая транзакция")
    void transaction_IdempotencyConflict_ReturnExisting() {
        Transaction existing = Transaction.builder()
                .id(77L)
                .fromAccount(sender)
                .toAccount(receiver)
                .amount(request.amount())
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(request.idempotencyKey())
                .build();

        when(transactionRepo.findByIdempotencyKey(request.idempotencyKey()))
                .thenReturn(Optional.empty())          // первая проверка
                .thenReturn(Optional.of(existing));    // после DataIntegrityViolationException

        when(accountService.findByAccountNumber("111")).thenReturn(sender);
        when(accountService.findByAccountNumber("222")).thenReturn(receiver);

        when(transactionRepo.saveAndFlush(any(Transaction.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));

        when(transactionMapper.toResponse(existing))
                .thenReturn(TransactionResponse.builder()
                        .id(77L)
                        .status(TransactionStatus.SUCCESS)
                        .amount(request.amount())
                        .fromAccountNumber("111")
                        .toAccountNumber("222")
                        .build());

        TransactionResponse response = transactionService.transaction(request);

        assertNotNull(response);
        assertEquals(77L, response.id());
        assertEquals(TransactionStatus.SUCCESS, response.status());

        verify(transactionRepo).saveAndFlush(any(Transaction.class));
        verify(transactionRepo, never()).save(any(Transaction.class));
        verify(accountService, never()).validateForTransfer(any(), any(), any());
        verify(accountService, never()).applyTransfer(any(), any(), any());
    }
}