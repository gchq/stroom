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

package stroom.explorer.shared;

import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class FindResult {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final String path;

    @JsonCreator
    public FindResult(@JsonProperty("docRef") final DocRef docRef,
                      @JsonProperty("path") final String path) {
        this.docRef = docRef;
        this.path = path;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    /**
     * @return The path like 'System / Parent Folder / Child item'
     */
    public String getPath() {
        return path;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FindResult that = (FindResult) o;
        return Objects.equals(docRef, that.docRef) &&
               Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, path);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    @Override
    public String toString() {
        return path + " / " + NullSafe.get(docRef, DocRef::toShortString);
    }

    // --------------------------------------------------------------------------------


    public static final class Builder {

        private DocRef docRef;
        private String path;

        private Builder() {
        }

        private Builder(final FindResult findResult) {
            this.docRef = findResult.docRef;
            this.path = findResult.path;
        }

        public Builder docRef(final DocRef docRef) {
            this.docRef = docRef;
            return this;
        }

        public Builder path(final String path) {
            this.path = path;
            return this;
        }

        public FindResult build() {
            return new FindResult(
                    docRef,
                    path);
        }
    }
}
