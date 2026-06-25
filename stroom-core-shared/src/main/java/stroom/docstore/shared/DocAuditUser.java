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

package stroom.docstore.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@SuppressWarnings("ClassCanBeRecord") // Cos GWT
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "uuid",
        "displayName"
})
public final class DocAuditUser {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String displayName;

    @JsonCreator
    public DocAuditUser(@JsonProperty("uuid") final String uuid,
                        @JsonProperty("displayName") final String displayName) {
        this.uuid = uuid;
        this.displayName = displayName;
    }

    /**
     * The stroom_user UUID.
     * No relation to any UUID on the IDP.
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * The friendly display name for the user
     * Maps to the IDP claim defined by
     * stroom.security.authentication.openId.userDisplayNameClaim
     */
    public String getDisplayName() {
        return displayName;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DocAuditUser docAuditUser = (DocAuditUser) o;
        return Objects.equals(uuid, docAuditUser.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public String toString() {
        return toDisplayString();
    }

    public String toDebugString() {
        return "DocAuditUser{" +
               "uuid='" + uuid + '\'' +
               ", displayName='" + displayName + '\'' +
               '}';
    }

    public String toDisplayString() {
        if (displayName != null) {
            return displayName;
        } else {
            return uuid;
        }
    }

    @SuppressWarnings("SizeReplaceableByIsEmpty")
    public String toInfoString() {
        final StringBuilder sb = new StringBuilder();
        if (displayName != null) {
            sb.append(displayName);
        }
        if (sb.length() > 0) {
            sb.append(" ");
        }
        sb.append("{");
        sb.append(uuid);
        sb.append("}");

        if (sb.length() > 0) {
            return sb.toString();
        }

        return toString();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String uuid;
        private String displayName;

        private Builder() {
        }

        private Builder(final DocAuditUser userRef) {
            this.uuid = userRef.uuid;
            this.displayName = userRef.displayName;
        }

        /**
         * The stroom_user UUID.
         * No relation to any UUID on the IDP.
         */
        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return this;
        }

        /**
         * The friendly display name for the user
         * Maps to the IDP claim defined by
         * stroom.security.authentication.openId.userDisplayNameClaim
         */
        public Builder displayName(final String displayName) {
            this.displayName = displayName;
            return this;
        }

        public DocAuditUser build() {
            return new DocAuditUser(uuid, displayName);
        }
    }
}
