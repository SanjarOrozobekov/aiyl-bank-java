package aiylbank.controller;

import aiylbank.dto.request.TransactionRequest;
import aiylbank.dto.response.TransactionResponse;
import aiylbank.enums.TransactionStatus;
import aiylbank.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {
    private final TransactionService transactionService;
    @Operation(
            summary = "Создать новый перевод",
            description = "Метод переводит средства с одного счета на другой. Поддерживает идемпотентность через заголовок или тело запроса."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Перевод успешно обработан (может быть SUCCESS или FAILED в теле)"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации входных данных"),
            @ApiResponse(responseCode = "404", description = "Один из указанных счетов не найден")
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> transaction(@Valid @RequestBody TransactionRequest request) {
        log.info("Received transfer request: from {} to {} amount {} key{}",
                request.fromAccountNumber(), request.toAccountNumber(), request.amount(),request.idempotencyKey());

        TransactionResponse response = transactionService.transaction(request);
        if (response.status() == TransactionStatus.SUCCESS) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
