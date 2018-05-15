/*
 *
 *  * Copyright 2018 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.entity;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Entity;
import stroom.entity.util.HqlBuilder;

import java.util.List;

public class CountServiceHelper<E extends Entity, C extends BaseCriteria>  {

    protected final StroomEntityManager entityManager;
    private final Class<E> entityClass;
    private final QueryAppender queryAppender;

    private final String ALIAS = "e";

    CountServiceHelper(
            final StroomEntityManager entityManager,
            final Class<E> entityClass,
            final QueryAppender queryAppender) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
        this.queryAppender = queryAppender;
    }

    public long count(final C criteria) {
        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT COUNT(1)");
        sql.append(" FROM ");
        sql.append(entityClass.getName());
        sql.append(" AS ");
        sql.append(ALIAS);

        // Fetch joins won't work with a SELECT COUNT
        queryAppender.appendNonFetchJoin(sql, ALIAS, criteria.getFetchSet());

        sql.append(" WHERE 1=1");

        queryAppender.appendBasicCriteria(sql, ALIAS, criteria);

        // We want to null any page request, otherwise we might get a limited number.
        criteria.setPageRequest(null);

        long count = entityManager.executeQueryLongResult(sql);
        return count;
    }
}
