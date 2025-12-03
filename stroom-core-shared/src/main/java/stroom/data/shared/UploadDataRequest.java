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

package stroom.data.shared;

import stroom.util.shared.ResourceKey;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class UploadDataRequest {

    @JsonProperty
    private final ResourceKey key;
    @JsonProperty
    private final String feedName;
    @JsonProperty
    private final String streamTypeName;
    @JsonProperty
    private final Long effectiveMs;
    @JsonProperty
    private final String metaData;
    @JsonProperty
    private final String fileName;

    @JsonCreator
    public UploadDataRequest(@JsonProperty("key") final ResourceKey key,
                             @JsonProperty("feedName") final String feedName,
                             @JsonProperty("streamTypeName") final String streamTypeName,
                             @JsonProperty("effectiveMs") final Long effectiveMs,
                             @JsonProperty("metaData") final String metaData,
                             @JsonProperty("fileName") final String fileName) {
        this.key = key;
        this.feedName = feedName;
        this.streamTypeName = streamTypeName;
        this.effectiveMs = effectiveMs;
        this.metaData = metaData;
        this.fileName = fileName;
    }

    public ResourceKey getKey() {
        return key;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getStreamTypeName() {
        return streamTypeName;
    }

    public Long getEffectiveMs() {
        return effectiveMs;
    }

    public String getMetaData() {
        return metaData;
    }

    public String getFileName() {
        return fileName;
    }
}
