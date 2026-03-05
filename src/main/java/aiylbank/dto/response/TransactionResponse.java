package aiylbank.dto.response;

import aiylbank.enums.TransactionStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record TransactionResponse(
        Long id,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        TransactionStatus status,
        LocalDateTime createdAt,
        String reason
){
}
