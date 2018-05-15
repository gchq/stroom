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

import stroom.entity.shared.Sort.Direction;
import stroom.util.shared.SharedObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base criteria object used to aid getting pages of data.
 */
public abstract class BaseCriteria implements SharedObject {
    public static final String FIELD_ID = "Id";
    private static final long serialVersionUID = 779306892977183446L;
    private PageRequest pageRequest = null;
    private Set<String> fetchSet = new HashSet<>();
    private List<Sort> sortList;

    public PageRequest getPageRequest() {
        return pageRequest;
    }

    public void setPageRequest(final PageRequest pageRequest) {
        this.pageRequest = pageRequest;
    }

    public PageRequest obtainPageRequest() {
        if (pageRequest == null) {
            pageRequest = new PageRequest();
        }
        return pageRequest;
    }

    protected Set<Long> clone(Set<Long> set) {
        if (set == null) {
            return null;
        }
        return new HashSet<>(set);
    }

    protected void copyFrom(BaseCriteria other) {
        if (other != null) {
            if (other.pageRequest == null) {
                this.pageRequest = null;
            } else {
                this.obtainPageRequest().copyFrom(other.pageRequest);
            }
            if (other.sortList == null) {
                this.sortList = null;
            } else {
                this.sortList = new ArrayList<>(other.sortList);
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

    public void setSort(final String field) {
        setSort(new Sort(field, Direction.ASCENDING, false));
    }

    public void setSort(final String field, final Direction direction, final boolean ignoreCase) {
        setSort(new Sort(field, direction, ignoreCase));
    }

    public void setSort(final Sort sort) {
        sortList = null;
        addSort(sort);
    }

    public void addSort(final String field) {
        addSort(new Sort(field, Direction.ASCENDING, false));
    }

    public void addSort(final String field, final Direction direction, final boolean ignoreCase) {
        addSort(new Sort(field, direction, ignoreCase));
    }

    public void addSort(final Sort sort) {
        if (sortList == null) {
            sortList = new ArrayList<>();
        }
        sortList.add(sort);
    }

    public void removeSorts() {
        sortList.clear();
    }

    public List<Sort> getSortList() {
        return sortList;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseCriteria)) return false;

        final BaseCriteria that = (BaseCriteria) o;

        if (pageRequest != null ? !pageRequest.equals(that.pageRequest) : that.pageRequest != null) return false;
        if (fetchSet != null ? !fetchSet.equals(that.fetchSet) : that.fetchSet != null) return false;
        return sortList != null ? sortList.equals(that.sortList) : that.sortList == null;
    }

//    /**
//     * TEMPORARY TO MAINTAIN COMPATIBILITY
//     */
//    public OrderBy getOrderBy() {
//        final Sort orderBySetting = getFirstOrderBy();
//        if (orderBySetting == null) {
//            return null;
//        }
//        return orderBySetting.getOrderBy();
//    }
//
//    public OrderByDirection getOrderByDirection() {
//        final Sort orderBySetting = getFirstOrderBy();
//        if (orderBySetting == null) {
//            return Direction.ASCENDING;
//        }
//        return orderBySetting.getDirection();
//    }
//
//    private Sort getFirstOrderBy() {
//        if (sortList == null || sortList.size() == 0) {
//            return null;
//        }
//        return sortList.get(0);
//    }

    @Override
    public int hashCode() {
        int result = pageRequest != null ? pageRequest.hashCode() : 0;
        result = 31 * result + (fetchSet != null ? fetchSet.hashCode() : 0);
        result = 31 * result + (sortList != null ? sortList.hashCode() : 0);
        return result;
    }
}
