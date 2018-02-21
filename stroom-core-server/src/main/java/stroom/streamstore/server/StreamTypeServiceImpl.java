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

import org.springframework.beans.factory.InitializingBean;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.server.NamedEntityServiceImpl;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseResultList;
import stroom.security.Insecure;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.StreamType;

import javax.inject.Inject;
import java.util.List;

@Transactional
@Insecure
class StreamTypeServiceImpl extends NamedEntityServiceImpl<StreamType, FindStreamTypeCriteria>
        implements StreamTypeService, InitializingBean {
    private final StreamTypeServiceTransactionHelper streamTypeServiceTransactionHelper;

    @Inject
    StreamTypeServiceImpl(final StroomEntityManager entityManager,
                          final StreamTypeServiceTransactionHelper streamTypeServiceTransactionHelper) {
        super(entityManager);
        this.streamTypeServiceTransactionHelper = streamTypeServiceTransactionHelper;
    }

    /**
     * Ensure all standard stream types exist.
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        streamTypeServiceTransactionHelper.doInserts();
    }

    /**
     * @return the feed by it's ID or null
     */
    @SuppressWarnings("unchecked")
    public StreamType get(final String streamTypeName) {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT e FROM ");
        sql.append(getEntityClass().getName());
        sql.append(" AS e");
        sql.append(" WHERE e.name = ");
        sql.arg(streamTypeName);

        // This should just bring back 1
        final List<StreamType> results = getEntityManager().executeQueryResultList(sql);

        if (results == null || results.size() == 0) {
            return null;
        }
        return results.get(0);
    }

    @Override
    @Insecure
    public BaseResultList<StreamType> find(final FindStreamTypeCriteria criteria) throws RuntimeException {
        return super.find(criteria);
    }

    @Override
    public Class<StreamType> getEntityClass() {
        return StreamType.class;
    }

    @Override
    public FindStreamTypeCriteria createCriteria() {
        return new FindStreamTypeCriteria();
    }

    @Override
    protected QueryAppender<StreamType, FindStreamTypeCriteria> createQueryAppender(StroomEntityManager entityManager) {
        return new StreamTypeQueryAppender(entityManager);
    }

    private static class StreamTypeQueryAppender extends QueryAppender<StreamType, FindStreamTypeCriteria> {
        public StreamTypeQueryAppender(final StroomEntityManager entityManager) {
            super(entityManager);
        }

        @Override
        public void appendBasicCriteria(HqlBuilder sql, String alias, FindStreamTypeCriteria criteria) {
            super.appendBasicCriteria(sql, alias, criteria);
            sql.appendPrimitiveValueSetQuery(alias + ".ppurpose", criteria.getPurpose());
        }
    }
}
