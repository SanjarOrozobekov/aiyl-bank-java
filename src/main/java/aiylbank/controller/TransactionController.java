package aiylbank.controller;

import aiylbank.dto.request.TransactionRequest;
import aiylbank.dto.response.TransactionResponse;
import aiylbank.enums.TransactionStatus;
import aiylbank.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Transfers", description = "API для внутренних банковских переводов")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "Выполнить перевод средств",
            description = "Метод осуществляет перевод между счетами. " +
                    "Поддерживает идемпотентность через поле idempotencyKey в теле запроса."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Запрос обработан (результат SUCCESS или FAILED указан в теле)"),
            @ApiResponse(responseCode = "400", description = "Ошибка валидации входных данных или нарушение бизнес-логики"),
            @ApiResponse(responseCode = "404", description = "Один из указанных счетов не найден"),
            @ApiResponse(responseCode = "409", description = "Конфликт данных при повторном использовании ключа идемпотентности")
    })
    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(@Valid @RequestBody TransactionRequest request) {
        log.info("Received transfer request: from {} to {} amount {} key={}",
                request.fromAccountNumber(),
                request.toAccountNumber(),
                request.amount(),
                request.idempotencyKey());

        TransactionResponse response = transactionService.transaction(request);
        if (response.status() == TransactionStatus.SUCCESS) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}