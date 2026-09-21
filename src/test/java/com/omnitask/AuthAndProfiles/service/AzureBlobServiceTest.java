package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.AzureBlobService;
import com.omnitask.AuthAndProfiles.application.services.SignedUrl;
import com.omnitask.AuthAndProfiles.application.services.ValidatedDocument;
import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
 * AzureBlobService crea el BlobServiceClientBuilder con "new" en cada operación, así que se intercepta la
 * construcción con Mockito.mockConstruction (mock-maker inline, por defecto en Mockito 5 / Spring Boot 3.3)
 * y no se hace ninguna llamada de red a Azure.
 */
class AzureBlobServiceTest {

    private static final String PHOTOS = "profiles-photos";
    private static final String DOCS = "identity-documents";

    private AzureBlobService azureBlobService;

    @BeforeEach
    void setUp() {
        azureBlobService = new AzureBlobService();
        ReflectionTestUtils.setField(azureBlobService, "connectionString", "connection-string-de-prueba");
        ReflectionTestUtils.setField(azureBlobService, "containerName", PHOTOS);
        ReflectionTestUtils.setField(azureBlobService, "documentsContainerName", DOCS);
        ReflectionTestUtils.setField(azureBlobService, "sasTtlMinutes", 10L);
    }

    private MockedConstruction<BlobServiceClientBuilder> mockBuilder(BlobServiceClient serviceClient) {
        return mockConstruction(BlobServiceClientBuilder.class, (builder, context) -> {
            when(builder.connectionString(anyString())).thenReturn(builder);
            when(builder.buildClient()).thenReturn(serviceClient);
        });
    }

    // ---------------------------------------------------------------- fotos de perfil

