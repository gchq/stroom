/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.annotation.shared;


import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;

/**
 * Simply an Annotation's {@link DocRef} and its database ID value.
 */
public class AnnotationIdentity {

    private final DocRef docRef;
    private final long id;

    public AnnotationIdentity(final DocRef docRef,
                              final long id) {
        this.docRef = Objects.requireNonNull(docRef);
        this.docRef.validateType(Annotation.TYPE);
        this.id = id;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public long getId() {
        return id;
    }

    public String getType() {
        return docRef.getType();
    }

    public String getUuid() {
        return docRef.getUuid();
    }

    public String getName() {
        return docRef.getName();
    }

    @JsonIgnore
    public String getDisplayValue() {
        return docRef.getDisplayValue();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnnotationIdentity that = (AnnotationIdentity) o;
        return id == that.id && Objects.equals(docRef, that.docRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, id);
    }

    @Override
    public String toString() {
        return "AnnotationIdentity{" +
               "docRef=" + docRef +
               ", id=" + id +
               '}';
    }
}
