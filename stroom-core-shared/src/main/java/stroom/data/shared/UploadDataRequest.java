/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.data.shared;

import stroom.util.shared.ResourceKey;

public class UploadDataRequest {
    private ResourceKey key;
    private String feedName;
    private String streamTypeName;
    private Long effectiveMs;
    private String metaData;
    private String fileName;

    public UploadDataRequest() {
    }

    public UploadDataRequest(final ResourceKey resourceKey,
                             final String feedName,
                             final String streamTypeName,
                             final Long effectiveMs,
                             final String metaData,
                             final String fileName) {
        this.key = resourceKey;
        this.feedName = feedName;
        this.streamTypeName = streamTypeName;
        this.effectiveMs = effectiveMs;
        this.metaData = metaData;
        this.fileName = fileName;
    }

    public ResourceKey getKey() {
        return key;
    }

    public void setKey(final ResourceKey key) {
        this.key = key;
    }

    public String getFeedName() {
        return feedName;
    }

    public void setFeedName(final String feedName) {
        this.feedName = feedName;
    }

    public String getStreamTypeName() {
        return streamTypeName;
    }

    public void setStreamTypeName(final String streamTypeName) {
        this.streamTypeName = streamTypeName;
    }

    public Long getEffectiveMs() {
        return effectiveMs;
    }

    public void setEffectiveMs(final Long effectiveMs) {
        this.effectiveMs = effectiveMs;
    }

    public String getMetaData() {
        return metaData;
    }

    public void setMetaData(final String metaData) {
        this.metaData = metaData;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }
}
