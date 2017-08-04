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
 *
 */

package stroom.streamstore.server;

import com.googlecode.ehcache.annotations.Cacheable;
import com.googlecode.ehcache.annotations.KeyGenerator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.SystemEntityServiceImpl;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.security.Insecure;
import stroom.streamstore.shared.FindStreamAttributeKeyCriteria;
import stroom.streamstore.shared.StreamAttributeKey;

import javax.inject.Inject;

@Transactional
@Component
@Insecure
public class StreamAttributeKeyServiceImpl
        extends SystemEntityServiceImpl<StreamAttributeKey, FindStreamAttributeKeyCriteria>
        implements StreamAttributeKeyService {
    @Inject
    StreamAttributeKeyServiceImpl(final StroomEntityManager entityManager) {
        super(entityManager);
    }

    @Override
    public Class<StreamAttributeKey> getEntityClass() {
        return StreamAttributeKey.class;
    }

    @Override
    @Cacheable(cacheName = "serviceCache", keyGenerator = @KeyGenerator(name = "ListCacheKeyGenerator"))
    public BaseResultList<StreamAttributeKey> findAll() throws RuntimeException {
        return find(new FindStreamAttributeKeyCriteria());
    }

    @Override
    public FindStreamAttributeKeyCriteria createCriteria() {
        return new FindStreamAttributeKeyCriteria();
    }
}
