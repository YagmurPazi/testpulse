package io.github.yagmurpazi.testpulse.ingestion.adapter.out.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yagmurpazi.testpulse.ingestion.application.port.in.ReportFile;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArtifactArchiveTest {

    @Test
    void readsEveryXmlFileAndNamesItAfterTheArtifact() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("ingestion-service/target/surefire-reports/", "");
        entries.put("ingestion-service/target/surefire-reports/TEST-a.xml", "<testsuite name=\"a\"/>");
        entries.put("ingestion-service/target/surefire-reports/a.txt", "summary");
        entries.put("TEST-b.xml", "<testsuite name=\"b\"/>");

        List<ReportFile> files = ArtifactArchive.xmlFiles("junit-reports", Zips.zip(entries), 1_000);

        assertThat(files).extracting(ReportFile::name).containsExactly(
                "junit-reports/ingestion-service/target/surefire-reports/TEST-a.xml", "junit-reports/TEST-b.xml");
        assertThat(new String(files.getFirst().content(), StandardCharsets.UTF_8)).isEqualTo("<testsuite name=\"a\"/>");
    }

    @Test
    void rejectsAnArchiveThatExpandsBeyondTheLimit() {
        byte[] zip = Zips.zip(Map.of("TEST-a.xml", "x".repeat(600), "TEST-b.xml", "y".repeat(600)));

        assertThatThrownBy(() -> ArtifactArchive.xmlFiles("junit-reports", zip, 1_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("size limit");
    }

    @Test
    void acceptsFilesUpToExactlyTheLimit() {
        byte[] zip = Zips.zip(Map.of("TEST-a.xml", "x".repeat(1_000)));

        assertThat(ArtifactArchive.xmlFiles("junit-reports", zip, 1_000)).hasSize(1);
    }

    @Test
    void rejectsSomethingThatIsNotAnArchive() {
        byte[] notZip = "PK\3\4 garbage".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> ArtifactArchive.xmlFiles("junit-reports", notZip, 1_000))
                .isInstanceOfAny(IllegalStateException.class, UncheckedIOException.class)
                .hasMessageContaining("junit-reports");
    }

    @Test
    void rejectsAnHtmlErrorPageInsteadOfAnArchive() {
        byte[] html = "<html>Service unavailable</html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ArtifactArchive.xmlFiles("junit-reports", html, 1_000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Artifact junit-reports is not a ZIP archive");
    }

    @Test
    void anEmptyArchiveHasNoReports() {
        assertThat(ArtifactArchive.xmlFiles("junit-reports", Zips.zip(Map.of()), 1_000)).isEmpty();
    }
}
