/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * A named piece of 'Ask Stroom AI' memory, e.g. the user's preferences, or a chat's plan.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiMemory {

    @JsonProperty
    private final int id;
    @JsonProperty
    private final int version;
    @JsonProperty
    private final AiMemoryScope scope;
    @JsonProperty
    private final String scopeKey;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final String content;
    @JsonProperty
    private final long updateTimeMs;
    @JsonProperty
    private final String updateUser;

    /**
     * @param id           The id.
     * @param version      The version, which goes up with every change.
     * @param scope        Who or what it belongs to.
     * @param scopeKey     The chat id for CHAT scope, the document UUID for DOC scope, or empty for USER scope.
     * @param name         The name, unique within its scope.
     * @param content      The content.
     * @param updateTimeMs When it was last changed.
     * @param updateUser   Who last changed it.
     */
    @JsonCreator
    public AiMemory(@JsonProperty("id") final Integer id,
                    @JsonProperty("version") final Integer version,
                    @JsonProperty("scope") final AiMemoryScope scope,
                    @JsonProperty("scopeKey") final String scopeKey,
                    @JsonProperty("name") final String name,
                    @JsonProperty("content") final String content,
                    @JsonProperty("updateTimeMs") final Long updateTimeMs,
                    @JsonProperty("updateUser") final String updateUser) {
        this.id = Objects.requireNonNullElse(id, 0);
        this.version = Objects.requireNonNullElse(version, 0);
        this.scope = scope;
        this.scopeKey = scopeKey;
        this.name = name;
        this.content = content;
        this.updateTimeMs = Objects.requireNonNullElse(updateTimeMs, 0L);
        this.updateUser = updateUser;
    }

    public int getId() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public AiMemoryScope getScope() {
        return scope;
    }

    public String getScopeKey() {
        return scopeKey;
    }

    public String getName() {
        return name;
    }

    public String getContent() {
        return content;
    }

    public long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiMemory that = (AiMemory) o;
        return id == that.id
               && version == that.version
               && updateTimeMs == that.updateTimeMs
               && scope == that.scope
               && Objects.equals(scopeKey, that.scopeKey)
               && Objects.equals(name, that.name)
               && Objects.equals(content, that.content)
               && Objects.equals(updateUser, that.updateUser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, version, scope, scopeKey, name, content, updateTimeMs, updateUser);
    }

    @Override
    public String toString() {
        return "AiMemory{" +
               "id=" + id +
               ", version=" + version +
               ", scope=" + scope +
               ", scopeKey='" + scopeKey + '\'' +
               ", name='" + name + '\'' +
               '}';
    }
}
