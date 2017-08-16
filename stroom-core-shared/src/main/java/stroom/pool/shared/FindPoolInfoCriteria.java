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

package stroom.pool.shared;

import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.Sort;
import stroom.entity.shared.Sort.Direction;
import stroom.util.shared.CompareUtil;

import java.util.Comparator;

public class FindPoolInfoCriteria extends FindNamedEntityCriteria implements Comparator<PoolInfo> {
    public static final String FIELD_LAST_ACCESS = "Last Access";
    public static final String FIELD_IN_USE = "In Use";
    public static final String FIELD_IN_POOL = "In Pool";
    public static final String FIELD_IDLE_TIME = "Idle Time (s)";
    public static final String FIELD_LIVE_TIME = "Live Time (s)";
    private static final long serialVersionUID = 2756271393367666136L;

    @Override
    public int compare(final PoolInfo o1, final PoolInfo o2) {
        if (getSortList() != null) {
            for (final Sort sort : getSortList()) {
                final String field = sort.getField();

                int compare = 0;
                if (FIELD_NAME.equals(field)) {
                    compare = CompareUtil.compareString(o1.getName(), o2.getName());
                } else if (FIELD_LAST_ACCESS.equals(field)) {
                    compare = CompareUtil.compareLong(o1.getLastAccessTime(), o2.getLastAccessTime());
                } else if (FIELD_IN_USE.equals(field)) {
                    compare = CompareUtil.compareInteger(o1.getInUse(), o2.getInUse());
                } else if (FIELD_IN_POOL.equals(field)) {
                    compare = CompareUtil.compareInteger(o1.getInPool(), o2.getInPool());
                } else if (FIELD_IDLE_TIME.equals(field)) {
                    compare = CompareUtil.compareLong(o1.getTimeToIdleMs(), o2.getTimeToIdleMs());
                } else if (FIELD_LIVE_TIME.equals(field)) {
                    compare = CompareUtil.compareLong(o1.getTimeToLiveMs(), o2.getTimeToLiveMs());
                }
                if (Direction.DESCENDING.equals(sort.getDirection())) {
                    compare = compare * -1;
                }

                if (compare != 0) {
                    return compare;
                }
            }
        }

        return 0;
    }
}
