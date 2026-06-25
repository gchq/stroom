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

package stroom.cluster.lock.impl.db;

import stroom.config.common.HasDbConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ClusterLockConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private final ClusterLockDbConfig dbConfig;
    private final StroomDuration lockTimeout;

    public ClusterLockConfig() {
        dbConfig = new ClusterLockDbConfig();
        lockTimeout = StroomDuration.ofMinutes(10);
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ClusterLockConfig(@JsonProperty("db") final ClusterLockDbConfig dbConfig,
                             @JsonProperty("lockTimeout") final StroomDuration lockTimeout) {
        this.dbConfig = dbConfig;
        this.lockTimeout = lockTimeout;
    }

    @Override
    @JsonProperty("db")
    public ClusterLockDbConfig getDbConfig() {
        return dbConfig;
    }

    @JsonProperty("lockTimeout")
    @JsonPropertyDescription("The minimum period of time to keep trying to obtain a cluster lock for. " +
            "This allows a timeout to be set that is greater than that configured on the database, but not less. ")
    public StroomDuration getLockTimeout() {
        return lockTimeout;
    }

    @Override
    public String toString() {
        return "ClusterLockConfig{" +
                "dbConfig=" + dbConfig +
                ", lockTimeout=" + lockTimeout +
                '}';
    }
}
