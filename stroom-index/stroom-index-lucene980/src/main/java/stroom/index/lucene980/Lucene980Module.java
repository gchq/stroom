/*
 * Copyright 2018 Crown Copyright
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

package stroom.index.lucene980;

import stroom.docstore.api.ContentIndex;
import stroom.index.impl.LuceneProvider;
import stroom.job.api.ScheduledJobsBinder;
import stroom.search.extraction.MemoryIndex;
import stroom.util.RunnableWrapper;
import stroom.util.entityevent.EntityEvent;
import stroom.util.guice.GuiceUtil;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class Lucene980Module extends AbstractModule {

    @Override
    protected void configure() {
        bind(MemoryIndex.class).to(stroom.index.lucene980.Lucene980MemoryIndex.class);
        bind(ContentIndex.class).to(ContentIndexImpl.class);

        GuiceUtil.buildMultiBinder(binder(), EntityEvent.Handler.class)
                .addBinding(ContentIndexImpl.class);

        // Bind this provider.
        GuiceUtil.buildMultiBinder(binder(), LuceneProvider.class).addBinding(Lucene980Provider.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(IndexContent.class, builder -> builder
                        .name("Index Content")
                        .description("Index Stroom content to improve content find performance.")
                        .managed(true)
                        .enabled(false)
                        .advanced(true)
                        .frequencySchedule("1m"));
    }

    private static class IndexContent extends RunnableWrapper {

        @Inject
        IndexContent(final ContentIndexImpl contentIndex) {
            super(contentIndex::reindex);
        }
    }
}
