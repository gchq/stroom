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

import java.nio.file.Path;
import java.util.UUID;

/// Helpers shared by the join tests.
final class JoinTestHelper {

    private JoinTestHelper() {
        // Static methods only.
    }

    /// Creates a builder for an LMDB environment in its own directory under the given one.
    static LmdbEnv.Builder createLmdbEnvBuilder(final Path tempDir) {
        final SearchResultStoreConfig resultStoreConfig = new SearchResultStoreConfig();
        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final LmdbLibraryConfig lmdbLibraryConfig = new LmdbLibraryConfig();
        final LmdbEnvDirFactory lmdbEnvDirFactory = new LmdbEnvDirFactory(
                new LmdbLibrary(pathCreator, tempDirProvider, () -> lmdbLibraryConfig), pathCreator);
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
