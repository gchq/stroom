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
import stroom.streamstore.shared.Feed;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class FeedServiceImpl extends NamedEntityServiceImpl<Feed, FindFeedCriteria> implements FeedService, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeedServiceImpl.class);

    private final Map<String, Feed> cache = new ConcurrentHashMap<>();

    @Inject
    FeedServiceImpl(final StroomEntityManager entityManager,
                    final Security security) {
        super(entityManager, security);
    }

    /**
     * @return the feed by it's ID or null
     */
    @SuppressWarnings("unchecked")
    @Override
    public Feed get(final String name) {
        Feed feed = cache.get(name);
        if (feed != null) {
            return feed;
        }

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");
        sql.append(" WHERE e.name = ");
        sql.arg(name);

        // This should just bring back 1
        final List<Feed> results = getEntityManager().executeQueryResultList(sql);

        if (results != null && results.size() > 0) {
            feed = results.get(0);
            cache.put(name, feed);
        }

        return feed;
    }

    @Override
    public Feed getOrCreate(final String name) {
        Feed feed = get(name);
        if (feed == null) {
            try {
                // Try and create.
                feed = create(name);
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
                feed = get(name);
            }

            cache.put(name, feed);
        }

        return feed;
    }

    @Override
    public long getId(final String name) {
        return getOrCreate(name).getId();
    }

    @Override
    public EntityIdSet<Feed> convertNameSet(final CriteriaSet<String> feeds) {
        if (feeds == null) {
            return null;
        }

        final EntityIdSet<Feed> entityIdSet = new EntityIdSet<>();
        entityIdSet.setMatchAll(feeds.getMatchAll());
        entityIdSet.setMatchNull(feeds.getMatchNull());
        feeds.forEach(feedName -> entityIdSet.add(getId(feedName)));

        return entityIdSet;
    }

    @Override
    public BaseResultList<Feed> find(final FindFeedCriteria criteria) {
        return super.find(criteria);
    }

    @Override
    public Class<Feed> getEntityClass() {
        return Feed.class;
    }

    @Override
    public FindFeedCriteria createCriteria() {
        return new FindFeedCriteria();
    }

    @Override
    protected QueryAppender<Feed, FindFeedCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new FeedQueryAppender(entityManager);
    }

    @Override
    protected String permission() {
        return null;
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private static class FeedQueryAppender extends QueryAppender<Feed, FindFeedCriteria> {
        FeedQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

//        @Override
//        public void appendBasicCriteria(HqlBuilder sql, String alias, FindStreamTypeCriteria criteria) {
//            super.appendBasicCriteria(sql, alias, criteria);
//            sql.appendPrimitiveValueSetQuery(alias + ".ppurpose", criteria.getPurpose());
//        }
    }



//
//
//
//
//
//
//
//    @Override
//    public DocRef copyDocument(String uuid) {
//        throw new EntityServiceException("You cannot copy Feeds");
//    }
//
//    @SuppressWarnings("unchecked")
//    @Override
//    public Fd loadByName(final String name) {
//        final HqlBuilder sql = new HqlBuilder();
//        sql.append("SELECT e");
//        sql.append(" FROM ");
//        sql.append(getEntityClass().getName());
//        sql.append(" AS e");
//        sql.append(" WHERE e.name = ");
//        sql.arg(name);
//
//        // This should just bring back 1
//        final List<Fd> results = getEntityManager().executeQueryResultList(sql, null, true);
//
//        Fd feed = null;
//        if (results != null && results.size() > 0) {
//            feed = results.get(0);
//        }
//
//        return feed;
//    }
//
//    @Override
//    public Fd readDocument(final DocRef docRef) {
//        return loadByUuid(docRef.getUuid(), FETCH_SET);
//    }
//
//    @Override
//    public Class<Fd> getEntityClass() {
//        return Fd.class;
//    }
//
//    @Override
//    public FindFdCriteria createCriteria() {
//        return new FindFdCriteria();
//    }
//
//    @Transient
//    @Override
//    public String getNamePattern() {
//        return StroomProperties.getProperty(FEED_NAME_PATTERN_PROPERTY, FEED_NAME_PATTERN_VALUE);
//    }
//
//    @Override
//    protected Fd internalSave(final Fd entity) {
//        if (entity != null) {
//            if (entity.getStreamType() == null) {
//                if (entity.isReference()) {
//                    entity.setStreamType(StreamType.RAW_REFERENCE);
//                } else {
//                    entity.setStreamType(StreamType.RAW_EVENTS);
//                }
//            }
//        }
//        return super.internalSave(entity);
//    }
//
//    @Override
//    protected QueryAppender<Fd, FindFdCriteria> createQueryAppender(final StroomEntityManager entityManager) {
//        return new FeedQueryAppender(entityManager);
//    }
//
//    @Override
//    protected FieldMap createFieldMap() {
//        return super.createFieldMap()
//                .add(FindFdCriteria.FIELD_TYPE, Fd.REFERENCE, "reference")
//                .add(FindFdCriteria.FIELD_CLASSIFICATION, Fd.CLASSIFICATION, "classification");
//    }
//
//    @Override
//    public DocumentType getDocumentType() {
//        return new DocumentType(3, Fd.ENTITY_TYPE, Fd.ENTITY_TYPE);
//    }
//
//    private static class FeedQueryAppender extends QueryAppender<Fd, FindFdCriteria> {
//        FeedQueryAppender(final StroomEntityManager entityManager) {
//            super(entityManager);
//        }
//
//        @Override
//        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
//            super.appendBasicJoin(sql, alias, fetchSet);
//            if (fetchSet != null && fetchSet.contains(StreamType.ENTITY_TYPE)) {
//                sql.append(" INNER JOIN FETCH " + alias + ".streamType");
//            }
//        }
//
//        @Override
//        protected void appendBasicCriteria(final HqlBuilder sql, final String alias, final FindFdCriteria criteria) {
//            super.appendBasicCriteria(sql, alias, criteria);
//
//            sql.appendValueQuery(alias + ".reference", criteria.getReference());
//            sql.appendEntityIdSetQuery(alias, criteria.getFeedIdSet());
//        }
//
//        @Override
//        protected void preSave(final Fd entity) {
//            if (entity != null) {
//                if (entity.getStreamType() == null) {
//                    if (entity.isReference()) {
//                        entity.setStreamType(StreamType.RAW_REFERENCE);
//                    } else {
//                        entity.setStreamType(StreamType.RAW_EVENTS);
//                    }
//                }
//            }
//
//            super.preSave(entity);
//        }
//    }
}
