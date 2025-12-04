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

package stroom.query.api;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({"visualisation", "json"})
@JsonInclude(Include.NON_NULL)
public class QLVisSettings {

    @JsonProperty
    private final DocRef visualisation;
    @JsonProperty
    private final String json;

    @JsonCreator
    public QLVisSettings(@JsonProperty("visualisation") final DocRef visualisation,
                         @JsonProperty("json") final String json) {
        this.visualisation = visualisation;
        this.json = json;
    }

    public DocRef getVisualisation() {
        return visualisation;
    }

    public String getJson() {
        return json;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QLVisSettings that = (QLVisSettings) o;
        return Objects.equals(visualisation, that.visualisation) &&
                Objects.equals(json, that.json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(visualisation, json);
    }

    @Override
    public String toString() {
        return "QLVisSettings{" +
                "visualisation=" + visualisation +
                ", json=" + json +
                '}';
    }
}
