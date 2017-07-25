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

import stroom.entity.server.util.HqlBuilder;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.Folder;
import stroom.entity.shared.FolderIdSet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility to build SQL.
 */
public final class UserManagerQueryUtil {
    private UserManagerQueryUtil() {
        // NA
    }

    private static EntityIdSet<Folder> buildNestedFolderList(final StroomEntityManager entityManager,
                                                             final FolderIdSet queryFolderIdSet) {
        final EntityIdSet<Folder> totalFolderIdSet = new EntityIdSet<>();
        if (queryFolderIdSet.isDeep() && Boolean.TRUE.equals(queryFolderIdSet.getMatchNull())) {
            totalFolderIdSet.setMatchAll(Boolean.TRUE);
            return totalFolderIdSet;
        }
        totalFolderIdSet.setMatchAll(false);

        final EntityIdSet<Folder> initialFolderIdSet = new EntityIdSet<>();

        initialFolderIdSet.copyFrom(queryFolderIdSet);

        if (initialFolderIdSet.isConstrained()) {
            doBuildNestedFolderList(entityManager, totalFolderIdSet, initialFolderIdSet);
        }
        return totalFolderIdSet;
    }

    @SuppressWarnings("rawtypes")
    private static void doBuildNestedFolderList(final StroomEntityManager entityManager,
                                                final EntityIdSet<Folder> totalFolderIdSet, final EntityIdSet<Folder> initialFolderIdSet) {
        totalFolderIdSet.addAll(initialFolderIdSet.getSet());

        final SqlBuilder sql = new SqlBuilder();
        sql.append("SELECT ");
        sql.append(BaseEntity.ID);
        sql.append(" FROM ");
        sql.append(Folder.TABLE_NAME);
        sql.append(" WHERE 1=1");
        sql.appendEntityIdSetQuery(Folder.FOREIGN_KEY, initialFolderIdSet);

        // Find all the child rows and remove out any we already know about
        final List rawResults = entityManager.executeNativeQueryResultList(sql);
        final Set<Long> subSet = new HashSet<>();
        for (final Object o : rawResults) {
            subSet.add(Long.valueOf(String.valueOf(o)));
        }
        subSet.removeAll(totalFolderIdSet.getSet());

        if (!subSet.isEmpty()) {
            final EntityIdSet<Folder> subFolderIdSet = new EntityIdSet<>();
            subFolderIdSet.addAll(subSet);
            doBuildNestedFolderList(entityManager, totalFolderIdSet, subFolderIdSet);
        }
    }

    /**
     * Append on some HQL.
     */
    public static void appendFolderCriteria(final FolderIdSet folderIdSet, final String folderAlias,
                                            final HqlBuilder sql, final StroomEntityManager entityManager) {
        if (folderIdSet != null && folderIdSet.isConstrained()) {
            // Null is a special case and not deep
            if (Boolean.TRUE.equals(folderIdSet.getMatchNull())) {
                sql.append(" AND (");
                sql.append(folderAlias);
                sql.append(".id = NULL)");
            } else {
                EntityIdSet<Folder> realIdSet = folderIdSet;

                if (folderIdSet.isDeep()) {
                    realIdSet = buildNestedFolderList(entityManager, folderIdSet);
                }

                sql.appendEntityIdSetQuery(folderAlias, realIdSet);
            }
        }
    }

    /**
     * Append on some SQL.
     */
    public static void appendFolderCriteria(final FolderIdSet folderIdSet, final String folderAlias,
                                            final SqlBuilder sql, final StroomEntityManager entityManager) {
        if (folderIdSet != null && folderIdSet.isConstrained()) {
            // Null is a special case and not deep
            if (Boolean.TRUE.equals(folderIdSet.getMatchNull())) {
                sql.append(" AND (");
                sql.append(folderAlias);
                sql.append(" IS NULL)");
            } else {
                EntityIdSet<Folder> realIdSet = folderIdSet;

                if (folderIdSet.isDeep()) {
                    realIdSet = buildNestedFolderList(entityManager, folderIdSet);
                }

                sql.appendEntityIdSetQuery(folderAlias, realIdSet);
            }
        }
    }
}
