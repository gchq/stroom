package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.serde.IntegerSerde;
import stroom.lmdb.serde.Serde;
import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.io.ByteSize;

import org.apache.hadoop.hbase.util.ByteBufferUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.GetOp;
import org.lmdbjava.KeyRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Disabled // manual perf only
public class TestLmdbCursorPerformance extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbCursorPerformance.class);

    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(500);
    //    private static final int REC_COUNT = 2_000_000;
    private static final int REC_COUNT = 1_000_000;
    //    private static final int REC_COUNT = 50_000;
    private static final int TEST_REPEAT_COUNT = 3;

    private final Serde<Integer> integerSerde = new IntegerSerde();
    private BasicLmdbDb<Integer, Integer> basicLmdbDb;

    @BeforeEach
    void setup() {
        basicLmdbDb = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                integerSerde,
                integerSerde,
                "basicDb");

        //just to ensure the DB is created and ready to use
        basicLmdbDb.get(1);
    }

    /**
     * Compare getting single entries using a normal get vs a cursor with a start key
     */
    @Test
    void testGets() {
        putData();
        LOGGER.info("Done puts");
        TimedCase getCase = TimedCase.of("get (one txn per get)", (round, iterations) -> {
            for (int i = 0; i < REC_COUNT; i++) {
                final int finalI = i;
                final Optional<Integer> val = basicLmdbDb.get(finalI);
                if (val.get() != i) {
                    throw new RuntimeException("invalid i: " + i);
                }
            }
        });
        TimedCase getCase2 = TimedCase.of("get (one txn)", (round, iterations) -> {
            lmdbEnv.doWithReadTxn(readTxn -> {
                for (int i = 0; i < REC_COUNT; i++) {
                    final int finalI = i;
                    final Optional<Integer> val = basicLmdbDb.get(readTxn, finalI);
                    if (val.get() != finalI) {
                        throw new RuntimeException("invalid i: " + finalI);
                    }
                }
            });
        });
        TimedCase iterableCase = TimedCase.of("iterable (one txn per get)", (round, iterations) -> {
            for (int i = 0; i < REC_COUNT; i++) {
                final int finalI = i;
                lmdbEnv.getWithReadTxn(readTxn -> {
                    try (PooledByteBuffer pooledStartKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {
                        final Integer startKey = finalI;

                        final ByteBuffer startKeyBuffer = pooledStartKeyBuffer.getByteBuffer();
                        basicLmdbDb.serializeKey(startKeyBuffer, startKey);
                        final KeyRange<ByteBuffer> keyRange = KeyRange.atLeast(startKeyBuffer);

                        try (CursorIterable<ByteBuffer> cursorIterable = basicLmdbDb.iterate(readTxn, keyRange)) {
                            final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                            if (iterator.hasNext()) {
                                final KeyVal<ByteBuffer> keyVal = iterator.next();
                                final Integer val = basicLmdbDb.deserializeValue(keyVal.val());
                                if (val != finalI) {
                                    throw new RuntimeException("invalid i: " + finalI);
                                }
                                return val;
                            } else {
                                throw new RuntimeException("Not found for i: " + finalI);
                            }
                        }
                    }
                });
            }
        });
        TimedCase cursorCase = TimedCase.of("cursor (one txn per get)", (round, iterations) -> {
            for (int i = 0; i < REC_COUNT; i++) {
                final int finalI = i;
                lmdbEnv.getWithReadTxn(readTxn -> {
                    try (PooledByteBuffer pooledStartKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {
                        final Integer startKey = finalI;

                        final ByteBuffer startKeyBuffer = pooledStartKeyBuffer.getByteBuffer();
                        basicLmdbDb.serializeKey(startKeyBuffer, startKey);
                        try (Cursor<ByteBuffer> cursor = basicLmdbDb.getLmdbDbi().openCursor(readTxn)) {
                            cursor.get(startKeyBuffer, GetOp.MDB_SET_RANGE);
                            final Integer val = basicLmdbDb.deserializeValue(cursor.val());
                            if (val != finalI) {
                                throw new RuntimeException("invalid i: " + finalI);
                            }
                            return val;
                        }
                    }
                });
            }
        });
        TestUtil.comparePerformance(
                TEST_REPEAT_COUNT,
                REC_COUNT,
                LOGGER::info,
                getCase,
                getCase2,
                iterableCase,
                cursorCase);

    }

    @Test
    void testDeletes() {
        final int caseCount = 5;
        putData(TEST_REPEAT_COUNT * REC_COUNT * caseCount);
        LOGGER.info("Done puts");
        final AtomicInteger startIdx = new AtomicInteger(-REC_COUNT);
        final AtomicInteger endIdx = new AtomicInteger(0);
        TimedCase deleteCase = TimedCase.of("delete (one txn per delete)", (round, iterations) -> {
            startIdx.addAndGet(REC_COUNT);
            endIdx.addAndGet(REC_COUNT);
            for (int i = startIdx.get(); i < endIdx.get(); i++) {
                final boolean didDelete = basicLmdbDb.delete(i);
                if (!didDelete) {
                    throw new RuntimeException("invalid i: " + i);
                }
            }
        });
        TimedCase deleteCase2 = TimedCase.of("delete (one txn)", (round, iterations) -> {
            startIdx.addAndGet(REC_COUNT);
            endIdx.addAndGet(REC_COUNT);
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                for (int i = startIdx.get(); i < endIdx.get(); i++) {
                    final boolean didDelete = basicLmdbDb.delete(writeTxn, i);
                    if (!didDelete) {
                        throw new RuntimeException("invalid i: " + i);
                    }
                }
            });
        });
        TimedCase iterableCase = TimedCase.of("iterable (one txn)", (round, iterations) -> {
            startIdx.addAndGet(REC_COUNT);
            endIdx.addAndGet(REC_COUNT);
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                try (PooledByteBuffer pooledStartKeyBuffer = basicLmdbDb.getPooledKeyBuffer();
                        PooledByteBuffer pooledEndKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {
                    final Integer startKey = startIdx.get();
                    final Integer endKey = endIdx.get();

                    final ByteBuffer startKeyBuffer = pooledStartKeyBuffer.getByteBuffer();
                    final ByteBuffer endKeyBuffer = pooledEndKeyBuffer.getByteBuffer();
                    basicLmdbDb.serializeKey(startKeyBuffer, startKey);
                    basicLmdbDb.serializeKey(endKeyBuffer, endKey);
                    final KeyRange<ByteBuffer> keyRange = KeyRange.closed(startKeyBuffer, endKeyBuffer);

                    try (CursorIterable<ByteBuffer> cursorIterable = basicLmdbDb.iterate(writeTxn, keyRange)) {
                        final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                        while (iterator.hasNext()) {
                            iterator.next();
                            iterator.remove();
                        }
                    }
                }
            });
        });
        TimedCase cursorBackwardsCase = TimedCase.of("cursor backwards (one txn)", (round, iterations) -> {
            startIdx.addAndGet(REC_COUNT);
            endIdx.addAndGet(REC_COUNT);
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                try (PooledByteBuffer pooledStartKeyBuffer = basicLmdbDb.getPooledKeyBuffer();
                        PooledByteBuffer pooledEndKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {
                    final Integer startKey = startIdx.get();
                    final Integer endKey = endIdx.get();

                    final ByteBuffer startKeyBuffer = pooledStartKeyBuffer.getByteBuffer();
                    final ByteBuffer endKeyBuffer = pooledEndKeyBuffer.getByteBuffer();
                    basicLmdbDb.serializeKey(startKeyBuffer, startKey);
                    basicLmdbDb.serializeKey(endKeyBuffer, endKey - 1); // Make it inclusive
                    try (Cursor<ByteBuffer> cursor = basicLmdbDb.getLmdbDbi().openCursor(writeTxn)) {
                        // Position at the end key
                        cursor.get(endKeyBuffer, GetOp.MDB_SET);
                        // Scan backwards
                        do {
                            cursor.delete();
                        } while (cursor.prev());
                    }
                }
            });
        });
        TimedCase cursorForwardsCase = TimedCase.of("cursor forwards (one txn)", (round, iterations) -> {
            startIdx.addAndGet(REC_COUNT);
            endIdx.addAndGet(REC_COUNT);
            lmdbEnv.doWithWriteTxn(writeTxn -> {
                try (PooledByteBuffer pooledStartKeyBuffer = basicLmdbDb.getPooledKeyBuffer();
                        PooledByteBuffer pooledEndKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {
                    final Integer startKey = startIdx.get();
                    final Integer endKey = endIdx.get();

                    final ByteBuffer startKeyBuffer = pooledStartKeyBuffer.getByteBuffer();
                    final ByteBuffer endKeyBuffer = pooledEndKeyBuffer.getByteBuffer();
                    basicLmdbDb.serializeKey(startKeyBuffer, startKey);
                    basicLmdbDb.serializeKey(endKeyBuffer, endKey);
                    try (Cursor<ByteBuffer> cursor = basicLmdbDb.getLmdbDbi().openCursor(writeTxn)) {
                        // Position at the end key
                        cursor.get(startKeyBuffer, GetOp.MDB_SET);
                        do {
                            final ByteBuffer key = cursor.key();
                            if (ByteBufferUtils.compareTo(key, key.position(), key.remaining(),
                                    endKeyBuffer, endKeyBuffer.position(), endKeyBuffer.remaining()) == 0) {
                                // Reached exclusive end key
                                break;
                            }
                            cursor.delete();
                        } while (cursor.next());
                    }
                }
            });
        });

        TestUtil.comparePerformance(
                TEST_REPEAT_COUNT,
                REC_COUNT,
                LOGGER::info,
                deleteCase,
                deleteCase2,
                iterableCase,
                cursorBackwardsCase,
                cursorForwardsCase);

        final long entryCount = basicLmdbDb.getEntryCount();
        Assertions.assertThat(entryCount)
                .isZero();
    }

    @Test
    void testGetThenDelete() {
        final int caseCount = 5;
        putData(TEST_REPEAT_COUNT * REC_COUNT * caseCount);
        LOGGER.info("Done puts");
        final AtomicInteger startIdx = new AtomicInteger(-REC_COUNT);
        final AtomicInteger endIdx = new AtomicInteger(0);

        TimedCase getThenSingleDelete = TimedCase.of(
                "Get then single delete (one txn per get)",
                (round, iterations) -> {
                    startIdx.addAndGet(REC_COUNT);
                    endIdx.addAndGet(REC_COUNT);
                    try (PooledByteBuffer pooledKeyBuffer = basicLmdbDb.getPooledKeyBuffer();) {
                        final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                        for (int i = startIdx.get(); i < endIdx.get(); i++) {
                            final int finalI = i;
                            lmdbEnv.doWithWriteTxn(writeTxn -> {
                                keyBuffer.clear();
                                basicLmdbDb.serializeKey(keyBuffer, finalI);
                                final ByteBuffer valueBuffer = basicLmdbDb.getAsBytes(writeTxn, keyBuffer)
                                        .orElseThrow();
                                basicLmdbDb.deserializeValue(valueBuffer);
                                final boolean didDelete = basicLmdbDb.delete(writeTxn, keyBuffer);
                                if (!didDelete) {
                                    throw new RuntimeException("invalid i: " + finalI);
                                }
                            });
                        }
                    }
                });
        TimedCase getThenSingleDeleteBigTxn = TimedCase.of(
                "Get then single delete (one txn)",
                (round, iterations) -> {
                    startIdx.addAndGet(REC_COUNT);
                    endIdx.addAndGet(REC_COUNT);
                    try (PooledByteBuffer pooledKeyBuffer = basicLmdbDb.getPooledKeyBuffer();) {
                        final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                        lmdbEnv.doWithWriteTxn(writeTxn -> {
                            for (int i = startIdx.get(); i < endIdx.get(); i++) {
                                final int finalI = i;
                                keyBuffer.clear();
                                basicLmdbDb.serializeKey(keyBuffer, finalI);
                                final ByteBuffer valueBuffer = basicLmdbDb.getAsBytes(writeTxn, keyBuffer)
                                        .orElseThrow();
                                basicLmdbDb.deserializeValue(valueBuffer);
                                final boolean didDelete = basicLmdbDb.delete(writeTxn, keyBuffer);
                                if (!didDelete) {
                                    throw new RuntimeException("invalid i: " + finalI);
                                }
                            }
                        });
                    }
                });
        TimedCase cursorGetThenDelete = TimedCase.of(
                "Cursor get then single delete (one txn per get)",
                (round, iterations) -> {
                    startIdx.addAndGet(REC_COUNT);
                    endIdx.addAndGet(REC_COUNT);
                    try (PooledByteBuffer pooledKeyBuffer = basicLmdbDb.getPooledKeyBuffer();) {
                        final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();

                        for (int i = startIdx.get(); i < endIdx.get(); i++) {
                            final int finalI = i;
                            lmdbEnv.doWithWriteTxn(writeTxn -> {
                                keyBuffer.clear();
                                basicLmdbDb.serializeKey(keyBuffer, finalI);
                                try (Cursor<ByteBuffer> cursor = basicLmdbDb.getLmdbDbi().openCursor(writeTxn)) {
                                    cursor.get(keyBuffer, GetOp.MDB_SET_RANGE);
                                    final Integer val = basicLmdbDb.deserializeValue(cursor.val());
                                    if (val != finalI) {
                                        throw new RuntimeException("invalid i: " + finalI);
                                    }
                                    // Now delete it
                                    cursor.delete();
                                }
                            });
                        }
                    }
                });
        TimedCase cursorGetThenDeleteBigTxn = TimedCase.of(
                "Cursor get then single delete (one txn)",
                (round, iterations) -> {
                    startIdx.addAndGet(REC_COUNT);
                    endIdx.addAndGet(REC_COUNT);
                    try (PooledByteBuffer pooledKeyBuffer = basicLmdbDb.getPooledKeyBuffer();) {
                        final ByteBuffer keyBuffer = pooledKeyBuffer.getByteBuffer();
                        lmdbEnv.doWithWriteTxn(writeTxn -> {
                            for (int i = startIdx.get(); i < endIdx.get(); i++) {
                                final int finalI = i;
                                keyBuffer.clear();
                                basicLmdbDb.serializeKey(keyBuffer, finalI);
                                try (Cursor<ByteBuffer> cursor = basicLmdbDb.getLmdbDbi().openCursor(writeTxn)) {
                                    cursor.get(keyBuffer, GetOp.MDB_SET_RANGE);
                                    final Integer val = basicLmdbDb.deserializeValue(cursor.val());
                                    if (val != finalI) {
                                        throw new RuntimeException("invalid i: " + finalI);
                                    }
                                    // Now delete it
                                    cursor.delete();
                                }
                            }
                        });
                    }
                });
        TimedCase getsThenBatchDelete = TimedCase.of(
                "Gets then batch cursor delete (one txn per get)",
                (round, iterations) -> {
                    startIdx.addAndGet(REC_COUNT);
                    endIdx.addAndGet(REC_COUNT);

                    lmdbEnv.doWithReadTxn(readTxn -> {
                        // Do all the gets
                        for (int i = startIdx.get(); i < endIdx.get(); i++) {
                            final Integer value = basicLmdbDb.get(readTxn, i)
                                    .orElseThrow();
                        }
                    });

                    lmdbEnv.doWithWriteTxn(writeTxn -> {
                        // Do all the deletes
                        try (PooledByteBuffer pooledStartKeyBuffer = basicLmdbDb.getPooledKeyBuffer();
                                PooledByteBuffer pooledEndKeyBuffer = basicLmdbDb.getPooledKeyBuffer()) {
                            final Integer startKey = startIdx.get();
                            final Integer endKey = endIdx.get();

                            final ByteBuffer startKeyBuffer = pooledStartKeyBuffer.getByteBuffer();
                            final ByteBuffer endKeyBuffer = pooledEndKeyBuffer.getByteBuffer();

                            basicLmdbDb.serializeKey(startKeyBuffer, startKey);
                            basicLmdbDb.serializeKey(endKeyBuffer, endKey);
                            try (Cursor<ByteBuffer> cursor = basicLmdbDb.getLmdbDbi().openCursor(writeTxn)) {
                                // Position at the end key
                                cursor.get(startKeyBuffer, GetOp.MDB_SET);
                                do {
                                    final ByteBuffer key = cursor.key();
                                    if (ByteBufferUtils.compareTo(key, key.position(), key.remaining(),
                                            endKeyBuffer, endKeyBuffer.position(), endKeyBuffer.remaining()) == 0) {
                                        // Reached exclusive end key
                                        break;
                                    }
                                    cursor.delete();
                                } while (cursor.next());
                            }
                        }
                    });
                });

        TestUtil.comparePerformance(
                TEST_REPEAT_COUNT,
                REC_COUNT,
                LOGGER::info,
                getThenSingleDelete,
                getThenSingleDeleteBigTxn,
                cursorGetThenDelete,
                cursorGetThenDeleteBigTxn,
                getsThenBatchDelete);

        final long entryCount = basicLmdbDb.getEntryCount();
        Assertions.assertThat(entryCount)
                .isZero();
    }

    @Override
    protected ByteSize getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }

    private void putData() {
        putData(REC_COUNT);
    }

    private void putData(final int recCount) {
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            for (int i = 0; i < recCount; i++) {
                basicLmdbDb.put(writeTxn, i, i, false, true);
            }
        });
    }

}
