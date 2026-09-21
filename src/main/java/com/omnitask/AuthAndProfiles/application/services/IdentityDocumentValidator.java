package com.omnitask.AuthAndProfiles.application.services;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Valida el documento de identidad: no vacío, tamaño máximo y formato real (PDF, JPG o PNG) según los
 * primeros bytes del archivo. No se confía en el nombre ni en el Content-Type que manda el cliente.
 */
@Component
public class IdentityDocumentValidator {

    static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private static final byte[] PDF = { 0x25, 0x50, 0x44, 0x46, 0x2D };
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG = { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

    public ValidatedDocument validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo del documento de identidad está vacío.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("El documento supera el tamaño máximo permitido de 5 MB.");
        }

        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(PNG.length);
        } catch (IOException e) {
            throw new IllegalArgumentException("No se pudo leer el archivo del documento.");
        }

        if (startsWith(header, PDF)) {
            return new ValidatedDocument("pdf", "application/pdf");
        }
        if (startsWith(header, JPEG)) {
            return new ValidatedDocument("jpg", "image/jpeg");
        }
        if (startsWith(header, PNG)) {
            return new ValidatedDocument("png", "image/png");
        }
        throw new IllegalArgumentException("Formato no permitido. Sube el documento como PDF, JPG o PNG.");
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
