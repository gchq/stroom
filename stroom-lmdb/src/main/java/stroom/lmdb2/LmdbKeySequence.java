package stroom.lmdb2;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.KeyRange;
import org.lmdbjava.KeyRangeType;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class LmdbKeySequence {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbKeySequence.class);

    private final ByteBufferFactory byteBufferFactory;

    public LmdbKeySequence(final ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    public void find(final Dbi<ByteBuffer> dbi,
                     final Txn<ByteBuffer> writeTxn,
                     final ByteBuffer rowKey,
                     final ByteBuffer rowValue,
                     final Predicate<BBKV> matchPredicate,
                     final Consumer<Match> matchConsumer) {
        // Just try to find without a cursor.
        final ByteBuffer valueBuffer = dbi.get(writeTxn, rowKey);
        if (valueBuffer != null && matchPredicate.test(new BBKV(rowKey, valueBuffer))) {
            // Found our value, job done
            LOGGER.debug("Found row directly {}", rowValue);
            matchConsumer.accept(new Match(rowKey, null));

        } else {
            // Look forward from the provided row key across all subsequent sequence numbers.
            final KeyRange<ByteBuffer> keyRange = new KeyRange<>(KeyRangeType.FORWARD_GREATER_THAN, rowKey, rowKey);
            // Iterate over all entries with the same hash. Will only be one unless
            // we get a hash clash. Have to use a cursor as entries can be deleted, thus leaving
            // gaps in the seq numbers.
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(writeTxn, keyRange)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
                Long lastSeqNo = null;
                Long nextNo = null;
                while (iterator.hasNext()) {
                    final BBKV kv = BBKV.create(iterator.next());
                    final ByteBuffer key = kv.key();

                    // Stop iterating if we go beyond the prefix.
                    if (!ByteBufferUtils.containsPrefix(key, rowKey)) {
                        break;
                    }

                    final long seqNo = extractSequenceNumber(key, rowKey.limit());

                    // See if the value is the same as ours
                    if (matchPredicate.test(kv)) {
                        // Found our value, job done
                        LOGGER.debug("Found row with cursor {}", kv.val());
                        matchConsumer.accept(new Match(key, null));
                        return;
                    } else {
                        LOGGER.debug(() -> LogUtil.message("Same hash different value, sequenceNo: {}, key {}, val {}",
                                seqNo,
                                ByteBufferUtils.byteBufferInfo(kv.key()),
                                ByteBufferUtils.byteBufferInfo(kv.val())));
                    }

                    // Remember the last sequence number.
                    if (lastSeqNo == null) {
                        lastSeqNo = seqNo;
                    } else if (seqNo > lastSeqNo) {
                        // See if we have found a possible insert position.
                        if (nextNo == null && lastSeqNo + 1 < seqNo) {
                            nextNo = lastSeqNo + 1;
                        }
                        lastSeqNo = seqNo;
                    }
                }

                if (lastSeqNo == null) {
                    nextNo = 1L;
                } else if (nextNo == null) {
                    nextNo = lastSeqNo + 1;
                }

                matchConsumer.accept(new Match(null, nextNo));
            }
        }
    }

    public void delete(final Dbi<ByteBuffer> dbi,
                       final Txn<ByteBuffer> writeTxn,
                       final ByteBuffer rowKey,
                       final Predicate<ByteBuffer> valueMatchPredicate) {
        // Iterate forward from the key onwards until we find a match to delete.
        final KeyRange<ByteBuffer> keyRange = new KeyRange<>(KeyRangeType.FORWARD_AT_LEAST, rowKey, rowKey);
        // Iterate over all entries with the same hash. Will only be one unless
        // we get a hash clash. Have to use a cursor as entries can be deleted, thus leaving
        // gaps in the seq numbers.
        try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(writeTxn, keyRange)) {
            final Iterator<KeyVal<ByteBuffer>> iterator = cursorIterable.iterator();
            while (iterator.hasNext()) {
                final KeyVal<ByteBuffer> cursorKeyVal = iterator.next();
                final ByteBuffer key = cursorKeyVal.key();

                // Stop iterating if we go beyond the prefix.
                if (!ByteBufferUtils.containsPrefix(key, rowKey)) {
                    break;
                }

                // See if the value is the same as ours
                if (valueMatchPredicate.test(cursorKeyVal.val())) {
                    // Found our value, delete it
                    LOGGER.debug("Deleted via iterator {}", cursorKeyVal);
                    iterator.remove();
                    return;
                }
            }
        }
    }

    /**
     * Extracts the sequence number value from the keyBuffer.
     * Absolute, no flip required
     */
    public long extractSequenceNumber(final ByteBuffer keyBuffer, final int offset) {
        final int len = keyBuffer.limit() - offset;
        if (len == 0) {
            return 0;
        }
        final UnsignedBytes unsignedBytes = UnsignedBytesInstances.ofLength(len);
        return unsignedBytes.get(keyBuffer, offset);
    }

    /**
     * Updates the keyBuffer with the provided sequence number value.
     * Absolute, no flip required.
     */
    public void addSequenceNumber(final ByteBuffer keyBuffer,
                                  final int offset,
                                  final long sequenceNumber,
                                  final Consumer<ByteBuffer> keyBufferConsumer) {
        final UnsignedBytes unsignedBytes = UnsignedBytesInstances.forValue(sequenceNumber);

        // See if the key byte buffer is big enough to add the sequence number.
        if (keyBuffer.capacity() - keyBuffer.limit() >= unsignedBytes.length()) {
            keyBuffer.limit(offset + unsignedBytes.length());
            unsignedBytes.put(keyBuffer, offset, sequenceNumber);
            keyBufferConsumer.accept(keyBuffer);

        } else {
            // We need to make a bigger buffer to store the sequence number.
            final ByteBuffer newBuffer = byteBufferFactory
                    .acquire(offset + unsignedBytes.length());
            try {
                newBuffer.put(keyBuffer);
                unsignedBytes.put(newBuffer, sequenceNumber);
                newBuffer.flip();
                keyBufferConsumer.accept(newBuffer);

            } finally {
                byteBufferFactory.release(newBuffer);
            }
        }
    }

    public record Match(ByteBuffer foundKey, Long nextSequenceNumber) {

    }
}
