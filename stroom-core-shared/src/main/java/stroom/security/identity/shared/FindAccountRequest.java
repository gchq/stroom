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

package stroom.security.identity.shared;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindAccountRequest extends ExpressionCriteria {


//    @JsonProperty
//    private final String quickFilter;

    @JsonCreator
    public FindAccountRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                              @JsonProperty("expression") final ExpressionOperator expression) {
        super(pageRequest, sortList, expression);
//        this.quickFilter = quickFilter;
    }

//    public String getQuickFilter() {
//        return quickFilter;
//    }


    // --------------------------------------------------------------------------------


    public static class Builder extends ExpressionCriteriaBuilder<FindAccountRequest, Builder> {

        @Override
        protected Builder self() {
            return null;
        }

        @Override
        public FindAccountRequest build() {
            return new FindAccountRequest(pageRequest, sortList, expression);
        }
    }
}
