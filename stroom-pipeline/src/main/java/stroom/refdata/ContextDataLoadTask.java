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
import stroom.feed.shared.Feed;
import stroom.refdata.store.RefDataStore;
import stroom.refdata.store.RefStreamDefinition;
import stroom.streamstore.shared.Stream;
import stroom.task.ServerTask;
import stroom.util.shared.VoidResult;

import java.io.InputStream;

public class ContextDataLoadTask extends ServerTask<VoidResult> {
    private InputStream inputStream;
    private Stream stream;
    private Feed feed;
    private DocRef contextPipeline;
    private RefStreamDefinition refStreamDefinition;
    private RefDataStore refDataStore;

    public ContextDataLoadTask() {
    }

    public ContextDataLoadTask(final InputStream inputStream,
                               final Stream stream,
                               final Feed feed,
                               final DocRef contextPipeline,
                               final RefStreamDefinition refStreamDefinition,
                               final RefDataStore refDataStore) {
        this.inputStream = inputStream;
        this.stream = stream;
        this.feed = feed;
        this.contextPipeline = contextPipeline;
        this.refStreamDefinition = refStreamDefinition;
        this.refDataStore = refDataStore;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public Stream getStream() {
        return stream;
    }

    public Feed getFeed() {
        return feed;
    }

    public DocRef getContextPipeline() {
        return contextPipeline;
    }

    public RefStreamDefinition getRefStreamDefinition() {
        return refStreamDefinition;
    }

    public RefDataStore getRefDataStore() {
        return refDataStore;
    }
}
