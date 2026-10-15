/*
 * Copyright 2026 Crown Copyright
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

import java.util.List;

/**
 * A criteria carrying the text a user typed into a quick filter.
 * <p>
 * The text travels verbatim and is parsed by the server against the fields that surface declares -
 * see the syntax spec §9 and §11. Before this existed the same idea appeared on a dozen criteria
 * under four different names: {@code filter}, {@code partialName}, {@code nameFilter} and
 * {@code quickFilterInput}, each with its own accessor and its own chance of being forgotten.
 * <p>
 * A criteria that also needs a structured {@code ExpressionOperator} - because the screen composes
 * terms of its own, such as "children of this group" - cannot extend this and carries its own
 * {@code quickFilter} field instead. There are two: {@code FindUserCriteria} and
 * {@code AdvancedDocumentFindRequest}.
 */
@JsonInclude(Include.NON_NULL)
public abstract class QuickFilterCriteria extends BaseCriteria {

    @JsonProperty
    private String quickFilter;

    public QuickFilterCriteria() {
    }

    public QuickFilterCriteria(final String quickFilter) {
        this.quickFilter = quickFilter;
    }

    /**
     * There is deliberately no {@code (pageRequest, sortList)} overload. One existed briefly and a
     * subclass calling {@code super(pageRequest, sortList)} then compiled cleanly while silently
     * discarding the user's filter - which is the whole class of bug this type exists to prevent.
     * A subclass with no filter to pass should pass null explicitly.
     */
    @JsonCreator
    public QuickFilterCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                               @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                               @JsonProperty("quickFilter") final String quickFilter) {
        super(pageRequest, sortList);
        this.quickFilter = quickFilter;
    }

    /**
     * What the user typed, verbatim. Null or blank means no filter.
     */
    public String getQuickFilter() {
        return quickFilter;
    }

    public void setQuickFilter(final String quickFilter) {
        this.quickFilter = quickFilter;
    }


    // --------------------------------------------------------------------------------


    /**
     * Self-typed so subclasses can extend it and still return their own builder from the setters
     * here. Same shape as {@code ExpressionCriteria.ExpressionCriteriaBuilder}.
     */
    public abstract static class QuickFilterCriteriaBuilder
            <T extends QuickFilterCriteria, B extends QuickFilterCriteriaBuilder<T, B>>
            extends BaseCriteriaBuilder<T, B> {

        protected String quickFilter;

        protected QuickFilterCriteriaBuilder() {
        }

        /**
         * Carrying the filter here is the point of the copy constructor: five hand-written ones
         * silently dropped it, so a request rebuilt server-side - as the permission services do
         * when the caller cannot manage permissions - came back unfiltered.
         */
        protected QuickFilterCriteriaBuilder(final T criteria) {
            super(criteria);
            this.quickFilter = criteria.getQuickFilter();
        }

        /**
         * @see QuickFilterCriteria#getQuickFilter()
         */
        public B quickFilter(final String quickFilter) {
            this.quickFilter = quickFilter;
            return self();
        }

        public String getQuickFilter() {
            return quickFilter;
        }
    }
}
