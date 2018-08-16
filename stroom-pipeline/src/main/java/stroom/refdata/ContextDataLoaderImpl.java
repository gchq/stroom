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
import stroom.task.TaskManager;

import javax.inject.Inject;
import java.io.InputStream;

public class ContextDataLoaderImpl implements ContextDataLoader {
    private final TaskManager taskManager;

    @Inject
    ContextDataLoaderImpl(final TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public void load(final InputStream inputStream,
                     final Stream stream,
                     final Feed feed,
                     final DocRef contextPipeline,
                     final RefStreamDefinition refStreamDefinition,
                     final RefDataStore refDataStore) {

        taskManager.exec(new ContextDataLoadTask(
                inputStream, stream, feed, contextPipeline, refStreamDefinition, refDataStore));
    }
}
