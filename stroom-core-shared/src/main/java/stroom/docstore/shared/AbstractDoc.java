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

package stroom.docstore.shared;

import stroom.util.shared.Document;
import stroom.util.shared.HasAuditInfoBuilder;
import stroom.util.shared.HasAuditableUserIdentity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser"})
@JsonInclude(Include.NON_NULL)
public abstract class AbstractDoc implements Document {

    @JsonProperty
    private final String type;
    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String version;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final Long updateTimeMs;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final String updateUser;

    @JsonCreator
    public AbstractDoc(@JsonProperty("type") final String type,
                       @JsonProperty("uuid") final String uuid,
                       @JsonProperty("name") final String name,
                       @JsonProperty("version") final String version,
                       @JsonProperty("createTimeMs") final Long createTimeMs,
                       @JsonProperty("updateTimeMs") final Long updateTimeMs,
                       @JsonProperty("createUser") final String createUser,
                       @JsonProperty("updateUser") final String updateUser) {
        Objects.requireNonNull(type, "Null Type");
        Objects.requireNonNull(uuid, "Null UUID");
        this.type = type;
        this.uuid = uuid;
        this.name = name;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.updateTimeMs = updateTimeMs;
        this.createUser = createUser;
        this.updateUser = updateUser;
    }

    @Override
    public final String getType() {
        return type;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    @Override
    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    @Override
    public String getCreateUser() {
        return createUser;
    }

    @Override
    public String getUpdateUser() {
        return updateUser;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractDoc that = (AbstractDoc) o;
        return Objects.equals(type, that.type) &&
               Objects.equals(uuid, that.uuid) &&
               Objects.equals(name, that.name) &&
               Objects.equals(version, that.version) &&
               Objects.equals(createTimeMs, that.createTimeMs) &&
               Objects.equals(updateTimeMs, that.updateTimeMs) &&
               Objects.equals(createUser, that.createUser) &&
               Objects.equals(updateUser, that.updateUser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
    }

    @Override
    public String toString() {
        return "DocRef{" +
               "type='" + type + '\'' +
               ", uuid='" + uuid + '\'' +
               ", name='" + name + '\'' +
               '}';
    }


    // --------------------------------------------------------------------------------


    public abstract static class AbstractBuilder<T extends AbstractDoc, B extends AbstractBuilder<T, ?>>
            implements HasAuditInfoBuilder<T, B> {

        protected String uuid;
        protected String name;
        protected String version;
        protected Long createTimeMs;
        protected String createUser;
        protected Long updateTimeMs;
        protected String updateUser;

        public AbstractBuilder() {
        }

        public AbstractBuilder(final AbstractDoc doc) {
            this.uuid = doc.uuid;
            this.name = doc.name;
            this.version = doc.version;
            this.createTimeMs = doc.createTimeMs;
            this.createUser = doc.createUser;
            this.updateUser = doc.updateUser;
            this.updateTimeMs = doc.updateTimeMs;
        }

        public B uuid(final String uuid) {
            this.uuid = uuid;
            return self();
        }

        public B name(final String name) {
            this.name = name;
            return self();
        }

        public B version(final String version) {
            this.version = version;
            return self();
        }

        @Override
        public B createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return self();
        }

        @Override
        public B createUser(final String createUser) {
            this.createUser = createUser;
            return self();
        }

        @Override
        public B updateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return self();
        }

        @Override
        public B updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return self();
        }

        @Override
        public final B stampAudit(final HasAuditableUserIdentity hasAuditableUserIdentity) {
            return stampAudit(hasAuditableUserIdentity.getUserIdentityForAudit());
        }

        @Override
        public final B stampAudit(final String user) {
            final long now = System.currentTimeMillis();
            if (createTimeMs == null) {
                this.createTimeMs = now;
            }
            if (createUser == null) {
                this.createUser = user;
            }
            updateTimeMs = now;
            updateUser = user;
            return self();
        }

        @Override
        public final B removeAudit() {
            this.createTimeMs = null;
            this.createUser = null;
            updateTimeMs = null;
            updateUser = null;
            return self();
        }

        protected abstract B self();
    }
}
