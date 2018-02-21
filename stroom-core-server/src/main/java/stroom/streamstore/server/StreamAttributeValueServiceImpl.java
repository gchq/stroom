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

package stroom.streamstore.server;

import event.logging.BaseAdvancedQueryItem;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.CriteriaLoggingUtil;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.SystemEntityServiceImpl;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.streamstore.shared.StreamAttributeValue;

import javax.inject.Inject;
import java.util.List;

@Transactional
class StreamAttributeValueServiceImpl
        extends SystemEntityServiceImpl<StreamAttributeValue, FindStreamAttributeValueCriteria>
        implements StreamAttributeValueService {
    private final StroomEntityManager entityManager;

    @Inject
    StreamAttributeValueServiceImpl(final StroomEntityManager entityManager) {
        super(entityManager);
        this.entityManager = entityManager;
    }

    @Override
    public Long findDelete(final FindStreamAttributeValueCriteria criteria) throws RuntimeException {
        final SqlBuilder sql = new SqlBuilder();
        sql.append("DELETE FROM ");
        sql.append(StreamAttributeValue.TABLE_NAME);
        sql.append(" WHERE 1=1");

        if (criteria.getCreatePeriod() == null || !criteria.getCreatePeriod().isConstrained()) {
            throw new IllegalArgumentException("findDelete must be called with a create range");
        }

        sql.appendRangeQuery(StreamAttributeValue.CREATE_MS, criteria.getCreatePeriod());

        return entityManager.executeNativeUpdate(sql);
    }

    @Override
    public Class<StreamAttributeValue> getEntityClass() {
        return StreamAttributeValue.class;
    }

//    @Override
//    public BaseResultList<StreamAttributeValue> find(final FindStreamAttributeValueCriteria criteria)
//            throws RuntimeException {
//        return doBasicFind(criteria);
//    }

    @Override
    public FindStreamAttributeValueCriteria createCriteria() {
        return new FindStreamAttributeValueCriteria();
    }
//
//    /**
//     * StreamAttributeValue are NA from a security POV as they just use the
//     * permission's of the parent stream.
//     */
//    @Override
//    public String getPermissionType() {
//        return null;
//    }

    @Override
    public void appendCriteria(final List<BaseAdvancedQueryItem> items,
                               final FindStreamAttributeValueCriteria criteria) {
        CriteriaLoggingUtil.appendEntityIdSet(items, "streamIdSet", criteria.getStreamIdSet());
        CriteriaLoggingUtil.appendRangeTerm(items, "createPeriod", criteria.getCreatePeriod());
        super.appendCriteria(items, criteria);
    }

    @Override
    protected QueryAppender<StreamAttributeValue, FindStreamAttributeValueCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new StreamAttributeValueQueryAppender(entityManager);
    }

    private static class StreamAttributeValueQueryAppender extends QueryAppender<StreamAttributeValue, FindStreamAttributeValueCriteria> {
        public StreamAttributeValueQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        public void appendBasicCriteria(HqlBuilder sql, String alias, FindStreamAttributeValueCriteria criteria) {
            sql.appendEntityIdSetQuery(alias + ".streamId", criteria.getStreamIdSet());
        }
    }
}
