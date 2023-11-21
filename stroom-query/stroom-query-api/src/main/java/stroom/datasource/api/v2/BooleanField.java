/*
 * Copyright 2019 Crown Copyright
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

package stroom.datasource.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class BooleanField extends QueryField {

    public BooleanField(final String name) {
        super(name, Boolean.TRUE, ConditionSet.DEFAULT_BOOLEAN);
    }

    public BooleanField(final String name,
                        final Boolean queryable) {
        super(name, queryable, ConditionSet.DEFAULT_BOOLEAN);
    }

    @JsonCreator
    public BooleanField(@JsonProperty("name") final String name,
                        @JsonProperty("conditionSet") final ConditionSet conditionSet,
                        @JsonProperty("docRefType") final String docRefType,
                        @JsonProperty("queryable") final Boolean queryable) {
        super(name, conditionSet, docRefType, queryable);
    }

    @JsonIgnore
    @Override
    public FieldType getFieldType() {
        return FieldType.BOOLEAN;
    }
}
