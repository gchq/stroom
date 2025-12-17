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

package stroom.util.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@SuppressWarnings("ClassCanBeRecord") // Cos GWT
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class UserDependency {

    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final String details;
    @JsonProperty
    private final DocRef docRef;

    @JsonCreator
    public UserDependency(@JsonProperty("userRef") final UserRef userRef,
                          @JsonProperty("details") final String details,
                          @JsonProperty("docRef") final DocRef docRef) {
        this.userRef = Objects.requireNonNull(userRef);
        this.details = Objects.requireNonNull(details);
        this.docRef = docRef;
    }

    public UserDependency(final UserRef userRef,
                          final String details) {
        this.userRef = Objects.requireNonNull(userRef);
        this.details = Objects.requireNonNull(details);
        this.docRef = null;
    }

    @SerialisationTestConstructor
    private UserDependency() {
        this(UserRef.builder().build(), "test");
    }

    /**
     * @return The user that the document/item has a dependency on.
     * Not null.
     */
    public UserRef getUserRef() {
        return userRef;
    }

    /**
     * @return The details of the dependency.
     * Not null.
     */
    public String getDetails() {
        return details;
    }

    /**
     * The document that has the dependency on the user.
     * May be null.
     */
    public DocRef getDocRef() {
        return docRef;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final UserDependency that = (UserDependency) object;
        return Objects.equals(userRef, that.userRef) &&
               Objects.equals(details, that.details) &&
               Objects.equals(docRef, that.docRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userRef, details, docRef);
    }

    @Override
    public String toString() {
        return "UserDependency{" +
               "userRef=" + userRef +
               ", details='" + details + '\'' +
               ", docRef=" + docRef +
               '}';
    }
}
