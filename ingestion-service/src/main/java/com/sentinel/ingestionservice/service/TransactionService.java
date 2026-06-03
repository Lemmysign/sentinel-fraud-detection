package com.sentinel.ingestionservice.service;

import com.sentinel.ingestionservice.dto.TransactionRequest;
import com.sentinel.ingestionservice.dto.TransactionResponse;


public interface TransactionService {

    /**
     * Receives a validated transaction request,
     * publishes it to the Kafka transactions.raw topic,
     * and returns an acknowledgement to the caller.

     * This method is non-blocking from the caller's
     * perspective — the fraud assessment happens
     * asynchronously after this returns.
     *
     * @param request the validated inbound transaction
     * @return acknowledgement with transaction ID and status
     */
    TransactionResponse submitTransaction(TransactionRequest request);
}