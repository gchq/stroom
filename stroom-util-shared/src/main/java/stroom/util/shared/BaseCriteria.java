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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Base criteria object used to aid getting pages of data.
 */
@JsonInclude(Include.NON_NULL)
public abstract class BaseCriteria {

    @JsonProperty
    private PageRequest pageRequest;
    @JsonProperty
    private List<CriteriaFieldSort> sortList;

    public BaseCriteria() {
    }

    @JsonCreator
    public BaseCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                        @JsonProperty("sortList") final List<CriteriaFieldSort> sortList) {
        this.pageRequest = pageRequest;
        this.sortList = sortList;
    }

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

    protected Set<Long> clone(final Set<Long> set) {
        if (set == null) {
            return null;
        }
        return new HashSet<>(set);
    }

    protected void copyFrom(final BaseCriteria other) {
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
        }
    }

    public void setSortList(final List<CriteriaFieldSort> sortList) {
        this.sortList = sortList;
    }

    public void setSort(final String field) {
        setSort(new CriteriaFieldSort(field, false, false));
    }

    public void setSort(final String field, final boolean desc, final boolean ignoreCase) {
        setSort(new CriteriaFieldSort(field, desc, ignoreCase));
    }

    public void setSort(final CriteriaFieldSort sort) {
        sortList = null;
        addSort(sort);
    }

    public void addSort(final String field) {
        addSort(new CriteriaFieldSort(field, false, false));
    }

    public void addSort(final String field, final boolean desc, final boolean ignoreCase) {
        addSort(new CriteriaFieldSort(field, desc, ignoreCase));
    }

    public void addSort(final CriteriaFieldSort sort) {
        if (sortList == null) {
            sortList = new ArrayList<>();
        }
        sortList.add(sort);
    }

    public List<CriteriaFieldSort> getSortList() {
        return sortList;
    }

    /**
     * @return True if fieldId is included in the sortList
     */
    public boolean isFieldInSort(final String fieldId) {
        if (sortList != null && NullSafe.isNonBlankString(fieldId)) {
            return sortList.stream()
                    .map(CriteriaFieldSort::getId)
                    .anyMatch(fieldId::equals);
        } else {
            return false;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseCriteria)) {
            return false;
        }
        final BaseCriteria that = (BaseCriteria) o;
        return Objects.equals(pageRequest, that.pageRequest) &&
               Objects.equals(sortList, that.sortList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageRequest, sortList);
    }

    @Override
    public String toString() {
        return "BaseCriteria{" +
               "pageRequest=" + pageRequest +
               ", sortList=" + sortList +
               '}';
    }


    // --------------------------------------------------------------------------------


    public abstract static class BaseCriteriaBuilder<T extends BaseCriteria, B extends BaseCriteriaBuilder<T, B>>
            extends AbstractBuilder<T, B> {

        protected PageRequest pageRequest;
        protected List<CriteriaFieldSort> sortList;

        protected BaseCriteriaBuilder() {

        }

        protected BaseCriteriaBuilder(final T criteria) {
            if (criteria.getPageRequest() != null) {
                pageRequest = new PageRequest(
                        criteria.getPageRequest().getOffset(),
                        criteria.getPageRequest().getLength());
            }
            if (criteria.getSortList() != null) {
                sortList = new ArrayList<>(criteria.getSortList());
            }
        }

        public B pageRequest(final PageRequest pageRequest) {
            this.pageRequest = pageRequest;
            return self();
        }

        public B sortList(final List<CriteriaFieldSort> sortList) {
            this.sortList = sortList;
            return self();
        }
    }
}
