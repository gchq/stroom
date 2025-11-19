package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.PlanBDocStore;
import stroom.planb.impl.data.FileDescriptor;
import stroom.planb.impl.data.FileHashUtil;
import stroom.planb.impl.data.MergeProcessor;
import stroom.planb.impl.data.ShardManager;
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.serde.SpanDataLoaderTestUtil;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.StateType;
import stroom.planb.shared.TraceSettings;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.zip.ZipUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class TestTraceDb {

    private static final ByteBufferFactory BYTE_BUFFER_FACTORY = new ByteBufferFactoryImpl();
    private static final ByteBuffers BYTE_BUFFERS = new ByteBuffers(BYTE_BUFFER_FACTORY);
    private static final TraceSettings BASIC_SETTINGS = new TraceSettings
            .Builder()
            .maxStoreSize(ByteSize.ofGibibytes(100).getBytes())
            .retention(new RetentionSettings.Builder().duration(SimpleDuration.ZERO).enabled(true).build())
            .build();
    private static final PlanBDoc DOC = getDoc(BASIC_SETTINGS);
    private static final String MAP_UUID = "map-uuid";
    private static final String MAP_NAME = "map-name";

    @Test
    void testWriteRead(@TempDir final Path tempDir) {

        final Function<Integer, SpanKey> keyFunction = i -> createSpanKey();
        final Function<Integer, SpanValue> valueFunction = i -> createSpanValue();
        testWriteRead(tempDir, BASIC_SETTINGS, keyFunction, valueFunction);
    }

    private SpanKey createSpanKey() {
        return SpanKey.builder()
                .traceId("d18ea88869434c083a361644267ecf32")
                .parentSpanId("")
                .spanId("e0a94d9f5cd3a306")
                .build();
    }

    private SpanValue createSpanValue() {
        return SpanValue.builder()
                .build();
    }

    @Test
    void testMerge(@TempDir final Path rootDir) throws IOException {
        final Path dbPath1 = rootDir.resolve("db1");
        final Path dbPath2 = rootDir.resolve("db2");
        Files.createDirectory(dbPath1);
        Files.createDirectory(dbPath2);

        testWrite(dbPath1, BASIC_SETTINGS);
        testWrite(dbPath2, BASIC_SETTINGS);

        try (final TraceDb db = TraceDb.create(dbPath1, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, DOC, false)) {
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
                .stateType(StateType.TRACE)
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
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
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
                    writePart(mergeProcessor, createSpanKey())));
            list.add(CompletableFuture.runAsync(() ->
                    writePart(mergeProcessor, createSpanKey())));
        }
        CompletableFuture.allOf(list.toArray(new CompletableFuture[0])).join();

        // Consume and merge parts.
        mergeProcessor.mergeCurrent();

        // Read merged
        try (final TraceDb db = TraceDb.create(
                statePaths.getShardDir().resolve(MAP_UUID),
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(166);
        }

        // Try compaction.
        shardManager.compactAll();
        shardManager.compactAll();

        // Read compacted
        try (final TraceDb db = TraceDb.create(
                statePaths.getShardDir().resolve(MAP_UUID),
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(166);
            assertThat(db.getInfo().env().dbNames().size()).isEqualTo(9);
        }

        // Try deletion.
        shardManager.condenseAll(new SimpleTaskContext());

        // Read after deletion
        try (final TraceDb db = TraceDb.create(
                statePaths.getShardDir().resolve(MAP_UUID),
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(166);
            System.err.println(db.getInfoString());
            assertThat(db.getInfo().env().dbNames().size()).isEqualTo(9);
        }

        // Try compaction.
        shardManager.compactAll();
        shardManager.compactAll();

        // Read compacted
        try (final TraceDb db = TraceDb.create(
                statePaths.getShardDir().resolve(MAP_UUID),
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(166);
            assertThat(db.getInfo().env().stat().entries).isEqualTo(9);
        }
    }

    @Test
    void testDeleteWhileRead(@TempDir final Path tempDir) {
        final Function<Integer, SpanKey> keyFunction = i -> createSpanKey();
        final Function<Integer, SpanValue> valueFunction = i -> createSpanValue();
        testWrite(tempDir, BASIC_SETTINGS);

        try (final TraceDb db = TraceDb.create(
                tempDir,
                BYTE_BUFFERS,
                BYTE_BUFFER_FACTORY,
                DOC,
                true)) {
            assertThat(db.count()).isEqualTo(166);
            final SpanKey key = createSpanKey();

            // Read the data.
            SpanValue value = db.get(key);
            assertThat(value).isNotNull();
//            assertThat(value.type()).isEqualTo(Type.STRING);
//            assertThat(value.toString()).isEqualTo("test99");

            // Delete the data.
            FileUtil.deleteDir(tempDir);

            // Try and read.
            value = db.get(key);
            assertThat(value).isNotNull();
//            assertThat(value.type()).isEqualTo(Type.STRING);
//            assertThat(value.toString()).isEqualTo("test99");
        }
    }

    private void writePart(final MergeProcessor mergeProcessor, final SpanKey keyName) {
        try {
            final Function<Integer, SpanKey> keyFunction = i -> keyName;
            final Function<Integer, SpanValue> valueFunction = i -> createSpanValue();
            final Path partPath = Files.createTempDirectory("part");
            final Path mapPath = partPath.resolve(MAP_UUID);
            Files.createDirectories(mapPath);
            testWrite(mapPath, BASIC_SETTINGS);
            final Path zipFile = Files.createTempFile("lmdb", "zip");
            ZipUtil.zip(zipFile, partPath);
            FileUtil.deleteDir(partPath);
            final String fileHash = FileHashUtil.hash(zipFile);
            mergeProcessor.add(new FileDescriptor(System.currentTimeMillis(), 1, fileHash), zipFile, false);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TraceSettings getSettings() {
        return new TraceSettings.Builder().build();
    }

//    @TestFactory
//    Collection<DynamicTest> testWrite() {
//        return createWriteTest(false);
//    }
//
//    @TestFactory
//    Collection<DynamicTest> testWritePerformance() {
//        return createWriteTest(false);
//    }
//
//    @TestFactory
//    Collection<DynamicTest> testMultiWrite() {
//        return createMultiKeyTest(false);
//    }
//
//    @TestFactory
//    Collection<DynamicTest> testMultiWritePerformance() {
//        return createMultiKeyTest(false);
//    }
//
//    @TestFactory
//    Collection<DynamicTest> testMultiWriteRead() {
//        return createMultiKeyTest(true);
//    }
//
//    @TestFactory
//    Collection<DynamicTest> testMultiWriteReadPerformance() {
//        return createMultiKeyTest(true);
//    }
//
//    @TestFactory
//    Collection<DynamicTest> testWriteRead() {
//        return createWriteTest(true);
//    }
//
//    @TestFactory
//    Collection<DynamicTest> testWriteReadPerformance() {
//        return createWriteTest(true);
//    }
//
//    private Executable createTest(final TraceSettings settings,
//                                  final Function<Integer, SpanKey> keyFunction,
//                                  final Function<Integer, SpanValue> valueFunction,
//                                  final boolean read) {
//        return () -> {
//            Path path = null;
//            try {
//                path = Files.createTempDirectory("stroom");
//                testWrite(path, settings);
//                if (read) {
//                    testRead(path, settings, keyFunction, valueFunction);
//                }
//            } catch (final IOException e) {
//                throw new UncheckedIOException(e);
//            } finally {
//                if (path != null) {
//                    FileUtil.deleteDir(path);
//                }
//            }
//        };
//    }
//
//    private DynamicTest createStaticKeyTest(final String displayName,
//                                            final SpanKey key,
//                                            final boolean read) {
//        final Function<Integer, SpanKey> keyFunction = i -> key;
//        final Function<Integer, SpanValue> valueFunction = i -> createSpanValue();
//        return DynamicTest.dynamicTest(displayName,
//                createTest(getSettings(), keyFunction, valueFunction, read));
//    }
//
//    Collection<DynamicTest> createWriteTest(final boolean read) {
//        final List<DynamicTest> tests = new ArrayList<>();
//
//        // Byte keys.
//        tests.add(createStaticKeyTest(
//                "Byte key max",
//                createSpanKey(),
//                read));
//        return tests;
//    }
//
//    Collection<DynamicTest> createMultiKeyTest(final boolean read) {
//        final List<DynamicTest> tests = new ArrayList<>();
////        for (final TestTraceDb.KeyFunction keyFunction : keyFunctions) {
////            for (final ValueFunction valueFunction : StateValueTestUtil.getValueFunctions()) {
////                tests.add(DynamicTest.dynamicTest("key type = " + keyFunction +
////                                                  ", value type = " + valueFunction,
////                        () -> {
////                            final TraceSettings settings = new TraceSettings
////                                    .Builder()
////                                    .build();
////
////                            Path path = null;
////                            try {
////                                path = Files.createTempDirectory("stroom");
////
////                                testWrite(path, settings, iterations,
////                                        keyFunction.function,
////                                        valueFunction.function());
////                                if (read) {
////                                    testSimpleRead(path, settings, iterations,
////                                            keyFunction.function,
////                                            valueFunction.function());
////                                }
////
////                            } catch (final IOException e) {
////                                throw new UncheckedIOException(e);
////                            } finally {
////                                if (path != null) {
////                                    FileUtil.deleteDir(path);
////                                }
////                            }
////                        }));
////            }
////        }
//        return tests;
//    }

    private void testWriteRead(final Path tempDir,
                               final TraceSettings settings,
                               final Function<Integer, SpanKey> keyFunction,
                               final Function<Integer, SpanValue> valueFunction) {
        testWrite(tempDir, settings);
        testRead(tempDir, settings, keyFunction, valueFunction);
    }

    private void testWrite(final Path dbDir,
                           final TraceSettings settings) {
        try (final TraceDb db = TraceDb.create(dbDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, getDoc(settings), false)) {
            SpanDataLoaderTestUtil.load(span -> {
                insertData(db);
            });
        }
    }

    private void testRead(final Path tempDir,
                          final TraceSettings settings,
                          final Function<Integer, SpanKey> keyFunction,
                          final Function<Integer, SpanValue> valueFunction) {
        try (final TraceDb db = TraceDb.create(tempDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, getDoc(settings), false)) {
            assertThat(db.count()).isEqualTo(166);
//            final SpanKey key = keyFunction.apply(0);
//            final SpanValue expectedVal = valueFunction.apply(0);
//            final SpanValue value = db.get(key);
//            assertThat(value).isNotNull();
//            assertThat(value).isEqualTo(expectedVal);

//            final FieldIndex fieldIndex = new FieldIndex();
//            fieldIndex.create(TraceFields.KEY);
//            fieldIndex.create(TraceFields.VALUE_TYPE);
//            fieldIndex.create(TraceFields.VALUE);
//            final List<SpanValue[]> results = new ArrayList<>();
//            final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();
//            db.search(
//                    new ExpressionCriteria(ExpressionOperator.builder().build()),
//                    fieldIndex,
//                    null,
//                    expressionPredicateFactory,
//                    results::add);
//            assertThat(results.size()).isEqualTo(1);
//            assertThat(results.getFirst()[0]).isEqualTo(key.getVal());
//            assertThat(results.getFirst()[1].toString()).isEqualTo(expectedVal.type().toString());
//            assertThat(results.getFirst()[2]).isEqualTo(expectedVal);

            // Test deleting data.
            db.deleteOldData(Instant.now(), false);
        }
    }

    private void testSimpleRead(final Path tempDir,
                                final TraceSettings settings,
                                final int rows,
                                final Function<Integer, SpanKey> keyFunction,
                                final Function<Integer, SpanValue> valueFunction) {
        try (final TraceDb db = TraceDb.create(tempDir, BYTE_BUFFERS, BYTE_BUFFER_FACTORY, getDoc(settings), false)) {
            for (int i = 0; i < rows; i++) {
                final SpanKey key = keyFunction.apply(i);
                final SpanValue value = db.get(key);
                assertThat(value).isNotNull();
                assertThat(value).isEqualTo(valueFunction.apply(i));
            }

            // Test deleting data.
            db.deleteOldData(Instant.now(), false);
        }
    }

    private void insertData(final TraceDb db) {
        db.write(writer -> {
            SpanDataLoaderTestUtil.load(span -> {
                final SpanKey key = SpanKey.create(span);
                final SpanValue value = SpanValue.create(span);
                db.insert(writer, new SpanKV(key, value));
            });
        });
    }

    private static PlanBDoc getDoc(final TraceSettings settings) {
        return PlanBDoc.builder().uuid(UUID.randomUUID().toString()).name("test").settings(settings).build();
    }

    private record KeyFunction(String description,
                               Function<Integer, SpanKey> function) {

        @Override
        public String toString() {
            return description;
        }
    }
}
