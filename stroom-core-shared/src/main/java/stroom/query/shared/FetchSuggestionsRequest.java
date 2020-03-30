/*
 * Copyright 2017 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FetchSuggestionsRequest {
    @JsonProperty
    private final DocRef dataSource;
    @JsonProperty
    private final AbstractField field;
    @JsonProperty
    private final String text;

    @JsonCreator
    public FetchSuggestionsRequest(@JsonProperty("dataSource") final DocRef dataSource,
                                   @JsonProperty("field") final AbstractField field,
                                   @JsonProperty("text") final String text) {
        this.dataSource = dataSource;
        this.field = field;
        this.text = text;
    }

    public DocRef getDataSource() {
        return dataSource;
    }

    public AbstractField getField() {
        return field;
    }

    public String getText() {
        return text;
    }
}
