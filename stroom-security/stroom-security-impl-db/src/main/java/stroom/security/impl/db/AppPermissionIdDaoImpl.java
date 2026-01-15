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

package stroom.security.impl.db;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.cache.api.StroomCache;
import stroom.db.util.JooqUtil;
import stroom.security.impl.AppPermissionIdDao;
import stroom.security.impl.AuthorisationConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.types.UByte;

import java.util.Optional;

import static stroom.security.impl.db.jooq.tables.PermissionAppId.PERMISSION_APP_ID;

@Singleton
class AppPermissionIdDaoImpl implements AppPermissionIdDao, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AppPermissionIdDaoImpl.class);

    private static final String ID_CACHE_NAME = "App Permission Id Cache";
    private static final String NAME_CACHE_NAME = "App Permission Name Cache";

    private final LoadingStroomCache<String, Integer> idCache;
    private final StroomCache<Integer, String> nameCache;
    private final SecurityDbConnProvider securityDbConnProvider;

    @Inject
    AppPermissionIdDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                           final CacheManager cacheManager,
                           final Provider<AuthorisationConfig> authorisationConfigProvider) {
        this.securityDbConnProvider = securityDbConnProvider;
        idCache = cacheManager.createLoadingCache(
                ID_CACHE_NAME,
                () -> authorisationConfigProvider.get().getAppPermissionIdCache(),
                this::load);
        nameCache = cacheManager.create(
                NAME_CACHE_NAME,
                () -> authorisationConfigProvider.get().getAppPermissionIdCache());
    }

    private int load(final String permission) {
        // Try and get the existing id from the DB.
        return fetchIdFromDb(permission)
                .or(() -> {
                    // The id isn't in the DB so create it.
                    return tryCreate(permission)
                            .or(() -> {
                                // If the id is still null then this may be because the create method failed
                                // due to the name having been inserted into the DB by another thread prior
                                // to us calling create and the DB preventing duplicate names.
                                // Assuming this is the case, try and get the id from the DB one last time.
                                return fetchIdFromDb(permission);
                            });
                })
                .orElseThrow();
    }

    @Override
    public int getOrCreateId(final String docType) {
        return idCache.get(docType);
    }

    @Override
    public String get(final int id) {
        String docType = nameCache.get(id);
        if (docType == null) {
            final Optional<String> optional = fetchNameFromDb(id);
            docType = optional.orElseThrow(() ->
                    new RuntimeException("No app permission can be found in " +
                            PERMISSION_APP_ID.getName() +
                            " table for id=" +
                            id));
            nameCache.put(id, docType);
        }
        return docType;
    }

    private Optional<String> fetchNameFromDb(final int id) {
        final UByte appPermissionId = UByte.valueOf(id);
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                .select(PERMISSION_APP_ID.PERMISSION)
                .from(PERMISSION_APP_ID)
                .where(PERMISSION_APP_ID.ID.eq(appPermissionId))
                .fetchOptional(PERMISSION_APP_ID.PERMISSION));
    }

    private Optional<Integer> fetchIdFromDb(final String permission) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(PERMISSION_APP_ID.ID)
                        .from(PERMISSION_APP_ID)
                        .where(PERMISSION_APP_ID.PERMISSION.eq(permission))
                        .fetchOptional(PERMISSION_APP_ID.ID))
                .map(UByte::intValue);
    }

    Optional<Integer> tryCreate(final String permission) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .insertInto(PERMISSION_APP_ID)
                        .columns(PERMISSION_APP_ID.PERMISSION)
                        .values(permission)
                        .onDuplicateKeyUpdate()
                        .set(PERMISSION_APP_ID.PERMISSION, permission)
                        .returning(PERMISSION_APP_ID.ID)
                        .fetchOptional()
                )
                .map(r -> r.get(PERMISSION_APP_ID.ID).intValue());
    }

    @Override
    public void clear() {
        idCache.clear();
        nameCache.clear();
    }
}
