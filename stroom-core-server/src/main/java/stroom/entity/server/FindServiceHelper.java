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

package stroom.entity.server;

import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.SQLUtil;
import stroom.entity.shared.Entity;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class FindServiceHelper<E extends Entity, C extends BaseCriteria> {
    protected final StroomEntityManager entityManager;
    private final Class<E> entityClass;
    private final QueryAppender queryAppender;

    public FindServiceHelper(final StroomEntityManager entityManager, final Class<E> entityClass, final QueryAppender queryAppender) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.queryAppender = queryAppender;
    }

    @Transactional(readOnly = true)
    public BaseResultList<E> find(final C criteria) throws RuntimeException {
        return doBasicFind(criteria);
    }

    protected BaseResultList<E> doBasicFind(final C criteria) throws RuntimeException {
        return doBasicFind(criteria, "e");
    }

    @SuppressWarnings("unchecked")
    protected BaseResultList<E> doBasicFind(final C criteria, final String alias) throws RuntimeException {
        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT ");
        sql.append(alias);
        sql.append(" FROM ");
        sql.append(entityClass.getName());
        sql.append(" AS ");
        sql.append(alias);

        queryAppender.appendBasicJoin(sql, alias, criteria.getFetchSet());

        sql.append(" WHERE 1=1");

        queryAppender.appendBasicCriteria(sql, alias, criteria);

        // Append order by criteria.
        SQLUtil.appendOrderBy(sql, true, criteria, alias);

        List<E> results = entityManager.executeQueryResultList(sql, criteria);
        results = queryAppender.postLoad(criteria, results);
        results.forEach(queryAppender::postLoad);

        return BaseResultList.createCriterialBasedList(results, criteria);
    }
}
