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

package stroom.query.shared;

import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FetchSuggestionsRequest {

    @NotNull
    @JsonProperty
    private final DocRef dataSource;

    @NotNull
    @JsonProperty
    private final QueryField field;

    @JsonProperty
    private final String text;

    @JsonCreator
    public FetchSuggestionsRequest(@JsonProperty("dataSource") final DocRef dataSource,
                                   @JsonProperty("field") final QueryField field,
                                   @JsonProperty("text") final String text) {
        this.dataSource = dataSource;
        this.field = field;
        this.text = text;
    }

    public DocRef getDataSource() {
        return dataSource;
    }

    public QueryField getField() {
        return field;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FetchSuggestionsRequest that = (FetchSuggestionsRequest) o;
        return Objects.equals(dataSource, that.dataSource) && Objects.equals(field,
                that.field) && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dataSource, field, text);
    }

    @Override
    public String toString() {
        return "FetchSuggestionsRequest{" +
                "dataSource=" + dataSource +
                ", field=" + field +
                ", text='" + text + '\'' +
                '}';
    }
}
