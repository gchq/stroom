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

package stroom.query.client;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionTerm.Condition;

public final class Term extends Item {

    private String field;
    private Condition condition;
    private String value;
    private DocRef docRef;

    public String getField() {
        return field;
    }

    public void setField(final String field) {
        this.field = field;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(final Condition condition) {
        this.condition = condition;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (field != null) {
            sb.append(field);
            sb.append(" ");
        }
        if (condition != null) {
            sb.append(condition.getDisplayValue());
            sb.append(" ");
        }
        if (Condition.IN_DICTIONARY.equals(condition)) {
            if (docRef != null && docRef.getName() != null) {
                sb.append(docRef.getName());
            }
        } else if (Condition.IN_FOLDER.equals(condition)) {
            if (docRef != null && docRef.getName() != null) {
                sb.append(docRef.getName());
            }
        } else if (Condition.IS_DOC_REF.equals(condition)) {
            if (docRef != null && docRef.getName() != null) {
                sb.append(docRef.getName());
            }
        } else {
            if (value != null) {
                sb.append(value);
            }
        }
        return sb.toString();
    }
}
