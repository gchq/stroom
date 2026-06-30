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

package stroom.meta.impl.db;

import stroom.meta.shared.Status;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>
 * The type of lock held on the data. For the moment this is just simple lock
 * and unlocked.
 * </p>
 */
class MetaStatusId {

    public static final byte UNLOCKED = 0;
    public static final byte LOCKED = 1;
    public static final byte DELETED = 99;

    static byte getPrimitiveValue(final Status status) {
        return switch (status) {
            case UNLOCKED -> 0;
            case LOCKED -> 1;
            case DELETED -> 99;
        };
    }

    static byte getPrimitiveValue(final String statusName) {
        if (NullSafe.isBlankString(statusName)) {
            throw new IllegalArgumentException("No status name supplied");
        }
        try {
            final Status status = Status.valueOf(statusName.toUpperCase());
            return getPrimitiveValue(status);
        } catch (final IllegalArgumentException e) {
            throw new RuntimeException(LogUtil.message("'{}' is not a valid status value. Valid values are: '{}'",
                    statusName,
                    Arrays.stream(Status.values())
                            .map(Objects::toString)
                            .collect(Collectors.joining(", "))), e);
        }
    }

    static Status getStatus(final byte primitiveValue) {
        return switch (primitiveValue) {
            case 0 -> Status.UNLOCKED;
            case 1 -> Status.LOCKED;
            case 99 -> Status.DELETED;
            default -> throw new RuntimeException("Unknown status primitiveValue " + primitiveValue);
        };
    }

//    public static CriteriaSet<HasPrimitiveValue> convertStatusSet(final CriteriaSet<StreamStatus> statuses) {
//        if (statuses == null) {
//            return null;
//        }
//
//        final CriteriaSet<HasPrimitiveValue> criteriaSet = new CriteriaSet<>();
//        criteriaSet.setMatchAll(statuses.getMatchAll());
//        criteriaSet.setMatchNull(statuses.getMatchNull());
//        for (final StreamStatus status : statuses) {
//            final byte primitiveValue = StreamStatusId.getPrimitiveValue(status);
//            final HasPrimitiveValue hasPrimitiveValue = () -> primitiveValue;
//            criteriaSet.add(hasPrimitiveValue);
//        }
//        return criteriaSet;
//    }
}
