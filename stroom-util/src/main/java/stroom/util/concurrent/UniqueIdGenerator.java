package stroom.util.concurrent;

import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.common.base.Strings;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.regex.Pattern;

/**
 * A class for generating globally unique IDs that can be used on multiple nodes/threads.
 * This is stateful so the node should hold a singleton instance of this class.
 * IDs are globally unique on the conditions that each node only has one instance of this
 * class and each node provides a unique nodeId.
 */
public class UniqueIdGenerator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UniqueIdGenerator.class);

    private static final String NODE_ID_BASE_REGEX = "[A-Za-z0-9][A-Za-z0-9-]+";
    private static final Pattern NODE_ID_PATTERN = Pattern.compile("^" + NODE_ID_BASE_REGEX + "$");

    private static final short MAX_SEQUENCE_NO = 9_999;

    private final String nodeId;

    private final AtomicReference<State> stateRef = new AtomicReference<>(State.ofNow());

    public UniqueIdGenerator(final String nodeId) {
        Objects.requireNonNull(nodeId);
        if (!NODE_ID_PATTERN.matcher(nodeId).matches()) {
            throw new IllegalArgumentException(LogUtil.message(
                    "nodeId '{}' must match the pattern {}", nodeId, NODE_ID_PATTERN));
        }
        this.nodeId = nodeId;
    }

    /**
     * Each call will generate a new globally unique ID.
     */
    public UniqueId generateId() {
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

        public static final String UNIQUE_ID_DELIMITER = "_";
        public static final Pattern UNIQUE_ID_DELIMITER_PATTERN = Pattern.compile("_");

        private static final int EPOCH_MS_DIGITS = String.valueOf(Long.MAX_VALUE).length();
        private static final int SEQUENCE_NO_DIGITS = String.valueOf(MAX_SEQUENCE_NO).length();
        private static final String ZERO_SEQUENCE_NO = Strings.padStart(
                "0", SEQUENCE_NO_DIGITS, '0');

        public static final Pattern UNIQUE_ID_PATTERN = Pattern.compile(
                "^"
                + "\\d{" + EPOCH_MS_DIGITS + "}"
                + UNIQUE_ID_DELIMITER
                + "\\d{" + SEQUENCE_NO_DIGITS + "}"
                + UNIQUE_ID_DELIMITER
                + NODE_ID_BASE_REGEX
                + "$");

        /**
         * Parse a {@link UniqueId} from a string.
         */
        public static UniqueId parse(final String uniqueIdStr) {
            final String trimmed = NullSafe.trim(uniqueIdStr);
            if (NullSafe.isEmptyString(trimmed)) {
                return null;
            } else {
                if (!uniqueIdStr.contains(UNIQUE_ID_DELIMITER)) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "Invalid uniqueIdStr '{}', no '{}' found",
                            uniqueIdStr, UNIQUE_ID_DELIMITER));
                }
                final String[] parts = UNIQUE_ID_DELIMITER_PATTERN.split(trimmed);
                if (parts.length != 3) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "Invalid uniqueIdStr '{}', expecting three parts when splitting on '{}'",
                            trimmed, UNIQUE_ID_DELIMITER));
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
            // Minor optimisation as 0 will be used a lot, so have a hard coded zero string
            final String sequenceNoStr = sequenceNo == 0
                    ? ZERO_SEQUENCE_NO
                    : Strings.padStart(String.valueOf(sequenceNo), SEQUENCE_NO_DIGITS, '0');

            return Strings.padStart(String.valueOf(epochMs), EPOCH_MS_DIGITS, '0')
                   + UNIQUE_ID_DELIMITER
                   + sequenceNoStr
                   + UNIQUE_ID_DELIMITER
                   + nodeId;
        }

        /**
         * @return The time in millis since epoch that the ID was generated.
         */
        @Override
        public long epochMs() {
            return epochMs;
        }

        /**
         * @return A Sequence number that is used to differentiate {@link UniqueId}s that
         * are generated during the same millisecond.
         */
        @Override
        public short sequenceNo() {
            return sequenceNo;
        }

        /**
         * @return The name/identifier for the node instance, e.g. in a cluster,
         * each node generating {@link UniqueId}s must have a unique node ID.
         */
        @Override
        public String nodeId() {
            return nodeId;
        }
    }
}
