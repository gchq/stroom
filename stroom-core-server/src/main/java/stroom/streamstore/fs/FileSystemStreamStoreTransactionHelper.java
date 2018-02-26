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

package stroom.streamstore.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import stroom.entity.StroomDatabaseInfo;
import stroom.entity.StroomEntityManager;

import javax.inject.Inject;

@Transactional
public class FileSystemStreamStoreTransactionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemStreamStoreTransactionHelper.class);

    private final StroomDatabaseInfo stroomDatabaseInfo;
    private final StroomEntityManager stroomEntityManager;

    @Inject
    FileSystemStreamStoreTransactionHelper(final StroomDatabaseInfo stroomDatabaseInfo,
                                           final StroomEntityManager stroomEntityManager) {
        this.stroomDatabaseInfo = stroomDatabaseInfo;
        this.stroomEntityManager = stroomEntityManager;
    }

//    public StroomEntityManager getEntityManager() {
//        return stroomEntityManager;
//    }
//
//    private Long executeUpdate(final SQLBuilder sql) {
//        return stroomEntityManager.executeNativeUpdate(sql);
//    }
//
//    private void incrementVersion(final SQLBuilder sql, final String prefix) {
//        sql.append(" SET ");
//        sql.append(prefix);
//        sql.append(BaseEntity.VERSION);
//        sql.append(" = ");
//        sql.append(prefix);
//        sql.append(BaseEntity.VERSION);
//        sql.append(" + 1, ");
//        sql.append(prefix);
//    }
//
//    private void addJoin(final SQLBuilder sql, final String alias, final String joinTable, final String joinAlias,
//                         final String fk) {
//        sql.append(" JOIN ");
//        sql.append(joinTable);
//        sql.append(" AS ");
//        sql.append(joinAlias);
//        sql.append(" ON (");
//        sql.append(joinAlias);
//        sql.append(".ID = ");
//        sql.append(alias);
//        sql.append(".");
//        sql.append(fk);
//        sql.append(")");
//    }
//
//    public Long updateStreamStatus(final FindStreamCriteria criteria, final StreamStatus newStatus,
//                                   final long statusMs) {
//        final SQLBuilder sql = new SQLBuilder();
//
//        final boolean joinStreamProcessor = criteria.getPipelineIdSet() != null
//                && criteria.getPipelineIdSet().isConstrained();
//
//        if (stroomDatabaseInfo.isMysql()) {
//            sql.append("UPDATE ");
//            sql.append(Stream.TABLE_NAME);
//            sql.append(" S");
//
//            if (joinStreamProcessor) {
//                addJoin(sql, "S", StreamProcessor.TABLE_NAME, "SP", StreamProcessor.FOREIGN_KEY);
//            }
//
//            incrementVersion(sql, "S.");
//
//            sql.append(Stream.STATUS);
//            sql.append(" = ");
//            sql.arg(newStatus.getPrimitiveValue());
//            sql.append(", ");
//            sql.append(Stream.STATUS_MS);
//            sql.append(" = ");
//            sql.arg(statusMs);
//            sql.append(" WHERE");
//            sql.append(" S.");
//            sql.append(Stream.STATUS);
//            sql.append(" <> ");
//            sql.arg(newStatus.getPrimitiveValue());
//
//        } else {
//            sql.append("UPDATE ");
//            sql.append(Stream.TABLE_NAME);
//            sql.append(" US");
//
//            incrementVersion(sql, "US.");
//
//            sql.append(SQLNameConstants.STATUS);
//            sql.append(" = ");
//            sql.arg(newStatus.getPrimitiveValue());
//            sql.append(", US.");
//            sql.append(SQLNameConstants.STATUS_MS);
//            sql.append(" = ");
//            sql.arg(statusMs);
//            sql.append(" WHERE US.ID IN (");
//            sql.append("SELECT S.");
//            sql.append(Stream.ID);
//            sql.append(" FROM ");
//            sql.append(Stream.TABLE_NAME);
//            sql.append(" S");
//
//            if (joinStreamProcessor) {
//                addJoin(sql, "S", StreamProcessor.TABLE_NAME, "SP", StreamProcessor.FOREIGN_KEY);
//            }
//
//            sql.append(" WHERE");
//            sql.append(" S.");
//            sql.append(Stream.STATUS);
//            sql.append(" <> ");
//            sql.arg(newStatus.getPrimitiveValue());
//        }
//
//        SQLUtil.appendIncludeExcludeSetQuery(sql, false, "S." + Feed.FOREIGN_KEY, criteria.getFeeds());
//
//        // applySqlCriteriaStream(criteria, "S.", sql);
//        SQLUtil.appendSetQuery(sql, false, "S." + StreamType.FOREIGN_KEY, criteria.getStreamTypeIdSet());
//        SQLUtil.appendSetQuery(sql, false, "S." + Stream.ID, criteria.getStreamIdSet());
//        SQLUtil.appendSetQuery(sql, false, "S." + Stream.PARENT_STREAM_ID, criteria.getParentStreamIdSet());
//        SQLUtil.appendSetQuery(sql, false, "S." + StreamProcessor.FOREIGN_KEY, criteria.getStreamProcessorIdSet());
//        SQLUtil.appendSetQuery(sql, false, "S." + Stream.STATUS, criteria.obtainStatusSet(), false);
//        SQLUtil.appendRangeQuery(sql, "S." + Stream.CREATE_MS, criteria.getCreatePeriod());
//        SQLUtil.appendRangeQuery(sql, "S." + Stream.EFFECTIVE_MS, criteria.getEffectivePeriod());
//        SQLUtil.appendRangeQuery(sql, "S." + Stream.STATUS_MS, criteria.getStatusPeriod());
//
//        // applySqlCriteriaStreamProcessor(criteria, "SP.", sql);
//        if (joinStreamProcessor) {
//            SQLUtil.appendSetQuery(sql, false, "SP." + PipelineEntity.FOREIGN_KEY, criteria.getPipelineIdSet());
//        }
////        if (joinFolder) {
////            UserManagerQueryUtil.appendFolderCriteria(criteria.getFolderIdSet(), "F.ID", sql, false,
////                    getEntityManager());
////        }
//        if (criteria.getPageRequest() != null && criteria.getPageRequest().getLength() != null) {
//            sql.append(" LIMIT ");
//            sql.append(criteria.getPageRequest().getLength());
//        }
//
//        if (!stroomDatabaseInfo.isMysql()) {
//            sql.append(")");
//        }
//
//        return executeUpdate(sql);
//    }
}
