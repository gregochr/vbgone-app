package com.vbgone.service;

import com.vbgone.model.VbSourceFile;
import com.vbgone.model.ZipManifest;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ZipExtractorService {

    static final long MAX_ZIP_SIZE = 10 * 1024 * 1024; // 10 MB
    static final int MAX_FILES = 100;

    private final SessionStore sessionStore;

    public ZipExtractorService(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public ZipManifest extract(MultipartFile file) {
        validateFile(file);

        String sessionId = sessionStore.create().getSessionId();
        List<VbSourceFile> vbFiles = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String entryName = entry.getName();
                if (!entryName.toLowerCase().endsWith(".vb")) continue;

                if (vbFiles.size() >= MAX_FILES) {
                    throw new IllegalArgumentException(
                            "Zip contains more than " + MAX_FILES + " .vb files. Maximum is " + MAX_FILES + ".");
                }

                String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                String filename = extractFilename(entryName);
                vbFiles.add(new VbSourceFile(entryName, filename, content));

                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read zip file: " + e.getMessage(), e);
        }

        if (vbFiles.isEmpty()) {
            throw new IllegalArgumentException("Zip file contains no .vb files.");
        }

        return new ZipManifest(sessionId, vbFiles, vbFiles.size());
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("Only .zip files are supported.");
        }

        if (file.getSize() > MAX_ZIP_SIZE) {
            throw new IllegalArgumentException(
                    "Zip file exceeds maximum size of " + (MAX_ZIP_SIZE / 1024 / 1024) + " MB.");
        }
    }

    private String extractFilename(String entryName) {
        int lastSlash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
        return lastSlash >= 0 ? entryName.substring(lastSlash + 1) : entryName;
    }
}
