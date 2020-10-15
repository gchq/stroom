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
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FloatField extends AbstractNumericField {
    private static final long serialVersionUID = 1272545271946712570L;

    public FloatField(final String name) {
        super(name);
    }

    public FloatField(final String name, final Boolean queryable) {
        super(name, queryable);
    }

    @JsonCreator
    public FloatField(@JsonProperty("name") final String name,
                      @JsonProperty("queryable") final Boolean queryable,
                      @JsonProperty("conditions") final List<Condition> conditions) {
        super(name, queryable, conditions);
    }

    @JsonIgnore
    @Override
    public String getType() {
        return FieldTypes.FLOAT;
    }
}