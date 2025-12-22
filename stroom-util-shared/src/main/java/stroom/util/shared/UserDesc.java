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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "subjectId",
        "displayName",
        "fullName"
})
public class UserDesc {

    @JsonProperty
    private final String subjectId;
    @JsonProperty
    private final String displayName;
    @JsonProperty
    private final String fullName;

    @JsonCreator
    public UserDesc(@JsonProperty("subjectId") final String subjectId,
                    @JsonProperty("displayName") final String displayName,
                    @JsonProperty("fullName") final String fullName) {
        // Ensure there is no spurious leading/trailing space as these values may have come from user input
        this.subjectId = NullSafe.get(subjectId, String::trim);
        this.displayName = NullSafe.get(displayName, String::trim);
        this.fullName = NullSafe.get(fullName, String::trim);
    }

    public static UserDesc forSubjectId(final String subjectId) {
        return new UserDesc(subjectId, null, null);
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UserDesc that = (UserDesc) o;
        return Objects.equals(subjectId, that.subjectId) &&
               Objects.equals(displayName, that.displayName) &&
               Objects.equals(fullName, that.fullName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectId, displayName, fullName);
    }

    @Override
    public String toString() {
        return "ExternalUser{" +
               "subjectId='" + subjectId + '\'' +
               ", displayName='" + displayName + '\'' +
               ", fullName='" + fullName + '\'' +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder(final String subjectId) {
        return new Builder(subjectId);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private final String subjectId;
        private String displayName;
        private String fullName;

        private Builder(final String subjectId) {
            this.subjectId = Objects.requireNonNull(subjectId);
        }

        private Builder(final UserDesc externalUser) {
            this.subjectId = Objects.requireNonNull(externalUser.subjectId);
            this.displayName = externalUser.displayName;
            this.fullName = externalUser.fullName;
        }

        public Builder displayName(final String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder fullName(final String fullName) {
            this.fullName = fullName;
            return this;
        }

        public UserDesc build() {
            return new UserDesc(subjectId, displayName, fullName);
        }
    }
}
