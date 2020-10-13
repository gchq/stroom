/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.pipeline.refdata.store.offheapstore.databases;

import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.shared.ModelStringUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.lmdbjava.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class AbstractLmdbDbTest extends StroomUnitTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLmdbDbTest.class);
    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(2_000);
    protected Env<ByteBuffer> lmdbEnv = null;
    private Path dbDir = null;

    @BeforeEach
    final void createEnv() throws IOException {
        dbDir = Files.createTempDirectory("stroom");
        LOGGER.info("Creating LMDB environment with maxSize: {}, dbDir {}",
                getMaxSizeBytes(), dbDir.toAbsolutePath().toString());

        lmdbEnv = Env.create()
                .setMapSize(getMaxSizeBytes().getBytes())
                .setMaxDbs(10)
                .open(dbDir.toFile());
    }

    @AfterEach
    final void teardown() {
        if (lmdbEnv != null) {
            lmdbEnv.close();
        }
        lmdbEnv = null;
        if (Files.isDirectory(dbDir)) {
            try {
                Files.list(dbDir)
                        .filter(path -> path.endsWith("data.mdb"))
                        .forEach(path -> {
                            try {
                                long fileSizeBytes = Files.size(path);
                                LOGGER.info("LMDB file size: {}",
                                        ModelStringUtil.formatIECByteSizeString(fileSizeBytes));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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
