/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pipeline.benchmark;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Where the benchmark's generated input and its pipeline output live.
 * <p>
 * Input files are deliberately kept outside the repository's source tree and outside the JVM temp
 * directory: a million records is roughly 236MB and the corresponding output can approach a
 * gigabyte, which is too big to commit and too big to want on a tmpfs. Keeping them under
 * <code>build/</code> means they survive between runs — including forked JMH JVMs, which would
 * otherwise each pay the generation cost — and are removed by <code>./gradlew clean</code>.
 * <p>
 * Override the location with <code>-Dstroom.benchmark.dataDir=/some/path</code>.
 */
public final class BenchmarkPaths {

    public static final String DATA_DIR_PROP = "stroom.benchmark.dataDir";

    private BenchmarkPaths() {
        // Utility class.
    }

    public static Path dataDir() {
        return Path.of(System.getProperty(DATA_DIR_PROP, "build/benchmark-data")).toAbsolutePath();
    }

    /**
     * The generated <code>records:2</code> input file for the given record count, creating it if it
     * is not already there.
     */
    public static Path inputFile(final int recordCount) throws IOException {
        final Path path = dataDir().resolve(XMLEventsDataGenerator.fileName(recordCount));
        return XMLEventsDataGenerator.generateIfNecessary(path, recordCount);
    }

    /**
     * Where a run with the given record count and split count should write its output.
     */
    public static Path outputFile(final int recordCount, final int splitCount) {
        return dataDir()
                .resolve("output")
                .resolve("EVENTS-" + recordCount + "-split-" + splitCount + ".xml");
    }
}
