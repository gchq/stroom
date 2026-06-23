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

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class DocAuditEntry {

    @JsonProperty
    private final Long time;
    @JsonProperty
    private final DocAuditUser user;
    @JsonProperty
    private final AuditAction action;

    @JsonCreator
    public DocAuditEntry(@JsonProperty("time") final Long time,
                         @JsonProperty("user") final DocAuditUser user,
                         @JsonProperty("action") final AuditAction action) {
        this.time = time;
        this.user = user;
        this.action = action;
    }

    public Long getTime() {
        return time;
    }

    public DocAuditUser getUser() {
        return user;
    }

    public AuditAction getAction() {
        return action;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DocAuditEntry that = (DocAuditEntry) o;
        return Objects.equals(time, that.time) && Objects.equals(user,
                that.user) && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, user, action);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private Long time;
        private DocAuditUser user;
        private AuditAction action;

        private Builder() {
        }

        private Builder(final DocAuditEntry docAuditEntry) {
            this.time = docAuditEntry.time;
            this.user = docAuditEntry.user;
            this.action = docAuditEntry.action;
        }

        public Builder time(final Long time) {
            this.time = time;
            return this;
        }

        public Builder user(final DocAuditUser user) {
            this.user = user;
            return this;
        }

        public Builder action(final AuditAction action) {
            this.action = action;
            return this;
        }

        public DocAuditEntry build() {
            return new DocAuditEntry(time, user, action);
        }
    }
}
