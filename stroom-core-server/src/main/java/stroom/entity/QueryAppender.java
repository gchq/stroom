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

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.Entity;
import stroom.entity.shared.FindNamedEntityCriteria;
import stroom.entity.util.HqlBuilder;

import java.util.List;
import java.util.Set;

public class QueryAppender<E extends Entity, C extends BaseCriteria> {
    private final StroomEntityManager entityManager;

    public QueryAppender(final StroomEntityManager entityManager) {
        this.entityManager = entityManager;
    }

    protected void appendBasicJoin(HqlBuilder sql, String alias, Set<String> fetchSet) {
    }

    protected void appendNonFetchJoin(HqlBuilder sql, String alias, Set<String> fetchSet) {
    }

    protected void appendBasicCriteria(final HqlBuilder sql, final String alias, final C criteria) {
        if (criteria instanceof FindNamedEntityCriteria) {
            final FindNamedEntityCriteria findNamedEntityCriteria = (FindNamedEntityCriteria) criteria;
            sql.appendValueQuery(alias + ".name", findNamedEntityCriteria.getName());
        }
    }

    /**
     * Allow sub classes to pre-process item
     */
    protected void preSave(final E entity) {
    }

    /**
     * Allow sub classes to post-process item
     */
    protected void postLoad(final E entity) {
    }

    /**
     * Allow sub classes to post-process list
     */
    protected List<E> postLoad(final C criteria, final List<E> list) {
        list.forEach(this::postLoad);
        return list;
    }

    protected StroomEntityManager getEntityManager() {
        return entityManager;
    }
}
