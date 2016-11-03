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

package stroom.entity.server.util;

import java.util.List;

import javax.persistence.FlushModeType;

import stroom.entity.shared.Entity;
import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.Flushable;
import stroom.entity.shared.SummaryDataRow;

public interface StroomEntityManager extends Flushable {
    <T extends Entity> T loadEntity(Class<?> clazz, T entity);

    <T extends Entity> T loadEntityById(Class<?> clazz, long id);

    <T extends Entity> T saveEntity(T entity);

    <T extends Entity> Boolean deleteEntity(T entity);

    Long executeNativeUpdate(SQLBuilder sql);

    long executeNativeQueryLongResult(SQLBuilder sql);

    BaseResultList<SummaryDataRow> executeNativeQuerySummaryDataResult(SQLBuilder sql, int numberKeys);

    @SuppressWarnings("rawtypes")
    List executeNativeQueryResultList(SQLBuilder sql);

    <T> List<T> executeNativeQueryResultList(SQLBuilder sql, Class<?> clazz);

    @SuppressWarnings("rawtypes")
    List executeQueryResultList(SQLBuilder sql);

    @SuppressWarnings("rawtypes")
    List executeQueryResultList(SQLBuilder sql, BaseCriteria criteria);

    long executeQueryLongResult(SQLBuilder sql);

    String runSubSelectQuery(SQLBuilder sql, boolean handleNull);

    boolean hasNativeColumn(String nativeTable, String nativeColumn);

    void shutdown();

    void setFlushMode(FlushModeType mode);
}
