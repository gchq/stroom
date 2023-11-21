package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferPair;
import stroom.lmdb.serde.IntegerSerde;
import stroom.lmdb.serde.Serde;
import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.io.ByteSize;

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

@Disabled // manual perf only
public class TestLmdbCursorPerformance extends AbstractLmdbDbTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestLmdbCursorPerformance.class);

    private static final ByteSize DB_MAX_SIZE = ByteSize.ofMebibytes(500);
    private static final int REC_COUNT = 2_000_000;
//    private static final int REC_COUNT = 2_000;
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

    @Test
    void test() {
        putData();
        LOGGER.info("Done puts");
        TimedCase getCase = TimedCase.of("get", (round, iterations) -> {
            for (int i = 0; i < REC_COUNT; i++) {
                final int finalI = i;
                final Optional<Integer> val = basicLmdbDb.get(finalI);
                if (val.get() != i) {
                    throw new RuntimeException("invalid i: " + i);
                }
            }
        });
        TimedCase iterableCase = TimedCase.of("iterable", (round, iterations) -> {
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
        TimedCase cursorCase = TimedCase.of("cursor", (round, iterations) -> {
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
                iterableCase,
                cursorCase);
    }

    @Override
    protected ByteSize getMaxSizeBytes() {
        return DB_MAX_SIZE;
    }

    private void putData() {
        lmdbEnv.doWithWriteTxn(writeTxn -> {
            for (int i = 0; i < REC_COUNT; i++) {
                basicLmdbDb.put(writeTxn, i, i, false, true);
            }
        });
    }

}
