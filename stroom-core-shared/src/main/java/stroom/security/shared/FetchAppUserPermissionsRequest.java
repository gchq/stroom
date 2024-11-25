/*
 * Copyright 2017 Crown Copyright
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

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FetchAppUserPermissionsRequest extends ExpressionCriteria {

    @JsonProperty
    private final PermissionShowLevel showLevel;

    @JsonCreator
    public FetchAppUserPermissionsRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                          @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                          @JsonProperty("expression") final ExpressionOperator expression,
                                          @JsonProperty("showLevel") PermissionShowLevel showLevel) {
        super(pageRequest, sortList, expression);
        this.showLevel = showLevel;
    }

    public PermissionShowLevel getShowLevel() {
        return showLevel;
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends AbstractBuilder<FetchAppUserPermissionsRequest, Builder> {

        private PermissionShowLevel showLevel;

        public Builder() {

        }

        public Builder(final FetchAppUserPermissionsRequest expressionCriteria) {
            super(expressionCriteria);
            this.showLevel = expressionCriteria.showLevel;
        }

        public Builder showLevel(final PermissionShowLevel showLevel) {
            this.showLevel = showLevel;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public FetchAppUserPermissionsRequest build() {
            return new FetchAppUserPermissionsRequest(
                    pageRequest,
                    sortList,
                    expression,
                    showLevel);
        }
    }
}
