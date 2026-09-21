package com.omnitask.AuthAndProfiles.application.services;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Genera códigos OTP de 6 dígitos con un generador criptográficamente seguro. */
@Component
public class OtpGenerator {

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
