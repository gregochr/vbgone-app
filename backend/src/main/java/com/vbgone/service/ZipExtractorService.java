package com.vbgone.service;

import com.vbgone.model.VbSourceFile;
import com.vbgone.model.ZipManifest;
import com.vbgone.session.SessionStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ZipExtractorService {

    static final long MAX_ZIP_SIZE = 10 * 1024 * 1024; // 10 MB
    static final int MAX_FILES = 100;

    /**
     * Cap on total <em>uncompressed</em> {@code .vb} bytes read from one archive. The compressed
     * size is bounded elsewhere, but a small archive can deflate to gigabytes (a decompression
     * bomb), so we also bound what we materialise here — generous for real text source, fatal to a
     * bomb.
     */
    static final long MAX_TOTAL_UNCOMPRESSED = 64L * 1024 * 1024; // 64 MB

    /** Accept every entry — the uploaded-zip path keeps its original "any path ending in .vb" behaviour. */
    static final Predicate<String> ACCEPT_ALL = name -> true;

    private final SessionStore sessionStore;

    public ZipExtractorService(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public ZipManifest extract(MultipartFile file) {
        validateFile(file);

        String sessionId = sessionStore.create().getSessionId();
        List<VbSourceFile> vbFiles;
        try (InputStream is = file.getInputStream()) {
            vbFiles = readVbEntries(is, ACCEPT_ALL, MAX_FILES, "Zip");
        } catch (IOException e) {
            throw new RuntimeException("Failed to read zip file: " + e.getMessage(), e);
        }

        if (vbFiles.isEmpty()) {
            throw new IllegalArgumentException("Zip file contains no .vb files.");
        }

        return new ZipManifest(sessionId, vbFiles, vbFiles.size());
    }

    /**
     * Read the {@code .vb} entries out of a zip stream, in encounter order, applying the shared
     * {@code .vb}-only filter, the {@code maxFiles} count cap, and the {@link #MAX_TOTAL_UNCOMPRESSED}
     * decompression-bomb guard. {@code includePath} is a second gate on the raw entry name (the
     * uploaded-zip path passes {@link #ACCEPT_ALL}; the repo-ingest path uses it to drop build
     * output); rejected entries do not count toward the cap. {@code sourceNoun} words the
     * "too many files" message for the caller's context ("Zip" vs "Repository"). The caller owns
     * the stream and any session/emptiness handling.
     */
    List<VbSourceFile> readVbEntries(InputStream is, Predicate<String> includePath,
                                     int maxFiles, String sourceNoun) throws IOException {
        List<VbSourceFile> vbFiles = new ArrayList<>();
        long totalBytes = 0;
        try (ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                String entryName = entry.getName();
                if (!entryName.toLowerCase().endsWith(".vb")) continue;
                if (!includePath.test(entryName)) continue;

                if (vbFiles.size() >= maxFiles) {
                    throw new IllegalArgumentException(
                            sourceNoun + " contains more than " + maxFiles + " .vb files. Maximum is " + maxFiles + ".");
                }

                byte[] bytes = readEntryBounded(zis, MAX_TOTAL_UNCOMPRESSED - totalBytes);
                totalBytes += bytes.length;
                String content = new String(bytes, StandardCharsets.UTF_8);
                String filename = extractFilename(entryName);
                vbFiles.add(new VbSourceFile(entryName, filename, content));

                zis.closeEntry();
            }
        }
        return vbFiles;
    }

    /**
     * Read the current zip entry fully, but abort if doing so would push the running uncompressed
     * total past the cap ({@code remaining} = budget left). Guards against a small archive that
     * deflates to gigabytes.
     */
    private static byte[] readEntryBounded(ZipInputStream zis, long remaining) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zis.read(buf)) != -1) {
            if (out.size() + (long) n > remaining) {
                throw new IllegalArgumentException(
                        "Source exceeds the " + (MAX_TOTAL_UNCOMPRESSED / 1024 / 1024)
                                + " MB uncompressed limit.");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
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
