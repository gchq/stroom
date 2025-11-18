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
import stroom.planb.impl.data.TemporalState;
import stroom.planb.impl.db.StateValueTestUtil.ValueFunction;
import stroom.planb.impl.db.temporalstate.TemporalStateDb;
import stroom.planb.impl.db.temporalstate.TemporalStateFields;
import stroom.planb.impl.db.temporalstate.TemporalStateRequest;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalStateKeySchema;
import stroom.planb.shared.TemporalStateSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValByte;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValShort;
import stroom.query.language.functions.ValString;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.zip.ZipUtil;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class TestTemporalStateDb {

    private static final int ITERATIONS = 100;
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(new ByteBufferFactoryImpl());
    private static final TemporalStateSettings BASIC_SETTINGS = new TemporalStateSettings
            .Builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .build();
    private static final PlanBDoc DOC = getDoc(BASIC_SETTINGS);
    private static final String MAP_UUID = "map-uuid";
    private static final String MAP_NAME = "map-name";

    private final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
    private final List<KeyFunction> keyFunctions = List.of(
            new KeyFunction(KeyType.BOOLEAN.name(), KeyType.BOOLEAN,
                    i -> new TemporalKey(KeyPrefix.create(ValBoolean.create(i > 0)), refTime)),
            new KeyFunction(KeyType.BYTE.name(), KeyType.BYTE,
                    i -> new TemporalKey(KeyPrefix.create(ValByte.create(i.byteValue())), refTime)),
            new KeyFunction(KeyType.SHORT.name(), KeyType.SHORT,
                    i -> new TemporalKey(KeyPrefix.create(ValShort.create(i.shortValue())), refTime)),
            new KeyFunction(KeyType.INT.name(), KeyType.INT,
                    i -> new TemporalKey(KeyPrefix.create(ValInteger.create(i)), refTime)),
            new KeyFunction(KeyType.LONG.name(), KeyType.LONG,
                    i -> new TemporalKey(KeyPrefix.create(ValLong.create(i.longValue())), refTime)),
            new KeyFunction(KeyType.FLOAT.name(), KeyType.FLOAT,
                    i -> new TemporalKey(KeyPrefix.create(ValFloat.create(i.floatValue())), refTime)),
            new KeyFunction(KeyType.DOUBLE.name(), KeyType.DOUBLE,
                    i -> new TemporalKey(KeyPrefix.create(ValDouble.create(i.doubleValue())), refTime)),
            new KeyFunction(KeyType.STRING.name(), KeyType.STRING,
                    i -> new TemporalKey(KeyPrefix.create(ValString.create("test-" + i)), refTime)),
            new KeyFunction(KeyType.UID_LOOKUP.name(), KeyType.UID_LOOKUP,
                    i -> new TemporalKey(KeyPrefix.create(ValString.create("test-" + i)), refTime)),
            new KeyFunction(KeyType.HASH_LOOKUP.name(), KeyType.HASH_LOOKUP,
                    i -> new TemporalKey(KeyPrefix.create(ValString.create("test-" + i)), refTime)),
            new KeyFunction(KeyType.VARIABLE.name(), KeyType.VARIABLE,
                    i -> new TemporalKey(KeyPrefix.create(ValString.create("test-" + i)), refTime)),
            new KeyFunction("Variable mid", KeyType.VARIABLE,
                    i -> new TemporalKey(KeyPrefix.create(
                            ValString.create(StateValueTestUtil.makeString(400))), refTime)),
            new KeyFunction("Variable long", KeyType.VARIABLE,
                    i -> new TemporalKey(KeyPrefix.create(
                            ValString.create(StateValueTestUtil.makeString(1000))), refTime)));

    @Test
    void test(@TempDir final Path tempDir) {
        testWrite(tempDir);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(
                tempDir,
                BYTE_BUFFERS,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(100);

            final KeyPrefix byteKey = KeyPrefix.create("TEST_KEY");
            // Check exact time states.
            checkState(db, byteKey, refTime, true);
            // Check before time states.
            checkState(db, byteKey, refTime.minusMillis(1), false);
            // Check after time states.
            checkState(db, byteKey, refTime.plusMillis(1), true);
            final TemporalKey key = TemporalKey.builder().prefix(byteKey).time(refTime).build();
            final Val value = db.get(key);
            assertThat(value).isNotNull();
            assertThat(value.type()).isEqualTo(Type.STRING);
            assertThat(value.toString()).isEqualTo("test");

            final FieldIndex fieldIndex = new FieldIndex();
            fieldIndex.create(TemporalStateFields.KEY);
            fieldIndex.create(TemporalStateFields.EFFECTIVE_TIME);
            fieldIndex.create(TemporalStateFields.VALUE_TYPE);
            fieldIndex.create(TemporalStateFields.VALUE);
            final List<Val[]> results = new ArrayList<>();
            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();
            db.search(
                    new ExpressionCriteria(ExpressionOperator.builder().build()),
                    fieldIndex,
                    null,
                    expressionPredicateFactory,
                    results::add);
            assertThat(results.size()).isEqualTo(100);
            assertThat(results.getFirst()[0].toString()).isEqualTo("TEST_KEY");
            assertThat(results.getFirst()[1].toString()).isEqualTo("2000-01-01T00:00:00.000Z");
            assertThat(results.getFirst()[2].toString()).isEqualTo("string");
            assertThat(results.getFirst()[3].toString()).isEqualTo("test");


//            assertThat(count.get()).isEqualTo(100);
        }
    }


    @Test
    void testFullProcess(@TempDir final Path rootDir) {
        final StatePaths statePaths = new StatePaths(rootDir);
        final PlanBDocStore planBDocStore = Mockito.mock(PlanBDocStore.class);
        final PlanBDoc doc = DOC;
        Mockito.when(planBDocStore.findByName(Mockito.anyString()))
                .thenReturn(Collections.singletonList(doc.asDocRef()));
        Mockito.when(planBDocStore.readDocument(Mockito.any(DocRef.class)))
                .thenReturn(doc);
        final PlanBDocCache planBDocCache = Mockito.mock(PlanBDocCache.class);
        Mockito.when(planBDocCache.get(Mockito.any(String.class)))
                .thenReturn(doc);

        final String path = rootDir.toAbsolutePath().toString();
        final PlanBConfig planBConfig = new PlanBConfig(path);
        final ByteBufferFactoryImpl byteBufferFactory = new ByteBufferFactoryImpl();
        final ShardManager shardManager = new ShardManager(
                new ByteBuffers(byteBufferFactory),
                byteBufferFactory,
                planBDocCache,
                planBDocStore,
                null,
                () -> planBConfig,
                statePaths,
                null,
                new SimpleTaskContextFactory());
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
                    writePart(mergeProcessor, KeyPrefix.create(StringUtils.repeat('T', 400)))));
            list.add(CompletableFuture.runAsync(() ->
                    writePart(mergeProcessor, KeyPrefix.create(StringUtils.repeat('U', 400)))));
        }
        CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();

        // Consume and merge parts.
        mergeProcessor.mergeCurrent();

        // Read merged
        try (final TemporalStateDb db = TemporalStateDb.create(
                statePaths.getShardDir().resolve(MAP_UUID),
                new ByteBuffers(new ByteBufferFactoryImpl()),
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(2);
        }

        // Try compaction.
        shardManager.compactAll();
        shardManager.compactAll();

        // Read compacted
        try (final TemporalStateDb db = TemporalStateDb.create(
                statePaths.getShardDir().resolve(MAP_UUID),
                new ByteBuffers(new ByteBufferFactoryImpl()),
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(2);
            assertThat(db.getInfo().env().dbNames().size()).isEqualTo(14);
        }

        // Try deletion.
        shardManager.condenseAll(new SimpleTaskContext());

        // Read after deletion
        try (final TemporalStateDb db = TemporalStateDb.create(
                statePaths.getShardDir().resolve(MAP_UUID),
                new ByteBuffers(new ByteBufferFactoryImpl()),
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(2);
            System.err.println(db.getInfoString());
            assertThat(db.getInfo().env().dbNames().size()).isEqualTo(14);
        }

        // Try compaction.
        shardManager.compactAll();
        shardManager.compactAll();

        // Read compacted
        try (final TemporalStateDb db = TemporalStateDb.create(
                statePaths.getShardDir().resolve(MAP_UUID),
                new ByteBuffers(new ByteBufferFactoryImpl()),
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(2);
            assertThat(db.getInfo().env().stat().entries).isEqualTo(14);
        }
    }

    private void writePart(final MergeProcessor mergeProcessor, final KeyPrefix keyName) {
        try {
            final Function<Integer, TemporalKey> keyFunction = i -> new TemporalKey(keyName, Instant.ofEpochMilli(0));
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

    @Test
    void testGetState(@TempDir final Path tempDir) {
        final KeyPrefix name = KeyPrefix.create("test");
        final Instant effectiveTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(tempDir, BYTE_BUFFERS, DOC, false)) {
            db.write(writer -> {
                final TemporalKey k = TemporalKey
                        .builder()
                        .prefix(name)
                        .time(effectiveTime)
                        .build();
                final Val v = ValString.create("test");
                db.insert(writer, new TemporalState(k, v));
            });
        }

        try (final TemporalStateDb db = TemporalStateDb.create(
                tempDir,
                BYTE_BUFFERS,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(1);
            checkState(db, name, effectiveTime, true);
        }
    }

    @TestFactory
    Collection<DynamicTest> testMultiWrite() {
        return createMultiKeyTest(1, false);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWritePerformance() {
        return createMultiKeyTest(ITERATIONS, false);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWriteRead() {
        return createMultiKeyTest(1, true);
    }

    @TestFactory
    Collection<DynamicTest> testMultiWriteReadPerformance() {
        return createMultiKeyTest(ITERATIONS, true);
    }

    Collection<DynamicTest> createMultiKeyTest(final int iterations, final boolean read) {
        final List<DynamicTest> tests = new ArrayList<>();
        for (final KeyFunction keyFunction : keyFunctions) {
            for (final ValueFunction valueFunction : StateValueTestUtil.getValueFunctions()) {
                for (final TemporalPrecision temporalPrecision : TemporalPrecision.values()) {
                    tests.add(DynamicTest.dynamicTest("key type = " + keyFunction +
                                                      ", Value type = " + valueFunction +
                                                      ", Temporal precision = " + temporalPrecision,
                            () -> {
                                final TemporalStateSettings settings = new TemporalStateSettings
                                        .Builder()
                                        .keySchema(new TemporalStateKeySchema.Builder()
                                                .keyType(keyFunction.keyType)
                                                .temporalPrecision(temporalPrecision)
                                                .build())
                                        .valueSchema(new StateValueSchema.Builder()
                                                .stateValueType(valueFunction.stateValueType())
                                                .build())
                                        .build();

                                Path path = null;
                                try {
                                    path = Files.createTempDirectory("stroom");

                                    testWrite(path, settings, iterations,
                                            keyFunction.function,
                                            valueFunction.function());
                                    if (read) {
                                        testSimpleRead(path, settings, iterations,
                                                keyFunction.function,
                                                valueFunction.function());
                                    }

                                } catch (final IOException e) {
                                    throw new UncheckedIOException(e);
                                } finally {
                                    if (path != null) {
                                        FileUtil.deleteDir(path);
                                    }
                                }
                            }));
                }
            }
        }
        return tests;
    }

    @Test
    void testMerge(@TempDir final Path rootDir) throws IOException {
        final Path dbPath1 = rootDir.resolve("db1");
        final Path dbPath2 = rootDir.resolve("db2");
        Files.createDirectory(dbPath1);
        Files.createDirectory(dbPath2);

        testWrite(dbPath1);
        testWrite(dbPath2);

        try (final TemporalStateDb db = TemporalStateDb.create(dbPath1, BYTE_BUFFERS, DOC, false)) {
            db.merge(dbPath2);
        }
    }

    @Test
    void testCondenseAndDelete(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        testWrite(dbPath);

        try (final TemporalStateDb db = TemporalStateDb.create(dbPath, BYTE_BUFFERS, DOC, false)) {
            assertThat(db.count()).isEqualTo(100);
            db.condense(Instant.now());
            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(1);
            db.condense(Instant.now());
            db.deleteOldData(Instant.now(), true);
            assertThat(db.count()).isEqualTo(0);
        }
    }

    @Test
    void testCondense2(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(dbPath, BYTE_BUFFERS, DOC, false)) {
            insertData(db, refTime, KeyPrefix.create("TEST_KEY"), "test", 100, 60 * 60 * 24);
            insertData(db, refTime, KeyPrefix.create("TEST_KEY2"), "test2", 100, 60 * 60 * 24);
            insertData(db, refTime, KeyPrefix.create("TEST_KEY"), "test", 10, -60 * 60 * 24);
            insertData(db, refTime, KeyPrefix.create("TEST_KEY2"), "test2", 10, -60 * 60 * 24);

            assertThat(db.count()).isEqualTo(218);

            db.condense(refTime.plusMillis(1));
            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(200);

            db.condense(Instant.parse("2000-01-10T00:00:00.000Z").plusMillis(1));
            db.deleteOldData(Instant.MIN, true);
            assertThat(db.count()).isEqualTo(182);
        }
    }

    @Test
    void testCondense3(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(dbPath, BYTE_BUFFERS, DOC, false)) {
            final String prefix = StringUtils.repeat('T', 400);
            for (int i = 0; i < 10000; i++) {
                insertData(db, refTime, KeyPrefix.create(prefix + i), "test", 100, 60 * 60 * 24);
                insertData(db, refTime, KeyPrefix.create(prefix + i), "test2", 10, -60 * 60 * 24);

            }

            db.condense(Instant.now());
            db.deleteOldData(Instant.now(), true);
            db.condense(Instant.now());
        }
    }

    @Test
    void testCondense4(@TempDir final Path rootDir) throws IOException {
        final Path dbPath = rootDir.resolve("db");
        Files.createDirectory(dbPath);

        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(dbPath, BYTE_BUFFERS, DOC, false)) {

            for (int i = 0; i < 10000; i++) {
                final String prefix = StringUtils.repeat('T', i);
                insertData(db, refTime, KeyPrefix.create(prefix + i), "test", 100, 60 * 60 * 24);
                insertData(db, refTime, KeyPrefix.create(prefix + i), "test2", 100, -60 * 60 * 24);
            }

            for (int i = 0; i < 100; i++) {
                final Instant effectiveTime = refTime.plusSeconds(i * 60 * 60 * 24);
                db.deleteOldData(effectiveTime, true);
                db.condense(Instant.now());
            }
        }
    }
//
//    @Test
//    void testRepeatedCondense(@TempDir final Path rootDir) throws IOException {
//        final Path dbPath = rootDir.resolve("db");
//        Files.createDirectory(dbPath);
//
//        Instant refTime1 = Instant.parse("2000-01-01T00:00:00.000Z");
//        final Path src = dbPath.resolve("src");
//        Instant refTime2 = Instant.parse("2000-01-01T00:00:00.000Z");
//        final Path dest = dbPath.resolve("dest");
//        Files.createDirectory(dest);
//
//        for (int j = 0; j < 100; j++) {
//            Files.createDirectories(src);
//            try (final TemporalStateDb db = TemporalStateDb.create(src, BYTE_BUFFERS, DOC, false)) {
//                for (int i = 100; i < 400; i++) {
//                    final String prefix = StringUtils.repeat('T', i);
//                    insertData(db, refTime1, KeyPrefix.create(prefix + i), prefix, 100, 60 * 60 * 24);
//                    insertData(db, refTime1, KeyPrefix.create(prefix + i), prefix, 100, -60 * 60 * 24);
//                }
//                refTime1 = refTime1.plusSeconds(60 * 60 * 24);
//            }
//
//            Files.createDirectories(dest);
//            try (final TemporalStateDb db = TemporalStateDb.create(dest, BYTE_BUFFERS, DOC, false)) {
//                db.merge(src);
//
//                for (int i = 0; i < 100; i++) {
//                    long total = 0;
//                    total += db.condense(refTime2);
//                    total += db.deleteOldData(refTime2, true);
//                    if (total > 0) {
//                        // If we removed data then compact the shard.
////                        taskContext.info(() -> "Compacting shard");
////                        db.compact();
//                    }
//                }
//
//                refTime2 = refTime2.plusSeconds(60 * 60 * 24);
//            }
//        }
//    }

    private void testWrite(final Path dbDir) {
        final Instant refTime = Instant.parse("2000-01-01T00:00:00.000Z");
        try (final TemporalStateDb db = TemporalStateDb.create(dbDir, BYTE_BUFFERS, DOC, false)) {
            insertData(db, refTime, KeyPrefix.create("TEST_KEY"), "test", 100, 10);
        }
    }

    private void testWrite(final Path dbDir,
                           final TemporalStateSettings settings,
                           final int insertRows,
                           final Function<Integer, TemporalKey> keyFunction,
                           final Function<Integer, Val> valueFunction) {
        try (final TemporalStateDb db = TemporalStateDb.create(dbDir, BYTE_BUFFERS, getDoc(settings), false)) {
            insertData(db, insertRows, keyFunction, valueFunction);
        }
    }

    private void insertData(final TemporalStateDb db,
                            final int rows,
                            final Function<Integer, TemporalKey> keyFunction,
                            final Function<Integer, Val> valueFunction) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final TemporalKey k = keyFunction.apply(i);
                final Val v = valueFunction.apply(i);
                db.insert(writer, new TemporalState(k, v));
            }
        });
    }

    private void testSimpleRead(final Path dbDir,
                                final TemporalStateSettings settings,
                                final int rows,
                                final Function<Integer, TemporalKey> keyFunction,
                                final Function<Integer, Val> valueFunction) {
        try (final TemporalStateDb db = TemporalStateDb.create(dbDir, BYTE_BUFFERS, getDoc(settings), true)) {
            for (int i = 0; i < rows; i++) {
                final TemporalKey key = keyFunction.apply(i);
                final TemporalState temporalState = db.getState(new TemporalStateRequest(key));
                assertThat(temporalState).isNotNull();
                assertThat(temporalState.val().type()).isEqualTo(valueFunction.apply(i).type());
//                assertThat(value).isEqualTo(expectedVal); // Values will not be the same due to key overwrite.
            }
        }
    }

    private void insertData(final TemporalStateDb db,
                            final Instant refTime,
                            final KeyPrefix key,
                            final String value,
                            final int rows,
                            final long deltaSeconds) {
        db.write(writer -> {
            for (int i = 0; i < rows; i++) {
                final Instant effectiveTime = refTime.plusSeconds(i * deltaSeconds);
                final TemporalKey k = TemporalKey
                        .builder()
                        .prefix(key)
                        .time(effectiveTime)
                        .build();
                final Val v = ValString.create(value);
                db.insert(writer, new TemporalState(k, v));
            }
        });
    }

    private void checkState(final TemporalStateDb db,
                            final KeyPrefix key,
                            final Instant effectiveTime,
                            final boolean expected) {
        final TemporalStateRequest request =
                new TemporalStateRequest(new TemporalKey(key, effectiveTime));
        final TemporalState state = db.getState(request);
        assertThat(state != null).isEqualTo(expected);
    }

    private static PlanBDoc getDoc(final TemporalStateSettings settings) {
        return PlanBDoc
                .builder()
                .uuid(MAP_UUID)
                .name(MAP_NAME)
                .stateType(StateType.TEMPORAL_STATE)
                .settings(settings)
                .build();
    }

    private record KeyFunction(String description,
                               KeyType keyType,
                               Function<Integer, TemporalKey> function) {

        @Override
        public String toString() {
            return description;
        }
    }
}
