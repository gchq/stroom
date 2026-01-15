/*
 * Copyright 2016-2025 Crown Copyright
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
 */

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
 * <p>
 * A class for generating globally unique IDs that can be used on multiple nodes/threads.
 * This is stateful so the node should hold a singleton instance of this class.
 * It uses non-blocking CAS logic to avoid contention between threads generating IDs.
 * </p>
 * <p>
 * IDs are globally unique IF the following conditions are met:
 * <ul>
 *     <li>Each node only has one instance of this class</li>
 *     <li>Each node provides a nodeId that is unique across all nodes of that nodeType
 *     in the environment that the {@link UniqueId}s will be used.</li>
 * </ul>
 * </p>
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

                // With 24 threads generating a total of 2.5mil UniqueIds, you see this message
                // <10 times, often not at all.
                LOGGER.debug("Run out of sequence numbers, waiting for the next epoch milli so " +
                             "we can restart at seq no zero. " +
                             "epochMs: {}, newEpochMs: {}", epochMs, newEpochMs);
                while (newEpochMs <= epochMs) {
                    LockSupport.parkNanos(PARK_NANOS);
                    newEpochMs = System.currentTimeMillis();
                }
                return State.ofNewTimestamp(newEpochMs);
            }
        }
    }
}
