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

package stroom.config.global.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class GlobalConfigCriteria extends BaseCriteria {

    @JsonProperty
    private String quickFilterInput;

    @JsonCreator
    public GlobalConfigCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                @JsonProperty("quickFilterInput") final String quickFilterInput) {
        super(pageRequest, sortList);
        this.quickFilterInput = quickFilterInput;
    }

    public GlobalConfigCriteria() {
        this(null);
    }

    public GlobalConfigCriteria(final String quickFilterInput) {
        super(PageRequest.unlimited(), new ArrayList<>());
        this.quickFilterInput = quickFilterInput;
    }

    public String getQuickFilterInput() {
        return quickFilterInput;
    }

    public void setQuickFilterInput(final String quickFilterInput) {
        this.quickFilterInput = quickFilterInput;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final GlobalConfigCriteria that = (GlobalConfigCriteria) o;
        return Objects.equals(quickFilterInput, that.quickFilterInput);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), quickFilterInput);
    }

    @Override
    public String toString() {
        return "GlobalConfigCriteria{" +
                "quickFilterInput='" + quickFilterInput + '\'' +
                '}';
    }
}
