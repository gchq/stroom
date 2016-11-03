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

import java.util.Comparator;

import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.shared.OrderBy;
import stroom.util.shared.CompareUtil;

public class FindPoolInfoCriteria extends FindNamedEntityCriteria implements Comparator<PoolInfo> {
    private static final long serialVersionUID = 2756271393367666136L;

    public static final OrderBy ORDER_BY_LAST_ACCESS = new OrderBy("Last Access");
    public static final OrderBy ORDER_BY_IN_USE = new OrderBy("In Use");
    public static final OrderBy ORDER_BY_IN_POOL = new OrderBy("In Pool");
    public static final OrderBy ORDER_BY_IDLE_TIME = new OrderBy("Idle Time (s)");
    public static final OrderBy ORDER_BY_LIVE_TIME = new OrderBy("Live Time (s)");

    @Override
    public int compare(final PoolInfo o1, final PoolInfo o2) {
        int compare = 0;
        if (getOrderBy() != null) {
            if (ORDER_BY_NAME.equals(getOrderBy())) {
                compare = CompareUtil.compareString(o1.getName(), o2.getName());
            } else if (ORDER_BY_LAST_ACCESS.equals(getOrderBy())) {
                compare = CompareUtil.compareLong(o1.getLastAccessTime(), o2.getLastAccessTime());
            } else if (ORDER_BY_IN_USE.equals(getOrderBy())) {
                compare = CompareUtil.compareInteger(o1.getInUse(), o2.getInUse());
            } else if (ORDER_BY_IN_POOL.equals(getOrderBy())) {
                compare = CompareUtil.compareInteger(o1.getInPool(), o2.getInPool());
            } else if (ORDER_BY_IDLE_TIME.equals(getOrderBy())) {
                compare = CompareUtil.compareLong(o1.getTimeToIdleMs(), o2.getTimeToIdleMs());
            } else if (ORDER_BY_LIVE_TIME.equals(getOrderBy())) {
                compare = CompareUtil.compareLong(o1.getTimeToLiveMs(), o2.getTimeToLiveMs());
            }
        }

        if (getOrderByDirection() == OrderByDirection.DESCENDING) {
            compare = compare * -1;
        }
        return compare;
    }
}
