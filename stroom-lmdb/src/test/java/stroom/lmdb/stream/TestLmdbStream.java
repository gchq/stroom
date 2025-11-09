package stroom.lmdb.stream;


import stroom.bytebuffer.ByteBufferUtils;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;

import org.junit.jupiter.api.Test;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestLmdbStream {

    @Test
    void testStream() {
        final Result result = run(LmdbStream::stream);
        assertThat(result.count).isEqualTo(10000);
        assertThat(result.first).isEqualTo(createTestBuffer(0, 0));
        assertThat(result.last).isEqualTo(createTestBuffer(99, 99));
    }

    @Test
    void testStreamReversed() {
        final Result result = run((txn, dbi) -> LmdbStream.stream(txn, dbi, LmdbKeyRange.allReverse()));
        assertThat(result.count).isEqualTo(10000);
        assertThat(result.first).isEqualTo(createTestBuffer(99, 99));
        assertThat(result.last).isEqualTo(createTestBuffer(0, 0));
    }

    @Test
    void testStreamRangeStartInclusive() {
        final ByteBuffer start = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start)
                        .build()));
        assertThat(result.count).isEqualTo(5758);
        assertThat(result.first).isEqualTo(createTestBuffer(42, 42));
        assertThat(result.last).isEqualTo(createTestBuffer(99, 99));
    }

    @Test
    void testStreamRangeStartExclusive() {
        final ByteBuffer start = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start, false)
                        .build()));
        assertThat(result.count).isEqualTo(5757);
        assertThat(result.first).isEqualTo(createTestBuffer(42, 43));
        assertThat(result.last).isEqualTo(createTestBuffer(99, 99));
    }

    @Test
    void testStreamRangeStopInclusive() {
        final ByteBuffer stop = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .stop(stop)
                        .build()));
        assertThat(result.count).isEqualTo(4243);
        assertThat(result.first).isEqualTo(createTestBuffer(0, 0));
        assertThat(result.last).isEqualTo(createTestBuffer(42, 42));
    }

    @Test
    void testStreamRangeStopExclusive() {
        final ByteBuffer stop = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .stop(stop, false)
                        .build()));
        assertThat(result.count).isEqualTo(4242);
        assertThat(result.first).isEqualTo(createTestBuffer(0, 0));
        assertThat(result.last).isEqualTo(createTestBuffer(42, 41));
    }


    @Test
    void testStreamRangeStartInclusiveStopInclusive() {
        final ByteBuffer start = createTestBuffer(40, 40);
        final ByteBuffer stop = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start)
                        .stop(stop)
                        .build()));
        assertThat(result.count).isEqualTo(203);
        assertThat(result.first).isEqualTo(createTestBuffer(40, 40));
        assertThat(result.last).isEqualTo(createTestBuffer(42, 42));
    }

    @Test
    void testStreamRangeStartInclusiveStopExclusive() {
        final ByteBuffer start = createTestBuffer(40, 40);
        final ByteBuffer stop = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start)
                        .stop(stop, false)
                        .build()));
        assertThat(result.count).isEqualTo(202);
        assertThat(result.first).isEqualTo(createTestBuffer(40, 40));
        assertThat(result.last).isEqualTo(createTestBuffer(42, 41));
    }

    @Test
    void testStreamRangeStartExclusiveStopInclusive() {
        final ByteBuffer start = createTestBuffer(40, 40);
        final ByteBuffer stop = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start, false)
                        .stop(stop)
                        .build()));
        assertThat(result.count).isEqualTo(202);
        assertThat(result.first).isEqualTo(createTestBuffer(40, 41));
        assertThat(result.last).isEqualTo(createTestBuffer(42, 42));
    }

    @Test
    void testStreamRangeStartExclusiveStopExclusive() {
        final ByteBuffer start = createTestBuffer(40, 40);
        final ByteBuffer stop = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start, false)
                        .stop(stop, false)
                        .build()));
        assertThat(result.count).isEqualTo(201);
        assertThat(result.first).isEqualTo(createTestBuffer(40, 41));
        assertThat(result.last).isEqualTo(createTestBuffer(42, 41));
    }


    @Test
    void testStreamRangeStartInclusiveReversed() {
        final ByteBuffer start = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start)
                        .reverse()
                        .build()));
        assertThat(result.count).isEqualTo(4243);
        assertThat(result.first).isEqualTo(createTestBuffer(42, 42));
        assertThat(result.last).isEqualTo(createTestBuffer(0, 0));
    }

    @Test
    void testStreamRangeStartExclusiveReversed() {
        final ByteBuffer start = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start, false)
                        .reverse()
                        .build()));
        assertThat(result.count).isEqualTo(4242);
        assertThat(result.first).isEqualTo(createTestBuffer(42, 41));
        assertThat(result.last).isEqualTo(createTestBuffer(0, 0));
    }

    @Test
    void testStreamRangeStopInclusiveReversed() {
        final ByteBuffer stop = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .stop(stop)
                        .reverse()
                        .build()));
        assertThat(result.count).isEqualTo(5758);
        assertThat(result.first).isEqualTo(createTestBuffer(99, 99));
        assertThat(result.last).isEqualTo(createTestBuffer(42, 42));
    }

    @Test
    void testStreamRangeStopExclusiveReversed() {
        final ByteBuffer stop = createTestBuffer(42, 42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .stop(stop, false)
                        .reverse()
                        .build()));
        assertThat(result.count).isEqualTo(5757);
        assertThat(result.first).isEqualTo(createTestBuffer(99, 99));
        assertThat(result.last).isEqualTo(createTestBuffer(42, 43));
    }


    @Test
    void testStreamRangeStartInclusiveStopInclusiveReversed() {
        final ByteBuffer start = createTestBuffer(42, 42);
        final ByteBuffer stop = createTestBuffer(40, 40);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start)
                        .stop(stop)
                        .reverse()
                        .build()));
        assertThat(result.count).isEqualTo(203);
        assertThat(result.first).isEqualTo(createTestBuffer(42, 42));
        assertThat(result.last).isEqualTo(createTestBuffer(40, 40));
    }

    @Test
    void testStreamRangeStartInclusiveStopExclusiveReversed() {
        final ByteBuffer start = createTestBuffer(42, 42);
        final ByteBuffer stop = createTestBuffer(40, 40);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start)
                        .stop(stop, false)
                        .reverse()
                        .build()));
        assertThat(result.count).isEqualTo(202);
        assertThat(result.first).isEqualTo(createTestBuffer(42, 42));
        assertThat(result.last).isEqualTo(createTestBuffer(40, 41));
    }

    @Test
    void testStreamRangeStartExclusiveStopInclusiveReversed() {
        final ByteBuffer start = createTestBuffer(42, 42);
        final ByteBuffer stop = createTestBuffer(40, 40);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start, false)
                        .stop(stop)
                        .reverse()
                        .build()));
        assertThat(result.count).isEqualTo(202);
        assertThat(result.first).isEqualTo(createTestBuffer(42, 41));
        assertThat(result.last).isEqualTo(createTestBuffer(40, 40));
    }

    @Test
    void testStreamRangeStartExclusiveStopExclusiveReversed() {
        final ByteBuffer start = createTestBuffer(42, 42);
        final ByteBuffer stop = createTestBuffer(40, 40);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .start(start, false)
                        .stop(stop, false)
                        .reverse()
                        .build()));
        assertThat(result.count).isEqualTo(201);
        assertThat(result.first).isEqualTo(createTestBuffer(42, 41));
        assertThat(result.last).isEqualTo(createTestBuffer(40, 41));
    }

    @Test
    void testStreamPrefix() {
        final ByteBuffer prefix = createTestBuffer(42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .prefix(prefix)
                        .build()));
        assertThat(result.count).isEqualTo(100);
        assertThat(result.first).isEqualTo(createTestBuffer(42, 0));
        assertThat(result.last).isEqualTo(createTestBuffer(42, 99));
    }

    @Test
    void testStreamPrefixReversed() {
        final ByteBuffer prefix = createTestBuffer(42);
        final Result result = run((txn, dbi) -> LmdbStream
                .stream(txn, dbi, LmdbKeyRange.builder()
                        .prefix(prefix)
                        .reverse()
                        .build()));
        assertThat(result.count).isEqualTo(100);
        assertThat(result.first).isEqualTo(createTestBuffer(42, 99));
        assertThat(result.last).isEqualTo(createTestBuffer(42, 0));
    }

    private Dbi<ByteBuffer> setupDb(final Env<ByteBuffer> env) {
        final Dbi<ByteBuffer> dbi = env.openDbi("test".getBytes(StandardCharsets.UTF_8), DbiFlags.MDB_CREATE);
        try (final Txn<ByteBuffer> txn = env.txnWrite()) {
            final ByteBuffer key = ByteBuffer.allocateDirect(Integer.BYTES + Integer.BYTES);
            final ByteBuffer value = ByteBuffer.allocateDirect(0);
            for (int i = 0; i < 100; i++) {
                for (int j = 0; j < 100; j++) {
                    key.putInt(i);
                    key.putInt(j);
                    key.flip();
                    dbi.put(txn, key, value);
                }
            }
            txn.commit();
        }
        return dbi;
    }

    private ByteBuffer createTestBuffer(final int i1) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.BYTES);
        byteBuffer.putInt(i1);
        byteBuffer.flip();
        return byteBuffer;
    }

    private ByteBuffer createTestBuffer(final int i1, final int i2) {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(Integer.BYTES + Integer.BYTES);
        byteBuffer.putInt(i1);
        byteBuffer.putInt(i2);
        byteBuffer.flip();
        return byteBuffer;
    }

    private Result run(final BiFunction<Txn<ByteBuffer>, Dbi<ByteBuffer>, Stream<LmdbEntry>> function) {
        try {
            final Path path = Files.createTempDirectory("stroom");
            final AtomicReference<ByteBuffer> firstItem = new AtomicReference<>();
            final AtomicReference<ByteBuffer> lastItem = new AtomicReference<>();
            final AtomicInteger count = new AtomicInteger();
            try (final Env<ByteBuffer> env = Env.create()
                    .setMapSize(ByteSize.ofMebibytes(1).getBytes())
                    .open(path.toFile())) {
                final Dbi<ByteBuffer> dbi = setupDb(env);
                try (final Txn<ByteBuffer> txn = env.txnRead()) {
                    try (final Stream<LmdbEntry> stream = function.apply(txn, dbi)) {
                        stream.forEach(entry -> {
                            if (firstItem.get() == null) {
                                firstItem.set(ByteBufferUtils.copyToDirectBuffer(entry.getKey()));
                            }
                            lastItem.set(ByteBufferUtils.copyToDirectBuffer(entry.getKey()));
                            count.incrementAndGet();
                        });
                    }
                }
            }
            FileUtil.deleteDir(path);
            return new Result(count.get(), firstItem.get(), lastItem.get());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Result(int count, ByteBuffer first, ByteBuffer last) {

    }
}
