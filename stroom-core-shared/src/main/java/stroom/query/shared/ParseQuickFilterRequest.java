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
 * Text a user typed into a quick filter, plus the fields the surface parses it against, so the
 * server can turn it into the expression tree the Advanced Query dialog edits. The fields ride on
 * the request, as they do on {@link ValidateExpressionRequest}, so the server needs no registry
 * of surfaces.
 */
@JsonInclude(Include.NON_NULL)
public class ParseQuickFilterRequest {

    @JsonProperty
    private final String text;
    @JsonProperty
    private final List<QueryField> defaultFields;
    @JsonProperty
    private final List<QueryField> qualifiedFields;

    @JsonCreator
    public ParseQuickFilterRequest(@JsonProperty("text") final String text,
                                   @JsonProperty("defaultFields") final List<QueryField> defaultFields,
                                   @JsonProperty("qualifiedFields") final List<QueryField> qualifiedFields) {
        this.text = text;
        this.defaultFields = NullSafe.list(defaultFields);
        this.qualifiedFields = NullSafe.list(qualifiedFields);
    }

    @SerialisationTestConstructor
    private ParseQuickFilterRequest() {
        this("", Collections.emptyList(), Collections.emptyList());
    }

    public String getText() {
        return text;
    }

    public List<QueryField> getDefaultFields() {
        return defaultFields;
    }

    public List<QueryField> getQualifiedFields() {
        return qualifiedFields;
    }

    @Override
    public String toString() {
        return "ParseQuickFilterRequest{" +
               "text='" + text + '\'' +
               ", defaultFields=" + defaultFields +
               ", qualifiedFields=" + qualifiedFields +
               '}';
    }
}
