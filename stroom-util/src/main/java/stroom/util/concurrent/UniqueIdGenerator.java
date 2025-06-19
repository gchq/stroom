package stroom.util.concurrent;

import stroom.util.concurrent.UniqueId.NodeType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

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

    static final int MAX_SEQUENCE_NO = 9_999;
    static final String NODE_ID_BASE_REGEX = "[A-Za-z0-9-]+";
    private static final Pattern NODE_ID_PATTERN = Pattern.compile("^" + NODE_ID_BASE_REGEX + "$");

    private final NodeType nodeType;
    private final String nodeId;

    private final AtomicReference<State> stateRef = new AtomicReference<>(State.ofNow());

    public UniqueIdGenerator(final NodeType nodeType, final String nodeId) {
        Objects.requireNonNull(nodeId);
        if (!NODE_ID_PATTERN.matcher(nodeId).matches()) {
            throw new IllegalArgumentException(LogUtil.message(
                    "nodeId '{}' must match the pattern {}", nodeId, NODE_ID_PATTERN));
        }
        this.nodeType = nodeType;
        this.nodeId = nodeId;
    }

    /**
     * Each call will generate a new globally unique ID.
     */
    public UniqueId generateId() {
        final State state = stateRef.accumulateAndGet(State.ofNow(), (currState, newState) -> {
            return newState.epochMs > currState.epochMs
                    ? newState
                    : currState.withNextSequenceNo();
        });
        return new UniqueId(state.epochMs, state.sequenceNo, nodeType, nodeId);
    }


    // --------------------------------------------------------------------------------


    private record State(long epochMs,
                         int sequenceNo) {

        private static final int INITIAL_SEQUENCE_NO = 0;
        private static final long PARK_NANOS = 100_000; // 0.1 millis

        public static State ofNow() {
            return new State(System.currentTimeMillis(), INITIAL_SEQUENCE_NO);
        }

        public static State ofNewTimestamp(final long epochMs) {
            return new State(epochMs, INITIAL_SEQUENCE_NO);
        }

        public State withNextSequenceNo() {
            if (sequenceNo < MAX_SEQUENCE_NO) {
                return new State(epochMs, sequenceNo + 1);
            } else {
                // Overflowed sequenceNo so wait for the next epochMs to roll round.
                // Very unlikely for us to need more than 10k IDs in a millisecond on
                // one node.
                long newEpochMs = System.currentTimeMillis();

                LOGGER.debug("About to loop, epochMs {}, newEpochMs {}", epochMs, newEpochMs);
                while (newEpochMs <= epochMs) {
                    LockSupport.parkNanos(PARK_NANOS);
                    newEpochMs = System.currentTimeMillis();
                }
                return State.ofNewTimestamp(newEpochMs);
            }
        }
    }
}
