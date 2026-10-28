package com.omnitask.AuthAndProfiles.service;

import com.omnitask.AuthAndProfiles.application.services.S3StorageService;
import com.omnitask.AuthAndProfiles.application.services.SignedUrl;
import com.omnitask.AuthAndProfiles.application.services.ValidatedDocument;
import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los clientes del SDK (S3Client y S3Presigner) se reemplazan por mocks: no se hace ninguna llamada de red a AWS.
 */
class S3StorageServiceTest {

    private static final String PHOTOS = "taskit-profile-photos";
    private static final String DOCS = "taskit-identity-documents";

    private S3StorageService storageService;
    private S3Client s3Client;
    private S3Presigner presigner;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        presigner = mock(S3Presigner.class);
        storageService = new S3StorageService();
        ReflectionTestUtils.setField(storageService, "region", "us-east-2");
        ReflectionTestUtils.setField(storageService, "photosBucket", PHOTOS);
        ReflectionTestUtils.setField(storageService, "documentsBucket", DOCS);
        ReflectionTestUtils.setField(storageService, "presignTtlMinutes", 10L);
        ReflectionTestUtils.setField(storageService, "endpoint", "");
        ReflectionTestUtils.setField(storageService, "accessKey", "");
        ReflectionTestUtils.setField(storageService, "secretKey", "");
        ReflectionTestUtils.setField(storageService, "s3Client", s3Client);
        ReflectionTestUtils.setField(storageService, "presigner", presigner);
    }

    // ---------------------------------------------------------------- construcción de los clientes

    @Test
    void init_deberiaConstruirLosClientes_conLaCadenaDeCredencialesPorDefecto() {
        S3StorageService service = new S3StorageService();
        ReflectionTestUtils.setField(service, "region", "us-east-2");
        ReflectionTestUtils.setField(service, "endpoint", "");
        ReflectionTestUtils.setField(service, "accessKey", "");
        ReflectionTestUtils.setField(service, "secretKey", "");

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "init")).doesNotThrowAnyException();
        assertThat(ReflectionTestUtils.getField(service, "s3Client")).isNotNull();
        assertThat(ReflectionTestUtils.getField(service, "presigner")).isNotNull();
    }

    @Test
    void init_deberiaConstruirLosClientes_conEndpointYClavesEstaticas_paraMinio() {
        S3StorageService service = new S3StorageService();
        ReflectionTestUtils.setField(service, "region", "us-east-2");
        ReflectionTestUtils.setField(service, "endpoint", "http://localhost:9000");
        ReflectionTestUtils.setField(service, "accessKey", "minioadmin");
        ReflectionTestUtils.setField(service, "secretKey", "minioadmin");

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "init")).doesNotThrowAnyException();
        assertThat(ReflectionTestUtils.getField(service, "s3Client")).isNotNull();
        assertThat(ReflectionTestUtils.getField(service, "presigner")).isNotNull();
    }

    // ---------------------------------------------------------------- fotos de perfil

    @Test
    void uploadFile_deberiaSubirLaFotoYRetornarLaUrlPublica_cuandoTodoSaleBien() {
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", new byte[] { 1, 2, 3 });

        String url = storageService.uploadFile(file);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo(PHOTOS);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().key()).matches("^[0-9a-fA-F-]{36}_foto\\.png$");
        assertThat(url).isEqualTo("https://" + PHOTOS + ".s3.us-east-2.amazonaws.com/" + request.getValue().key());
    }

    @Test
    void uploadFile_deberiaSanearElNombreDelArchivo_paraQueLaUrlSeaValida() {
        MockMultipartFile file = new MockMultipartFile("file", "mi foto (1)/x.png", "image/png", new byte[] { 1 });

        String url = storageService.uploadFile(file);

        assertThat(url).doesNotContain(" ", "(", ")");
        assertThat(url).matches("^https://[^ ]+_mi_foto__1__x\\.png$");
    }

    @Test
    void uploadFile_deberiaUsarUnNombreGenerico_cuandoElArchivoNoTieneNombre() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(null);
        when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[] { 1 }));
        when(file.getSize()).thenReturn(1L);

        String url = storageService.uploadFile(file);

        assertThat(url).matches("^https://[^ ]+/[0-9a-fA-F-]{36}_foto$");
    }

    @Test
    void uploadFile_deberiaUsarElEndpointConRuta_cuandoSeConfiguraMinio() {
        ReflectionTestUtils.setField(storageService, "endpoint", "http://localhost:9000/");
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", new byte[] { 1 });

        String url = storageService.uploadFile(file);

        assertThat(url).startsWith("http://localhost:9000/" + PHOTOS + "/").endsWith("_foto.png");
    }

    @Test
    void uploadFile_deberiaLanzarExternalServiceException_cuandoFallaLaLecturaDelArchivo() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("foto.png");
        when(file.getInputStream()).thenThrow(new IOException("fallo de lectura"));

        assertThatThrownBy(() -> storageService.uploadFile(file))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Error al subir el archivo a Amazon S3: fallo de lectura");
    }

    @Test
    void uploadFile_deberiaLanzarExternalServiceException_cuandoS3Falla() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("S3 no disponible"));
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", new byte[] { 1 });

        assertThatThrownBy(() -> storageService.uploadFile(file))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Error al subir el archivo a Amazon S3")
                .hasMessageContaining("S3 no disponible");
    }

    // ---------------------------------------------------------------- documentos de identidad (bucket privado)

    @Test
    void uploadIdentityDocument_deberiaSubirAlBucketPrivadoConNombreGeneradoYContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "mi cedula (1).pdf", "application/pdf",
                new byte[] { 1, 2, 3 });

        String key = storageService.uploadIdentityDocument("user-1", file,
                new ValidatedDocument("pdf", "application/pdf"));

        // El nombre original del archivo nunca se usa: userId/uuid.extensión
        assertThat(key).matches("^user-1/[0-9a-fA-F-]{36}\\.pdf$");
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo(DOCS);
        assertThat(request.getValue().key()).isEqualTo(key);
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void uploadIdentityDocument_deberiaLanzarExternalServiceException_cuandoFallaLaLectura() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("fallo de lectura"));

        assertThatThrownBy(() -> storageService.uploadIdentityDocument("user-1", file,
                new ValidatedDocument("png", "image/png")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Error al subir el documento a Amazon S3: fallo de lectura");
    }

    @Test
    void uploadIdentityDocument_deberiaLanzarExternalServiceException_cuandoS3Falla() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("acceso denegado"));
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", new byte[] { 1 });

        assertThatThrownBy(() -> storageService.uploadIdentityDocument("user-1", file,
                new ValidatedDocument("pdf", "application/pdf")))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("Error al subir el documento a Amazon S3");
    }

    @Test
    void generateIdentityDocumentReadUrl_deberiaGenerarUnEnlaceDeSoloLecturaQueVenceEnPocosMinutos() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://" + DOCS + ".s3.amazonaws.com/user-1/abc.pdf?X-Amz-Signature=firma")
                .toURL());
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        Instant antes = Instant.now();
        SignedUrl signedUrl = storageService.generateIdentityDocumentReadUrl("user-1/abc.pdf");

        assertThat(signedUrl.url()).isEqualTo("https://" + DOCS + ".s3.amazonaws.com/user-1/abc.pdf?X-Amz-Signature=firma");
        assertThat(signedUrl.expiresAt()).isBetween(antes.plus(9, ChronoUnit.MINUTES), antes.plus(11, ChronoUnit.MINUTES));

        ArgumentCaptor<GetObjectPresignRequest> request = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(request.capture());
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo(DOCS);
        assertThat(request.getValue().getObjectRequest().key()).isEqualTo("user-1/abc.pdf");
        assertThat(request.getValue().signatureDuration()).hasMinutes(10);
    }

    @Test
    void deleteIdentityDocument_deberiaEliminarElObjetoDelBucketPrivado() {
        storageService.deleteIdentityDocument("user-1/abc.pdf");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(DOCS);
        assertThat(request.getValue().key()).isEqualTo("user-1/abc.pdf");
    }

    @Test
    void deleteIdentityDocument_noDeberiaPropagarElError_cuandoFallaElBorrado() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(new RuntimeException("storage caído"));

        assertThatCode(() -> storageService.deleteIdentityDocument("user-1/abc.pdf")).doesNotThrowAnyException();
    }
}
