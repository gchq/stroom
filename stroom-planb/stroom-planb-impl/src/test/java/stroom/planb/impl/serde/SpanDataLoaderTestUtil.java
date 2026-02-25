/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
