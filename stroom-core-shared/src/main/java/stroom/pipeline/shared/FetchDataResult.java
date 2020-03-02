/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class FetchDataResult extends AbstractFetchDataResult {
    @JsonProperty
    private final String data;
    @JsonProperty
    private final boolean html;

    @JsonCreator
    public FetchDataResult(@JsonProperty("streamTypeName") final String streamTypeName,
                           @JsonProperty("classification") final String classification,
                           @JsonProperty("streamRange") final OffsetRange<Long> streamRange,
                           @JsonProperty("streamRowCount") final RowCount<Long> streamRowCount,
                           @JsonProperty("pageRange") final OffsetRange<Long> pageRange,
                           @JsonProperty("pageRowCount") final RowCount<Long> pageRowCount,
                           @JsonProperty("availableChildStreamTypes") final List<String> availableChildStreamTypes,
                           @JsonProperty("data") final String data,
                           @JsonProperty("html") final boolean html) {
        super(streamTypeName, classification, streamRange, streamRowCount, pageRange, pageRowCount, availableChildStreamTypes);
        this.data = data;
        this.html = html;
    }

    public String getData() {
        return data;
    }

    public boolean isHtml() {
        return html;
    }
}