    @Test
    void uploadFile_deberiaSubirLaFotoYRetornarLaUrl_cuandoTodoSaleBien() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", new byte[] { 1, 2, 3 });
        BlobClient blobClient = mock(BlobClient.class);
        when(blobClient.getBlobUrl()).thenReturn("https://storage/profiles-photos/foto.png");
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(PHOTOS)).thenReturn(containerClient);

        try (MockedConstruction<BlobServiceClientBuilder> builders = mockBuilder(serviceClient)) {
            String url = azureBlobService.uploadFile(file);

            assertThat(url).isEqualTo("https://storage/profiles-photos/foto.png");
            verify(builders.constructed().get(0)).connectionString("connection-string-de-prueba");

            ArgumentCaptor<String> fileNameCaptor = ArgumentCaptor.forClass(String.class);
            verify(containerClient).getBlobClient(fileNameCaptor.capture());
            assertThat(fileNameCaptor.getValue()).matches("^[0-9a-fA-F-]{36}_foto\\.png$");
            verify(blobClient).upload(any(InputStream.class), anyLong(), anyBoolean());
        }
    }

    @Test
    void uploadFile_deberiaLanzarExternalServiceException_cuandoFallaLaLecturaDelArchivo() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("foto.png");
        when(file.getInputStream()).thenThrow(new IOException("fallo de lectura"));

        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient(anyString())).thenReturn(mock(BlobClient.class));
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(PHOTOS)).thenReturn(containerClient);

        try (MockedConstruction<BlobServiceClientBuilder> ignored = mockBuilder(serviceClient)) {
            assertThatThrownBy(() -> azureBlobService.uploadFile(file))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessage("Error al subir el archivo a Azure Blob Storage: fallo de lectura");
        }
    }

    @Test
    void uploadFile_deberiaLanzarExcepcion_cuandoElConnectionStringEsInvalido() {
        // El SDK real valida el formato del connection string localmente, sin red.
        ReflectionTestUtils.setField(azureBlobService, "connectionString", "connection-string-invalida");
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", new byte[] { 1, 2, 3 });

        assertThatThrownBy(() -> azureBlobService.uploadFile(file)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------- documentos de identidad (contenedor privado)

    @Test
    void uploadIdentityDocument_deberiaSubirAlContenedorPrivadoConNombreGeneradoYContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "mi cedula (1).pdf", "application/pdf",
                new byte[] { 1, 2, 3 });
        BlobClient blobClient = mock(BlobClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient(anyString())).thenReturn(blobClient);
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(DOCS)).thenReturn(containerClient);

        try (MockedConstruction<BlobServiceClientBuilder> ignored = mockBuilder(serviceClient)) {
            String blobName = azureBlobService.uploadIdentityDocument("user-1", file,
                    new ValidatedDocument("pdf", "application/pdf"));

            // El nombre original del archivo nunca se usa: userId/uuid.extensión
            assertThat(blobName).matches("^user-1/[0-9a-fA-F-]{36}\\.pdf$");
            verify(containerClient).createIfNotExists();
            verify(containerClient).getBlobClient(blobName);
            verify(blobClient).upload(any(InputStream.class), anyLong(), anyBoolean());

            ArgumentCaptor<BlobHttpHeaders> headers = ArgumentCaptor.forClass(BlobHttpHeaders.class);
            verify(blobClient).setHttpHeaders(headers.capture());
            assertThat(headers.getValue().getContentType()).isEqualTo("application/pdf");
        }
    }

    @Test
    void uploadIdentityDocument_deberiaLanzarExternalServiceException_cuandoFallaLaLectura() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("fallo de lectura"));

        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient(anyString())).thenReturn(mock(BlobClient.class));
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(DOCS)).thenReturn(containerClient);

        try (MockedConstruction<BlobServiceClientBuilder> ignored = mockBuilder(serviceClient)) {
            assertThatThrownBy(() -> azureBlobService.uploadIdentityDocument("user-1", file,
                    new ValidatedDocument("png", "image/png")))
                    .isInstanceOf(ExternalServiceException.class)
                    .hasMessage("Error al subir el documento a Azure Blob Storage: fallo de lectura");
        }
    }

    @Test
    void generateIdentityDocumentReadUrl_deberiaGenerarUnSasDeSoloLecturaQueVenceEnPocosMinutos() {
        BlobClient blobClient = mock(BlobClient.class);
        when(blobClient.getBlobUrl()).thenReturn("https://storage/identity-documents/user-1/abc.pdf");
        when(blobClient.generateSas(any(BlobServiceSasSignatureValues.class))).thenReturn("sv=2024&sig=firma");
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient("user-1/abc.pdf")).thenReturn(blobClient);
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(DOCS)).thenReturn(containerClient);

        try (MockedConstruction<BlobServiceClientBuilder> ignored = mockBuilder(serviceClient)) {
            Instant antes = Instant.now();
            SignedUrl signedUrl = azureBlobService.generateIdentityDocumentReadUrl("user-1/abc.pdf");

            assertThat(signedUrl.url())
                    .isEqualTo("https://storage/identity-documents/user-1/abc.pdf?sv=2024&sig=firma");
            assertThat(signedUrl.expiresAt()).isBetween(antes.plus(9, ChronoUnit.MINUTES),
                    antes.plus(11, ChronoUnit.MINUTES));

            ArgumentCaptor<BlobServiceSasSignatureValues> values = ArgumentCaptor
                    .forClass(BlobServiceSasSignatureValues.class);
            verify(blobClient).generateSas(values.capture());
            assertThat(values.getValue().getPermissions()).isEqualTo("r");
        }
    }

    @Test
    void deleteIdentityDocument_deberiaEliminarElBlobDelContenedorPrivado() {
        BlobClient blobClient = mock(BlobClient.class);
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient("user-1/abc.pdf")).thenReturn(blobClient);
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(DOCS)).thenReturn(containerClient);

        try (MockedConstruction<BlobServiceClientBuilder> ignored = mockBuilder(serviceClient)) {
            azureBlobService.deleteIdentityDocument("user-1/abc.pdf");

            verify(blobClient).deleteIfExists();
        }
    }

    @Test
    void deleteIdentityDocument_noDeberiaPropagarElError_cuandoFallaElBorrado() {
        BlobClient blobClient = mock(BlobClient.class);
        when(blobClient.deleteIfExists()).thenThrow(new RuntimeException("storage caído"));
        BlobContainerClient containerClient = mock(BlobContainerClient.class);
        when(containerClient.getBlobClient("user-1/abc.pdf")).thenReturn(blobClient);
        BlobServiceClient serviceClient = mock(BlobServiceClient.class);
        when(serviceClient.getBlobContainerClient(DOCS)).thenReturn(containerClient);

        try (MockedConstruction<BlobServiceClientBuilder> ignored = mockBuilder(serviceClient)) {
            assertThatCode(() -> azureBlobService.deleteIdentityDocument("user-1/abc.pdf"))
                    .doesNotThrowAnyException();
        }
    }
}
