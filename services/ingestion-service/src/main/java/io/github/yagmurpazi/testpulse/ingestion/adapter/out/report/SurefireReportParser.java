package io.github.yagmurpazi.testpulse.ingestion.adapter.out.report;

import io.github.yagmurpazi.testpulse.ingestion.application.port.out.InvalidReportException;
import io.github.yagmurpazi.testpulse.ingestion.application.port.out.ReportParser;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestCaseResult;
import io.github.yagmurpazi.testpulse.ingestion.domain.TestOutcome;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.stereotype.Component;

/**
 * Reads JUnit XML reports in the Surefire/Failsafe format, also accepting the aggregated {@code <testsuites>} form.
 *
 * <p>Streaming (StAX), so large reports are never held in memory as a tree. DTDs and external entities are disabled,
 * which rules out XXE (reading local files through entity references) and entity expansion attacks.
 *
 * <p>Surefire rerun elements are counted as failed attempts:
 * {@code flakyFailure}/{@code flakyError} (the test passed in the end) and
 * {@code rerunFailure}/{@code rerunError} (the test failed on every attempt).
 */
@Component
public final class SurefireReportParser implements ReportParser {

    private static final int MAX_MESSAGE_SCAN_LENGTH = 4_096;

    @Override
    public List<TestCaseResult> parse(InputStream input) {
        XMLStreamReader reader = null;
        try {
            reader = secureFactory().createXMLStreamReader(input);
            return new Parse(reader).run();
        } catch (XMLStreamException e) {
            throw new InvalidReportException("Malformed XML report: " + e.getMessage(), e);
        } finally {
            closeQuietly(reader);
        }
    }

    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    private static void closeQuietly(XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // nothing useful to do; the stream itself is closed by the caller
        }
    }

    /** State of one parse; not shared between threads. */
    private static final class Parse {

        private final XMLStreamReader reader;
        private final List<TestCaseResult> results = new ArrayList<>();
        private final Deque<String> suiteNames = new ArrayDeque<>();
        private boolean rootSeen;
        private TestCaseBuilder testCase;
        private int depthInTestCase;

        Parse(XMLStreamReader reader) {
            this.reader = reader;
        }

        List<TestCaseResult> run() throws XMLStreamException {
            while (reader.hasNext()) {
                switch (reader.next()) {
                    case XMLStreamConstants.START_ELEMENT -> onStart(reader.getLocalName());
                    case XMLStreamConstants.END_ELEMENT -> onEnd(reader.getLocalName());
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> onText();
                    default -> {
                        // comments, processing instructions and whitespace carry no test data
                    }
                }
            }
            if (!rootSeen) {
                throw new InvalidReportException("Not a JUnit XML report: no root element");
            }
            return List.copyOf(results);
        }

        private void onStart(String element) {
            if (!rootSeen) {
                if (!"testsuite".equals(element) && !"testsuites".equals(element)) {
                    throw new InvalidReportException("Not a JUnit XML report: root element is <" + element + ">");
                }
                rootSeen = true;
            }
            if (testCase != null) {
                depthInTestCase++;
                if (depthInTestCase == 1) {
                    testCase.onChild(element, attribute("message"), attribute("type"));
                }
                return;
            }
            if ("testsuite".equals(element)) {
                String name = attribute("name");
                suiteNames.push(name == null ? "" : name);
            } else if ("testcase".equals(element)) {
                testCase = new TestCaseBuilder(className(), required("name"), parseDuration(attribute("time")));
                depthInTestCase = 0;
            }
        }

        private void onEnd(String element) {
            if (testCase != null) {
                if (depthInTestCase == 0) {
                    results.add(testCase.build());
                    testCase = null;
                } else {
                    depthInTestCase--;
                }
            } else if ("testsuite".equals(element)) {
                suiteNames.pop();
            }
        }

        private void onText() {
            if (testCase != null && depthInTestCase == 1) {
                testCase.onText(reader.getText());
            }
        }

        private String className() {
            String className = attribute("classname");
            if (className != null && !className.isBlank()) {
                return className;
            }
            String suiteName = suiteNames.peek();
            if (suiteName == null || suiteName.isBlank()) {
                throw new InvalidReportException("<testcase> without classname outside a named <testsuite>");
            }
            return suiteName;
        }

        private String required(String name) {
            String value = attribute(name);
            if (value == null || value.isBlank()) {
                throw new InvalidReportException("<testcase> without " + name + " attribute");
            }
            return value;
        }

        private String attribute(String name) {
            return reader.getAttributeValue(null, name);
        }
    }

    /** Surefire writes seconds with a dot; some tools add thousands separators ("1,234.5"). */
    static Duration parseDuration(String seconds) {
        if (seconds == null || seconds.isBlank()) {
            return Duration.ZERO;
        }
        try {
            BigDecimal millis = new BigDecimal(seconds.replace(",", "").trim()).movePointRight(3);
            if (millis.signum() < 0) {
                throw new InvalidReportException("Negative test time: " + seconds);
            }
            return Duration.ofMillis(millis.setScale(0, RoundingMode.HALF_UP).longValueExact());
        } catch (NumberFormatException | ArithmeticException e) {
            throw new InvalidReportException("Invalid test time: " + seconds, e);
        }
    }

    /** Collects one {@code <testcase>} element. */
    private static final class TestCaseBuilder {

        private final String className;
        private final String testName;
        private final Duration duration;
        private TestOutcome outcome = TestOutcome.PASSED;
        private int rerunFailures;
        private String message;
        private String type;
        private StringBuilder text;

        TestCaseBuilder(String className, String testName, Duration duration) {
            this.className = className;
            this.testName = testName;
            this.duration = duration;
        }

        void onChild(String element, String messageAttribute, String typeAttribute) {
            switch (element) {
                case "failure" -> fail(TestOutcome.FAILED, messageAttribute, typeAttribute);
                case "error" -> fail(TestOutcome.ERROR, messageAttribute, typeAttribute);
                case "skipped" -> outcome = TestOutcome.SKIPPED;
                case "flakyFailure", "flakyError", "rerunFailure", "rerunError" -> rerunFailures++;
                default -> {
                    // system-out, system-err, properties and unknown extensions are ignored
                }
            }
        }

        private void fail(TestOutcome failure, String messageAttribute, String typeAttribute) {
            outcome = failure;
            message = messageAttribute;
            type = typeAttribute;
            text = messageAttribute == null ? new StringBuilder() : null;
        }

        void onText(String chunk) {
            if (text != null && text.length() < MAX_MESSAGE_SCAN_LENGTH) {
                text.append(chunk, 0, Math.min(chunk.length(), MAX_MESSAGE_SCAN_LENGTH - text.length()));
            }
        }

        TestCaseResult build() {
            return new TestCaseResult(className, testName, outcome, duration, rerunFailures, failureMessage());
        }

        private String failureMessage() {
            if (!outcome.isFailure()) {
                return null;
            }
            String firstLine = firstLine(message);
            if (firstLine == null && text != null) {
                firstLine = firstLine(text.toString());
            }
            return firstLine != null ? firstLine : type;
        }

        private static String firstLine(String value) {
            if (value == null) {
                return null;
            }
            String line = value.strip().lines().findFirst().orElse("").strip();
            return line.isEmpty() ? null : line;
        }
    }
}
