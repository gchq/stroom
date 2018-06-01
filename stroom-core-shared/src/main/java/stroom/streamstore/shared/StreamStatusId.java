/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamstore.shared;

import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.HasPrimitiveValue;

/**
 * <p>
 * The type of lock held on the stream. For the moment this is just simple lock
 * and unlocked.
 * </p>
 */
public class StreamStatusId {
    public static final byte UNLOCKED = 0;
    public static final byte LOCKED = 1;
    public static final byte DELETED = 99;

    public static byte getPrimitiveValue(final StreamStatus streamStatus) {
        switch (streamStatus) {
            case UNLOCKED:
                return 0;
            case LOCKED:
                return 1;
            case DELETED:
                return 99;
                default:
                    throw new RuntimeException("Unknown stream status " + streamStatus);
        }
    }

    public static StreamStatus getStreamStatus(byte primitiveValue) {
        switch (primitiveValue) {
            case 0:
                return StreamStatus.UNLOCKED;
            case 1:
                return StreamStatus.LOCKED;
            case 9:
                return StreamStatus.DELETED;
            default:
                throw new RuntimeException("Unknown stream status " + primitiveValue);
        }
    }

    public static CriteriaSet<HasPrimitiveValue> convertStatusSet(final CriteriaSet<StreamStatus> streamStatuses) {
        if (streamStatuses == null) {
            return null;
        }

        final CriteriaSet<HasPrimitiveValue> criteriaSet = new CriteriaSet<>();
        criteriaSet.setMatchAll(streamStatuses.getMatchAll());
        criteriaSet.setMatchNull(streamStatuses.getMatchNull());
        for (final StreamStatus streamStatus : streamStatuses) {
            final byte primitiveValue = StreamStatusId.getPrimitiveValue(streamStatus);
            final HasPrimitiveValue hasPrimitiveValue = () -> primitiveValue;
            criteriaSet.add(hasPrimitiveValue);
        }
        return criteriaSet;
    }
}
