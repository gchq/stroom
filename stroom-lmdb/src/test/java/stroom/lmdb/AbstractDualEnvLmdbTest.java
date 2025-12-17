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

package stroom.lmdb;

import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.lmdbjava.EnvFlags;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.stream.Stream;

/**
 * Useful for comparing the behaviour of two different envs
 */
public abstract class AbstractDualEnvLmdbTest extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDualEnvLmdbTest.class);
    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(2_000);

    protected LmdbEnv lmdbEnv1 = null;
    protected LmdbEnv lmdbEnv2 = null;
    private Path dbDir1 = null;
    private Path dbDir2 = null;

    @BeforeEach
    final void createEnvs() throws IOException {
        final TestEnv testEnv1 = createEnv();
        lmdbEnv1 = testEnv1.lmdbEnv;
        dbDir1 = testEnv1.dbDir;

        final TestEnv testEnv2 = createEnv();
        lmdbEnv2 = testEnv2.lmdbEnv;
        dbDir2 = testEnv2.dbDir;
    }

    @AfterEach
    final void teardown() throws IOException {
        teardown(dbDir1, lmdbEnv1);
        lmdbEnv1 = null;
        teardown(dbDir2, lmdbEnv2);
        lmdbEnv2 = null;
    }

    final void teardown(final Path dbDir, final LmdbEnv lmdbEnv) throws IOException {
        if (lmdbEnv != null) {
            lmdbEnv.close();
        }
        if (Files.isDirectory(dbDir)) {
            try (final Stream<Path> fileStream = Files.list(dbDir)) {
                fileStream
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
            }
            LOGGER.info("Deleting dir {}", dbDir.toAbsolutePath().normalize().toString());
            FileUtil.deleteDir(dbDir);
        }
    }

    private TestEnv createEnv() throws IOException {
        final Path dbDir = Files.createTempDirectory("stroom");
        final EnvFlags[] envFlags = new EnvFlags[]{
                EnvFlags.MDB_NOTLS
        };

        LOGGER.info("Creating LMDB environment with maxSize: {}, dbDir {}, envFlags {}",
                getMaxSizeBytes(),
                dbDir.toAbsolutePath(),
                Arrays.toString(envFlags));

        final PathCreator pathCreator = new SimplePathCreator(() -> dbDir, () -> dbDir);
        final TempDirProvider tempDirProvider = () -> dbDir;

        final LmdbEnv lmdbEnv = new LmdbEnvFactory(pathCreator,
                new LmdbLibrary(pathCreator, tempDirProvider, LmdbLibraryConfig::new))
                .builder(dbDir)
                .withMapSize(getMaxSizeBytes())
                .withMaxDbCount(10)
                .withEnvFlags(envFlags)
                .makeWritersBlockReaders()
                .build();

        return new TestEnv(dbDir, lmdbEnv);
    }

    protected ByteSize getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }

    protected Path getDbDir1() {
        return dbDir1;
    }

    protected Path getDbDir2() {
        return dbDir2;
    }

    protected <K, V> void putValues(final AbstractLmdbDb<K, V> lmdbDb,
                                    final boolean overwriteExisting,
                                    final boolean append,
                                    final Collection<Entry<K, V>> data) {
        lmdbDb.getLmdbEnvironment()
                .doWithWriteTxn(writeTxn -> {
                    data.forEach(entry -> {
                        lmdbDb.put(writeTxn,
                                entry.getKey(),
                                entry.getValue(),
                                overwriteExisting,
                                append);
                    });
                });
    }

    protected <K, V> void deleteValues(final AbstractLmdbDb<K, V> lmdbDb,
                                       final Collection<Entry<K, V>> data) {
        lmdbDb.getLmdbEnvironment()
                .doWithWriteTxn(writeTxn -> {
                    data.forEach(entry -> {
                        lmdbDb.delete(writeTxn, entry.getKey());
                    });
                });
    }


    // --------------------------------------------------------------------------------


    private record TestEnv(Path dbDir, LmdbEnv lmdbEnv) {

    }
}
