package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.OtpGenerator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OtpGeneratorTest {

    private final OtpGenerator otpGenerator = new OtpGenerator();

    @Test
    void generate_deberiaRetornarSiempreUnCodigoDeSeisDigitos() {
        for (int i = 0; i < 500; i++) {
            assertThat(otpGenerator.generate()).matches("\\d{6}");
        }
    }

    @Test
    void generate_deberiaProducirCodigosDistintos() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            codes.add(otpGenerator.generate());
        }
        assertThat(codes.size()).isGreaterThan(1);
    }
}
