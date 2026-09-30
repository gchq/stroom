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

package stroom.pipeline.task;

import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.meta.api.MetaProperties;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Generates and loads a stream of N records in the shape the {@code XML-EVENTS} feed's parser expects.
 * Generated rather than checked in, because the scale tests and benchmarks exist precisely to vary N, and a
 * file of tens of thousands of records has no place in the repo.
 * <p>
 * Shared by the scenario tests and both benchmark classes so the streams they measure are identical - a
 * benchmark comparing two sweeps is only meaningful if the record shape did not quietly diverge between them.
 */
final class GeneratedEventStream {

    private GeneratedEventStream() {
    }

    /**
     * Write a stream of {@code recordCount} records to the store under the given feed.
     *
     * @return the new stream's meta id.
     */
    static long load(final Store store, final String feedName, final int recordCount) {
        final byte[] data = generate(recordCount);
        try (final Target target = store.openTarget(MetaProperties.builder()
                .feedName(feedName)
                .typeName(StreamTypeNames.RAW_EVENTS)
                .build())) {
            try (final OutputStreamProvider outputStreamProvider = target.next()) {
                try (final OutputStream outputStream = outputStreamProvider.get()) {
                    outputStream.write(data);
                }
            }
            return target.getMeta().getId();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] generate(final int recordCount) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<records xmlns=\"records:2\" ")
                .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("xsi:schemaLocation=\"records:2 file://records-v2.0.xsd\" version=\"2.0\">\n");
        for (int i = 0; i < recordCount; i++) {
            // Keep the clock valid however many records are generated.
            final int seconds = i % 60;
            final int minutes = (i / 60) % 60;
            final int hours = (i / 3600) % 24;
            sb.append("<record>")
                    .append("<data name=\"Date\" value=\"01/01/2010\"/>")
                    .append(String.format("<data name=\"Time\" value=\"%02d:%02d:%02d\"/>", hours, minutes, seconds))
                    .append("<data name=\"FileNo\" value=\"1\"/>")
                    .append("<data name=\"LineNo\" value=\"").append(i + 1).append("\"/>")
                    .append("<data name=\"User\" value=\"user").append(i % 50).append("\"/>")
                    .append("<data name=\"Message\" value=\"Benchmark message ").append(i).append("\"/>")
                    .append("</record>\n");
        }
        sb.append("</records>\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @return the record count from the {@code STEPPING_BENCHMARK_RECORDS} environment variable if set (Gradle
     * does not forward {@code -D} to the test JVM), else the system property, else the given default.
     */
    static int configuredRecordCount(final int defaultRecords) {
        final String env = System.getenv("STEPPING_BENCHMARK_RECORDS");
        if (env != null && !env.isBlank()) {
            return Integer.parseInt(env.trim());
        }
        return Integer.getInteger("stepping.benchmark.records", defaultRecords);
    }
}
