/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.common.v2;

import stroom.lmdb.LmdbLibrary;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.lmdb2.LmdbEnv;
import stroom.lmdb2.LmdbEnvDir;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/// Helpers shared by the join tests.
final class JoinTestHelper {

    private static final Path LIBRARY_EXTRACT_DIR = createLibraryExtractDir();

    private JoinTestHelper() {
        // Static methods only.
    }

    /// Creates a factory for LMDB environment directories under the given directory.
    ///
    /// The first LMDB library set up in a JVM decides where the native library is extracted, but it is
    /// only extracted when the first environment opens. So every join test extracts it to one directory
    /// kept for the whole run, rather than to a test's own directory, which is deleted when the test
    /// ends, perhaps before any environment opened.
    static LmdbEnvDirFactory createLmdbEnvDirFactory(final Path tempDir) {
        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final LmdbLibraryConfig lmdbLibraryConfig = new LmdbLibraryConfig()
                .withSystemLibraryExtractDir(LIBRARY_EXTRACT_DIR.toString());
        return new LmdbEnvDirFactory(new LmdbLibrary(pathCreator, tempDirProvider, () -> lmdbLibraryConfig),
                pathCreator);
    }

    /// Creates a builder for an LMDB environment in its own directory under the given one.
    static LmdbEnv.Builder createLmdbEnvBuilder(final Path tempDir) {
        final SearchResultStoreConfig resultStoreConfig = new SearchResultStoreConfig();
        final LmdbEnvDirFactory lmdbEnvDirFactory = createLmdbEnvDirFactory(tempDir);
        final LmdbEnvDir lmdbEnvDir = lmdbEnvDirFactory
                .builder()
                .config(resultStoreConfig.getLmdbConfig())
                .subDir("join_" + UUID.randomUUID())
                .build();
        return LmdbEnv
                .builder()
                .config(resultStoreConfig.getLmdbConfig())
                .lmdbEnvDir(lmdbEnvDir);
    }

    private static Path createLibraryExtractDir() {
        try {
            return Files.createTempDirectory("stroom-join-test-lmdb-library");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Makes a row of string values, where a `null` is a null value.
    static Val[] row(final String... values) {
        final Val[] row = new Val[values.length];
        for (int i = 0; i < values.length; i++) {
            row[i] = values[i] == null
                    ? ValNull.INSTANCE
                    : ValString.create(values[i]);
        }
        return row;
    }
}
