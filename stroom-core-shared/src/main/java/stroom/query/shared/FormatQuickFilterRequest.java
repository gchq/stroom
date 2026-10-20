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

package stroom.query.shared;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

/**
 * A tree the Advanced Query dialog built, plus the fields the surface parses against, so the
 * server can write it as quick filter text. The fields are needed for this direction too: without
 * knowing which fields are defaults and what each field's default condition is, the printer cannot
 * decide between {@code abc} and {@code name:+abc}.
 */
@JsonInclude(Include.NON_NULL)
public class FormatQuickFilterRequest {

    @JsonProperty
    private final ExpressionOperator expression;
    @JsonProperty
    private final List<QueryField> defaultFields;
    @JsonProperty
    private final List<QueryField> qualifiedFields;

    @JsonCreator
    public FormatQuickFilterRequest(@JsonProperty("expression") final ExpressionOperator expression,
                                    @JsonProperty("defaultFields") final List<QueryField> defaultFields,
                                    @JsonProperty("qualifiedFields") final List<QueryField> qualifiedFields) {
        this.expression = expression;
        this.defaultFields = NullSafe.list(defaultFields);
        this.qualifiedFields = NullSafe.list(qualifiedFields);
    }

    @SerialisationTestConstructor
    private FormatQuickFilterRequest() {
        this(null, Collections.emptyList(), Collections.emptyList());
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public List<QueryField> getDefaultFields() {
        return defaultFields;
    }

    public List<QueryField> getQualifiedFields() {
        return qualifiedFields;
    }

    @Override
    public String toString() {
        return "FormatQuickFilterRequest{" +
               "expression=" + expression +
               ", defaultFields=" + defaultFields +
               ", qualifiedFields=" + qualifiedFields +
               '}';
    }
}
