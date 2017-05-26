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

package stroom.statistics.server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.AutoMarshal;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.SQLUtil;
import stroom.entity.server.util.StroomEntityManager;
import stroom.importexport.server.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.statistics.common.FindStatisticsEntityCriteria;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.shared.StatisticStoreEntity;

import javax.inject.Inject;

@Component
@Transactional
@AutoMarshal
public class StatisticStoreEntityServiceImpl
        extends DocumentEntityServiceImpl<StatisticStoreEntity, FindStatisticsEntityCriteria>
        implements StatisticStoreEntityService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticStoreEntityServiceImpl.class);

    @Inject
    StatisticStoreEntityServiceImpl(final StroomEntityManager entityManager, final ImportExportHelper importExportHelper, final SecurityContext securityContext) {
        super(entityManager, importExportHelper, securityContext);
        LOGGER.debug("StatisticsDataSourceServiceImpl initialised");
    }

    @Override
    public Class<StatisticStoreEntity> getEntityClass() {
        return StatisticStoreEntity.class;
    }

    @Override
    public FindStatisticsEntityCriteria createCriteria() {
        return FindStatisticsEntityCriteria.instance();
    }

    @Override
    protected QueryAppender<StatisticStoreEntity, FindStatisticsEntityCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new StatisticStoreEntityQueryAppender(entityManager);
    }

    private static class StatisticStoreEntityQueryAppender extends QueryAppender<StatisticStoreEntity, FindStatisticsEntityCriteria> {
        public StatisticStoreEntityQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        protected void appendBasicCriteria(final stroom.entity.server.util.SQLBuilder sql, final String alias,
                                           final FindStatisticsEntityCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);

            if (criteria.getStatisticType() != null) {
                SQLUtil.appendValueQuery(sql, alias + ".pStatisticType", criteria.getStatisticType().getPrimitiveValue());
            }
        }
    }
}
