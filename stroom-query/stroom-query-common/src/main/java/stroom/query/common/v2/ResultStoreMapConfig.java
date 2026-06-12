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

package stroom.query.common.v2;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class ResultStoreMapConfig extends AbstractConfig implements IsStroomConfig {

    private final int trimmedSizeLimit;
    private final int minUntrimmedSize;


    public ResultStoreMapConfig() {
        this(500_000, 100_000);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ResultStoreMapConfig(@JsonProperty("trimmedSizeLimit") final int trimmedSizeLimit,
                                @JsonProperty("minUntrimmedSize") final int minUntrimmedSize) {
        this.trimmedSizeLimit = trimmedSizeLimit;
        this.minUntrimmedSize = minUntrimmedSize;
    }

    @JsonPropertyDescription("The trimmed size of sorted results for on heap result stores.")
    @JsonProperty("trimmedSizeLimit")
    public int getTrimmedSizeLimit() {
        return trimmedSizeLimit;
    }

    @JsonPropertyDescription("The minimum size of sorted results for on heap result stores before they are trimmed.")
    @JsonProperty("minUntrimmedSize")
    public int getMinUntrimmedSize() {
        return minUntrimmedSize;
    }
}
