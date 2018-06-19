/*
 * Copyright 2017 Crown Copyright
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

package stroom.streamstore.store;

import stroom.task.ServerTask;
import stroom.util.shared.VoidResult;

import java.nio.file.Path;

public class StreamUploadTask extends ServerTask<VoidResult> {
    private String fileName;
    private Path file;
    private String feedName;
    private String streamTypeName;
    private Long effectiveMs;
    private String metaData;

    public StreamUploadTask() {
    }

    public StreamUploadTask(final String userToken, final String fileName, final Path file,
                            final String feedName, final String streamTypeName, final Long effectiveMs,
                            final String metaData) {
        super(null, userToken);
        this.fileName = fileName;
        this.file = file;
        this.feedName = feedName;
        this.streamTypeName = streamTypeName;
        this.metaData = metaData;
        this.effectiveMs = effectiveMs;
    }

    public Path getFile() {
        return file;
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
