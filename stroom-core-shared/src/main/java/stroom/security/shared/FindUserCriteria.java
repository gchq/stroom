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

package stroom.security.shared;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * Criteria class.
 */
@JsonInclude(Include.NON_NULL)
public class FindUserCriteria extends ExpressionCriteria {

    @JsonProperty
    private final FindUserContext context;
    /**
     * What the user typed into the quick filter, verbatim.
     * <p>
     * Carried as text rather than as a parsed {@link ExpressionOperator} so that the server owns
     * the grammar - see the syntax spec §9 and §11. It sits alongside {@code expression} rather
     * than replacing it because that field also carries structural terms the client composes
     * itself, such as {@code ChildrenOf} and {@code isgroup}, which are not things a user types.
     * The DAO parses this and ANDs the result with the expression.
     */
    @JsonProperty
    private final String quickFilter;

    public FindUserCriteria() {
        this(PageRequest.unlimited(),
                Collections.emptyList(),
                ExpressionOperator.builder().build(),
                FindUserContext.PERMISSIONS,
                null);
    }

    public FindUserCriteria(final PageRequest pageRequest,
                            final List<CriteriaFieldSort> sortList,
                            final ExpressionOperator expression,
                            final FindUserContext context) {
        this(pageRequest, sortList, expression, context, null);
    }

    @JsonCreator
    public FindUserCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                            @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                            @JsonProperty("expression") final ExpressionOperator expression,
                            @JsonProperty("context") final FindUserContext context,
                            @JsonProperty("quickFilter") final String quickFilter) {
        super(pageRequest, sortList, expression);
        this.context = context;
        this.quickFilter = quickFilter;
    }

    public FindUserContext getContext() {
        return context;
    }

    public String getQuickFilter() {
        return quickFilter;
    }

    // --------------------------------------------------------------------------------


    public static class Builder extends ExpressionCriteriaBuilder<FindUserCriteria, Builder> {

        private FindUserContext context;
        private String quickFilter;

        public Builder() {
        }

        public Builder(final FindUserCriteria criteria) {
            super(criteria);
            this.context = criteria.context;
            this.quickFilter = criteria.quickFilter;
        }

        public Builder context(final FindUserContext context) {
            this.context = context;
            return self();
        }

        /**
         * @see FindUserCriteria#getQuickFilter()
         */
        public Builder quickFilter(final String quickFilter) {
            this.quickFilter = quickFilter;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public FindUserCriteria build() {
            return new FindUserCriteria(
                    pageRequest,
                    sortList,
                    expression,
                    context,
                    quickFilter);
        }
    }
}
