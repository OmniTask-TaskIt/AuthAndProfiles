package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * NOTA IMPORTANTE:
 * Igual que GoogleAuthService, AzureBlobService construye el BlobServiceClient directamente
 * dentro del método uploadFile (no se inyecta), así que el camino feliz de subir un archivo
 * requiere una llamada de red real a Azure y no se puede testear como unidad pura tal como
 * está escrito hoy.
 * <p>
 * Lo que sí se puede probar sin red es que un connection string mal formado falla rápido:
 * el SDK de Azure valida el formato del connection string de forma local antes de intentar
 * cualquier llamada de red, y esa excepción (IllegalArgumentException) NO está siendo
 * capturada por el catch actual del método (solo captura IOException) — se propaga sin
 * envolver. Vale la pena revisar si eso es intencional o si conviene capturar Exception
 * en general para devolver siempre un error controlado.
 * <p>
 * Recomendación: para cubrir el camino feliz, extraer la creación de BlobServiceClient a un
 * método/bean inyectable para poder mockearlo en el test.
 */
class AzureBlobServiceTest {

    private AzureBlobService azureBlobService;

    @BeforeEach
    void setUp() {
        azureBlobService = new AzureBlobService();
        ReflectionTestUtils.setField(azureBlobService, "connectionString", "connection-string-invalida");
        ReflectionTestUtils.setField(azureBlobService, "containerName", "profiles-photos");
    }

    @Test
    void uploadFile_deberiaLanzarExcepcion_cuandoElConnectionStringEsInvalido() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", new byte[] { 1, 2, 3 });

        // Act & Assert
        assertThatThrownBy(() -> azureBlobService.uploadFile(file))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
