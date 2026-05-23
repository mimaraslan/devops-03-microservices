package com.mimaraslan.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse implements Serializable {

    private Long userId;
    private String email;
    private String message;

    /** Keycloak access token — Authorization: Bearer ... ile gateway ve korumalı uçlarda kullanın */
    private String accessToken;
    private String refreshToken;
    private Integer expiresIn;
    private String tokenType;
}

