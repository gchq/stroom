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

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.data.FileDescriptor;
import stroom.planb.impl.data.FileHashUtil;
import stroom.planb.impl.data.MergeProcessor;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.db.state.State;
import stroom.planb.impl.db.state.StateDb;
import stroom.planb.impl.db.state.StateFields;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateKeyType;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateType;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValByte;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValShort;
import stroom.query.language.functions.ValString;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.test.common.TestUtil;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.zip.ZipUtil;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestStateDb {

    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final StateSettings BASIC_SETTINGS = StateSettings
            .builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();
    private static final String MAP_UUID = "map-uuid";
    private static final String MAP_NAME = "map-name";
    private static final Val MIN_FLOAT = ValFloat.create(Float.MIN_VALUE);
    private static final Val MAX_FLOAT = ValFloat.create(Float.MAX_VALUE);
    private static final Val MIN_DOUBLE = ValDouble.create(Double.MIN_VALUE);
    private static final Val MAX_DOUBLE = ValDouble.create(Double.MAX_VALUE);

    @Test
    void testReadWrite(@TempDir final Path tempDir) {
        final Function<Integer, Val> keyFunction = i -> ValString.create("TEST_KEY");
        final Function<Integer, Val> valueFunction = i -> ValString.create("test" + i);
        testWriteRead(tempDir, BASIC_SETTINGS, 100, keyFunction, valueFunction);
    }

    @Test
    void testReadWriteIntegerMax(@TempDir final Path tempDir) {
        testReadWriteKeyType(tempDir, StateKeyType.INT, ValInteger.create(Integer.MAX_VALUE));
    }

    @Test
    void testReadWriteIntegerMin(@TempDir final Path tempDir) {
        testReadWriteKeyType(tempDir, StateKeyType.INT, ValInteger.create(Integer.MIN_VALUE));
    }

    @Test
    void testReadWriteLongMax(@TempDir final Path tempDir) {
        testReadWriteKeyType(tempDir, StateKeyType.LONG, ValLong.create(Long.MAX_VALUE));
    }

    @Test
    void testReadWriteLongMin(@TempDir final Path tempDir) {
        testReadWriteKeyType(tempDir, StateKeyType.LONG, ValLong.create(Long.MIN_VALUE));
    }

    void testReadWriteKeyType(@TempDir final Path tempDir, final StateKeyType stateKeyType, final Val key) {
        final Function<Integer, Val> keyFunction = i -> key;
        final Function<Integer, Val> valueFunction = i -> ValString.create("test" + i);
        final StateSettings settings = StateSettings
                .builder()
                .stateKeySchema(StateKeySchema.builder().stateKeyType(stateKeyType).build())
                .build();
        testWriteRead(tempDir, settings, 100, keyFunction, valueFunction);
    }

    @Test
    void testMerge(@TempDir final Path rootDir) throws IOException {
        final Path dbPath1 = rootDir.resolve("db1");
        final Path dbPath2 = rootDir.resolve("db2");
        Files.createDirectory(dbPath1);
        Files.createDirectory(dbPath2);

        final Function<Integer, Val> keyFunction = i -> ValString.create("TEST_KEY1");
        final Function<Integer, Val> valueFunction = i -> ValString.create("test1" + i);
        testWrite(dbPath1, BASIC_SETTINGS, 100, keyFunction, valueFunction);

        final Function<Integer, Val> keyFunction2 = i -> ValString.create("TEST_KEY2");
        final Function<Integer, Val> valueFunction2 = i -> ValString.create("test2" + i);
        testWrite(dbPath2, BASIC_SETTINGS, 100, keyFunction2, valueFunction2);

        try (final StateDb db = StateDb.create(dbPath1, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testFullProcess(@TempDir final Path rootDir) {
        final StatePaths statePaths = new StatePaths(rootDir);
        final PlanBDocStore planBDocStore = Mockito.mock(PlanBDocStore.class);
        final PlanBDoc doc = PlanBDoc
                .builder()
                .uuid(MAP_UUID)
                .name(MAP_NAME)
                .stateType(StateType.STATE)
                .settings(BASIC_SETTINGS)
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
                new ByteBuffers(new ByteBufferFactoryImpl()),
                planBDocCache,
                planBDocStore,
                null,
                () -> planBConfig,
                statePaths,
                null);
        final MergeProcessor mergeProcessor = new MergeProcessor(
                statePaths,
                new MockSecurityContext(),
                new SimpleTaskContextFactory(),
                shardManager);

        final int threads = 10;

        // Write parts.
        final List<CompletableFuture<Void>> list = new ArrayList<>();
        for (int thread = 0; thread < threads; thread++) {
            list.add(CompletableFuture.runAsync(() ->
                    writePart(mergeProcessor, ValString.create("TEST_KEY_1"))));
            list.add(CompletableFuture.runAsync(() ->
                    writePart(mergeProcessor, ValString.create("TEST_KEY_2"))));
        }
        CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();

        // Consume and merge parts.
        mergeProcessor.mergeCurrent();

        // Read merged
        try (final StateDb db = StateDb.create(
                statePaths.getShardDir().resolve(MAP_UUID),
                new ByteBuffers(new ByteBufferFactoryImpl()),
                BASIC_SETTINGS,
                true)) {
            assertThat(db.count()).isEqualTo(2);
        }
    }

    @Test
    void testZipUnzip(@TempDir final Path rootDir) throws IOException {
        // Simulate constant writing to shard.
        final Function<Integer, Val> keyFunction = i -> ValString.create("TEST_KEY1");
        final Function<Integer, Val> valueFunction = i -> ValString.create("test1" + i);

        final Path source = rootDir.resolve("source");
        final Path zipFile = rootDir.resolve("zip.zip");
        final Path target = rootDir.resolve("target");
        Files.createDirectories(source);
        Files.createDirectories(target);

        final AtomicBoolean writeComplete = new AtomicBoolean();
        final List<CompletableFuture<?>> list = new ArrayList<>();

        try (final StateDb db1 = StateDb.create(source, BYTE_BUFFERS, BASIC_SETTINGS, false)) {
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
                            db1.lock(() -> {
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
                            try (final StateDb db2 = StateDb.create(
                                    target,
                                    BYTE_BUFFERS,
                                    BASIC_SETTINGS,
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
    void testDeleteWhileRead(@TempDir final Path tempDir) {
        final Function<Integer, Val> keyFunction = i -> ValString.create("TEST_KEY");
        final Function<Integer, Val> valueFunction = i -> ValString.create("test" + i);
        testWrite(tempDir, BASIC_SETTINGS, 100, keyFunction, valueFunction);

        try (final StateDb db = StateDb.create(
                tempDir,
                BYTE_BUFFERS,
                BASIC_SETTINGS,
                true)) {
            assertThat(db.count()).isEqualTo(1);
            final Val key = ValString.create("TEST_KEY");

            // Read the data.
            Val value = db.get(key);
            assertThat(value).isNotNull();
            assertThat(value.type()).isEqualTo(Type.STRING);
            assertThat(value.toString()).isEqualTo("test99");

            // Delete the data.
            FileUtil.deleteDir(tempDir);

            // Try and read.
            value = db.get(key);
            assertThat(value).isNotNull();
            assertThat(value.type()).isEqualTo(Type.STRING);
            assertThat(value.toString()).isEqualTo("test99");
        }
    }

    private void writePart(final MergeProcessor mergeProcessor, final Val keyName) {
        try {
            final Function<Integer, Val> keyFunction = i -> keyName;
            final Function<Integer, Val> valueFunction = i -> ValString.create("test1" + i);
            final Path partPath = Files.createTempDirectory("part");
            final Path mapPath = partPath.resolve(MAP_UUID);
            Files.createDirectories(mapPath);
            testWrite(mapPath, BASIC_SETTINGS, 100, keyFunction, valueFunction);
            final Path zipFile = Files.createTempFile("lmdb", "zip");
            ZipUtil.zip(zipFile, partPath);
            FileUtil.deleteDir(partPath);
            final String fileHash = FileHashUtil.hash(zipFile);
            mergeProcessor.add(new FileDescriptor(System.currentTimeMillis(), 1, fileHash), zipFile, false);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record TestRun(StateSettings settings, Val key, int iterations) {

    }

    private StateSettings getSettings(final StateKeyType stateKeyType) {
        return StateSettings
                .builder()
                .stateKeySchema(StateKeySchema.builder()
                        .stateKeyType(stateKeyType)
                        .build())
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testWrite() {
        return createWriteTest(1, false);
    }

    @TestFactory
    Stream<DynamicTest> testWritePerformance() {
        return createWriteTest(10000000, false);
    }

    @TestFactory
    Stream<DynamicTest> testWriteRead() {
        return createWriteTest(1, true);
    }

    @TestFactory
    Stream<DynamicTest> testWriteReadPerformance() {
        return createWriteTest(10000000, true);
    }

    Stream<DynamicTest> createWriteTest(final int iterations, final boolean read) {
        return TestUtil.buildDynamicTestStream()
                .withWrappedInputType(new TypeLiteral<TestRun>() {
                })
                .withOutputType(Boolean.class)
                .withTestFunction(testCase -> {
                    Path path = null;
                    try {
                        path = Files.createTempDirectory("stroom");
//                        final Path path = tempDir.resolve(UUID.randomUUID().toString());
//                        Files.createDirectories(path);
                        testSameKey(
                                path,
                                testCase.getInput().settings,
                                testCase.getInput().key,
                                testCase.getInput().iterations,
                                read);
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    } finally {
                        if (path != null) {
                            FileUtil.deleteDir(path);
                        }
                    }
                    return true;
                })
                .withSimpleEqualityAssertion()

                // Byte keys.
                .addNamedCase("Byte key min",
                        new TestRun(getSettings(StateKeyType.BYTE), ValByte.create(Byte.MIN_VALUE), iterations),
                        true)
                .addNamedCase("Byte key max",
                        new TestRun(getSettings(StateKeyType.BYTE), ValByte.create(Byte.MAX_VALUE), iterations),
                        true)

                // Short keys.
                .addNamedCase("Short key min",
                        new TestRun(getSettings(StateKeyType.SHORT),
                                ValShort.create(Short.MIN_VALUE), iterations),
                        true)
                .addNamedCase("Short key max",
                        new TestRun(getSettings(StateKeyType.SHORT), ValShort.create(Short.MAX_VALUE), iterations),
                        true)

                // Integer keys.
                .addNamedCase("Integer key min",
                        new TestRun(getSettings(StateKeyType.INT), ValInteger.create(Integer.MIN_VALUE), iterations),
                        true)
                .addNamedCase("Integer key max",
                        new TestRun(getSettings(StateKeyType.INT), ValInteger.create(Integer.MAX_VALUE), iterations),
                        true)

                // Long keys.
                .addNamedCase("Long key min",
                        new TestRun(getSettings(StateKeyType.LONG), ValLong.create(Long.MIN_VALUE), iterations),
                        true)
                .addNamedCase("Long key max",
                        new TestRun(getSettings(StateKeyType.LONG), ValLong.create(Long.MAX_VALUE), iterations),
                        true)
                // Float keys.
                .addNamedCase("Float key min",
                        new TestRun(getSettings(StateKeyType.FLOAT), MIN_FLOAT, iterations),
                        true)
                .addNamedCase("Float key max",
                        new TestRun(getSettings(StateKeyType.FLOAT), MAX_FLOAT, iterations),
                        true)

                // Double keys.
                .addNamedCase("Double key min",
                        new TestRun(getSettings(StateKeyType.DOUBLE), MIN_DOUBLE, iterations),
                        true)
                .addNamedCase("Double key max",
                        new TestRun(getSettings(StateKeyType.DOUBLE), MAX_DOUBLE, iterations),
                        true)

                // String keys.
                .addNamedCase("String key",
                        new TestRun(getSettings(StateKeyType.STRING), ValString.create("TEST_KEY"), iterations),
                        true)

                // Lookup keys.
                .addNamedCase("Uid lookup key",
                        new TestRun(getSettings(StateKeyType.UID_LOOKUP), ValString.create("TEST_KEY"), iterations),
                        true)

                .addNamedCase("Hash lookup key",
                        new TestRun(getSettings(StateKeyType.HASH_LOOKUP), ValString.create("TEST_KEY"), iterations),
                        true)

                .addNamedCase("Hash lookup key (long)",
                        new TestRun(getSettings(StateKeyType.HASH_LOOKUP), ValString.create(makeKey(800)), iterations),
                        true)


//                // Auto keys.
//                .addNamedCase("Auto byte key min",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), String.valueOf(Byte.MIN_VALUE), iterations),
//                        true)
//                .addNamedCase("Auto byte key max",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), String.valueOf(Byte.MAX_VALUE), iterations),
//                        true)
//
//                .addNamedCase("Auto short key min",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), String.valueOf(Short.MIN_VALUE), iterations),
//                        true)
//                .addNamedCase("Auto short key max",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), String.valueOf(Short.MAX_VALUE), iterations),
//                        true)
//
//                .addNamedCase("Auto integer key min",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), String.valueOf(Integer.MIN_VALUE),
//                        iterations),
//                        true)
//                .addNamedCase("Auto integer key max",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), String.valueOf(Integer.MAX_VALUE),
//                        iterations),
//                        true)
//
//                .addNamedCase("Auto long key min",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), String.valueOf(Long.MIN_VALUE), iterations),
//                        true)
//                .addNamedCase("Auto long key max",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), String.valueOf(Long.MAX_VALUE), iterations),
//                        true)
//
//                .addNamedCase("Auto float key min",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), MIN_FLOAT, iterations),
//                        true)
//                .addNamedCase("Auto float key max",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), MAX_FLOAT, iterations),
//                        true)
//
//                .addNamedCase("Auto double key min",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), MIN_DOUBLE, iterations),
//                        true)
//                .addNamedCase("Auto double key max",
//                        new TestRun(getSettings(StateKeyType.VARIABLE), MAX_DOUBLE, iterations),
//                        true)

                .addNamedCase("Variable string key",
                        new TestRun(getSettings(StateKeyType.VARIABLE), ValString.create("TEST_KEY"), iterations),
                        true)
                .addNamedCase("Variable string uid lookup key",
                        new TestRun(getSettings(StateKeyType.VARIABLE), ValString.create(makeKey(200)), iterations),
                        true)
                .addNamedCase("Variable string hash lookup key",
                        new TestRun(getSettings(StateKeyType.VARIABLE), ValString.create(makeKey(800)), iterations),
                        true)
                .build();
    }

    private String makeKey(int len) {
        final char[] chars = new char[len];
        Arrays.fill(chars, 'T');
        return new String(chars);
    }

//    @Test
//    void testWritePerformanceSameIntegerKey(@TempDir Path tempDir) {
//        testWritePerformanceSameKey(tempDir, StateKeyType.INTEGER, String.valueOf(Integer.MAX_VALUE));
//    }
//
//    @Test
//    void testWritePerformanceSameLongKey(@TempDir Path tempDir) {
//        testWritePerformanceSameKey(tempDir, StateKeyType.LONG, String.valueOf(Long.MAX_VALUE));
//    }
//
//    @Test
//    void testWritePerformanceSameStringKey(@TempDir Path tempDir) {
//        testWritePerformanceSameKey(tempDir, StateKeyType.BYTES, "TEST_KEY");
//    }
//
//    @Test
//    void testWritePerformanceSameHashKey(@TempDir Path tempDir) {
//        testWritePerformanceSameKey(tempDir, StateKeyType.HASH_KEY, "TEST_KEY");
//    }
//
//    @Test
//    void testWritePerformanceSameForeignKey(@TempDir Path tempDir) {
//        testWritePerformanceSameKey(tempDir, StateKeyType.FOREIGN_KEY, "TEST_KEY");
//    }
//
//    @Test
//    void testWritePerformanceSameSmartKey1(@TempDir Path tempDir) {
//        testWritePerformanceSameKey(tempDir, StateKeyType.SMART, String.valueOf(Integer.MAX_VALUE));
//    }
//
//    @Test
//    void testWritePerformanceSameSmartKey2(@TempDir Path tempDir) {
//        testWritePerformanceSameKey(tempDir, StateKeyType.SMART, "TEST_KEY");
//    }

    void testSameKey(final Path tempDir,
                     final StateSettings settings,
                     final Val key,
                     final int rows,
                     final boolean read) {
        final Function<Integer, Val> keyFunction = i -> key;
        final Function<Integer, Val> valueFunction = i -> ValString.create("test" + i);
        testWrite(tempDir, settings, rows, keyFunction, valueFunction);
        if (read) {
            testRead(tempDir, settings, rows, keyFunction);
        }
    }

    private void testWriteRead(final Path tempDir,
                               final StateSettings settings,
                               final int insertRows,
                               final Function<Integer, Val> keyFunction,
                               final Function<Integer, Val> valueFunction) {
        testWrite(tempDir, settings, insertRows, keyFunction, valueFunction);
        testRead(tempDir, settings, insertRows, keyFunction);
    }

    private void testWrite(final Path dbDir,
                           final StateSettings settings,
                           final int insertRows,
                           final Function<Integer, Val> keyFunction,
                           final Function<Integer, Val> valueFunction) {
        try (final StateDb db = StateDb.create(dbDir, BYTE_BUFFERS, settings, false)) {
            insertData(db, insertRows, keyFunction, valueFunction);
        }
    }

    private void testRead(final Path tempDir,
                          final StateSettings settings,
                          final int expectedRows,
                          final Function<Integer, Val> keyFunction) {
        try (final StateDb db = StateDb.create(tempDir, BYTE_BUFFERS, settings, true)) {
            assertThat(db.count()).isEqualTo(1);
            final Val key = keyFunction.apply(0);
            final Val value = db.get(key);
            assertThat(value).isNotNull();
            assertThat(value.type()).isEqualTo(Type.STRING);
            assertThat(value.toString()).isEqualTo("test" + (expectedRows - 1));

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
            assertThat(results.getFirst()[0]).isEqualTo(key);
            assertThat(results.getFirst()[1].toString()).isEqualTo("string");
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
                            final Function<Integer, Val> keyFunction,
                            final Function<Integer, Val> valueFunction) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Val k = keyFunction.apply(i);
                final Val v = valueFunction.apply(i);
                db.insert(writer, new State(k, v));
            }
        });
    }
}
