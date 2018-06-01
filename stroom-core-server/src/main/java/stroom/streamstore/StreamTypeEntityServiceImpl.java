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

package stroom.streamstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.NamedEntityServiceImpl;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.util.HqlBuilder;
import stroom.security.Security;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.StreamTypeEntity;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StreamTypeEntityServiceImpl extends NamedEntityServiceImpl<StreamTypeEntity, FindStreamTypeCriteria> implements StreamTypeEntityService, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamTypeEntityServiceImpl.class);

    private final Map<String, StreamTypeEntity> cache = new ConcurrentHashMap<>();

    @Inject
    StreamTypeEntityServiceImpl(final StroomEntityManager entityManager,
                                final Security security) {
        super(entityManager, security);
    }

    /**
     * @return the stream type by it's ID or null
     */
    @SuppressWarnings("unchecked")
    @Override
    public StreamTypeEntity get(final String name) {
        StreamTypeEntity streamType = cache.get(name);
        if (streamType != null) {
            return streamType;
        }

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");
        sql.append(" WHERE e.name = ");
        sql.arg(name);

        // This should just bring back 1
        final List<StreamTypeEntity> results = getEntityManager().executeQueryResultList(sql);

        if (results != null && results.size() > 0) {
            streamType = results.get(0);
            cache.put(name, streamType);
        }

        return streamType;
    }

    @Override
    public StreamTypeEntity getOrCreate(final String name) {
        StreamTypeEntity streamType = get(name);
        if (streamType == null) {
            try {
                // Try and create.
                streamType = create(name);
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
                streamType = get(name);
            }

            cache.put(name, streamType);
        }

        return streamType;
    }

    @Override
    public long getId(final String name) {
        return getOrCreate(name).getId();
    }

    @Override
    public EntityIdSet<StreamTypeEntity> convertNameSet(final CriteriaSet<String> streamTypes) {
        if (streamTypes == null) {
            return null;
        }

        final EntityIdSet<StreamTypeEntity> entityIdSet = new EntityIdSet<>();
        entityIdSet.setMatchAll(streamTypes.getMatchAll());
        entityIdSet.setMatchNull(streamTypes.getMatchNull());
        streamTypes.forEach(streamTypeName -> entityIdSet.add(getId(streamTypeName)));

        return entityIdSet;
    }

    @Override
    public BaseResultList<StreamTypeEntity> find(final FindStreamTypeCriteria criteria) {
        return super.find(criteria);
    }

    @Override
    public Class<StreamTypeEntity> getEntityClass() {
        return StreamTypeEntity.class;
    }

    @Override
    public FindStreamTypeCriteria createCriteria() {
        return new FindStreamTypeCriteria();
    }

    @Override
    protected QueryAppender<StreamTypeEntity, FindStreamTypeCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new StreamTypeQueryAppender(entityManager);
    }

    @Override
    protected String permission() {
        return null;
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private static class StreamTypeQueryAppender extends QueryAppender<StreamTypeEntity, FindStreamTypeCriteria> {
        StreamTypeQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        public void appendBasicCriteria(HqlBuilder sql, String alias, FindStreamTypeCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);
            sql.appendPrimitiveValueSetQuery(alias + ".ppurpose", criteria.getPurpose());
        }
    }
}
