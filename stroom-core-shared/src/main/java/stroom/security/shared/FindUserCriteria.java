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
import stroom.query.api.v2.ExpressionOperator;
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

    public FindUserCriteria() {
        this(PageRequest.unlimited(), Collections.emptyList(), ExpressionOperator.builder().build());
    }

    @JsonCreator
    public FindUserCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                            @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                            @JsonProperty("expression") final ExpressionOperator expression) {
        super(pageRequest, sortList, expression);
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends AbstractBuilder<FindUserCriteria, Builder> {

        public Builder() {

        }

        public Builder(final FindUserCriteria expressionCriteria) {
            super(expressionCriteria);
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
                    expression);
        }
    }
}
