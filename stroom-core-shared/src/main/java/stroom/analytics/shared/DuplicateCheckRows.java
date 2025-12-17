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

package stroom.analytics.shared;

import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DuplicateCheckRows {

    @JsonProperty
    private final List<String> columnNames;
    @JsonProperty
    private final ResultPage<DuplicateCheckRow> resultPage;

    @JsonCreator
    public DuplicateCheckRows(@JsonProperty("columnNames") final List<String> columnNames,
                              @JsonProperty("resultPage") final ResultPage<DuplicateCheckRow> resultPage) {
        this.columnNames = columnNames;
        this.resultPage = resultPage;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public ResultPage<DuplicateCheckRow> getResultPage() {
        return resultPage;
    }
}
