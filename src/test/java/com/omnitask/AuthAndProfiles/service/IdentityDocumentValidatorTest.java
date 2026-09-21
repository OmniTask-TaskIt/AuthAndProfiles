package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.IdentityDocumentValidator;
import com.omnitask.AuthAndProfiles.application.services.ValidatedDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityDocumentValidatorTest {

    private final IdentityDocumentValidator validator = new IdentityDocumentValidator();

    private MultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }

    @Test
    void validate_deberiaAceptarUnPdf() {
        byte[] pdf = "%PDF-1.7 contenido".getBytes();

        ValidatedDocument result = validator.validate(file("cedula.pdf", "application/pdf", pdf));

        assertThat(result.extension()).isEqualTo("pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void validate_deberiaAceptarUnJpeg() {
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10 };

        ValidatedDocument result = validator.validate(file("foto.jpg", "image/jpeg", jpeg));

        assertThat(result.extension()).isEqualTo("jpg");
        assertThat(result.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void validate_deberiaAceptarUnPng() {
        byte[] png = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00 };

        ValidatedDocument result = validator.validate(file("foto.png", "image/png", png));

        assertThat(result.extension()).isEqualTo("png");
        assertThat(result.contentType()).isEqualTo("image/png");
    }

    @Test
    void validate_noDeberiaConfiarEnElContentTypeNiEnLaExtensionDelCliente() {
        byte[] ejecutable = "MZ\u0090\u0000 esto no es un pdf".getBytes();

        assertThatThrownBy(() -> validator.validate(file("virus.pdf", "application/pdf", ejecutable)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato no permitido");
    }

    @Test
    void validate_deberiaRechazarUnaFirmaIncompleta() {
        assertThatThrownBy(() -> validator.validate(file("a.pdf", "application/pdf", new byte[] { 0x25 })))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato no permitido");
        assertThatThrownBy(() -> validator.validate(file("b.pdf", "application/pdf", "%PDX-1".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Formato no permitido");
    }

    @Test
    void validate_deberiaRechazarArchivosVaciosONulos() {
        assertThatThrownBy(() -> validator.validate(file("vacio.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacío");
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacío");
    }

    @Test
    void validate_deberiaRechazarArchivosMayoresA5Mb() {
        MultipartFile grande = mock(MultipartFile.class);
        when(grande.isEmpty()).thenReturn(false);
        when(grande.getSize()).thenReturn(6L * 1024 * 1024);

        assertThatThrownBy(() -> validator.validate(grande))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void validate_deberiaRechazarElArchivo_cuandoNoSePuedeLeer() throws Exception {
        MultipartFile roto = mock(MultipartFile.class);
        when(roto.isEmpty()).thenReturn(false);
        when(roto.getSize()).thenReturn(10L);
        when(roto.getInputStream()).thenThrow(new IOException("disco"));

        assertThatThrownBy(() -> validator.validate(roto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No se pudo leer");
    }
}
