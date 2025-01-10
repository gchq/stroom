package stroom.proxy.app.handler;

import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;

/**
 * A class for generating globally unique IDs that can be used on multiple nodes/threads.
 * This is stateful so the node should hold a singleton instance of this class.
 */
public class UniqueIdGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UniqueIdGenerator.class);

    private static final short MAX_SEQUENCE_NO = 9_999;

    private final String nodeId;

    private final AtomicReference<State> stateRef = new AtomicReference<>(State.ofNow());

    public UniqueIdGenerator(final String nodeId) {
        this.nodeId = nodeId;
    }

    public UniqueId nextId() {
        State state = State.ofNow();
        state = stateRef.accumulateAndGet(state, (currState, newState) -> {
            final long currEpochMs = currState.epochMs;
            final long newEpochMs = newState.epochMs;
            return newEpochMs > currEpochMs
                    ? newState
                    : currState.withNextSequenceNo();
        });
        return new UniqueId(state.epochMs, state.sequenceNo, nodeId);
    }


    // --------------------------------------------------------------------------------


    private record State(long epochMs,
                         short sequenceNo) {

        private static final short INITIAL_SEQUENCE_NO = (short) 0;
        private static final long PARK_NANOS = 50 * 1_000_000; // 100millis

        public static State ofNow() {
            return new State(System.currentTimeMillis(), INITIAL_SEQUENCE_NO);
        }

        public static State ofNewTimestamp(long epochMs) {
            return new State(epochMs, INITIAL_SEQUENCE_NO);
        }

        public State withNextSequenceNo() {
            int newSequenceNo = sequenceNo + 1;
            if (newSequenceNo <= MAX_SEQUENCE_NO) {
                return new State(epochMs, (short) (sequenceNo + 1));
            } else {
                // Overflowed sequenceNo so wait for the next epochMs to roll round.
                // very unlikely for us to need more than 10k IDs in a millisecond on
                // one node.
                long newEpochMs = System.currentTimeMillis();

                LOGGER.info("About to loop, epochMs {}, newEpochMs {}", epochMs, newEpochMs);
                while (newEpochMs <= epochMs) {
                    LockSupport.parkNanos(PARK_NANOS);
                    newEpochMs = System.currentTimeMillis();
                }
                return State.ofNewTimestamp(newEpochMs);
            }
        }
    }


    // --------------------------------------------------------------------------------


    public record UniqueId(
            long epochMs,
            short sequenceNo,
            String nodeId) {

        public static final String RECEIPT_ID_DELIMITER = "_";
        public static final Pattern RECEIPT_ID_DELIMITER_PATTERN = Pattern.compile("_");
        private static final int MAX_EPOCH_MS_DIGITS = String.valueOf(Long.MAX_VALUE).length();
        private static final int MAX_SEQUENCE_NO_DIGITS = String.valueOf(MAX_SEQUENCE_NO).length();

        /**
         * Parse a {@link ReceiptId} from a string.
         */
        public static UniqueId parse(final String receiptIdStr) {
            final String trimmed = NullSafe.trim(receiptIdStr);
            if (NullSafe.isEmptyString(trimmed)) {
                return null;
            } else {
                if (!receiptIdStr.contains(RECEIPT_ID_DELIMITER)) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "Invalid receiptIdStr '{}', no '{}' found",
                            receiptIdStr, RECEIPT_ID_DELIMITER));
                }
                final String[] parts = RECEIPT_ID_DELIMITER_PATTERN.split(trimmed);
                if (parts.length != 3) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "Invalid receiptIdStr '{}', expecting three parts when splitting on '{}'",
                            trimmed, RECEIPT_ID_DELIMITER));
                }
                final long epochMs = Long.parseLong(parts[0]);
                final short sequenceNo = Short.parseShort(parts[1]);
                final String nodeId = parts[2];
                return new UniqueId(epochMs, sequenceNo, nodeId);
            }
        }

        @Override
        public String toString() {
            return toString(epochMs, sequenceNo, nodeId);
        }

        public String toString(long epochMs,
                               short sequenceNo,
                               String nodeId) {
            return Strings.padStart(String.valueOf(epochMs), MAX_EPOCH_MS_DIGITS, '0')
                   + RECEIPT_ID_DELIMITER
                   + Strings.padStart(String.valueOf(sequenceNo), MAX_SEQUENCE_NO_DIGITS, '0')
                   + RECEIPT_ID_DELIMITER
                   + nodeId;
        }
    }
}
