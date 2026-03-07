package aiylbank.dto.request;

import jakarta.validation.constraints.*;
import lombok.Builder;


import java.math.BigDecimal;

@Builder
public record TransactionRequest(

        @NotBlank(message = "From account number must not be blank")
        @Size(max = 30, message = "From account number is too long")
        String fromAccountNumber,

        @NotBlank(message = "To account number must not be blank")
        @Size(max = 30, message = "To account number is too long")
        String toAccountNumber,

        @NotNull(message = "Amount must not be null")
        @DecimalMin(value = "0.01", inclusive = true, message = "Amount must be greater than zero")
        @Digits(integer = 12, fraction = 2, message = "Amount format is invalid")
        BigDecimal amount,

        @Size(max = 100, message = "Idempotency key is too long")
        String idempotencyKey
) {
}
