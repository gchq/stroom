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

package stroom.entity.shared;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import stroom.util.shared.SharedObject;

/**
 * Base criteria object used to aid getting pages of data.
 */
public abstract class BaseCriteria implements SharedObject {
    private static final long serialVersionUID = 779306892977183446L;
    public static final OrderBy ORDER_BY_ID = new OrderBy("Key", "id", BaseEntity.ID);

    /**
     * The direction of the results (based on their order by).
     */
    public enum OrderByDirection {
        ASCENDING, DESCENDING
    }

    public static class OrderBySetting implements Serializable {
        private static final long serialVersionUID = -5994197736743037915L;

        private OrderBy orderBy;
        private OrderByDirection orderByDirection;

        public OrderBySetting() {
            // Default constructor necessary for GWT serialisation.
        }

        public OrderBySetting(final OrderBy orderBy, final OrderByDirection orderByDirection) {
            this.orderBy = orderBy;
            this.orderByDirection = orderByDirection;
        }

        public OrderBy getOrderBy() {
            return orderBy;
        }

        public OrderByDirection getOrderByDirection() {
            return orderByDirection;
        }

        public boolean isAscending() {
            return OrderByDirection.ASCENDING.equals(orderByDirection);
        }
    }

    private PageRequest pageRequest = null;
    private Set<String> fetchSet = new HashSet<String>();

    private List<OrderBySetting> orderByList;

    public boolean isAscending() {
        return OrderByDirection.ASCENDING == getOrderByDirection();
    }

    public PageRequest getPageRequest() {
        return pageRequest;
    }

    public PageRequest obtainPageRequest() {
        if (pageRequest == null) {
            pageRequest = new PageRequest();
        }
        return pageRequest;
    }

    public void setPageRequest(final PageRequest pageRequest) {
        this.pageRequest = pageRequest;
    }

    protected Set<Long> clone(Set<Long> set) {
        if (set == null) {
            return null;
        }
        return new HashSet<Long>(set);
    }

    protected void copyFrom(BaseCriteria other) {
        if (other != null) {
            if (other.pageRequest == null) {
                this.pageRequest = null;
            } else {
                this.obtainPageRequest().copyFrom(other.pageRequest);
            }
            if (other.orderByList == null) {
                this.orderByList = null;
            } else {
                this.orderByList = new ArrayList<OrderBySetting>(other.orderByList);
            }
            this.fetchSet.clear();
            this.fetchSet.addAll(other.fetchSet);
        }
    }

    public Set<String> getFetchSet() {
        return fetchSet;
    }

    public void setFetchSet(Set<String> fetchSet) {
        this.fetchSet = fetchSet;
    }

    public void setOrderBy(final OrderBy orderBy) {
        setOrderBy(orderBy, OrderByDirection.ASCENDING);
    }

    public void setOrderBy(final OrderBy orderBy, final OrderByDirection orderByDirection) {
        orderByList = null;
        addOrderBy(orderBy, orderByDirection);
    }

    public void addOrderBy(final OrderBy orderBy) {
        addOrderBy(orderBy, OrderByDirection.ASCENDING);
    }

    public void addOrderBy(final OrderBy orderBy, final OrderByDirection orderByDirection) {
        if (orderByList == null) {
            orderByList = new ArrayList<BaseCriteria.OrderBySetting>();
        }
        final OrderBySetting orderBySetting = new OrderBySetting(orderBy, orderByDirection);
        orderByList.add(orderBySetting);
    }

    public List<OrderBySetting> getOrderByList() {
        return orderByList;
    }

    public void setOrderByList(List<OrderBySetting> orderByList) {
        this.orderByList = orderByList;
    }

    /**
     * TEMPORARY TO MAINTAIN COMPATIBILITY
     */
    public OrderBy getOrderBy() {
        final OrderBySetting orderBySetting = getFirstOrderBy();
        if (orderBySetting == null) {
            return null;
        }
        return orderBySetting.getOrderBy();
    }

    public OrderByDirection getOrderByDirection() {
        final OrderBySetting orderBySetting = getFirstOrderBy();
        if (orderBySetting == null) {
            return OrderByDirection.ASCENDING;
        }
        return orderBySetting.getOrderByDirection();
    }

    private OrderBySetting getFirstOrderBy() {
        if (orderByList == null || orderByList.size() == 0) {
            return null;
        }
        return orderByList.get(0);
    }
}
