package aiylbank.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;


import java.math.BigDecimal;

@Builder
public record TransactionRequest(

        @NotBlank(message = "From account number must not be blank")
        String fromAccountNumber,

        @NotBlank(message = "To account number must not be blank")
        String toAccountNumber,

        @NotNull(message = "Amount must not be null")
        @Positive(message = "Amount must be greater than zero")
        BigDecimal amount,

        @Size(max = 100)
        String idempotencyKey
) {}
