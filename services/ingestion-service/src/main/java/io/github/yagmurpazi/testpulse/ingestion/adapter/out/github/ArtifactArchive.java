package io.github.yagmurpazi.testpulse.ingestion.adapter.out.github;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ReportFile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads the XML files out of an artifact's ZIP archive, in memory. The archive comes from the network, so its size
 * is limited after decompression, not only before: a small archive can expand to gigabytes (a ZIP bomb).
 */
final class ArtifactArchive {

    private static final int BUFFER_SIZE = 8192;

    /** "PK\5\6": an archive without entries consists of only its end record. */
    private static final byte[] EMPTY_ARCHIVE_SIGNATURE = {'P', 'K', 5, 6};

    private ArtifactArchive() {
    }

    /**
     * @param maxBytes upper bound for the uncompressed size of all XML files together
     * @throws IllegalStateException if the XML files are larger than {@code maxBytes} or the archive is broken
     */
    static List<ReportFile> xmlFiles(String artifactName, byte[] zip, long maxBytes) {
        List<ReportFile> files = new ArrayList<>();
        long total = 0;
        int entryCount = 0;
        try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = entries.getNextEntry()) != null) {
                entryCount++;
                if (entry.isDirectory() || !entry.getName().endsWith(".xml")) {
                    continue;
                }
                byte[] content = read(entries, maxBytes - total, artifactName);
                total += content.length;
                files.add(new ReportFile(artifactName + "/" + entry.getName(), content));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Artifact " + artifactName + " is not a readable ZIP archive", e);
        }
        // ZipInputStream reads anything that is not an archive as an archive without entries.
        if (entryCount == 0 && !Arrays.equals(zip, 0, Math.min(zip.length, EMPTY_ARCHIVE_SIGNATURE.length),
                EMPTY_ARCHIVE_SIGNATURE, 0, EMPTY_ARCHIVE_SIGNATURE.length)) {
            throw new IllegalStateException("Artifact " + artifactName + " is not a ZIP archive");
        }
        return files;
    }

    private static byte[] read(InputStream entry, long remaining, String artifactName) throws IOException {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = entry.read(buffer)) != -1) {
            if (content.size() + (long) read > remaining) {
                throw new IllegalStateException("Artifact " + artifactName + " expands beyond the size limit");
            }
            content.write(buffer, 0, read);
        }
        return content.toByteArray();
    }
}
