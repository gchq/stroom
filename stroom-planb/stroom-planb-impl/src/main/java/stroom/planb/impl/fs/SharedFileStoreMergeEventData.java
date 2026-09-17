/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.fs;

import stroom.util.entityevent.EntityEvent.EntityEventData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class SharedFileStoreMergeEventData implements EntityEventData {

    @JsonProperty
    private final int shardIndex;
    @JsonProperty
    private final String batchDirName;
    @JsonProperty
    private final String version;

    @JsonCreator
    public SharedFileStoreMergeEventData(@JsonProperty("shardIndex") final int shardIndex,
                               @JsonProperty("batchDirName") final String batchDirName,
                               @JsonProperty("version") final String version) {
        this.shardIndex = shardIndex;
        this.batchDirName = batchDirName;
        this.version = version;
    }

    public int getShardIndex() {
        return shardIndex;
    }

    public String getBatchDirName() {
        return batchDirName;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return "SharedFileStoreMergeEventData{" +
                "shardIndex=" + shardIndex +
                ", batchDirName='" + batchDirName + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
