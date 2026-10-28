package com.omnitask.AuthAndProfiles.application.services;

import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Acceso a Amazon S3 con dos buckets:
 * <ul>
 * <li>fotos de perfil (bucket con lectura pública, solo para objetos): la URL guardada en el perfil es permanente;</li>
 * <li>documentos de identidad (bucket PRIVADO): nunca se expone su URL, solo enlaces prefirmados de solo lectura
 * que se generan bajo demanda para quien revisa y vencen en pocos minutos (aws.s3.presign-ttl-minutes).</li>
 * </ul>
 * En AWS no hay credenciales en la configuración: el SDK usa el rol IAM de la tarea (cadena por defecto). Para
 * desarrollo local con MinIO se definen aws.s3.endpoint y las claves aws.s3.access-key / aws.s3.secret-key.
 */
@Slf4j
@Service
public class S3StorageService {

    @Value("${aws.s3.region:us-east-2}")
    private String region;

    @Value("${aws.s3.photos-bucket}")
    private String photosBucket;

    @Value("${aws.s3.documents-bucket}")
    private String documentsBucket;

    @Value("${aws.s3.presign-ttl-minutes:10}")
    private long presignTtlMinutes;

    @Value("${aws.s3.endpoint:}")
    private String endpoint;

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    private S3Client s3Client;
    private S3Presigner presigner;

    /** Los clientes del SDK son pesados y seguros entre hilos: se crean una sola vez. */
    @PostConstruct
    void init() {
        AwsCredentialsProvider credentials = accessKey.isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));

        var clientBuilder = S3Client.builder().region(Region.of(region)).credentialsProvider(credentials);
        var presignerBuilder = S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials);

        if (!endpoint.isBlank()) {
            // MinIO / LocalStack: direccionamiento por ruta (http://host:9000/bucket/clave)
            clientBuilder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
            presignerBuilder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        this.s3Client = clientBuilder.build();
        this.presigner = presignerBuilder.build();
    }

    public String uploadFile(MultipartFile file) {
        // Se sanea el nombre: la clave termina en una URL pública y no debe llevar espacios ni separadores.
        String original = file.getOriginalFilename() == null ? "foto" : file.getOriginalFilename();
        String key = UUID.randomUUID() + "_" + original.replaceAll("[^A-Za-z0-9._-]", "_");

        try (InputStream content = file.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(photosBucket).key(key).contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(content, file.getSize()));
            return publicUrl(photosBucket, key);
        } catch (IOException | SdkException e) {
            throw new ExternalServiceException("Error al subir el archivo a Amazon S3: " + e.getMessage());
        }
    }

    /**
     * Sube el documento al bucket privado con la clave {userId}/{uuid}.{ext}. El nombre original del archivo no se
     * usa nunca.
     *
     * @return la clave del objeto (no una URL)
     */
    public String uploadIdentityDocument(String userId, MultipartFile file, ValidatedDocument document) {
        String key = userId + "/" + UUID.randomUUID() + "." + document.extension();

        try (InputStream content = file.getInputStream()) {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(documentsBucket).key(key).contentType(document.contentType())
                            .build(),
                    RequestBody.fromInputStream(content, file.getSize()));
            return key;
        } catch (IOException | SdkException e) {
            throw new ExternalServiceException("Error al subir el documento a Amazon S3: " + e.getMessage());
        }
    }

    /** Genera un enlace de solo lectura que vence en pocos minutos (aws.s3.presign-ttl-minutes). */
    public SignedUrl generateIdentityDocumentReadUrl(String key) {
        Duration ttl = Duration.ofMinutes(presignTtlMinutes);
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(documentsBucket).key(key).build())
                .build();

        String url = presigner.presignGetObject(request).url().toString();
        return new SignedUrl(url, Instant.now().plus(ttl));
    }

    /** Borrado de mejor esfuerzo: un fallo aquí no debe romper el flujo del usuario. */
    public void deleteIdentityDocument(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(documentsBucket).key(key).build());
        } catch (Exception e) {
            log.warn("[STORAGE] No se pudo eliminar el documento {}: {}", key, e.getMessage());
        }
    }

    private String publicUrl(String bucket, String key) {
        if (!endpoint.isBlank()) {
            return endpoint.replaceAll("/+$", "") + "/" + bucket + "/" + key;
        }
        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }
}
