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

package stroom.entity;

import com.google.inject.persist.Transactional;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Entity;
import stroom.entity.util.FieldMap;
import stroom.entity.util.HqlBuilder;

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

    @Transactional
    public BaseResultList<E> find(final C criteria, final FieldMap sqlFieldMap) throws RuntimeException {
        return doBasicFind(criteria, sqlFieldMap);
    }

    protected BaseResultList<E> doBasicFind(final C criteria, final FieldMap sqlFieldMap) throws RuntimeException {
        return doBasicFind(criteria, sqlFieldMap, "e");
    }

    @SuppressWarnings("unchecked")
    protected BaseResultList<E> doBasicFind(final C criteria, final FieldMap sqlFieldMap, final String alias) throws RuntimeException {
        final HqlBuilder sql = new HqlBuilder();
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
        sql.appendOrderBy(sqlFieldMap.getHqlFieldMap(), criteria, alias);

        List<E> results = entityManager.executeQueryResultList(sql, criteria);
        results = queryAppender.postLoad(criteria, results);

        return BaseResultList.createCriterialBasedList(results, criteria);
    }
}
