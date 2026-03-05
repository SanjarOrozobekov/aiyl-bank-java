package aiylbank.service.serviceImpl;

import aiylbank.dto.request.TransactionRequest;
import aiylbank.dto.response.TransactionResponse;
import aiylbank.entity.Account;
import aiylbank.entity.Transaction;
import aiylbank.enums.AccountStatus;
import aiylbank.enums.TransactionStatus;
import aiylbank.exceptions.EntityNotFoundException;
import aiylbank.exceptions.TransactionException;
import aiylbank.repo.AccountRepo;
import aiylbank.repo.TransactionRepo;
import aiylbank.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final AccountRepo accountRepo;
    private final TransactionRepo transactionRepo;

    @Override
    @Transactional
    public TransactionResponse transaction(TransactionRequest request) {
        log.info("Starting transfer from {} to {} amount {}",
                request.fromAccountNumber(), request.toAccountNumber(), request.amount());

        if(request.idempotencyKey() != null){
            Optional<Transaction> existing = transactionRepo.findByIdempotencyKey(request.idempotencyKey());
            if(existing.isPresent()){
                log.info("Duplicate request detected for key: {}", request.idempotencyKey());
                return mapToResponse(existing.get());
            }
        }

        Account fromAccount = accountRepo.findByAccountNumber(request.fromAccountNumber())
                .orElseThrow(() -> new EntityNotFoundException("Sender account not found"));
        Account toAccount = accountRepo.findByAccountNumber(request.toAccountNumber())
                .orElseThrow(() -> new EntityNotFoundException("Receiver account not found"));

        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.amount())
                .idempotencyKey(request.idempotencyKey())
                .build();

        try{
            validate(fromAccount, toAccount, request.amount());

            fromAccount.decrease(request.amount());
            toAccount.increase(request.amount());

            transaction.setStatus(TransactionStatus.SUCCESS);
            log.info("Transfer successful for key: {}", request.idempotencyKey());

            return mapToResponse(transactionRepo.save(transaction));
        }catch (Exception e){
            log.error("Transfer  failed: {}", e.getMessage());
            transaction.setStatus(TransactionStatus.FAILED);

            transaction.setReason(e.getMessage());

            Transaction savedTransaction = transactionRepo.save(transaction);
            return mapToResponse(savedTransaction);
        }
    }

    private void validate(Account from, Account to, BigDecimal amount){
        if(from.getId().equals(to.getId())){
            throw new TransactionException("Перевод самому себе запрещен");
        }
        if(from.getStatus() != AccountStatus.ACTIVE || to.getStatus() != AccountStatus.ACTIVE){
            throw new TransactionException("Оба счета должны быть ACTIVE");
        }
        if(from.getBalance().compareTo(amount) < 0){
            throw new TransactionException("Недостаточно средств на счете");
        }
        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new TransactionException("Сумма должна быть больше нуля");
        }
    }

    private TransactionResponse mapToResponse(Transaction transfer) {
        return TransactionResponse.builder()
                .id(transfer.getId())
                .fromAccountNumber(transfer.getFromAccount().getAccountNumber())
                .toAccountNumber(transfer.getToAccount().getAccountNumber())
                .amount(transfer.getAmount())
                .status(transfer.getStatus())
                .reason(transfer.getReason())
                .createdAt(transfer.getCreatedAt())
                .build();
    }
}
