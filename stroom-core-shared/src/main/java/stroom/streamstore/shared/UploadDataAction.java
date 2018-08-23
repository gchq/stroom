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

package stroom.streamstore.shared;

import stroom.task.shared.Action;
import stroom.util.shared.ResourceKey;

public class UploadDataAction extends Action<ResourceKey> {
    private static final long serialVersionUID = 1799514675431383541L;
    private ResourceKey key;
    private String feedName;
    private String streamTypeName;
    private Long effectiveMs;
    private String metaData;
    private String fileName;

    public UploadDataAction() {
    }

    public UploadDataAction(final ResourceKey resourceKey,
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

    @Override
    public String getTaskName() {
        return "Import Data";
    }
}
