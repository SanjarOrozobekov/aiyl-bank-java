package aiylbank.service.impl;

import aiylbank.dto.request.TransactionRequest;
import aiylbank.dto.response.TransactionResponse;
import aiylbank.entity.Account;
import aiylbank.entity.Transaction;
import aiylbank.enums.TransactionStatus;
import aiylbank.exceptions.EntityNotFoundException;
import aiylbank.exceptions.TransactionException;
import aiylbank.mapper.TransactionMapper;
import aiylbank.repo.AccountRepo;
import aiylbank.repo.TransactionRepo;
import aiylbank.service.AccountService;
import aiylbank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final AccountService accountService;
    private final TransactionRepo transactionRepo;
    private final AccountRepo accountRepo;
    private final TransactionMapper transactionMapper;

    @Override
    @Transactional
    public TransactionResponse transaction(TransactionRequest request) {
        log.info("Processing transfer: {} -> {} amount {}",
                request.fromAccountNumber(), request.toAccountNumber(), request.amount());

        String idempotencyKey = request.idempotencyKey();

        if (idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepo.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return transactionMapper.toResponse(existing.get());
            }
        }

        List<Account> lockedAccounts = accountRepo.findAllForTransferLocked(
                List.of(request.fromAccountNumber(), request.toAccountNumber()));

        Map<String, Account> accountMap = lockedAccounts.stream()
                .collect(Collectors.toMap(Account::getAccountNumber, a -> a));

        Account fromAccount = Optional.ofNullable(accountMap.get(request.fromAccountNumber()))
                .orElseThrow(() -> new EntityNotFoundException("Sender account not found: " + request.fromAccountNumber()));
        Account toAccount = Optional.ofNullable(accountMap.get(request.toAccountNumber()))
                .orElseThrow(() -> new EntityNotFoundException("Receiver account not found: " + request.toAccountNumber()));

        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.amount())
                .idempotencyKey(idempotencyKey)
                .status(TransactionStatus.FAILED)
                .reason("PROCESSING")
                .build();

        if (idempotencyKey != null) {
            transaction = persistOrReturnTransaction(transaction, idempotencyKey);
            if (transaction.getId() != null && !TransactionStatus.FAILED.equals(transaction.getStatus())
                    && !"PROCESSING".equals(transaction.getReason())) {
                return transactionMapper.toResponse(transaction);
            }
        } else {
            transaction = transactionRepo.saveAndFlush(transaction);
        }

        try {
            accountService.validateForTransfer(fromAccount, toAccount, request.amount());
            accountService.applyTransfer(fromAccount, toAccount, request.amount());

            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setReason(null);

        } catch (TransactionException e) {
            log.error("Business error: {}", e.getMessage());
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setReason(e.getMessage());
        }

        return transactionMapper.toResponse(transactionRepo.save(transaction));
    }

    private Transaction persistOrReturnTransaction(Transaction transaction, String idempotencyKey) {
        try {
            return transactionRepo.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException ex) {
            return transactionRepo.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new TransactionException("Idempotency conflict detected"));
        }
    }
}