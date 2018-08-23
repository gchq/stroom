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

package stroom.data.meta.impl.db;

import stroom.data.meta.api.DataStatus;

/**
 * <p>
 * The type of lock held on the data. For the moment this is just simple lock
 * and unlocked.
 * </p>
 */
class DataStatusId {
    public static final byte UNLOCKED = 0;
    public static final byte LOCKED = 1;
    public static final byte DELETED = 99;

    static byte getPrimitiveValue(final DataStatus status) {
        switch (status) {
            case UNLOCKED:
                return 0;
            case LOCKED:
                return 1;
            case DELETED:
                return 99;
            default:
                throw new RuntimeException("Unknown status " + status);
        }
    }

    static DataStatus getStatus(byte primitiveValue) {
        switch (primitiveValue) {
            case 0:
                return DataStatus.UNLOCKED;
            case 1:
                return DataStatus.LOCKED;
            case 99:
                return DataStatus.DELETED;
            default:
                throw new RuntimeException("Unknown status " + primitiveValue);
        }
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
