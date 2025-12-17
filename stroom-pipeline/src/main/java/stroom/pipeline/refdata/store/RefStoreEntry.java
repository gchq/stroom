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

package stroom.pipeline.refdata.store;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class RefStoreEntry {

    @JsonProperty
    private final String feedName;
    @JsonProperty
    private final MapDefinition mapDefinition;
    @JsonProperty
    private final String key; // simple key or a str representation of a range (e.g. 1-100)
    @JsonProperty
    private final String value;
    @JsonProperty
    private int valueReferenceCount;
    @JsonProperty
    private final RefDataProcessingInfo refDataProcessingInfo;

    @JsonCreator
    public RefStoreEntry(@JsonProperty("feedName") final String feedName,
                         @JsonProperty("mapDefinition") final MapDefinition mapDefinition,
                         @JsonProperty("key") final String key,
                         @JsonProperty("value") final String value,
                         @JsonProperty("valueReferenceCount") final int valueReferenceCount,
                         @JsonProperty("refDataProcessingInfo") final RefDataProcessingInfo refDataProcessingInfo) {
        this.feedName = feedName;
        this.mapDefinition = mapDefinition;
        this.key = key;
        this.value = value;
        this.valueReferenceCount = valueReferenceCount;
        this.refDataProcessingInfo = refDataProcessingInfo;
    }

    public String getFeedName() {
        return feedName;
    }

    public MapDefinition getMapDefinition() {
        return mapDefinition;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public int getValueReferenceCount() {
        return valueReferenceCount;
    }

    public RefDataProcessingInfo getRefDataProcessingInfo() {
        return refDataProcessingInfo;
    }

    @Override
    public String toString() {
        return "RefStoreEntry{" +
                "feedName=" + feedName +
                ", mapDefinition=" + mapDefinition +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", valueReferenceCount=" + valueReferenceCount +
                ", refDataProcessingInfo=" + refDataProcessingInfo +
                '}';
    }
}
