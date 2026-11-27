package io.github.yagmurpazi.testpulse.ingestion.application.port.in;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/** One uploaded report file, held in memory. Immutable. */
public final class ReportFile {

    private final String name;
    private final byte[] content;

    public ReportFile(String name, byte[] content) {
        this.name = Objects.requireNonNull(name, "name");
        this.content = Arrays.copyOf(Objects.requireNonNull(content, "content"), content.length);
    }

    public String name() {
        return name;
    }

    public InputStream open() {
        return new ByteArrayInputStream(content);
    }

    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }
}
