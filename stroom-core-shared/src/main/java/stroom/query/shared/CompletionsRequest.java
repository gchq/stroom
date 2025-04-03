/*
 * Copyright 2024 Crown Copyright
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
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class CompletionsRequest {

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String text;
    @JsonProperty
    private final int row;
    @JsonProperty
    private final int column;
    @JsonProperty
    private final String pattern;
    @JsonProperty
    private final Set<QueryHelpType> includedTypes;
    @JsonProperty
    private final Integer maxCompletions;


    /**
     * @param includedTypes  Set to false to exclude datasources and structure items.
     * @param maxCompletions
     */
    @JsonCreator
    public CompletionsRequest(@JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                              @JsonProperty("text") final String text,
                              @JsonProperty("row") final int row,
                              @JsonProperty("column") final int column,
                              @JsonProperty("pattern") final String pattern,
                              @JsonProperty("includedTypes") final Set<QueryHelpType> includedTypes,
                              @JsonProperty("maxCompletions") final Integer maxCompletions) {
        this.dataSourceRef = dataSourceRef;
        this.text = text;
        this.row = row;
        this.column = column;
        this.pattern = pattern;
        this.includedTypes = includedTypes;
        this.maxCompletions = maxCompletions;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getText() {
        return text;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public String getPattern() {
        return pattern;
    }

    public Set<QueryHelpType> getIncludedTypes() {
        return includedTypes;
    }

    public Integer getMaxCompletions() {
        return maxCompletions;
    }

    public boolean isTypeIncluded(final QueryHelpType queryHelpType) {
        if (queryHelpType == null || NullSafe.isEmptyCollection(includedTypes)) {
            return false;
        } else {
            return includedTypes.contains(queryHelpType);
        }
    }

    @Override
    public String toString() {
        return "CompletionsRequest{" +
                "dataSourceRef=" + dataSourceRef +
                ", text='" + text + '\'' +
                ", row=" + row +
                ", column=" + column +
                ", pattern='" + pattern + '\'' +
                ", includedTypes=" + includedTypes +
                ", maxCompletions=" + maxCompletions +
                '}';
    }
}
