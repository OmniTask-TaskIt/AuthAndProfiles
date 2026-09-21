package com.omnitask.AuthAndProfiles.application.services;

import com.omnitask.AuthAndProfiles.domain.exceptions.ExternalServiceException;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Acceso a Azure Blob Storage con dos contenedores:
 * <ul>
 * <li>fotos de perfil (contenedor público de lectura, como hasta ahora);</li>
 * <li>documentos de identidad (contenedor PRIVADO): nunca se expone su URL, solo enlaces temporales (SAS)
 * de solo lectura que se generan bajo demanda para quien revisa.</li>
 * </ul>
 */
@Slf4j
@Service
public class AzureBlobService {

    @Value("${azure.storage.connection-string}")
    private String connectionString;

    @Value("${azure.storage.container-name}")
    private String containerName;

    @Value("${azure.storage.documents-container-name}")
    private String documentsContainerName;

    @Value("${azure.storage.sas-ttl-minutes:10}")
    private long sasTtlMinutes;

    public String uploadFile(MultipartFile file) {
        try {
            BlobContainerClient containerClient = newServiceClient().getBlobContainerClient(containerName);

            String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            BlobClient blobClient = containerClient.getBlobClient(fileName);

            blobClient.upload(file.getInputStream(), file.getSize(), true);

            return blobClient.getBlobUrl();

        } catch (IOException e) {
            throw new ExternalServiceException("Error al subir el archivo a Azure Blob Storage: " + e.getMessage());
        }
    }

    /**
     * Sube el documento al contenedor privado (se crea si no existe) con el nombre {userId}/{uuid}.{ext}.
     * El nombre original del archivo no se usa nunca.
     *
     * @return el nombre del blob (no una URL)
     */
    public String uploadIdentityDocument(String userId, MultipartFile file, ValidatedDocument document) {
        try {
            BlobContainerClient containerClient = newServiceClient().getBlobContainerClient(documentsContainerName);
            containerClient.createIfNotExists();

            String blobName = userId + "/" + UUID.randomUUID() + "." + document.extension();
            BlobClient blobClient = containerClient.getBlobClient(blobName);

            blobClient.upload(file.getInputStream(), file.getSize(), true);
            blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType(document.contentType()));

            return blobName;
        } catch (IOException e) {
            throw new ExternalServiceException("Error al subir el documento a Azure Blob Storage: " + e.getMessage());
        }
    }

    /** Genera un enlace de solo lectura que vence en pocos minutos (azure.storage.sas-ttl-minutes). */
    public SignedUrl generateIdentityDocumentReadUrl(String blobName) {
        BlobClient blobClient = newServiceClient().getBlobContainerClient(documentsContainerName)
                .getBlobClient(blobName);

        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(sasTtlMinutes);
        BlobServiceSasSignatureValues values = new BlobServiceSasSignatureValues(expiresAt,
                new BlobSasPermission().setReadPermission(true));
        // Margen hacia atrás para tolerar diferencias de reloj entre servidores.
        values.setStartTime(OffsetDateTime.now().minusMinutes(5));

        String sasToken = blobClient.generateSas(values);
        return new SignedUrl(blobClient.getBlobUrl() + "?" + sasToken, expiresAt.toInstant());
    }

    /** Borrado de mejor esfuerzo: un fallo aquí no debe romper el flujo del usuario. */
    public void deleteIdentityDocument(String blobName) {
        try {
            newServiceClient().getBlobContainerClient(documentsContainerName).getBlobClient(blobName)
                    .deleteIfExists();
        } catch (Exception e) {
            log.warn("[STORAGE] No se pudo eliminar el documento {}: {}", blobName, e.getMessage());
        }
    }

    private BlobServiceClient newServiceClient() {
        return new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
    }
}
