package aiylbank.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransactionException.class)
    public ResponseEntity<ApiError> handleTransactionException(
            TransactionException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleEntityNotFoundException(
            EntityNotFoundException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ApiError.FieldValidationError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new ApiError.FieldValidationError(err.getField(), err.getDefaultMessage()))
                .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                request.getRequestURI(),
                fieldErrors
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleException(
            Exception ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request.getRequestURI(), null);
    }

    private ResponseEntity<ApiError> build(
            HttpStatus status,
            String message,
            String path,
            List<ApiError.FieldValidationError> fieldErrors
    ) {
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .fieldErrors(fieldErrors)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}
