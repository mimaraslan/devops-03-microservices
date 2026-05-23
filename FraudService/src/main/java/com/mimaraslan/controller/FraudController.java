package com.mimaraslan.controller;

import com.mimaraslan.dto.TransferRequest;
import com.mimaraslan.dto.FraudCheckResponse;
import com.mimaraslan.service.FraudService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/fraud")
public class FraudController {

    private final FraudService fraudService;

    @PostMapping("/check")
    public FraudCheckResponse checkFraud(@RequestBody TransferRequest request) {
        boolean isFraud = fraudService.checkFraud(request);

        FraudCheckResponse response = new FraudCheckResponse();
        response.setFraud(isFraud);
        response.setMessage(isFraud ? "Fraudulent transaction detected" : "Transaction is valid");

        return response;
    }

}
