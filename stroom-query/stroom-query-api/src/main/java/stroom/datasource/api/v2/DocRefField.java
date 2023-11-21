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

@JsonPropertyOrder({"type", "docRefType", "name", "queryable", "conditionSet"})
@JsonInclude(Include.NON_NULL)
public class DocRefField extends QueryField {

    public DocRefField(final String docRefType,
                       final String name) {
        super(name, ConditionSet.DOC_REF_UUID, docRefType, Boolean.TRUE);
    }

    @JsonCreator
    public DocRefField(@JsonProperty("name") final String name,
                       @JsonProperty("conditionSet") final ConditionSet conditionSet,
                       @JsonProperty("docRefType") final String docRefType,
                       @JsonProperty("queryable") final Boolean queryable) {
        super(name, conditionSet, docRefType, queryable);
    }

    /**
     * A {@link DocRefField} for a {@link stroom.docref.DocRef} type whose names are unique, allowing
     * the name to be used as the value in expression terms.
     */
    public static DocRefField byUniqueName(final String docRefType,
                                           final String name) {
        return new DocRefField(name, ConditionSet.DOC_REF_ALL, docRefType, Boolean.TRUE);
    }

    /**
     * A {@link DocRefField} for a {@link stroom.docref.DocRef} type whose names are NOT unique.
     * The {@link stroom.docref.DocRef} name is used as the value in expression terms, accepting
     * that name=x may match >1 docrefs.
     */
    public static DocRefField byNonUniqueName(final String docRefType,
                                              final String name) {
        return new DocRefField(name, ConditionSet.DOC_REF_NAME, docRefType, Boolean.TRUE);
    }

    /**
     * A {@link DocRefField} for a {@link stroom.docref.DocRef} type whose names are NOT unique.
     * The {@link stroom.docref.DocRef} uuid is used as the value in expression terms for a unique
     * match. Other conditions are not supported as that would require the user to enter uuids,
     * and it is not clear in the UI whether they are dealing in UUIDs or names.
     */
    public static DocRefField byUuid(final String docRefType,
                                     final String name) {
        return new DocRefField(name, ConditionSet.DOC_REF_UUID, docRefType, Boolean.TRUE);
    }

    @JsonIgnore
    @Override
    public FieldType getFieldType() {
        return FieldType.DOC_REF;
    }
}
