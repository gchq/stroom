/*
 * Copyright 2016 Crown Copyright
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

package stroom.feed.server;

import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.SQLUtil;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FeedService;
import stroom.feed.shared.FindFeedCriteria;
import stroom.importexport.server.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.streamstore.shared.StreamType;
import stroom.util.config.StroomProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import javax.persistence.Transient;
import java.util.List;
import java.util.Set;

@Transactional
@Component("feedService")
public class FeedServiceImpl extends DocumentEntityServiceImpl<Feed, FindFeedCriteria> implements FeedService {
    public static final String FEED_NAME_PATTERN_PROPERTY = "stroom.feedNamePattern";
    public static final String FEED_NAME_PATTERN_VALUE = "^[A-Z0-9_\\-]{3,}$";

    @Inject
    FeedServiceImpl(final StroomEntityManager entityManager, final ImportExportHelper importExportHelper, final SecurityContext securityContext) {
        super(entityManager, importExportHelper, securityContext);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Feed loadByName(final String name) {
        final SQLBuilder sql = new SQLBuilder();
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
            checkReadPermission(feed);
        }

        return feed;
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
    protected QueryAppender<Feed, FindFeedCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new FeedQueryAppender(entityManager);
    }

    private static class FeedQueryAppender extends QueryAppender<Feed, FindFeedCriteria> {
        public FeedQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicJoin(final SQLBuilder sql, final String alias, final Set<String> fetchSet) {
            super.appendBasicJoin(sql, alias, fetchSet);
            if (fetchSet != null && fetchSet.contains(StreamType.ENTITY_TYPE)) {
                sql.append(" INNER JOIN FETCH " + alias + ".streamType");
            }
        }

        @Override
        protected void appendBasicCriteria(final SQLBuilder sql, final String alias, final FindFeedCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            SQLUtil.appendValueQuery(sql, alias + ".reference", criteria.getReference());
            SQLUtil.appendSetQuery(sql, true, alias, criteria.getFeedIdSet());
        }
    }
}
