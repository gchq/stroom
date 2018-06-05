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

package stroom.streamstore.meta.db;

import event.logging.BaseAdvancedQueryItem;
import stroom.entity.CriteriaLoggingUtil;
import stroom.entity.QueryAppender;
import stroom.entity.StroomEntityManager;
import stroom.entity.SystemEntityServiceImpl;
import stroom.entity.util.HqlBuilder;
import stroom.entity.util.SqlBuilder;
import stroom.security.Security;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.FindStreamAttributeValueCriteria;
import stroom.streamstore.shared.StreamAttributeValue;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
class StreamAttributeValueServiceImpl
        extends SystemEntityServiceImpl<StreamAttributeValue, FindStreamAttributeValueCriteria>
        implements StreamAttributeValueService {
    private final StroomEntityManager entityManager;
    private final Security security;

    @Inject
    StreamAttributeValueServiceImpl(final StroomEntityManager entityManager,
                                    final Security security) {
        super(entityManager, security);
        this.entityManager = entityManager;
        this.security = security;
    }

    @Override
    public Long findDelete(final FindStreamAttributeValueCriteria criteria) {
        return security.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
            final SqlBuilder sql = new SqlBuilder();
            sql.append("DELETE FROM ");
            sql.append(StreamAttributeValue.TABLE_NAME);
            sql.append(" WHERE 1=1");

            if (criteria.getCreatePeriod() == null || !criteria.getCreatePeriod().isConstrained()) {
                throw new IllegalArgumentException("findDelete must be called with a create range");
            }

            sql.appendRangeQuery(StreamAttributeValue.CREATE_MS, criteria.getCreatePeriod());

            return entityManager.executeNativeUpdate(sql);
        });
    }

    @Override
    public Class<StreamAttributeValue> getEntityClass() {
        return StreamAttributeValue.class;
    }

    @Override
    public FindStreamAttributeValueCriteria createCriteria() {
        return new FindStreamAttributeValueCriteria();
    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items,
                               final FindStreamAttributeValueCriteria criteria) {
        CriteriaLoggingUtil.appendCriteriaSet(items, "streamIdSet", criteria.getStreamIdSet());
        CriteriaLoggingUtil.appendRangeTerm(items, "createPeriod", criteria.getCreatePeriod());
        super.appendCriteria(items, criteria);
    }

    @Override
    protected QueryAppender<StreamAttributeValue, FindStreamAttributeValueCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new StreamAttributeValueQueryAppender(entityManager);
    }

    @Override
    protected String permission() {
        return null;
    }

    private static class StreamAttributeValueQueryAppender extends QueryAppender<StreamAttributeValue, FindStreamAttributeValueCriteria> {
        StreamAttributeValueQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        public void appendBasicCriteria(HqlBuilder sql, String alias, FindStreamAttributeValueCriteria criteria) {
            sql.appendCriteriaSetQuery(alias + ".streamId", criteria.getStreamIdSet());
        }
    }
}
