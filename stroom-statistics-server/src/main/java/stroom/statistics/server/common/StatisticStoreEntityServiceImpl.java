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

import stroom.entity.server.AutoMarshal;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.server.util.SQLUtil;
import stroom.entity.shared.StringCriteria;
import stroom.security.SecurityContext;
import stroom.statistics.common.FindStatisticsEntityCriteria;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.logging.StroomLogger;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.List;

@Component
@Transactional
@AutoMarshal
public class StatisticStoreEntityServiceImpl
        extends DocumentEntityServiceImpl<StatisticStoreEntity, FindStatisticsEntityCriteria>
        implements StatisticStoreEntityService {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StatisticStoreEntityServiceImpl.class);

    @Inject
    StatisticStoreEntityServiceImpl(final StroomEntityManager entityManager, final SecurityContext securityContext) {
        super(entityManager, securityContext);
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

            final List<String> engineNames = criteria.getEngineNames();

            if (engineNames != null) {
                if (engineNames.size() == 0) {
                } else if (engineNames.size() == 1) {
                    SQLUtil.appendValueQuery(sql, alias + ".engineName", engineNames.get(0));
                } else {
                    SQLUtil.appendValuesQuery(sql, alias + ".engineName", StringCriteria.convertStringList(engineNames));
                }
            }

            if (criteria.getStatisticType() != null) {
                SQLUtil.appendValueQuery(sql, alias + ".pStatisticType", criteria.getStatisticType().getPrimitiveValue());
            }
        }
    }
}
