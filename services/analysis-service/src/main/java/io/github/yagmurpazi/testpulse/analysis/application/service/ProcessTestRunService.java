package io.github.yagmurpazi.testpulse.analysis.application.service;

import io.github.yagmurpazi.testpulse.analysis.application.port.in.ExecutedTest;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunCommand;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessTestRunUseCase;
import io.github.yagmurpazi.testpulse.analysis.application.port.in.ProcessingResult;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.MonitoredTestRepository;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.ProcessedEvents;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.RecordedExecution;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.TestExecutionRepository;
import io.github.yagmurpazi.testpulse.analysis.domain.DetectionSettings;
import io.github.yagmurpazi.testpulse.analysis.domain.FlakinessDetector;
import io.github.yagmurpazi.testpulse.analysis.domain.MonitoredTest;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import io.github.yagmurpazi.testpulse.analysis.domain.TestExecution;
import io.github.yagmurpazi.testpulse.analysis.domain.TestHistory;
import io.github.yagmurpazi.testpulse.analysis.domain.TestKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Processes one ingested run: record every result, then evaluate each affected test on its recent history.
 *
 * <p>The whole method runs in one transaction (applied by configuration): the processed-event marker, the new
 * executions, the status changes and their outgoing events are committed together or not at all.
 */
public final class ProcessTestRunService implements ProcessTestRunUseCase {

    private final ProcessedEvents processedEvents;
    private final MonitoredTestRepository tests;
    private final TestExecutionRepository executions;
    private final StatusChangeRecorder recorder;
    private final FlakinessDetector detector;
    private final DetectionSettings settings;
    private final Supplier<UUID> idGenerator;

    public ProcessTestRunService(ProcessedEvents processedEvents, MonitoredTestRepository tests,
            TestExecutionRepository executions, StatusChangeRecorder recorder, FlakinessDetector detector,
            DetectionSettings settings, Supplier<UUID> idGenerator) {
        this.processedEvents = Objects.requireNonNull(processedEvents, "processedEvents");
        this.tests = Objects.requireNonNull(tests, "tests");
        this.executions = Objects.requireNonNull(executions, "executions");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    }

    @Override
    public ProcessingResult process(ProcessTestRunCommand command) {
        if (!processedEvents.markProcessed(command.eventId())) {
            return ProcessingResult.alreadyProcessed();
        }

        Map<TestKey, ExecutedTest> results = new LinkedHashMap<>();
        for (ExecutedTest test : command.tests()) {
            results.put(new TestKey(command.repository(), test.className(), test.testName()), test);
        }
        Map<TestKey, MonitoredTest> monitored = loadOrCreate(results.keySet(), command);

        List<RecordedExecution> recorded = new ArrayList<>();
        results.forEach((key, result) -> recorded.add(new RecordedExecution(monitored.get(key).id(),
                new TestExecution(command.runId(), command.commitSha(), command.attempt(), result.outcome(),
                        result.rerunFailures(), command.runTime()),
                result.durationMillis())));
        executions.appendAll(recorded);

        Map<UUID, List<TestExecution>> histories = executions.findLatest(
                monitored.values().stream().map(MonitoredTest::id).toList(), settings.window());
        List<StatusChange> changes = new ArrayList<>();
        for (MonitoredTest test : monitored.values()) {
            TestHistory history = new TestHistory(test.key(), histories.getOrDefault(test.id(), List.of()));
            Optional<StatusChange> change =
                    test.evaluate(detector, history, settings, command.runId(), command.runTime());
            change.ifPresent(applied -> {
                tests.updateStatus(test);
                recorder.recordAndPublish(applied);
                changes.add(applied);
            });
        }
        return new ProcessingResult(false, monitored.size(), changes);
    }

    private Map<TestKey, MonitoredTest> loadOrCreate(Set<TestKey> keys, ProcessTestRunCommand command) {
        Map<TestKey, MonitoredTest> monitored = new HashMap<>(tests.findByKeysForUpdate(keys));
        List<MonitoredTest> firstSeen = keys.stream()
                .filter(key -> !monitored.containsKey(key))
                .map(key -> MonitoredTest.firstSeen(idGenerator.get(), key, command.runTime()))
                .toList();
        tests.insert(firstSeen);
        firstSeen.forEach(test -> monitored.put(test.key(), test));
        return monitored;
    }
}
