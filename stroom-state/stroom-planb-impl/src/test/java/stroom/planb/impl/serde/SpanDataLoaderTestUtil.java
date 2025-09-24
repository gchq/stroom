package stroom.planb.impl.serde;

import stroom.pathways.shared.otel.trace.ExportTraceServiceRequest;
import stroom.pathways.shared.otel.trace.Span;
import stroom.test.common.ProjectPathUtil;
import stroom.util.json.JsonUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class SpanDataLoaderTestUtil {

    public static void load(final Consumer<Span> consumer) {
        try {
            final Path dir = ProjectPathUtil.resolveDir("stroom-planb-impl");
            final Path testDir = dir.resolve("src/test/resources/TestSpanValueSerde");
            try (final Stream<Path> stream = Files.list(testDir)) {
                stream.forEach(file -> {
                    try {
                        if (file.toString().endsWith(".in")) {
                            final String json = Files.readString(file);
                            final ExportTraceServiceRequest request = JsonUtil.readValue(json,
                                    ExportTraceServiceRequest.class);
                            request.getResourceSpans().forEach(resourceSpans -> {
                                resourceSpans.getScopeSpans().forEach(scopeSpans -> {
                                    final List<Span> spans = scopeSpans.getSpans();
                                    spans.forEach(consumer);
                                });
                            });
                        }

                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
