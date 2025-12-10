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

package stroom.statistics.impl.sql.search;

import stroom.util.Period;

public class FindAnalyticOutputCriteria {
    private final Period period;
    private final FilterTermsTree filterTermsTree;
    private final Integer rowNumber;

    private FindAnalyticOutputCriteria(final Period period, final FilterTermsTree filterTermsTree,
                                       final Integer rowNumber) {
        this.period = period;
        this.filterTermsTree = filterTermsTree;
        this.rowNumber = rowNumber;
    }

    public static Builder getBuilder() {
        return new Builder();
    }

    public Period getPeriod() {
        return period;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public FilterTermsTree getFilterTermsTree() {
        return filterTermsTree;
    }

    @Override
    public String toString() {
        return "FindAnalyticOutputCriteria [period=" + period + ", filterTermsTree=" + filterTermsTree + ", rowNumber="
                + rowNumber + "]";
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {
        private Period period;
        private Integer rowNumber;
        private FilterTermsTree filterTermsTree;

        private Builder() {
        }

        private Builder(final FindAnalyticOutputCriteria findAnalyticOutputCriteria) {
            period = findAnalyticOutputCriteria.period;
            rowNumber = findAnalyticOutputCriteria.rowNumber;
            filterTermsTree = findAnalyticOutputCriteria.filterTermsTree;
        }

        public Builder setPeriod(final Period period) {
            this.period = period;
            return this;
        }

        public Builder setFilterTermsTree(final FilterTermsTree filterTermsTree) {
            this.filterTermsTree = filterTermsTree;
            return this;
        }

        public Builder setRowNumber(final Integer rowNumber) {
            this.rowNumber = rowNumber;
            return this;
        }

        public FindAnalyticOutputCriteria build() {
            if (filterTermsTree == null) {
                filterTermsTree = FilterTermsTree.emptyTree();
            }
            return new FindAnalyticOutputCriteria(period, filterTermsTree, rowNumber);
        }
    }
}
