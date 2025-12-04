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
import stroom.security.impl.AuthorisationConfig;
import stroom.security.impl.DocTypeIdDao;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.types.UByte;

import java.util.Optional;

import static stroom.security.impl.db.jooq.tables.PermissionDocTypeId.PERMISSION_DOC_TYPE_ID;

@Singleton
class DocTypeIdDaoImpl implements DocTypeIdDao, Clearable {
    private static final String ID_CACHE_NAME = "Doc Type Id Cache";
    private static final String NAME_CACHE_NAME = "Doc Type Name Cache";

    private final LoadingStroomCache<String, Integer> idCache;
    private final StroomCache<Integer, String> nameCache;
    private final SecurityDbConnProvider securityDbConnProvider;

    @Inject
    DocTypeIdDaoImpl(final SecurityDbConnProvider securityDbConnProvider,
                     final CacheManager cacheManager,
                     final Provider<AuthorisationConfig> authorisationConfigProvider) {
        this.securityDbConnProvider = securityDbConnProvider;
        idCache = cacheManager.createLoadingCache(
                ID_CACHE_NAME,
                () -> authorisationConfigProvider.get().getDocTypeIdCache(),
                this::load);
        nameCache = cacheManager.create(
                NAME_CACHE_NAME,
                () -> authorisationConfigProvider.get().getDocTypeIdCache());
    }

    private int load(final String docType) {
        // Try and get the existing id from the DB.
        return fetchIdFromDb(docType)
                .or(() -> {
                    // The id isn't in the DB so create it.
                    return tryCreate(docType)
                            .or(() -> {
                                // If the id is still null then this may be because the create method failed
                                // due to the name having been inserted into the DB by another thread prior
                                // to us calling create and the DB preventing duplicate names.
                                // Assuming this is the case, try and get the id from the DB one last time.
                                return fetchIdFromDb(docType);
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
                    new RuntimeException("No document type can be found in " +
                            PERMISSION_DOC_TYPE_ID.getName() +
                            " table for id=" +
                            id));
            nameCache.put(id, docType);
        }
        return docType;
    }

    private Optional<String> fetchNameFromDb(final int id) {
        final UByte docTypeId = UByte.valueOf(id);
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                .select(PERMISSION_DOC_TYPE_ID.TYPE)
                .from(PERMISSION_DOC_TYPE_ID)
                .where(PERMISSION_DOC_TYPE_ID.ID.eq(docTypeId))
                .fetchOptional(PERMISSION_DOC_TYPE_ID.TYPE));
    }

    private Optional<Integer> fetchIdFromDb(final String docType) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select(PERMISSION_DOC_TYPE_ID.ID)
                        .from(PERMISSION_DOC_TYPE_ID)
                        .where(PERMISSION_DOC_TYPE_ID.TYPE.eq(docType))
                        .fetchOptional(PERMISSION_DOC_TYPE_ID.ID)
                )
                .map(UByte::intValue);
    }

    Optional<Integer> tryCreate(final String docType) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .insertInto(PERMISSION_DOC_TYPE_ID)
                        .columns(PERMISSION_DOC_TYPE_ID.TYPE)
                        .values(docType)
                        .onDuplicateKeyUpdate()
                        .set(PERMISSION_DOC_TYPE_ID.TYPE, docType)
                        .returning(PERMISSION_DOC_TYPE_ID.ID)
                        .fetchOptional()
                )
                .map(r -> r.get(PERMISSION_DOC_TYPE_ID.ID).intValue());
    }

    @Override
    public void clear() {
        idCache.clear();
        nameCache.clear();
    }
}
