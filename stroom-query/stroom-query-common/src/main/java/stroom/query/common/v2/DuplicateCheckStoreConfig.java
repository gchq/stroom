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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class DuplicateCheckStoreConfig extends AbstractConfig implements IsStroomConfig {

    private final ResultStoreLmdbConfig lmdbConfig;

    public DuplicateCheckStoreConfig() {
        this(ResultStoreLmdbConfig.builder().localDir("lmdb/duplicate_check").build());
    }

    @JsonCreator
    public DuplicateCheckStoreConfig(@JsonProperty("lmdb") final ResultStoreLmdbConfig lmdbConfig) {
        this.lmdbConfig = lmdbConfig;
    }

    @JsonProperty("lmdb")
    public ResultStoreLmdbConfig getLmdbConfig() {
        return lmdbConfig;
    }
}
