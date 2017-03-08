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

package stroom.streamstore.server.udload;

import stroom.query.api.DocRef;
import stroom.util.shared.VoidResult;
import stroom.util.task.ServerTask;

import java.io.File;

public class StreamUploadTask extends ServerTask<VoidResult> {
    private String fileName;
    private File file;
    private DocRef feed;
    private DocRef streamType;
    private Long effectiveMs;
    private String metaData;

    public StreamUploadTask() {
    }

    public StreamUploadTask(final String sessionId, final String userName, final String fileName, final File file,
                            final DocRef feed, final DocRef streamType, final Long effectiveMs,
            final String metaData) {
        super(null, sessionId, userName);
        this.fileName = fileName;
        this.file = file;
        this.feed = feed;
        this.streamType = streamType;
        this.metaData = metaData;
        this.effectiveMs = effectiveMs;
    }

    public File getFile() {
        return file;
    }

    public DocRef getFeed() {
        return feed;
    }

    public DocRef getStreamType() {
        return streamType;
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
