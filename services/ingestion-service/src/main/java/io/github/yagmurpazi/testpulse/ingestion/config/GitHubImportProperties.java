package io.github.yagmurpazi.testpulse.ingestion.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * {@code testpulse.importer.github.*}.
 *
 * @param token           GitHub token; the importer runs only when it is set. Downloading artifacts requires one,
 *                        even for public repositories.
 * @param repositories    repositories to import, {@code owner/name}
 * @param artifactPrefix  artifacts whose names start with it hold JUnit XML reports
 * @param runsPerImport   how many of the most recent completed runs each import looks at
 * @param maxArtifactSize upper bound for one artifact, compressed and uncompressed
 */
@ConfigurationProperties("testpulse.importer.github")
record GitHubImportProperties(String token, URI apiUrl, List<String> repositories, String artifactPrefix,
        int runsPerImport, DataSize maxArtifactSize) {
}
