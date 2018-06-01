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

package stroom.refdata.offheapstore.databases;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.lmdbjava.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.ByteSizeUnit;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public class AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractLmdbDbTest.class);
    private static final long DB_MAX_SIZE = ByteSizeUnit.KIBIBYTE.longBytes(100);

    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();

    protected Env<ByteBuffer> lmdbEnv = null;

    @Before
    public void setup() {

        Path dbDir = tmpDir.getRoot().toPath();
        LOGGER.debug("Creating LMDB environment with maxSize: {}, dbDir {}",
                DB_MAX_SIZE, dbDir.toAbsolutePath().toString());

        lmdbEnv = Env.<ByteBuffer>create()
                .setMapSize(DB_MAX_SIZE)
                .setMaxDbs(5)
                .open(dbDir.toFile());
    }
}
