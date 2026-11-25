package io.github.yagmurpazi.testpulse.analysis.application.service;

import io.github.yagmurpazi.testpulse.analysis.application.port.out.MonitoredTestRepository;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.ProcessedEvents;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.RecordedExecution;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.StatusChangeLog;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.StatusChangePublisher;
import io.github.yagmurpazi.testpulse.analysis.application.port.out.TestExecutionRepository;
import io.github.yagmurpazi.testpulse.analysis.domain.MonitoredTest;
import io.github.yagmurpazi.testpulse.analysis.domain.StatusChange;
import io.github.yagmurpazi.testpulse.analysis.domain.TestExecution;
import io.github.yagmurpazi.testpulse.analysis.domain.TestKey;
import io.github.yagmurpazi.testpulse.analysis.domain.TestStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Test doubles for the outbound ports, backed by collections. */
final class InMemoryPorts {

    private InMemoryPorts() {
    }

    static final class Events implements ProcessedEvents {

        private final Set<UUID> processed = new HashSet<>();

        @Override
        public boolean markProcessed(UUID eventId) {
            return processed.add(eventId);
        }
    }

    static final class Tests implements MonitoredTestRepository {

        private final Map<UUID, MonitoredTest> byId = new LinkedHashMap<>();
        private final Map<UUID, TestStatus> persistedStatus = new HashMap<>();
        private int inserts;

        @Override
        public Map<TestKey, MonitoredTest> findByKeysForUpdate(Collection<TestKey> keys) {
            Map<TestKey, MonitoredTest> found = new HashMap<>();
            byId.values().stream().filter(test -> keys.contains(test.key()))
                    .forEach(test -> found.put(test.key(), test));
            return found;
        }

        @Override
        public Optional<MonitoredTest> findByIdForUpdate(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void insert(Collection<MonitoredTest> tests) {
            tests.forEach(test -> {
                byId.put(test.id(), test);
                persistedStatus.put(test.id(), test.status());
            });
            inserts += tests.size();
        }

        @Override
        public void updateStatus(MonitoredTest test) {
            persistedStatus.put(test.id(), test.status());
        }

        void add(MonitoredTest test) {
            insert(List.of(test));
        }

        int inserts() {
            return inserts;
        }

        TestStatus persistedStatus(UUID id) {
            return persistedStatus.get(id);
        }

        MonitoredTest byName(String testName) {
            return byId.values().stream().filter(test -> test.key().testName().equals(testName)).findFirst()
                    .orElseThrow();
        }
    }

    static final class Executions implements TestExecutionRepository {

        private final Map<UUID, List<RecordedExecution>> byTest = new HashMap<>();
        private int lastPerTest;

        @Override
        public void appendAll(Collection<RecordedExecution> executions) {
            executions.forEach(e -> byTest.computeIfAbsent(e.testId(), id -> new ArrayList<>()).add(e));
        }

        @Override
        public Map<UUID, List<TestExecution>> findLatest(Collection<UUID> testIds, int perTest) {
            lastPerTest = perTest;
            Map<UUID, List<TestExecution>> latest = new HashMap<>();
            for (UUID id : testIds) {
                List<TestExecution> all = byTest.getOrDefault(id, List.of()).stream()
                        .map(RecordedExecution::execution)
                        .sorted(Comparator.comparing(TestExecution::executedAt)).toList();
                if (!all.isEmpty()) {
                    latest.put(id, all.subList(Math.max(0, all.size() - perTest), all.size()));
                }
            }
            return latest;
        }

        int count() {
            return byTest.values().stream().mapToInt(List::size).sum();
        }

        List<RecordedExecution> of(UUID testId) {
            return byTest.getOrDefault(testId, List.of());
        }

        int lastPerTest() {
            return lastPerTest;
        }
    }

    static final class Changes implements StatusChangeLog, StatusChangePublisher {

        private final List<StatusChange> logged = new ArrayList<>();
        private final List<StatusChange> published = new ArrayList<>();

        @Override
        public void record(StatusChange change) {
            logged.add(change);
        }

        @Override
        public void publish(StatusChange change) {
            published.add(change);
        }

        List<StatusChange> logged() {
            return List.copyOf(logged);
        }

        List<StatusChange> published() {
            return List.copyOf(published);
        }
    }
}
