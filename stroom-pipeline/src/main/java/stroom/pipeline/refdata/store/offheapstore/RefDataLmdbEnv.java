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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.lmdb.LmdbDb;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnv.BatchingWriteTxn;
import stroom.lmdb.LmdbEnv.WriteTxn;
import stroom.lmdb.LmdbEnvFactory;
import stroom.lmdb.LmdbEnvFactory.SimpleEnvBuilder;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.ReferenceDataLmdbConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.EnvFlags;
import org.lmdbjava.EnvInfo;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import java.util.function.Function;

public class RefDataLmdbEnv {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RefDataLmdbEnv.class);
    public static final String LEGACY_STORE_NAME = "Legacy";

    private final String feedName;
    private final LmdbEnv lmdbEnvironment;
    private final Map<String, LmdbDb> databaseMap = new HashMap<>();

    @Inject
    public RefDataLmdbEnv(final LmdbEnvFactory lmdbEnvFactory,
                          final Provider<ReferenceDataConfig> referenceDataConfigProvider,
                          @Assisted("feedName") @Nullable final String feedName,
                          @Assisted("subDirName") @Nullable final String subDirName) {
        this.feedName = Objects.requireNonNullElse(feedName, LEGACY_STORE_NAME);
        lmdbEnvironment = createEnvironment(
                lmdbEnvFactory,
                referenceDataConfigProvider.get().getLmdbConfig(),
                subDirName);
    }

    public LmdbEnv getEnvironment() {
        return lmdbEnvironment;
    }

    public static boolean isLmdbDataFile(final Path file) {
        return LmdbEnv.isLmdbDataFile(file);
    }

    public int getAvailableReadPermitCount() {
        return lmdbEnvironment.getAvailableReadPermitCount();
    }

    public Path getLocalDir() {
        return lmdbEnvironment.getLocalDir();
    }

    public String getFeedName() {
        return feedName;
    }

    public Optional<String> getName() {
        return lmdbEnvironment.getName();
    }

    public void sync(final boolean force) {
        lmdbEnvironment.sync(force);
    }

    public Set<EnvFlags> getEnvFlags() {
        return lmdbEnvironment.getEnvFlags();
    }

    public Dbi<ByteBuffer> openDbi(final String name, final DbiFlags... dbiFlags) {
        return lmdbEnvironment.openDbi(name, dbiFlags);
    }

    public void doWithWriteTxn(final Consumer<Txn<ByteBuffer>> work) {
        lmdbEnvironment.doWithWriteTxn(work);
    }

    public <T> T getWithWriteTxn(final Function<Txn<ByteBuffer>, T> work) {
        return lmdbEnvironment.getWithWriteTxn(work);
    }

    public WriteTxn openWriteTxn() {
        return lmdbEnvironment.openWriteTxn();
    }

    public BatchingWriteTxn openBatchingWriteTxn(final int batchSize) {
        return lmdbEnvironment.openBatchingWriteTxn(batchSize);
    }

    public <T> T getWithReadTxn(final Function<Txn<ByteBuffer>, T> work) {
        return lmdbEnvironment.getWithReadTxn(work);
    }

    public void doWithReadTxn(final Consumer<Txn<ByteBuffer>> work) {
        lmdbEnvironment.doWithReadTxn(work);
    }

    public <T> T getWithReadTxnUnderReadWriteLock(final Function<Txn<ByteBuffer>, T> work, final Lock readLock) {
        return lmdbEnvironment.getWithReadTxnUnderReadWriteLock(work, readLock);
    }

    public void close() {
        lmdbEnvironment.close();
    }

    public void delete() {
        lmdbEnvironment.delete();
    }

    public List<String> getDbiNames() {
        return lmdbEnvironment.getDbiNames();
    }

    public int getMaxKeySize() {
        return lmdbEnvironment.getMaxKeySize();
    }

    public EnvInfo info() {
        return lmdbEnvironment.info();
    }

    public boolean isClosed() {
        return lmdbEnvironment.isClosed();
    }

    public Stat stat() {
        return lmdbEnvironment.stat();
    }

    public Map<String, String> getEnvInfo() {
        return lmdbEnvironment.getEnvInfo();
    }

    public Map<String, String> getDbInfo(final Dbi<ByteBuffer> db) {
        return lmdbEnvironment.getDbInfo(db);
    }

    public long getSizeOnDisk() {
        return lmdbEnvironment.getSizeOnDisk();
    }

    public void registerDatabases(final LmdbDb... lmdbDbs) {
        if (lmdbDbs != null) {
            for (final LmdbDb lmdbDb : lmdbDbs) {
                this.databaseMap.put(lmdbDb.getDbName(), lmdbDb);
            }
        }
    }

    /**
     * For use in testing at SMALL scale. Dumps the content of each DB to the logger.
     */
    public void logAllContents(final Consumer<String> logEntryConsumer) {
        final AtomicLong counter = new AtomicLong();
        final Consumer<String> countingConsumer = str -> {
            counter.incrementAndGet();
            logEntryConsumer.accept(str);
        };
        databaseMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .forEach(lmdbDb ->
                        lmdbDb.logDatabaseContents(countingConsumer));
        if (counter.get() == 0) {
            logEntryConsumer.accept("All databases are empty");
        }
    }

    public long getEntryCount(final String dbName) {
        final LmdbDb lmdbDb = databaseMap.get(dbName);
        if (lmdbDb == null) {
            throw new IllegalArgumentException(LogUtil.message("No database with name {} exists", dbName));
        }
        return lmdbDb.getEntryCount();
    }

    private LmdbEnv createEnvironment(final LmdbEnvFactory lmdbEnvFactory,
                                      final ReferenceDataLmdbConfig lmdbConfig,
                                      final String subDirName) {

        // By default LMDB opens with readonly mmaps so you cannot mutate the bytebuffers inside a txn.
        // Instead you need to create a new bytebuffer for the value and put that. If you want faster writes
        // then you can use EnvFlags.MDB_WRITEMAP in the open() call to allow mutation inside a txn but that
        // comes with greater risk of corruption.

        // NOTE on setMapSize() from LMDB author found on https://groups.google.com/forum/#!topic/caffe-users/0RKsTTYRGpQ
        // On Windows the OS sets the filesize equal to the mapsize. (MacOS requires that too, and allocates
        // all of the physical space up front, it doesn't support sparse files.) The mapsize should not be
        // hardcoded into software, it needs to be reconfigurable. On Windows and MacOS you really shouldn't
        // set it larger than the amount of free space on the filesystem.

        final SimpleEnvBuilder builder = lmdbEnvFactory.builder(lmdbConfig)
                .withMaxDbCount(7)
                .addEnvFlag(EnvFlags.MDB_NOTLS);

        if (subDirName != null) {
            builder.withSubDirectory(subDirName);
        }

        final String name = Objects.requireNonNullElse(subDirName, LEGACY_STORE_NAME);
        final LmdbEnv env = builder
                .withSubDirectory(subDirName)
                .withName(name)
                .build();

        LOGGER.debug(() -> LogUtil.message("Created Ref Data LMDB Env: {}, localDir: {}, existing databases: [{}]",
                name, env.getLocalDir().toAbsolutePath(), String.join(",", env.getDbiNames())));
        return env;
    }

    @Override
    public String toString() {
        return "RefDataLmdbEnv{" +
                "feedName=" + feedName +
                ", localDir=" + getLocalDir() +
                ", name='" + getName() + '\'' +
                ", envFlags=" + getEnvFlags() +
                '}';
    }


    // --------------------------------------------------------------------------------


    public interface Factory {

        // Have to name the params to avoid ambiguity in guice binding
        RefDataLmdbEnv create(@Assisted("feedName") final String feedName,
                              @Assisted("subDirName") final String subDirName);
    }
}
