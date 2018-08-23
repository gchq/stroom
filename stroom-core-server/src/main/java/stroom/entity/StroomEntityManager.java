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
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Entity;
import stroom.entity.shared.Flushable;
import stroom.entity.shared.SummaryDataRow;
import stroom.entity.util.HqlBuilder;
import stroom.entity.util.SqlBuilder;

import javax.persistence.FlushModeType;
import java.util.List;

public interface StroomEntityManager extends Flushable {
    <T extends Entity> T loadEntity(Class<?> clazz, T entity);

    <T extends Entity> T loadEntityById(Class<?> clazz, long id);

    <T extends Entity> T saveEntity(T entity);

    <T extends Entity> Boolean deleteEntity(T entity);

    <T extends Entity> void detach(T entity);

    Long executeNativeUpdate(SqlBuilder sql);

    long executeNativeQueryLongResult(SqlBuilder sql);

    BaseResultList<SummaryDataRow> executeNativeQuerySummaryDataResult(SqlBuilder sql, int numberKeys);

    @SuppressWarnings("rawtypes")
    List executeNativeQueryResultList(SqlBuilder sql);

    <T> List<T> executeNativeQueryResultList(SqlBuilder sql, Class<?> clazz);

    @SuppressWarnings("rawtypes")
    List executeQueryResultList(HqlBuilder sql);

    @SuppressWarnings("rawtypes")
    List executeQueryResultList(HqlBuilder sql, BaseCriteria criteria);

    @SuppressWarnings("rawtypes")
    List executeQueryResultList(HqlBuilder sql, BaseCriteria criteria, boolean allowCaching);

    long executeQueryLongResult(HqlBuilder sql);

    String runSubSelectQuery(HqlBuilder sql, boolean handleNull);

    boolean hasNativeColumn(String nativeTable, String nativeColumn);

    void clearContext();
}
