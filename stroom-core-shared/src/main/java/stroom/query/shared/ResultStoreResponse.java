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

import stroom.query.api.ResultStoreInfo;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ResultStoreResponse extends ResultPage<ResultStoreInfo> {

    @JsonPropertyDescription("A list of errors that occurred in running the query")
    @JsonProperty
    private final List<String> errors;

    public ResultStoreResponse(final List<ResultStoreInfo> values) {
        super(values);
        this.errors = null;
    }

    @JsonCreator
    public ResultStoreResponse(@JsonProperty("values") final List<ResultStoreInfo> values,
                               @JsonProperty("errors") final List<String> errors,
                               @JsonProperty("pageResponse") final PageResponse pageResponse) {
        super(values, pageResponse);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
