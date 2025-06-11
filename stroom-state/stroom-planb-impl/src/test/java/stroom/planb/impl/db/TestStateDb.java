/*
 * Copyright 2017 Crown Copyright
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

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.pipeline.refdata.store.StringValue;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.data.FileDescriptor;
import stroom.planb.impl.data.FileHashUtil;
import stroom.planb.impl.data.MergeProcessor;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.data.StagingFileStore;
import stroom.planb.impl.db.State.Key;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateType;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.util.io.FileUtil;
import stroom.util.zip.ZipUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestStateDb {

    private static final String MAP_UUID = "map-uuid";
    private static final String MAP_NAME = "map-name";

    @Test
    void testReadWrite(@TempDir Path tempDir) {
        final Function<Integer, Key> keyFunction = i -> Key.builder().name("TEST_KEY").build();
        final Function<Integer, StateValue> valueFunction = i -> {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(("test" + i).getBytes(StandardCharsets.UTF_8));
            return StateValue.builder().typeId(StringValue.TYPE_ID).byteBuffer(byteBuffer).build();
        };
        testReadWrite(tempDir, 100, keyFunction, valueFunction);
    }

    @Test
    void testMerge(@TempDir final Path rootDir) throws IOException {
        final Path dbPath1 = rootDir.resolve("db1");
        final Path dbPath2 = rootDir.resolve("db2");
        Files.createDirectory(dbPath1);
        Files.createDirectory(dbPath2);

        final Function<Integer, Key> keyFunction = i -> Key.builder().name("TEST_KEY1").build();
        final Function<Integer, StateValue> valueFunction = i -> {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(("test1" + i).getBytes(StandardCharsets.UTF_8));
            return StateValue.builder().typeId(StringValue.TYPE_ID).byteBuffer(byteBuffer).build();
        };
        testWrite(dbPath1, 100, keyFunction, valueFunction);

        final Function<Integer, Key> keyFunction2 = i -> Key.builder().name("TEST_KEY2").build();
        final Function<Integer, StateValue> valueFunction2 = i -> {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(("test2" + i).getBytes(StandardCharsets.UTF_8));
            return StateValue.builder().typeId(StringValue.TYPE_ID).byteBuffer(byteBuffer).build();
        };
        testWrite(dbPath2, 100, keyFunction2, valueFunction2);

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final StateDb db = new StateDb(dbPath1, byteBufferFactory)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testFullProcess(@TempDir final Path rootDir) throws IOException {
        final StatePaths statePaths = new StatePaths(rootDir);
        final StagingFileStore fileStore = new StagingFileStore(statePaths);
        final int parts = 10;

        // Write parts.
        final List<CompletableFuture<Void>> list = new ArrayList<>();
        for (int thread = 0; thread < parts; thread++) {
            list.add(CompletableFuture.runAsync(() ->
                    writePart(fileStore, "TEST_KEY_1")));
            list.add(CompletableFuture.runAsync(() ->
                    writePart(fileStore, "TEST_KEY_2")));
        }
        CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();

        // Consume and merge parts.
        final PlanBDocStore planBDocStore = Mockito.mock(PlanBDocStore.class);
        final PlanBDoc doc = PlanBDoc
                .builder()
                .uuid(MAP_UUID)
                .name(MAP_NAME)
                .stateType(StateType.STATE)
                .settings(StateSettings.builder().build())
                .build();
        Mockito.when(planBDocStore.findByName(Mockito.anyString()))
                .thenReturn(Collections.singletonList(doc.asDocRef()));
        Mockito.when(planBDocStore.readDocument(Mockito.any(DocRef.class)))
                .thenReturn(doc);
        final PlanBDocCache planBDocCache = Mockito.mock(PlanBDocCache.class);
        Mockito.when(planBDocCache.get(Mockito.any(String.class)))
                .thenReturn(doc);

        final String path = rootDir.toAbsolutePath().toString();
        final PlanBConfig planBConfig = new PlanBConfig(path);
        final ShardManager shardManager = new ShardManager(
                new ByteBufferFactoryImpl(),
                planBDocCache,
                planBDocStore,
                null,
                () -> planBConfig,
                statePaths,
                null);
        final MergeProcessor mergeProcessor = new MergeProcessor(
                fileStore,
                statePaths,
                new MockSecurityContext(),
                new SimpleTaskContextFactory(),
                shardManager);
        for (int i = 0; i < parts; i++) {
            mergeProcessor.merge(i);
        }

        // Read merged
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final StateDb db = new StateDb(
                statePaths.getShardDir().resolve(MAP_UUID),
                byteBufferFactory,
                StateSettings.builder().build(),
                true)) {
            assertThat(db.count()).isEqualTo(2);
        }
    }

    @Test
    void testZipUnzip(@TempDir final Path rootDir) throws IOException {
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        // Simulate constant writing to shard.
        final Function<Integer, Key> keyFunction = i -> Key.builder().name("TEST_KEY1").build();
        final Function<Integer, StateValue> valueFunction = i -> {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(("test1" + i).getBytes(StandardCharsets.UTF_8));
            return StateValue.builder().typeId(StringValue.TYPE_ID).byteBuffer(byteBuffer).build();
        };

        final Path source = rootDir.resolve("source");
        final Path zipFile = rootDir.resolve("zip.zip");
        final Path target = rootDir.resolve("target");
        Files.createDirectories(source);
        Files.createDirectories(target);

        final AtomicBoolean writeComplete = new AtomicBoolean();
        final List<CompletableFuture<?>> list = new ArrayList<>();

        try (final StateDb db1 = new StateDb(source, byteBufferFactory)) {
            list.add(CompletableFuture.runAsync(() -> {
                insertData(db1, 1000000, keyFunction, valueFunction);
                writeComplete.set(true);
            }));

            list.add(CompletableFuture.runAsync(() -> {
                try {
                    while (!writeComplete.get()) {
                        final Path lmdbDataFile = source.resolve("data.mdb");
                        if (Files.exists(lmdbDataFile)) {
                            // Compress.

                            // Lock when compressing, so we don't zip half written data.
                            db1.lockCommits(() -> {
                                try {
                                    ZipUtil.zip(zipFile, source);
                                } catch (final IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });

                            // Decompress.
                            ZipUtil.unzip(zipFile, target);
                            Files.delete(zipFile);
                            // Read.
                            try (final StateDb db2 = new StateDb(
                                    target,
                                    byteBufferFactory,
                                    StateSettings.builder().build(),
                                    true)) {
                                assertThat(db2.count()).isGreaterThanOrEqualTo(0);
                            }
                            // Cleanup.
                            FileUtil.deleteDir(target);
                        }
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }));

            CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();
        }
    }

    @Test
    void testDeleteWhileRead(@TempDir Path tempDir) {
        final Function<Integer, Key> keyFunction = i -> Key.builder().name("TEST_KEY").build();
        final Function<Integer, StateValue> valueFunction = i -> {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(("test" + i).getBytes(StandardCharsets.UTF_8));
            return StateValue.builder().typeId(StringValue.TYPE_ID).byteBuffer(byteBuffer).build();
        };
        testWrite(tempDir, 100, keyFunction, valueFunction);

        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final StateDb db = new StateDb(
                tempDir,
                byteBufferFactory,
                StateSettings.builder().build(),
                true)) {
            assertThat(db.count()).isEqualTo(1);
            final Key key = Key.builder().name("TEST_KEY").build();

            // Read the data.
            Optional<StateValue> optional = db.get(key);
            assertThat(optional).isNotEmpty();
            StateValue res = optional.get();
            assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
            assertThat(res.toString()).isEqualTo("test99");

            // Delete the data.
            FileUtil.deleteDir(tempDir);

            // Try and read.
            optional = db.get(key);
            assertThat(optional).isNotEmpty();
            res = optional.get();
            assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
            assertThat(res.toString()).isEqualTo("test99");
        }
    }

    private void writePart(final StagingFileStore fileStore, final String keyName) {
        try {
            final Function<Integer, Key> keyFunction = i -> Key.builder().name(keyName).build();
            final Function<Integer, StateValue> valueFunction = i -> {
                final ByteBuffer byteBuffer = ByteBuffer.wrap(("test1" + i).getBytes(StandardCharsets.UTF_8));
                return StateValue.builder().typeId(StringValue.TYPE_ID).byteBuffer(byteBuffer).build();
            };
            final Path partPath = Files.createTempDirectory("part");
            final Path mapPath = partPath.resolve(MAP_UUID);
            Files.createDirectories(mapPath);
            testWrite(mapPath, 100, keyFunction, valueFunction);
            final Path zipFile = Files.createTempFile("lmdb", "zip");
            ZipUtil.zip(zipFile, partPath);
            FileUtil.deleteDir(partPath);
            final String fileHash = FileHashUtil.hash(zipFile);
            fileStore.add(new FileDescriptor(System.currentTimeMillis(), 1, fileHash), zipFile);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Disabled
    @Test
    void testWritePerformanceSameKey(@TempDir Path tempDir) {
        final Function<Integer, Key> keyFunction = i -> Key.builder().name("TEST_KEY").build();
        final Function<Integer, StateValue> valueFunction = i -> {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(("test" + i).getBytes(StandardCharsets.UTF_8));
            return StateValue.builder().typeId(StringValue.TYPE_ID).byteBuffer(byteBuffer).build();
        };
        testWrite(tempDir, 10000000, keyFunction, valueFunction);
    }

    @Disabled
    @Test
    void testWritePerformanceMultiKey(@TempDir Path tempDir) {
        final Function<Integer, Key> keyFunction = i -> Key.builder().name("TEST_KEY" + i).build();
        final Function<Integer, StateValue> valueFunction = i -> {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(("test" + i).getBytes(StandardCharsets.UTF_8));
            return StateValue.builder().typeId(StringValue.TYPE_ID).byteBuffer(byteBuffer).build();
        };
        testWrite(tempDir, 10000000, keyFunction, valueFunction);
    }

    private void testReadWrite(final Path tempDir,
                               final int insertRows,
                               final Function<Integer, Key> keyFunction,
                               final Function<Integer, StateValue> valueFunction) {
        testWrite(tempDir, insertRows, keyFunction, valueFunction);
        testRead(tempDir, insertRows);
    }

    private void testWrite(final Path dbDir,
                           final int insertRows,
                           final Function<Integer, Key> keyFunction,
                           final Function<Integer, StateValue> valueFunction) {
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final StateDb db = new StateDb(dbDir, byteBufferFactory)) {
            insertData(db, insertRows, keyFunction, valueFunction);
        }
    }

    private void testRead(final Path tempDir,
                          final int expectedRows) {
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        try (final StateDb db = new StateDb(tempDir, byteBufferFactory)) {
            assertThat(db.count()).isEqualTo(1);
            final Key key = Key.builder().name("TEST_KEY").build();
            final Optional<StateValue> optional = db.get(key);
            assertThat(optional).isNotEmpty();
            final StateValue res = optional.get();
            assertThat(res.typeId()).isEqualTo(StringValue.TYPE_ID);
            assertThat(res.toString()).isEqualTo("test" + (expectedRows - 1));

            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(StateFields.KEY);
            fieldIndex.create(StateFields.VALUE_TYPE);
            fieldIndex.create(StateFields.VALUE);
            final List<Val[]> results = new ArrayList<>();
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();
            db.search(
                    new ExpressionCriteria(ExpressionOperator.builder().build()),
                    fieldIndex,
                    null,
                    expressionPredicateFactory,
                    results::add);
            assertThat(results.size()).isEqualTo(1);
            assertThat(results.getFirst()[0].toString()).isEqualTo("TEST_KEY");
            assertThat(results.getFirst()[1].toString()).isEqualTo("String");
            assertThat(results.getFirst()[2].toString()).isEqualTo("test" + (expectedRows - 1));
        }
    }

//    @Test
//    void testRemoveOldData() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            final StateDao stateDao = new StateDao(sessionProvider, tableName);
//
//            insertData(stateDao, 100);
//            insertData(stateDao, 10);
//
//            assertThat(stateDao.count()).isEqualTo(1);
//
//            stateDao.removeOldData(Instant.parse("2000-01-01T00:00:00.000Z"));
//            assertThat(stateDao.count()).isEqualTo(1);
//
//            stateDao.removeOldData(Instant.now());
//            assertThat(stateDao.count()).isEqualTo(0);
//        });
//    }

    private void insertData(final StateDb db,
                            final int rows,
                            final Function<Integer, Key> keyFunction,
                            final Function<Integer, StateValue> valueFunction) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Key k = keyFunction.apply(i);
                final StateValue v = valueFunction.apply(i);
                db.insert(writer, k, v);
            }
        });
    }
}
