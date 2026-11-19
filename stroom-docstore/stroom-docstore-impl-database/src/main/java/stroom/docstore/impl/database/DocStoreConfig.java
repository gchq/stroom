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

package stroom.docstore.impl.database;

import stroom.config.common.HasDbConfig;
import stroom.docstore.impl.DocStoreCommonConfig;
import stroom.docstore.impl.db.DocStoreDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/// Config for the database implementation of the doc store
@JsonPropertyOrder(alphabetic = true)
public class DocStoreConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final DocStoreCommonConfig docStoreCommonConfig;
    private final DocStoreDbConfig dbConfig;
    private final StroomDuration physicalDeleteAge;

    public DocStoreConfig() {
        docStoreCommonConfig = new DocStoreCommonConfig();
        dbConfig = new DocStoreDbConfig();
        physicalDeleteAge = StroomDuration.ofDays(30);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public DocStoreConfig(@JsonProperty("db") final DocStoreDbConfig dbConfig,
                          @JsonProperty("common") final DocStoreCommonConfig docStoreCommonConfig,
                          @JsonProperty("physicalDeleteAge") final StroomDuration physicalDeleteAge) {
        this.dbConfig = dbConfig;
        this.docStoreCommonConfig = docStoreCommonConfig;
        this.physicalDeleteAge = physicalDeleteAge;
    }

    @JsonProperty("common")
    public DocStoreCommonConfig getDocStoreCommonConfig() {
        return docStoreCommonConfig;
    }

    @Override
    @JsonProperty("db")
    public DocStoreDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonPropertyDescription("How long to keep logically deleted documents before physically deleting them " +
                             "and all associated data, audit, and snapshot rows. " +
                             "In ISO-8601 duration format, e.g. 'P30D' for 30 days.")
    public StroomDuration getPhysicalDeleteAge() {
        return physicalDeleteAge;
    }
}
