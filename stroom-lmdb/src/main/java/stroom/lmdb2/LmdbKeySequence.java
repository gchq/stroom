package stroom.lmdb2;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.lmdbjava.Dbi;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;

public class LmdbKeySequence {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbKeySequence.class);

    private final ByteBuffers byteBuffers;

    public LmdbKeySequence(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    public <R> R find(final Dbi<ByteBuffer> dbi,
                      final Txn<ByteBuffer> writeTxn,
                      final ByteBuffer rowKey,
                      final ByteBuffer rowValue,
                      final Predicate<ByteBuffer> matchPredicate,
                      final Function<Match, R> matchConsumer) {
        long nextNo = 0;

        // Just try to find without a cursor.
        final ByteBuffer valueBuffer = dbi.get(writeTxn, rowKey);
        if (valueBuffer != null) {
            nextNo = 1;
            if (valueBuffer.equals(rowValue)) {
                // Found our value, job done
                LOGGER.debug("Found row directly {}", rowValue);
                return matchConsumer.apply(new Match(rowKey, null));
            }
        }

        // Look forward from the provided row key across all subsequent sequence numbers.
        // Iterate over all entries with the same hash. Will only be one unless
        // we get a hash clash. Have to use a cursor as entries can be deleted, thus leaving
        // gaps in the seq numbers.
        final LmdbKeyRange keyRange = LmdbKeyRange.builder().start(rowKey).build();
        try (final LmdbIterable iterable = LmdbIterable.create(writeTxn, dbi, keyRange)) {
            for (final LmdbEntry entry : iterable) {
                final ByteBuffer key = entry.getKey();
                final ByteBuffer val = entry.getVal();

                // Stop iterating if we go beyond the prefix.
                if (!ByteBufferUtils.containsPrefix(key, rowKey)) {
                    break;
                }

                // See if the value is the same as ours
                if (val.equals(rowValue)) {
                    // Found our value, job done
                    LOGGER.debug("Found row with cursor {}", val);
                    return matchConsumer.apply(new Match(key, null));
                }

                final long seqNo = extractSequenceNumber(key, rowKey.limit());
                LOGGER.debug(() -> LogUtil.message("Same hash different value, sequenceNo: {}, key {}, val {}",
                        seqNo,
                        ByteBufferUtils.byteBufferInfo(key),
                        ByteBufferUtils.byteBufferInfo(val)));

                // Figure out the next sequence number.
                if (seqNo >= nextNo) {
                    nextNo = seqNo + 1;
                }
            }

            return matchConsumer.apply(new Match(null, nextNo));
        }
    }

    public void delete(final Dbi<ByteBuffer> dbi,
                       final Txn<ByteBuffer> writeTxn,
                       final ByteBuffer rowKey,
                       final Predicate<ByteBuffer> valueMatchPredicate) {
        // Iterate forward from the key onwards until we find a match to delete.
        final LmdbKeyRange keyRange = LmdbKeyRange.builder().start(rowKey).build();
        try (final LmdbIterable iterable = LmdbIterable.create(writeTxn, dbi, keyRange)) {
            // Iterate over all entries with the same hash. Will only be one unless
            // we get a hash clash. Have to use a cursor as entries can be deleted, thus leaving
            // gaps in the seq numbers.
            final Iterator<LmdbEntry> iterator = iterable.iterator();
            while (iterator.hasNext()) {
                final LmdbEntry cursorKeyVal = iterator.next();
                final ByteBuffer key = cursorKeyVal.getKey();

                // Stop iterating if we go beyond the prefix.
                if (!ByteBufferUtils.containsPrefix(key, rowKey)) {
                    break;
                }

                // See if the value is the same as ours
                if (valueMatchPredicate.test(cursorKeyVal.getVal())) {
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
    public <R> R addSequenceNumber(final ByteBuffer keyBuffer,
                                   final int offset,
                                   final long sequenceNumber,
                                   final Function<ByteBuffer, R> keyBufferConsumer) {
        final UnsignedBytes unsignedBytes = UnsignedBytesInstances.forValue(sequenceNumber);

        // See if the key byte buffer is big enough to add the sequence number.
        if (keyBuffer.capacity() - keyBuffer.limit() >= unsignedBytes.length()) {
            keyBuffer.limit(offset + unsignedBytes.length());
            unsignedBytes.put(keyBuffer, offset, sequenceNumber);
            return keyBufferConsumer.apply(keyBuffer);

        } else {
            // We need to make a bigger buffer to store the sequence number.
            return byteBuffers.use(offset + unsignedBytes.length(), newBuffer -> {
                newBuffer.put(keyBuffer);
                unsignedBytes.put(newBuffer, sequenceNumber);
                newBuffer.flip();
                return keyBufferConsumer.apply(newBuffer);
            });
        }
    }

    public record Match(ByteBuffer foundKey, Long nextSequenceNumber) {

    }
}
