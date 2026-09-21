package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AzureBlobService construye el BlobServiceClientBuilder con "new" dentro de uploadFile, así que
 * se intercepta la construcción con Mockito.mockConstruction (mock-maker inline, por defecto en
 * Mockito 5 / Spring Boot 3.3) para no hacer ninguna llamada de red a Azure.
 */
class AzureBlobServiceTest {

    private static final String CONTAINER = "profiles-photos";

    private AzureBlobService azureBlobService;

    @BeforeEach
    void setUp() {
        azureBlobService = new AzureBlobService();
        ReflectionTestUtils.setField(azureBlobService, "connectionString", "connection-string-de-prueba");
        ReflectionTestUtils.setField(azureBlobService, "containerName", CONTAINER);
    }

    @Test
    void uploadFile_deberiaSubirElArchivoYRetornarLaUrl_cuandoTodoSaleBien() {
        // Arrange
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", new byte[] { 1, 2, 3 });

        BlobClient blobClient = mock(BlobClient.class);
        when(blobClient.getBlobUrl()).thenReturn("https://storage.blob.core.windows.net/profiles-photos/foto.png");
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(CONTAINER)).thenReturn(containerClient);

        try (MockedConstruction<BlobServiceClientBuilder> builders = mockConstruction(
                BlobServiceClientBuilder.class, (builder, context) -> {
                    when(builder.connectionString(anyString())).thenReturn(builder);
                    when(builder.buildClient()).thenReturn(serviceClient);
                })) {

            // Act
            String url = azureBlobService.uploadFile(file);

            // Assert
            assertThat(url).isEqualTo("https://storage.blob.core.windows.net/profiles-photos/foto.png");
            verify(builders.constructed().get(0)).connectionString("connection-string-de-prueba");

            ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
            verify(containerClient).getBlobClient(fileNameCaptor.capture());
            assertThat(fileNameCaptor.getValue()).matches("^[0-9a-fA-F-]{36}_foto\\.png$");

            verify(blobClient).upload(any(java.io.InputStream.class), anyLong(), anyBoolean());
        }
    }

    @Test
    void uploadFile_deberiaLanzarRuntimeException_cuandoFallaLaLecturaDelArchivo() throws Exception {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("foto.png");
        when(file.getInputStream()).thenThrow(new IOException("fallo de lectura"));

        BlobClient blobClient = mock(BlobClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(CONTAINER)).thenReturn(containerClient);

        try (MockedConstruction<BlobServiceClientBuilder> ignored = mockConstruction(
                BlobServiceClientBuilder.class, (builder, context) -> {
                    when(builder.connectionString(anyString())).thenReturn(builder);
                    when(builder.buildClient()).thenReturn(serviceClient);
                })) {

            // Act & Assert
            assertThatThrownBy(() -> azureBlobService.uploadFile(file))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Error al subir el archivo a Azure Blob Storage: fallo de lectura");
        }
    }

    @Test
    void uploadFile_deberiaLanzarExcepcion_cuandoElConnectionStringEsInvalido() {
        // Arrange: el SDK real valida el formato del connection string localmente, sin red.
        // Esa IllegalArgumentException no la captura el catch (solo IOException) y se propaga.
        ReflectionTestUtils.setField(azureBlobService, "connectionString", "connection-string-invalida");
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", new byte[] { 1, 2, 3 });

        // Act & Assert
        assertThatThrownBy(() -> azureBlobService.uploadFile(file))
                .isInstanceOf(IllegalArgumentException.class);
    }
}