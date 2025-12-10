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

package stroom.aws.s3.impl;

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.aws.s3.shared.S3ConfigDoc;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.json.JsonUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

@Singleton
@EntityEventHandler(
        type = S3ConfigDoc.TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
public class S3ClientConfigCache implements Clearable, EntityEvent.Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3ClientConfigCache.class);

    private static final String CACHE_NAME = "S3 Config Doc Cache";

    private final LoadingStroomCache<DocRef, Optional<S3ClientConfig>> cache;
    private final S3ConfigStore s3ConfigStore;
    private final SecurityContext securityContext;

    @Inject
    public S3ClientConfigCache(final CacheManager cacheManager,
                               final S3ConfigStore s3ConfigStore,
                               final Provider<S3Config> s3ConfigProvider,
                               final SecurityContext securityContext) {
        this.s3ConfigStore = s3ConfigStore;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> s3ConfigProvider.get().getS3ConfigDocCache(),
                this::create);
    }

    public Optional<S3ClientConfig> get(final DocRef s3ConfigDocRef) {
        if (!securityContext.hasDocumentPermission(s3ConfigDocRef, DocumentPermission.USE)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to use " + s3ConfigDocRef);
        }

        return cache.get(s3ConfigDocRef);
    }

    private Optional<S3ClientConfig> create(final DocRef s3ConfigDocRef) {
        Objects.requireNonNull(s3ConfigDocRef);
        return securityContext.asProcessingUserResult(() -> {
            final S3ConfigDoc s3ConfigDoc = s3ConfigStore.readDocument(s3ConfigDocRef);
            if (s3ConfigDoc == null) {
                return Optional.empty();
            }

            return Optional.ofNullable(JsonUtil.readValue(s3ConfigDoc.getData(), S3ClientConfig.class));
        });
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
