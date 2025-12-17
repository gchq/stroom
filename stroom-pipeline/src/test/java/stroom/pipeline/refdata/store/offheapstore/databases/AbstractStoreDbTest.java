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

package stroom.pipeline.refdata.store.offheapstore.databases;

import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnvFactory;
import stroom.lmdb.LmdbLibrary;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.store.offheapstore.RefDataLmdbEnv;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.ModelStringUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.lmdbjava.EnvFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public abstract class AbstractStoreDbTest extends StroomUnitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStoreDbTest.class);
    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(2_000);
    protected RefDataLmdbEnv refDataLmdbEnv = null;
    protected LmdbEnv lmdbEnv = null;
    private Path dbDir = null;

    @BeforeEach
    final void createEnv() throws IOException {
        dbDir = Files.createTempDirectory("stroom");
        final EnvFlags[] envFlags = new EnvFlags[]{
                EnvFlags.MDB_NOTLS
        };

        LOGGER.info("Creating LMDB environment with maxSize: {}, dbDir {}, envFlags {}",
                getMaxSizeBytes(),
                dbDir.toAbsolutePath(),
                Arrays.toString(envFlags));

        final PathCreator pathCreator = new SimplePathCreator(() -> dbDir, () -> dbDir);
        final TempDirProvider tempDirProvider = () -> dbDir;

        final LmdbEnvFactory lmdbEnvFactory = new LmdbEnvFactory(
                pathCreator,
                new LmdbLibrary(pathCreator,
                        tempDirProvider,
                        LmdbLibraryConfig::new));
        refDataLmdbEnv = new RefDataLmdbEnv(
                lmdbEnvFactory, ReferenceDataConfig::new, null, null);
        lmdbEnv = refDataLmdbEnv.getEnvironment();
    }

    @AfterEach
    final void teardown() throws IOException {
        if (lmdbEnv != null) {
            lmdbEnv.close();
        }
        lmdbEnv = null;
        if (Files.isDirectory(dbDir)) {
            Files.list(dbDir)
                    .filter(path -> path.endsWith("data.mdb"))
                    .forEach(path -> {
                        try {
                            final long fileSizeBytes = Files.size(path);
                            LOGGER.info("LMDB file size: {}",
                                    ModelStringUtil.formatIECByteSizeString(fileSizeBytes));
                        } catch (final IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            LOGGER.info("Deleting dir {}", dbDir.toAbsolutePath().normalize().toString());
            FileUtil.deleteDir(dbDir);
        }
    }

    protected Path getDbDir() {
        return dbDir;
    }

    protected ByteSize getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }
}
