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

package stroom.feed;

import com.google.inject.persist.Transactional;
import stroom.entity.DocumentEntityServiceImpl;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.util.FieldMap;
import stroom.entity.util.HqlBuilder;
import stroom.explorer.ExplorerActionHandler;
import stroom.explorer.shared.DocumentType;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FindFeedCriteria;
import stroom.importexport.ImportExportActionHandler;
import stroom.importexport.ImportExportHelper;
import stroom.query.api.v2.DocRef;
import stroom.security.SecurityContext;
import stroom.spring.EntityManagerSupport;
import stroom.streamstore.shared.StreamType;
import stroom.util.config.StroomProperties;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Transient;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Singleton
@Transactional
public class FeedServiceImpl extends DocumentEntityServiceImpl<Feed, FindFeedCriteria> implements FeedService, ExplorerActionHandler, ImportExportActionHandler {
    private static final String FEED_NAME_PATTERN_PROPERTY = "stroom.feedNamePattern";
    private static final String FEED_NAME_PATTERN_VALUE = "^[A-Z0-9_\\-]{3,}$";
    private static final Set<String> FETCH_SET = Collections.singleton(StreamType.ENTITY_TYPE);

    @Inject
    FeedServiceImpl(final StroomEntityManager entityManager,
                    final EntityManagerSupport entityManagerSupport,
                    final ImportExportHelper importExportHelper,
                    final SecurityContext securityContext) {
        super(entityManager, entityManagerSupport, importExportHelper, securityContext);
    }

    @Override
    public DocRef copyDocument(String uuid, String parentFolderUUID) {
        throw new EntityServiceException("You cannot copy Feeds");
    }

    @SuppressWarnings("unchecked")
    @Override
    public Feed loadByName(final String name) {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e");
        sql.append(" FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");
        sql.append(" WHERE e.name = ");
        sql.arg(name);

        // This should just bring back 1
        final List<Feed> results = getEntityManager().executeQueryResultList(sql, null, true);

        Feed feed = null;
        if (results != null && results.size() > 0) {
            feed = results.get(0);
        }

        if (feed != null) {
            checkReadPermission(DocRefUtil.create(feed));
        }

        return feed;
    }

    @Override
    public Feed readDocument(final DocRef docRef) {
        return loadByUuid(docRef.getUuid(), FETCH_SET);
    }

    @Override
    public Class<Feed> getEntityClass() {
        return Feed.class;
    }

    @Override
    public FindFeedCriteria createCriteria() {
        return new FindFeedCriteria();
    }

    @Transient
    @Override
    public String getNamePattern() {
        return StroomProperties.getProperty(FEED_NAME_PATTERN_PROPERTY, FEED_NAME_PATTERN_VALUE);
    }

    @Override
    public String getDisplayClassification(final Feed feed) {
        String classification = null;

        if (feed != null) {
            classification = feed.getClassification();
        }

        if (classification == null || classification.trim().isEmpty()) {
            return StroomProperties.getProperty("stroom.unknownClassification");
        }

        return classification.trim().toUpperCase();
    }

    @Override
    protected Feed internalSave(final Feed entity) {
        if (entity != null) {
            if (entity.getStreamType() == null) {
                if (entity.isReference()) {
                    entity.setStreamType(StreamType.RAW_REFERENCE);
                } else {
                    entity.setStreamType(StreamType.RAW_EVENTS);
                }
            }
        }
        return super.internalSave(entity);
    }

    @Override
    protected QueryAppender<Feed, FindFeedCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new FeedQueryAppender(entityManager);
    }

    @Override
    protected FieldMap createFieldMap() {
        return super.createFieldMap()
                .add(FindFeedCriteria.FIELD_TYPE, Feed.REFERENCE, "reference")
                .add(FindFeedCriteria.FIELD_CLASSIFICATION, Feed.CLASSIFICATION, "classification");
    }

    @Override
    public DocumentType getDocumentType() {
        return new DocumentType(3, Feed.ENTITY_TYPE, Feed.ENTITY_TYPE);
    }

    private static class FeedQueryAppender extends QueryAppender<Feed, FindFeedCriteria> {
        FeedQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicJoin(final HqlBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);
            if (fetchSet != null && fetchSet.contains(StreamType.ENTITY_TYPE)) {
                sql.append(" INNER JOIN FETCH " + alias + ".streamType");
            }
        }

        @Override
        protected void appendBasicCriteria(final HqlBuilder sql, final String alias, final FindFeedCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            sql.appendValueQuery(alias + ".reference", criteria.getReference());
            sql.appendEntityIdSetQuery(alias, criteria.getFeedIdSet());
        }

        @Override
        protected void preSave(final Feed entity) {
            if (entity != null) {
                if (entity.getStreamType() == null) {
                    if (entity.isReference()) {
                        entity.setStreamType(StreamType.RAW_REFERENCE);
                    } else {
                        entity.setStreamType(StreamType.RAW_EVENTS);
                    }
                }
            }

            super.preSave(entity);
        }
    }
}
