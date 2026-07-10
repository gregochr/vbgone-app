package com.vbgone.service;

import com.vbgone.model.VbSourceFile;
import com.vbgone.model.ZipManifest;
import com.vbgone.session.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipExtractorServiceTest {

    private ZipExtractorService service;

    @BeforeEach
    void setUp() {
        service = new ZipExtractorService(new SessionStore());
    }

    @Test
    void extract_singleVbFile_returnsManifestWithOneFile() throws Exception {
        byte[] zip = createZip("Form1.vb", "Public Class Form1\nEnd Class");
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", zip);

        ZipManifest manifest = service.extract(file);

        assertThat(manifest.sessionId()).isNotBlank();
        assertThat(manifest.totalFiles()).isEqualTo(1);
        assertThat(manifest.files()).hasSize(1);
        assertThat(manifest.files().get(0).filename()).isEqualTo("Form1.vb");
        assertThat(manifest.files().get(0).content()).contains("Public Class Form1");
    }

    @Test
    void extract_multipleVbFiles_returnsAll() throws Exception {
        byte[] zip = createZipMultiple(
                new String[]{"Form1.vb", "Public Class Form1\nEnd Class"},
                new String[]{"Utils.vb", "Public Class Utils\nEnd Class"},
                new String[]{"Helper.vb", "Public Class Helper\nEnd Class"}
        );
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", zip);

        ZipManifest manifest = service.extract(file);

        assertThat(manifest.totalFiles()).isEqualTo(3);
        assertThat(manifest.files()).extracting(VbSourceFile::filename)
                .containsExactly("Form1.vb", "Utils.vb", "Helper.vb");
    }

    @Test
    void extract_nestedDirectories_extractsAllVbFiles() throws Exception {
        byte[] zip = createZipMultiple(
                new String[]{"src/forms/Form1.vb", "Public Class Form1\nEnd Class"},
                new String[]{"src/utils/Helper.vb", "Public Class Helper\nEnd Class"}
        );
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", zip);

        ZipManifest manifest = service.extract(file);

        assertThat(manifest.totalFiles()).isEqualTo(2);
        assertThat(manifest.files().get(0).relativePath()).isEqualTo("src/forms/Form1.vb");
        assertThat(manifest.files().get(0).filename()).isEqualTo("Form1.vb");
        assertThat(manifest.files().get(1).relativePath()).isEqualTo("src/utils/Helper.vb");
        assertThat(manifest.files().get(1).filename()).isEqualTo("Helper.vb");
    }

    @Test
    void extract_ignoresNonVbFiles() throws Exception {
        byte[] zip = createZipMultiple(
                new String[]{"Form1.vb", "Public Class Form1\nEnd Class"},
                new String[]{"readme.md", "# Readme"},
                new String[]{"app.config", "<configuration/>"},
                new String[]{"Form1.Designer.vb", "Designer code"}
        );
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", zip);

        ZipManifest manifest = service.extract(file);

        // .Designer.vb still ends with .vb so it is included
        assertThat(manifest.totalFiles()).isEqualTo(2);
        assertThat(manifest.files()).extracting(VbSourceFile::filename)
                .containsExactly("Form1.vb", "Form1.Designer.vb");
    }

    @Test
    void extract_noVbFiles_throwsIllegalArgument() throws Exception {
        byte[] zip = createZip("readme.md", "# Readme");
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", zip);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Zip file contains no .vb files.");
    }

    @Test
    void extract_nullFile_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.extract(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File must not be empty.");
    }

    @Test
    void extract_emptyFile_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", new byte[0]);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("File must not be empty.");
    }

    @Test
    void extract_nonZipExtension_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "project.tar.gz", "application/gzip", new byte[]{1});

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only .zip files are supported.");
    }

    @Test
    void extract_noFilename_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", null, "application/zip", new byte[]{1});

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only .zip files are supported.");
    }

    @Test
    void extract_preservesFileContent() throws Exception {
        String vbContent = "Public Class Calculator\n    Public Function Add(a As Integer, b As Integer) As Integer\n        Return a + b\n    End Function\nEnd Class";
        byte[] zip = createZip("Calculator.vb", vbContent);
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", zip);

        ZipManifest manifest = service.extract(file);

        assertThat(manifest.files().get(0).content()).isEqualTo(vbContent);
    }

    @Test
    void extract_assignsSessionId() throws Exception {
        byte[] zip = createZip("Form1.vb", "Public Class Form1\nEnd Class");
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", zip);

        ZipManifest manifest = service.extract(file);

        assertThat(manifest.sessionId()).isNotBlank();
        assertThat(manifest.sessionId()).matches("[a-f0-9\\-]{36}");
    }

    @Test
    void extract_caseInsensitiveVbExtension() throws Exception {
        byte[] zip = createZipMultiple(
                new String[]{"Form1.VB", "Public Class Form1\nEnd Class"},
                new String[]{"Utils.Vb", "Public Class Utils\nEnd Class"}
        );
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", zip);

        ZipManifest manifest = service.extract(file);

        assertThat(manifest.totalFiles()).isEqualTo(2);
    }

    @Test
    void extract_ignoresDirectoryEntries() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Add a directory entry
            zos.putNextEntry(new ZipEntry("src/"));
            zos.closeEntry();
            // Add a .vb file
            zos.putNextEntry(new ZipEntry("src/Form1.vb"));
            zos.write("Public Class Form1\nEnd Class".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        MockMultipartFile file = new MockMultipartFile("file", "project.zip", "application/zip", baos.toByteArray());

        ZipManifest manifest = service.extract(file);

        assertThat(manifest.totalFiles()).isEqualTo(1);
        assertThat(manifest.files().get(0).filename()).isEqualTo("Form1.vb");
    }

    @Test
    void readVbEntries_appliesPathPredicate_andSkipsNonVb() throws Exception {
        byte[] zip = createZipMultiple(
                new String[]{"src/Keep.vb", "Public Class Keep\nEnd Class"},
                new String[]{"bin/Skip.vb", "Public Class Skip\nEnd Class"},
                new String[]{"notes.txt", "ignore me"}
        );

        java.util.List<VbSourceFile> files = service.readVbEntries(
                new java.io.ByteArrayInputStream(zip), name -> !name.contains("bin/"),
                ZipExtractorService.MAX_FILES, "Zip");

        // notes.txt is not .vb; bin/Skip.vb is rejected by the predicate; only src/Keep.vb survives.
        assertThat(files).extracting(VbSourceFile::relativePath).containsExactly("src/Keep.vb");
    }

    // ── Caps and hostile input (regression protection for the size/count/bomb guards) ──

    @Test
    void extract_moreThanMaxFiles_throwsFileCountCap() throws Exception {
        String[][] entries = new String[ZipExtractorService.MAX_FILES + 1][];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new String[]{"Class" + i + ".vb", "Public Class C" + i + "\nEnd Class"};
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "many.zip", "application/zip", createZipMultiple(entries));

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than " + ZipExtractorService.MAX_FILES + " .vb files");
    }

    @Test
    void extract_uploadExceedsMaxZipSize_throws() {
        byte[] tooBig = new byte[(int) ZipExtractorService.MAX_ZIP_SIZE + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.zip", "application/zip", tooBig);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum size");
    }

    @Test
    void extract_garbageBytes_throwsNoVbFiles() {
        // Non-zip bytes yield no parseable entries -> the "no .vb files" branch, not the IOException wrap.
        MockMultipartFile file = new MockMultipartFile(
                "file", "corrupt.zip", "application/zip", "this is not a zip".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Zip file contains no .vb files.");
    }

    @Test
    void extract_corruptedEntryData_throwsFailedToRead() throws Exception {
        // A valid zip whose single .vb entry's deflated data is corrupted -> inflate/CRC IOException,
        // which the service wraps as RuntimeException("Failed to read zip file: ...").
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 400; i++) sb.append("Public Class Form").append(i).append(" : End Class\n");
        byte[] zip = createZip("Form1.vb", sb.toString());
        // Corrupt a stretch of the deflated data, leaving the ~38-byte local header intact.
        for (int i = 45; i < Math.min(zip.length - 40, 120); i++) zip[i] ^= (byte) 0xFF;
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.zip", "application/zip", zip);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to read zip file");
    }

    @Test
    void extract_zipBombSingleEntry_rejectedByUncompressedCap() throws Exception {
        // One .vb entry that deflates from >64 MB of 'A' down to a few KB (well under the 10 MB upload cap).
        byte[] zip = zipWithLargeVbEntries(1, ZipExtractorService.MAX_TOTAL_UNCOMPRESSED + 8192);
        assertThat((long) zip.length).isLessThan(ZipExtractorService.MAX_ZIP_SIZE);
        MockMultipartFile file = new MockMultipartFile("file", "bomb.zip", "application/zip", zip);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uncompressed limit");
    }

    @Test
    void extract_cumulativeUncompressedAcrossEntries_rejected() throws Exception {
        // Three ~24 MB entries: each individually under the 64 MB cap, but together they exceed it.
        // Proves the cross-entry running total, which a per-entry-only cap would miss.
        byte[] zip = zipWithLargeVbEntries(3, 24L * 1024 * 1024);
        assertThat((long) zip.length).isLessThan(ZipExtractorService.MAX_ZIP_SIZE);
        MockMultipartFile file = new MockMultipartFile("file", "bomb.zip", "application/zip", zip);

        assertThatThrownBy(() -> service.extract(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uncompressed limit");
    }

    // ── Helpers ──

    /**
     * A zip with {@code count} {@code .vb} entries, each {@code bytesEach} bytes of highly
     * compressible 'A's, written in 8 KB chunks so the test never allocates the full uncompressed
     * payload (only the tiny deflated output is buffered here).
     */
    private byte[] zipWithLargeVbEntries(int count, long bytesEach) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        java.util.Arrays.fill(chunk, (byte) 'A');
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int e = 0; e < count; e++) {
                zos.putNextEntry(new ZipEntry("Bomb" + e + ".vb"));
                long written = 0;
                while (written < bytesEach) {
                    int n = (int) Math.min(chunk.length, bytesEach - written);
                    zos.write(chunk, 0, n);
                    written += n;
                }
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private byte[] createZip(String entryName, String content) throws IOException {
        return createZipMultiple(new String[]{entryName, content});
    }

    @SafeVarargs
    private byte[] createZipMultiple(String[]... entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String[] entry : entries) {
                zos.putNextEntry(new ZipEntry(entry[0]));
                zos.write(entry[1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
