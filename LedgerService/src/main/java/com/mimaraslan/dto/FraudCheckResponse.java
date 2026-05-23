package com.mimaraslan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FraudCheckResponse {
    private boolean fraud;
    private String transferId;
    private String message;
    private String authToken; // Token'ı Kafka üzerinden taşımak için

}
