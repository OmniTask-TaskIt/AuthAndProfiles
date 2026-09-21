package com.omnitask.AuthAndProfiles.domain.exceptions;

/**
 * La cuenta existe pero no puede operar: suspendida, bloqueada o sin verificar (HTTP 403).
 */
public class AccountRestrictedException extends RuntimeException {

    public AccountRestrictedException(String message) {
        super(message);
    }
}
