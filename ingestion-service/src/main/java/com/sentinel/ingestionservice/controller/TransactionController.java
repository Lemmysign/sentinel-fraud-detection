package com.sentinel.ingestionservice.controller;

import com.sentinel.ingestionservice.dto.TransactionRequest;
import com.sentinel.ingestionservice.dto.TransactionResponse;
import com.sentinel.ingestionservice.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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


@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(
        name = "Transaction Ingestion",
        description = "Submit transactions for real-time fraud assessment"
)
public class TransactionController {

    private final TransactionService transactionService;

    /**
     * Submit a transaction for fraud assessment.
     *
     * The @Valid annotation triggers Spring's validation
     * framework which checks all the constraints defined
     * on TransactionRequest fields (@NotBlank, @Pattern etc).
     *
     * If validation fails, MethodArgumentNotValidException
     * is thrown before this method body even executes.
     * GlobalExceptionHandler catches it and returns
     * a 400 with field-level error details.
     *
     * If validation passes, the request reaches the service.
     *
     * Returns 202 ACCEPTED — not 200 OK.
     * 202 means "we received your request and will process
     * it asynchronously." This is semantically correct
     * because fraud assessment happens after this returns.
     * 200 OK would imply processing is complete.
     */
    @PostMapping
    @Operation(
            summary = "Submit a transaction for fraud assessment",
            description = "Receives a transaction, validates it, and publishes " +
                    "it to the fraud detection pipeline. Returns immediately " +
                    "with a transaction ID. Assessment is asynchronous."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Transaction accepted for processing",
                    content = @Content(schema = @Schema(
                            implementation = TransactionResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid transaction data"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error"
            )
    })
    public ResponseEntity<TransactionResponse> submitTransaction(
            @Valid @RequestBody TransactionRequest request) {

        log.info("Received transaction request — account: {}, amount: {} {}",
                request.getAccountId(),
                request.getAmount(),
                request.getCurrency());

        TransactionResponse response =
                transactionService.submitTransaction(request);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }
}