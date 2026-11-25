package io.github.yagmurpazi.testpulse.analysis.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.ExecutedTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunCommand;
import io.github.yagmurpazi.testpulse.analysis.domain.DetectionSettings;
import io.github.yagmurpazi.testpulse.analysis.domain.FlakinessDetector;
import io.github.yagmurpazi.testpulse.analysis.domain.Outcome;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The demo data in {@code demo/runs} is part of the product: it is what a reviewer sees after
 * {@code docker compose up}. This test replays it through the real detection and lifecycle, in the order the seed
 * uploads it, and checks that every story still ends where the README and the generator say it does.
 */
class DemoRunsTest {

    private static final Path DEMO_RUNS = Path.of("../../demo/runs");
    private static final Instant START = Instant.parse("2026-11-25T10:00:00Z");

    private final InMemoryPorts.Tests tests = new InMemoryPorts.Tests();
    private final InMemoryPorts.Changes changes = new InMemoryPorts.Changes();

    @BeforeEach
    void replayTheDemo() throws IOException {
        ProcessTestRunService service = new ProcessTestRunService(new InMemoryPorts.Events(), tests,
                new InMemoryPorts.Executions(), new StatusChangeRecorder(changes, changes),
                FlakinessDetector.withDefaultStrategies(DetectionSettings.DEFAULTS), DetectionSettings.DEFAULTS,
                UUID::randomUUID);
        List<Path> runs;
        try (Stream<Path> dirs = Files.list(DEMO_RUNS)) {
            runs = dirs.filter(Files::isDirectory).sorted().toList();
        }
        for (int i = 0; i < runs.size(); i++) {
            service.process(command(runs.get(i), START.plusSeconds(60L * i)));
        }
    }

    @Test
    void appliesDiscountIsFlakyFromTheSameCommitConflict() {
        assertThat(history("appliesDiscount")).containsExactly("STABLE -> SUSPICIOUS", "SUSPICIOUS -> FLAKY");
    }

    @Test
    void refundsPartiallyIsFlakyByItsFlipRate() {
        assertThat(history("refundsPartially")).containsExactly("STABLE -> SUSPICIOUS", "SUSPICIOUS -> FLAKY");
        assertThat(reasons("refundsPartially")).allSatisfy(reason -> assertThat(reason).containsIgnoringCase("flip"));
    }

    @Test
    void capturesPaymentIsBrokenForAWhileButNotFlaky() {
        assertThat(history("capturesPayment")).isEmpty();
        assertThat(tests.byName("capturesPayment").status()).isEqualTo(TestStatus.STABLE);
    }

    @Test
    void reconcilesLedgerBecomesFlakyAndRecovers() {
        assertThat(history("reconcilesLedger"))
                .containsExactly("STABLE -> SUSPICIOUS", "SUSPICIOUS -> FLAKY", "FLAKY -> STABLE");
    }

    @Test
    void authorizesCardIsSuspicious() {
        assertThat(history("authorizesCard")).containsExactly("STABLE -> SUSPICIOUS");
    }

    @Test
    void theOtherTestsStayStable() {
        assertThat(history("formatsAmount")).isEmpty();
        assertThat(history("validatesIban")).isEmpty();
        assertThat(history("calculatesTotal")).isEmpty();
    }

    private List<String> history(String testName) {
        return changesOf(testName).stream().map(change -> change.from() + " -> " + change.to()).toList();
    }

    private List<String> reasons(String testName) {
        return changesOf(testName).stream().map(StatusChange::reason).toList();
    }

    private List<StatusChange> changesOf(String testName) {
        return changes.logged().stream().filter(change -> change.key().testName().equals(testName)).toList();
    }

    private static ProcessTestRunCommand command(Path run, Instant runTime) throws IOException {
        Map<String, String> env = new HashMap<>();
        for (String line : Files.readAllLines(run.resolve("run.env"))) {
            int separator = line.indexOf('=');
            if (!line.startsWith("#") && separator > 0) {
                env.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        List<ExecutedTest> executed = new ArrayList<>();
        try (Stream<Path> files = Files.list(run)) {
            for (Path report : files.filter(file -> file.toString().endsWith(".xml")).sorted().toList()) {
                executed.addAll(parse(report));
            }
        }
        return new ProcessTestRunCommand(UUID.randomUUID(), UUID.randomUUID(), env.get("REPOSITORY"),
                env.get("COMMIT_SHA"), Integer.parseInt(env.get("ATTEMPT")), runTime, executed);
    }

    /** Just enough of the Surefire format for the demo; ingestion-service has the real parser. */
    private static List<ExecutedTest> parse(Path report) {
        List<ExecutedTest> results = new ArrayList<>();
        try (InputStream in = Files.newInputStream(report)) {
            XMLStreamReader xml = XMLInputFactory.newFactory().createXMLStreamReader(in);
            String className = null;
            String testName = null;
            Outcome outcome = Outcome.PASSED;
            int reruns = 0;
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamReader.START_ELEMENT) {
                    switch (xml.getLocalName()) {
                        case "testcase" -> {
                            className = xml.getAttributeValue(null, "classname");
                            testName = xml.getAttributeValue(null, "name");
                            outcome = Outcome.PASSED;
                            reruns = 0;
                        }
                        case "failure" -> outcome = Outcome.FAILED;
                        case "error" -> outcome = Outcome.ERROR;
                        case "skipped" -> outcome = Outcome.SKIPPED;
                        case "flakyFailure", "flakyError", "rerunFailure", "rerunError" -> reruns++;
                        default -> {
                            // other elements carry no outcome
                        }
                    }
                } else if (event == XMLStreamReader.END_ELEMENT && "testcase".equals(xml.getLocalName())) {
                    results.add(new ExecutedTest(className, testName, outcome, reruns, 1));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (XMLStreamException e) {
            throw new IllegalStateException(report + " is not valid XML", e);
        }
        return results;
    }
}
