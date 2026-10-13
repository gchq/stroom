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

import java.util.List;
import java.util.Objects;

/**
 * A page of parsed attachment table data. Contains the column headers
 * (parsed from the markdown header row) and a page of data rows.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AiAttachmentDataPage {

    @JsonProperty
    private final List<String> headers;
    @JsonProperty
    private final List<List<String>> rows;
    @JsonProperty
    private final int totalRowCount;
    @JsonProperty
    private final int offset;

    @JsonCreator
    public AiAttachmentDataPage(@JsonProperty("headers") final List<String> headers,
                                @JsonProperty("rows") final List<List<String>> rows,
                                @JsonProperty("totalRowCount") final Integer totalRowCount,
                                @JsonProperty("offset") final Integer offset) {
        this.headers = headers;
        this.rows = rows;
        this.totalRowCount = Objects.requireNonNullElse(totalRowCount, 0);
        this.offset = Objects.requireNonNullElse(offset, 0);
    }

    public List<String> getHeaders() {
        return headers;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public int getTotalRowCount() {
        return totalRowCount;
    }

    public int getOffset() {
        return offset;
    }
}
