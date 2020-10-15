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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder({"type", "docRefType", "name", "queryable", "conditions"})
@JsonInclude(Include.NON_NULL)
public class DocRefField extends AbstractField {
    private static final long serialVersionUID = 1272545271946712570L;

    private static List<Condition> DEFAULT_CONDITIONS = new ArrayList<>();

    static {
        DEFAULT_CONDITIONS.add(Condition.IS_DOC_REF);
        DEFAULT_CONDITIONS.add(Condition.CONTAINS);
        DEFAULT_CONDITIONS.add(Condition.EQUALS);
        DEFAULT_CONDITIONS.add(Condition.IN);
        DEFAULT_CONDITIONS.add(Condition.IN_DICTIONARY);
        DEFAULT_CONDITIONS.add(Condition.IN_FOLDER);
    }

    @JsonProperty
    private String docRefType;

    public DocRefField(final String docRefType,
                       final String name) {
        super(name, Boolean.TRUE, DEFAULT_CONDITIONS);
        this.docRefType = docRefType;
    }

    public DocRefField(final String docRefType,
                       final String name,
                       final Boolean queryable) {
        super(name, queryable, DEFAULT_CONDITIONS);
        this.docRefType = docRefType;
    }

    @JsonCreator
    public DocRefField(@JsonProperty("docRefType") final String docRefType,
                       @JsonProperty("name") final String name,
                       @JsonProperty("queryable") final Boolean queryable,
                       @JsonProperty("conditions") final List<Condition> conditions) {
        super(name, queryable, conditions);
        this.docRefType = docRefType;
    }

    public String getDocRefType() {
        return docRefType;
    }

    @JsonIgnore
    @Override
    public String getType() {
        return FieldTypes.DOC_REF;
    }
}