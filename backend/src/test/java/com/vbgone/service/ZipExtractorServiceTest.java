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

    // ── Helpers ──

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
