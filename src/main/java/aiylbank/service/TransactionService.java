package aiylbank.service;

import aiylbank.dto.request.TransactionRequest;
import aiylbank.dto.response.TransactionResponse;

public interface TransactionService {
    TransactionResponse transaction(TransactionRequest transactionRequest);
}
