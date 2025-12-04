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

package stroom.feed.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.feed.shared.FeedDoc;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Singleton
@EntityEventHandler(
        type = FeedDoc.TYPE,
        // Need to react to CREATE as we hold an empty optional for non-existent feeds
        action = {EntityAction.CREATE, EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
public class FeedDocCache implements Clearable, EntityEvent.Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeedDocCache.class);

    private static final String CACHE_NAME = "Feed Doc Cache";

    private final LoadingStroomCache<String, Optional<FeedDoc>> cache;
    private final FeedStore feedStore;
    private final SecurityContext securityContext;

    @Inject
    public FeedDocCache(final CacheManager cacheManager,
                        final FeedStore feedStore,
                        final SecurityContext securityContext,
                        final Provider<FeedConfig> feedConfigProvider) {
        this.feedStore = feedStore;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> feedConfigProvider.get().getFeedDocCache(),
                this::create);
    }

    public Optional<FeedDoc> get(final String feedName) {
        return cache.get(feedName);
    }

    private Optional<FeedDoc> create(final String feedName) {
        return securityContext.asProcessingUserResult(() -> {
            final List<DocRef> list = feedStore.findByName(feedName);
            if (NullSafe.hasItems(list)) {
                return Optional.ofNullable(feedStore.readDocument(list.get(0)));
            }
            return Optional.empty();
        });
    }

    @Override
    public void clear() {
        LOGGER.debug("Clearing {}", CACHE_NAME);
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("Received entity event {}", event);
        final EntityAction eventAction = event.getAction();

        switch (eventAction) {
            case CLEAR_CACHE -> {
                LOGGER.debug("Clearing cache");
                clear();
            }
            case CREATE, UPDATE, DELETE -> {
                NullSafe.consume(event.getDocRef(), DocRef::getName, this::invalidateFeed);
                // Can't rename feeds, but here just in case
                NullSafe.consume(event.getOldDocRef(), DocRef::getName, this::invalidateFeed);
            }
            default -> LOGGER.debug("Unexpected event action {}", eventAction);
        }
    }

    private void invalidateFeed(final String feedName) {
        LOGGER.debug("Invalidating entry for feed '{}' in {}", feedName, CACHE_NAME);
        cache.invalidate(feedName);
    }
}
