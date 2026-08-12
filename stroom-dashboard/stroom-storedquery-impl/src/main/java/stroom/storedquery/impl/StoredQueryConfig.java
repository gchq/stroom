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

package stroom.storedquery.impl;

import stroom.config.common.HasDbConfig;
import stroom.storedquery.impl.db.StoredQueryDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class StoredQueryConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private static final int DEFAULT_ITEMS_RETENTION = 100;
    private static final int DEFAULT_DAYS_RETENTION = 365;

    private final int itemsRetention;
    private final int daysRetention;
    private final StoredQueryDbConfig dbConfig;

    public StoredQueryConfig() {
        itemsRetention = DEFAULT_ITEMS_RETENTION;
        daysRetention = DEFAULT_DAYS_RETENTION;
        dbConfig = new StoredQueryDbConfig();
    }

    @JsonCreator
    public StoredQueryConfig(@JsonProperty("itemsRetention") final Integer itemsRetention,
                             @JsonProperty("daysRetention") final Integer daysRetention,
                             @JsonProperty("db") final StoredQueryDbConfig dbConfig) {
        this.itemsRetention = Objects.requireNonNullElse(itemsRetention, DEFAULT_ITEMS_RETENTION);
        this.daysRetention = Objects.requireNonNullElse(daysRetention, DEFAULT_DAYS_RETENTION);
        this.dbConfig = dbConfig;
    }

    @JsonPropertyDescription("The maximum number of query history items that will be retained")
    public int getItemsRetention() {
        return itemsRetention;
    }

    @Min(0)
    @JsonPropertyDescription("The number of days query history items will be retained for")
    public int getDaysRetention() {
        return daysRetention;
    }

    @Override
    @JsonProperty("db")
    public StoredQueryDbConfig getDbConfig() {
        return dbConfig;
    }
}
