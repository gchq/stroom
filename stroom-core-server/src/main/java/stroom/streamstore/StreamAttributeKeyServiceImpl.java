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

package stroom.streamstore;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.SystemEntityServiceImpl;
import stroom.entity.util.FieldMap;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.security.Insecure;
import stroom.streamstore.shared.FindStreamAttributeKeyCriteria;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Transactional
@Insecure
class StreamAttributeKeyServiceImpl
        extends SystemEntityServiceImpl<StreamAttributeKey, FindStreamAttributeKeyCriteria>
        implements StreamAttributeKeyService {
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final LoadingCache<String, BaseResultList<StreamAttributeKey>> cache;

    @Inject
    @SuppressWarnings("unchecked")
    StreamAttributeKeyServiceImpl(final StroomEntityManager entityManager,
                                  final CacheManager cacheManager) {
        super(entityManager);
        final CacheLoader<String, BaseResultList<StreamAttributeKey>> cacheLoader = CacheLoader.from(k -> find(new FindStreamAttributeKeyCriteria()));
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(10, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Stream Attribute Key Cache", cacheBuilder, cache);
    }

    @Override
    public Class<StreamAttributeKey> getEntityClass() {
        return StreamAttributeKey.class;
    }

    @Override
    public BaseResultList<StreamAttributeKey> findAll() throws RuntimeException {
        return cache.getUnchecked("findAll");
    }

    @Override
    public FindStreamAttributeKeyCriteria createCriteria() {
        return new FindStreamAttributeKeyCriteria();
    }

    @Override
    protected FieldMap createFieldMap() {
        return super.createFieldMap()
                .add(FindStreamAttributeKeyCriteria.FIELD_NAME, StreamAttributeKey.NAME, "name");
    }
}
