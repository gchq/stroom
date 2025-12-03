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

package stroom.config.global.shared;

import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@Schema(description = "List of config properties")
public class ListConfigResponse extends ResultPage<ConfigProperty> {

    @JsonProperty
    private final String nodeName;

    public ListConfigResponse(final List<ConfigProperty> values,
                              final String nodeName) {
        super(values);
        this.nodeName = nodeName;
    }

    @JsonCreator
    public ListConfigResponse(@JsonProperty("values") final List<ConfigProperty> values,
                              @JsonProperty("pageResponse") final PageResponse pageResponse,
                              @JsonProperty("nodeName") final String nodeName) {
        super(values, pageResponse);
        this.nodeName = nodeName;
    }

    public String getNodeName() {
        return nodeName;
    }
}
