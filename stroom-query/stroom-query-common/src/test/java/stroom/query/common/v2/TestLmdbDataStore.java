/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.pipeline.refdata.util.ByteBufferPool;
import stroom.pipeline.refdata.util.ByteBufferPoolConfig;
import stroom.pipeline.refdata.util.ByteBufferPoolImpl4;
import stroom.query.api.v2.TableSettings;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

class TestLmdbDataStore extends AbstractDataStoreTest {
    private Path tempDir;

    @BeforeEach
    void setup(@TempDir final Path tempDir) {
        this.tempDir = tempDir;
    }

    @Override
    DataStore create(final TableSettings tableSettings, final Sizes maxResults, final Sizes storeSize) {
        final FieldIndex fieldIndex = new FieldIndex();

        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new PathCreator(() -> tempDir, () -> tempDir);
        final LmdbConfig lmdbConfig = new LmdbConfig();
        final ByteBufferPool byteBufferPool = new ByteBufferPoolImpl4(new ByteBufferPoolConfig());
        final LmdbEnvironment lmdbEnvironment = new LmdbEnvironment(tempDirProvider, lmdbConfig, pathCreator);

        return new LmdbDataStore(
                lmdbEnvironment,
                byteBufferPool,
                UUID.randomUUID().toString(),
                "0",
                tableSettings,
                fieldIndex,
                Collections.emptyMap(),
                maxResults,
                storeSize);
    }
}
