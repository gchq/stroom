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

import stroom.query.api.v2.ExpressionTerm.Condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder({"type", "docRefType", "name", "queryable", "conditions"})
@JsonInclude(Include.NON_NULL)
public class DocRefField extends AbstractField {

    private static final long serialVersionUID = 1272545271946712570L;

    // Conditions suitable for use when referencing the DocRef by UUID
    private static final List<Condition> DEFAULT_CONDITIONS_UUID = new ArrayList<>();
    // Conditions suitable for use when referencing the DocRef by name
    private static final List<Condition> DEFAULT_CONDITIONS_NAME = new ArrayList<>();
    // Conditions suitable for use when referencing the DocRef by name only IF the name is unique
    private static final List<Condition> DEFAULT_CONDITIONS_ALL = new ArrayList<>();

    static {
        DEFAULT_CONDITIONS_UUID.add(Condition.IS_DOC_REF);
        DEFAULT_CONDITIONS_UUID.add(Condition.IN_FOLDER);

        DEFAULT_CONDITIONS_NAME.add(Condition.CONTAINS);
        DEFAULT_CONDITIONS_NAME.add(Condition.EQUALS);
        DEFAULT_CONDITIONS_NAME.add(Condition.IN);
        DEFAULT_CONDITIONS_NAME.add(Condition.IN_DICTIONARY);

        DEFAULT_CONDITIONS_ALL.addAll(DEFAULT_CONDITIONS_UUID);
        DEFAULT_CONDITIONS_ALL.addAll(DEFAULT_CONDITIONS_NAME);
    }

    @JsonProperty
    private String docRefType;

    public DocRefField(final String docRefType,
                       final String name) {
        super(name, Boolean.TRUE, DEFAULT_CONDITIONS_UUID);
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

    /**
     * A {@link DocRefField} for a {@link stroom.docref.DocRef} type whose names are unique, allowing
     * the name to be used as the value in expression terms.
     */
    public static DocRefField byUniqueName(final String docRefType,
                                           final String name) {
        return new DocRefField(docRefType, name, Boolean.TRUE, DEFAULT_CONDITIONS_ALL);
    }

    /**
     * A {@link DocRefField} for a {@link stroom.docref.DocRef} type whose names are NOT unique.
     * The {@link stroom.docref.DocRef} name is used as the value in expression terms, accepting
     * that name=x may match >1 docrefs.
     */
    public static DocRefField byNonUniqueName(final String docRefType,
                                              final String name) {
        return new DocRefField(docRefType, name, Boolean.TRUE, DEFAULT_CONDITIONS_NAME);
    }

    /**
     * A {@link DocRefField} for a {@link stroom.docref.DocRef} type whose names are NOT unique.
     * The {@link stroom.docref.DocRef} uuid is used as the value in expression terms for a unique
     * match. Other conditions are not supported as that would require the user to enter uuids,
     * and it is not clear in the UI whether they are dealing in UUIDs or names.
     */
    public static DocRefField byUuid(final String docRefType,
                                     final String name) {
        return new DocRefField(docRefType, name, Boolean.TRUE, DEFAULT_CONDITIONS_UUID);
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
