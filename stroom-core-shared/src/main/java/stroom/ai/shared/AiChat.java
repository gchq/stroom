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

package stroom.ai.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class AiChat {

    @JsonProperty
    private final int id;
    @JsonProperty
    private final long createTimeMs;
    @JsonProperty
    private final long updateTimeMs;
    @JsonProperty
    private final String title;

    @JsonCreator
    public AiChat(@JsonProperty("id") final int id,
                  @JsonProperty("createTimeMs") final long createTimeMs,
                  @JsonProperty("updateTimeMs") final long updateTimeMs,
                  @JsonProperty("title") final String title) {
        this.id = id;
        this.createTimeMs = createTimeMs;
        this.updateTimeMs = updateTimeMs;
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
               Objects.equals(title, aiChat.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createTimeMs, updateTimeMs, title);
    }

    @Override
    public String toString() {
        return "AiChat{" +
               "id=" + id +
               ", createTimeMs=" + createTimeMs +
               ", updateTimeMs=" + updateTimeMs +
               ", title='" + title + '\'' +
               '}';
    }
}
