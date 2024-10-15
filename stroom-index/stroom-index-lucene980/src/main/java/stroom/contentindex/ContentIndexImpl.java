/*
 * Copyright 2024 Crown Copyright
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

package stroom.contentindex;

import stroom.docref.DocContentHighlights;
import stroom.docref.DocContentMatch;
import stroom.docstore.api.ContentIndex;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.index.impl.ContentIndexConfig;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
@EntityEventHandler(
        action = {EntityAction.CREATE, EntityAction.UPDATE, EntityAction.DELETE})
public class ContentIndexImpl implements ContentIndex, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentIndexImpl.class);

    private final ContentIndex contentIndex;
    private final EntityEvent.Handler handler;


    @Inject
    public ContentIndexImpl(final ContentIndexConfig contentIndexConfig,
                            final Provider<LuceneContentIndex> luceneContentIndexProvider,
                            final Provider<BasicContentIndex> basicContentIndexProvider) {

        if (contentIndexConfig.isEnabled()) {
            final LuceneContentIndex index = luceneContentIndexProvider.get();
            handler = index;
            contentIndex = index;
            contentIndex.reindex();
        } else {
            handler = event -> {
            };
            contentIndex = basicContentIndexProvider.get();
        }
    }

    @Override
    public ResultPage<DocContentMatch> findInContent(final FindInContentRequest request) {
        return LOGGER.logDurationIfDebugEnabled(() ->
                contentIndex.findInContent(request), "findInContent");
    }

    @Override
    public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
        return LOGGER.logDurationIfDebugEnabled(() ->
                contentIndex.fetchHighlights(request), "fetchHighlights");
    }

    @Override
    public void reindex() {
        contentIndex.reindex();
    }

    @Override
    public void onChange(final EntityEvent event) {
        handler.onChange(event);
    }
}
