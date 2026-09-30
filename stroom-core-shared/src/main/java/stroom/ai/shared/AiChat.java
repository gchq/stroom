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

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AiChat {

    @JsonProperty
    private final int id;
    @JsonProperty
    private final long createTimeMs;
    @JsonProperty
    private final long updateTimeMs;
    @JsonProperty
    private final String userUuid;
    @JsonProperty
    private final String title;

    @JsonCreator
    public AiChat(@JsonProperty("id") final Integer id,
                  @JsonProperty("createTimeMs") final Long createTimeMs,
                  @JsonProperty("updateTimeMs") final Long updateTimeMs,
                  @JsonProperty("userUuid") final String userUuid,
                  @JsonProperty("title") final String title) {
        this.id = Objects.requireNonNullElse(id, 0);
        this.createTimeMs = Objects.requireNonNullElse(createTimeMs, 0L);
        this.updateTimeMs = Objects.requireNonNullElse(updateTimeMs, 0L);
        this.userUuid = userUuid;
        this.title = title;
    }

    public int getId() {
        return id;
    }

    public long getCreateTimeMs() {
        return createTimeMs;
    }

    public long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public String getUserUuid() {
        return userUuid;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AiChat aiChat = (AiChat) o;
        return id == aiChat.id &&
               createTimeMs == aiChat.createTimeMs &&
               updateTimeMs == aiChat.updateTimeMs &&
               Objects.equals(userUuid, aiChat.userUuid) &&
               Objects.equals(title, aiChat.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createTimeMs, updateTimeMs, userUuid, title);
    }

    @Override
    public String toString() {
        return "AiChat{" +
               "id=" + id +
               ", createTimeMs=" + createTimeMs +
               ", updateTimeMs=" + updateTimeMs +
               ", userUuid='" + userUuid + '\'' +
               ", title='" + title + '\'' +
               '}';
    }
}
