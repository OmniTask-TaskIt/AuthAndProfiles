package com.omnitask.AuthAndProfiles.domain.policies;

/**
 * Versión vigente de los Términos y Condiciones. Es la que se guarda en el usuario al aceptar los términos.
 * Debe coincidir con la versión que muestra la página de términos del frontend (TermsPage).
 */
public final class TermsPolicy {

    public static final String CURRENT_VERSION = "1.1";

    private TermsPolicy() {
    }
}
