package aiylbank.service.impl;

import aiylbank.dto.request.TransactionRequest;
import aiylbank.dto.response.TransactionResponse;
import aiylbank.entity.Account;
import aiylbank.entity.Transaction;
import aiylbank.enums.TransactionStatus;
import aiylbank.exceptions.TransactionException;
import aiylbank.mapper.TransactionMapper;
import aiylbank.repo.TransactionRepo;
import aiylbank.service.AccountService;
import aiylbank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final AccountService accountService;
    private final TransactionRepo transactionRepo;
    private final TransactionMapper transactionMapper;

    @Override
    @Transactional
    public TransactionResponse transaction(TransactionRequest request) {
        log.info("Starting transfer from {} to {} amount {}",
                request.fromAccountNumber(), request.toAccountNumber(), request.amount());

        String idempotencyKey = request.idempotencyKey();

        if(idempotencyKey != null) {
            Optional<Transaction> existing = transactionRepo.findByIdempotencyKey(idempotencyKey);
            if(existing.isPresent()) {
                log.info("Duplicate request detected for key: {}", idempotencyKey);
                return transactionMapper.toResponse(existing.get());
            }
        }

        Account fromAccount = accountService.findByAccountNumber(request.fromAccountNumber());
        Account toAccount = accountService.findByAccountNumber(request.toAccountNumber());

        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.amount())
                .idempotencyKey(idempotencyKey)
                .status(TransactionStatus.FAILED)
                .build();

        if(idempotencyKey != null) {
            Optional<TransactionResponse> existingResponse = persistOrReturnExisting(transaction,idempotencyKey);
            if(existingResponse.isPresent()) {
                return existingResponse.get();
            }
        }
        try{
            accountService.validateForTransfer(fromAccount, toAccount, request.amount());
            accountService.applyTransfer(fromAccount, toAccount, request.amount());

            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setReason(null);
            log.info("Transfer successful for key: {}", request.idempotencyKey());

            return transactionMapper.toResponse(transactionRepo.save(transaction));
        }catch (Exception e){
            log.error("Transfer  failed: {}", e.getMessage());
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setReason(e.getMessage());
            return transactionMapper.toResponse(transactionRepo.save(transaction));
        }
    }
    private Optional<TransactionResponse> persistOrReturnExisting(Transaction transaction,String idempotencyKey) {
        try{
            transactionRepo.saveAndFlush(transaction);
            return Optional.empty();
        }catch(DataIntegrityViolationException ex){
            log.info("Idempotency conflict detected for key: {}", idempotencyKey);
            Transaction existingTransaction = transactionRepo.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new TransactionException("Не удалось получить транзакцию по ключу идемпотентности"));
            return Optional.of(transactionMapper.toResponse(existingTransaction));
        }
    }
}
