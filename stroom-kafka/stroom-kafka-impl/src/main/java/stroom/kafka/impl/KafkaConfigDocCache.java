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

package stroom.kafka.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.kafka.shared.KafkaConfigDoc;
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

import java.util.Objects;
import java.util.Optional;

@Singleton
@EntityEventHandler(
        type = KafkaConfigDoc.TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
public class KafkaConfigDocCache implements Clearable, EntityEvent.Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConfigDocCache.class);

    private static final String CACHE_NAME = "Kafka Config Doc Cache";

    private final LoadingStroomCache<DocRef, Optional<KafkaConfigDoc>> cache;
    private final KafkaConfigStore kafkaConfigStore;
    private final SecurityContext securityContext;

    @Inject
    public KafkaConfigDocCache(final CacheManager cacheManager,
                               final KafkaConfigStore kafkaConfigStore,
                               final Provider<KafkaConfig> kafkaConfigProvider,
                               final SecurityContext securityContext) {
        this.kafkaConfigStore = kafkaConfigStore;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> kafkaConfigProvider.get().getKafkaConfigDocCache(),
                this::create);
    }

    public Optional<KafkaConfigDoc> get(final DocRef kafkaConfigDocRef) {
        return cache.get(kafkaConfigDocRef);
    }

    private Optional<KafkaConfigDoc> create(final DocRef kafkaConfigDocRef) {
        Objects.requireNonNull(kafkaConfigDocRef);
        return securityContext.asProcessingUserResult(() ->
                Optional.ofNullable(kafkaConfigStore.readDocument(kafkaConfigDocRef)));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("Received event {}", event);
        final EntityAction eventAction = event.getAction();

        switch (eventAction) {
            case CLEAR_CACHE -> {
                LOGGER.debug("Clearing cache");
                clear();
            }
            case UPDATE, DELETE -> {
                NullSafe.consume(
                        event.getDocRef(),
                        docRef -> {
                            LOGGER.debug("Invalidating docRef {}", docRef);
                            cache.invalidate(docRef);
                        });
            }
            default -> LOGGER.debug("Unexpected event action {}", eventAction);
        }
    }
}
