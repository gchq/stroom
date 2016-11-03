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
import stroom.entity.shared.FindDocumentEntityCriteria;
import stroom.entity.shared.FindFolderCriteria;
import stroom.entity.shared.FindNamedEntityCriteria;

import java.util.List;
import java.util.Set;

public class QueryAppender<E extends Entity, C extends BaseCriteria> {
    private final StroomEntityManager entityManager;

    public QueryAppender(final StroomEntityManager entityManager) {
        this.entityManager = entityManager;
    }

    protected void appendBasicJoin(SQLBuilder sql, String alias, Set<String> fetchSet) {
    }

    protected void appendBasicCriteria(final SQLBuilder sql, final String alias, final C criteria) {
        if (criteria instanceof FindDocumentEntityCriteria) {
            final FindDocumentEntityCriteria findDocumentEntityCriteria = (FindDocumentEntityCriteria) criteria;
            if (findDocumentEntityCriteria instanceof FindFolderCriteria) {
                final FindFolderCriteria findFolderCriteria = (FindFolderCriteria) findDocumentEntityCriteria;
                if (findFolderCriteria.isSelf()) {
                    SQLUtil.appendSetQuery(sql, true, alias + ".id", findFolderCriteria.getFolderIdSet());
                } else {
                    UserManagerQueryUtil.appendFolderCriteria(findDocumentEntityCriteria.getFolderIdSet(),
                            alias + ".folder", sql, true, entityManager);
                }

            } else {
                UserManagerQueryUtil.appendFolderCriteria(findDocumentEntityCriteria.getFolderIdSet(), alias + ".folder",
                        sql, true, entityManager);
            }
        }

        if (criteria instanceof FindNamedEntityCriteria) {
            final FindNamedEntityCriteria findNamedEntityCriteria = (FindNamedEntityCriteria) criteria;
            SQLUtil.appendValueQuery(sql, alias + ".name", findNamedEntityCriteria.getName());
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
        return list;
    }

    protected StroomEntityManager getEntityManager() {
        return entityManager;
    }
}
