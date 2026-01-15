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

import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.common.base.Strings;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class UniqueId {

    public static final String UNIQUE_ID_DELIMITER = "_";
    public static final Pattern UNIQUE_ID_DELIMITER_PATTERN = Pattern.compile("_");

    // 13 digits is enough to take us to the year 2286, so plenty.
    private static final int EPOCH_MS_DIGITS = 13;
    private static final int SEQUENCE_NO_DIGITS = String.valueOf(UniqueIdGenerator.MAX_SEQUENCE_NO).length();

    // Cache 0-9 inc. in padded form as a minor optimisation
    private static final String[] CACHED_SEQUENCE_NO_STRINGS = IntStream.range(0, 10)
            .boxed()
            .map(i -> Strings.padStart(String.valueOf(i), SEQUENCE_NO_DIGITS, '0'))
            .toArray(String[]::new);

    public static final Pattern UNIQUE_ID_PATTERN = Pattern.compile(
            "^"
            + "\\d{" + EPOCH_MS_DIGITS + "}"
            + UNIQUE_ID_DELIMITER
            + "\\d{" + SEQUENCE_NO_DIGITS + "}"
            + UNIQUE_ID_DELIMITER
            + "[A-Z]"
            + UNIQUE_ID_DELIMITER
            + UniqueIdGenerator.NODE_ID_BASE_REGEX
            + "$");

    private final long epochMs;
    private final int sequenceNo;
    private final NodeType nodeType;
    private final String nodeId;

    public UniqueId(final long epochMs,
                    final int sequenceNo,
                    final NodeType nodeType,
                    final String nodeId) {

        if (sequenceNo > UniqueIdGenerator.MAX_SEQUENCE_NO) {
            throw new IllegalArgumentException(
                    "sequenceNo cannot be greater than " + UniqueIdGenerator.MAX_SEQUENCE_NO);
        }
        if (NullSafe.isBlankString(nodeId)) {
            throw new IllegalArgumentException("nodeId cannot be blank");
        }

        this.epochMs = epochMs;
        this.sequenceNo = sequenceNo;
        this.nodeType = Objects.requireNonNull(nodeType);
        this.nodeId = nodeId;
    }

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
            if (parts.length != 4) {
                throw new IllegalArgumentException(LogUtil.message(
                        "Invalid uniqueIdStr '{}', expecting four parts when splitting on '{}'",
                        trimmed, UNIQUE_ID_DELIMITER));
            }
            final long epochMs = Long.parseLong(parts[0]);
            final int sequenceNo = Integer.parseInt(parts[1]);
            final NodeType nodeType = NodeType.fromString(parts[2]);
            final String nodeId = parts[3];
            return new UniqueId(epochMs, sequenceNo, nodeType, nodeId);
        }
    }

    @Override
    public String toString() {
//        return toString(epochMs, sequenceNo, nodeType, nodeId);
        // Minor optimisation as 0 will be used a lot, so have a hard coded zero string
        final String sequenceNoStr = sequenceNo < 10
                ? CACHED_SEQUENCE_NO_STRINGS[sequenceNo]
                : Strings.padStart(String.valueOf(sequenceNo), SEQUENCE_NO_DIGITS, '0');

        return Strings.padStart(String.valueOf(epochMs), EPOCH_MS_DIGITS, '0')
               + UNIQUE_ID_DELIMITER
               + sequenceNoStr
               + UNIQUE_ID_DELIMITER
               + nodeType.toString()
               + UNIQUE_ID_DELIMITER
               + nodeId;
    }

    /**
     * @return The time in millis since epoch that the ID was generated.
     */
    public long getEpochMs() {
        return epochMs;
    }

    /**
     * @return A Sequence number that is used to differentiate {@link UniqueId}s that
     * are generated during the same millisecond.
     */
    public int getSequenceNo() {
        return sequenceNo;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    /**
     * @return The name/identifier for the node instance, e.g. in a cluster,
     * each node generating {@link UniqueId}s must have a unique node ID.
     */
    public String getNodeId() {
        return nodeId;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final UniqueId that = (UniqueId) obj;
        return this.epochMs == that.epochMs &&
               this.sequenceNo == that.sequenceNo &&
               Objects.equals(this.nodeId, that.nodeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(epochMs, sequenceNo, nodeId);
    }


    // --------------------------------------------------------------------------------


    /**
     * Whether the node is a stroom-proxy or stroom node.
     */
    public enum NodeType {
        PROXY("P"),
        STROOM("S"),
        ;

        private final String displayValue;
        private static final Map<String, NodeType> DISPLAY_VALUE_TO_ENUM_MAP = Arrays.stream(NodeType.values())
                .collect(Collectors.toMap(
                        nodeType -> nodeType.toString().toUpperCase(),
                        Function.identity()));

        NodeType(final String displayValue) {
            this.displayValue = displayValue;
        }

        public static NodeType fromString(final String str) {
            if (NullSafe.isBlankString(str)) {
                throw new IllegalArgumentException("Blank string not a valid UniqueIdType");
            }
            NodeType nodeType = DISPLAY_VALUE_TO_ENUM_MAP.get(str);
            if (nodeType == null) {
                nodeType = DISPLAY_VALUE_TO_ENUM_MAP.get(str.toUpperCase());
            }
            if (nodeType == null) {
                throw new IllegalArgumentException(LogUtil.message(
                        "Unable to parse {} to a UniqueIdType. Valid values are {}", str, NodeType.values()));
            }
            return nodeType;
        }

        @Override
        public String toString() {
            return displayValue;
        }
    }
}
