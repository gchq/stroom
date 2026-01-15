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

import java.util.Collections;
import java.util.Set;

public class FindEventCriteria {

    private static final long serialVersionUID = -3542902750151448776L;

    private final Period period;
    private final String statisticName;
    private final FilterTermsTree filterTermsTree;
    private final Set<String> rolledUpFieldNames;

    private FindEventCriteria(final Period period, final String statisticName, final FilterTermsTree filterTermsTree,
                              final Set<String> rolledUpFieldNames) {
        this.period = period;
        this.statisticName = statisticName;
        this.filterTermsTree = filterTermsTree;
        this.rolledUpFieldNames = rolledUpFieldNames;
    }

    public static FindEventCriteria instance(final Period period, final String statisticName,
                                             final FilterTermsTree filterTermsTree) {
        return new FindEventCriteria(period, statisticName, filterTermsTree, Collections.emptySet());
    }

    public static FindEventCriteria instance(final Period period, final String statisticName) {
        return new FindEventCriteria(period, statisticName, FilterTermsTree.emptyTree(),
                Collections.emptySet());
    }

    public static FindEventCriteria instance(final Period period,
                                             final String statisticName,
                                             final FilterTermsTree filterTermsTree,
                                             final Set<String> rolledUpFieldNames) {
        return new FindEventCriteria(period, statisticName, filterTermsTree, rolledUpFieldNames);
    }

    public static FindEventCriteria instance(final Period period, final String statisticName,
                                             final Set<String> rolledUpFieldNames) {
        return new FindEventCriteria(period, statisticName, FilterTermsTree.emptyTree(), rolledUpFieldNames);
    }

    public Period getPeriod() {
        return period;
    }

    public String getStatisticName() {
        return statisticName;
    }

    /**
     * @return A list names of fields that have a roll up operation applied to
     * them
     */
    public Set<String> getRolledUpFieldNames() {
        return rolledUpFieldNames;
    }

    /**
     * Return the {@link FilterTermsTree} object on this criteria
     *
     * @return The filter tree, may be null if not filter is defined.
     */
    public FilterTermsTree getFilterTermsTree() {
        return this.filterTermsTree;
    }

    @Override
    public String toString() {
        return "FindEventCriteria [period=" + period + ", statisticName=" + statisticName + ", filterTermsTree="
                + filterTermsTree + ", rolledUpFieldNames=" + rolledUpFieldNames + "]";
    }
}
