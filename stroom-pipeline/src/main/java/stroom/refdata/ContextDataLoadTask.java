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

package stroom.refdata;

import stroom.docref.DocRef;
import stroom.data.meta.api.Data;
import stroom.task.api.ServerTask;

import java.io.InputStream;

public class ContextDataLoadTask extends ServerTask<MapStore> {
    private InputStream inputStream;
    private Data stream;
    private String feedName;
    private DocRef contextPipeline;

    public ContextDataLoadTask() {
    }

    public ContextDataLoadTask(final InputStream inputStream, final Data stream, final String feedName,
                               final DocRef contextPipeline) {
        this.inputStream = inputStream;
        this.stream = stream;
        this.feedName = feedName;
        this.contextPipeline = contextPipeline;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public Data getStream() {
        return stream;
    }

    public String getFeedName() {
        return feedName;
    }

    public DocRef getContextPipeline() {
        return contextPipeline;
    }
}
