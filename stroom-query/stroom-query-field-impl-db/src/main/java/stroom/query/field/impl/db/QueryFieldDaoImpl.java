/*
 * Copyright 2018 Crown Copyright
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

package stroom.query.field.impl.db;

import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.query.field.impl.QueryFieldDao;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static stroom.query.field.impl.db.jooq.tables.FieldInfo.FIELD_INFO;
import static stroom.query.field.impl.db.jooq.tables.FieldSource.FIELD_SOURCE;

public class QueryFieldDaoImpl implements QueryFieldDao {

    private final QueryFieldDbConnProvider queryDatasourceDbConnProvider;

    @Inject
    QueryFieldDaoImpl(final QueryFieldDbConnProvider queryDatasourceDbConnProvider) {
        this.queryDatasourceDbConnProvider = queryDatasourceDbConnProvider;
    }

    public int getOrCreateFieldSource(final DocRef docRef) {
        Optional<Integer> optional = getFieldSource(docRef);
        if (optional.isEmpty()) {
            createFieldSource(docRef);
            optional = getFieldSource(docRef);
        }
        return optional.orElseThrow();
    }

    public Optional<Integer> getFieldSource(final DocRef docRef) {
        return JooqUtil
                .contextResult(queryDatasourceDbConnProvider, context -> context
                        .select(FIELD_SOURCE.ID)
                        .from(FIELD_SOURCE)
                        .where(FIELD_SOURCE.TYPE.eq(docRef.getType()))
                        .and(FIELD_SOURCE.UUID.eq(docRef.getUuid()))
                        .fetchOptional(FIELD_SOURCE.ID));
    }

    public void createFieldSource(final DocRef docRef) {
        JooqUtil.context(queryDatasourceDbConnProvider, context -> context
                .insertInto(FIELD_SOURCE)
                .set(FIELD_SOURCE.TYPE, docRef.getType())
                .set(FIELD_SOURCE.UUID, docRef.getUuid())
                .set(FIELD_SOURCE.NAME, docRef.getName())
                .onDuplicateKeyIgnore()
                .execute());
    }

//    public void addField(final int datasourceId, final IndexField field) {
//        JooqUtil.context(queryDatasourceDbConnProvider, context -> context
//                .insertInto(QUERY_DATASOURCE_FIELD)
//                .set(QUERY_DATASOURCE_FIELD.FK_QUERY_DATASOURCE_ID, datasourceId)
//                .set(QUERY_DATASOURCE_FIELD.FIELD_TYPE, field.getFieldType().getId())
//                .set(QUERY_DATASOURCE_FIELD.FIELD_NAME, field.getFieldName())
//                .set(QUERY_DATASOURCE_FIELD.ANALYZER_TYPE, field.getAnalyzerType().getId())
//                .set(QUERY_DATASOURCE_FIELD.INDEXED, field.isIndexed())
//                .set(QUERY_DATASOURCE_FIELD.STORED, field.isStored())
//                .set(QUERY_DATASOURCE_FIELD.TERM_POSITIONS, field.isTermPositions())
//                .set(QUERY_DATASOURCE_FIELD.CASE_SENSITIVE, field.isCaseSensitive())
//                .onDuplicateKeyIgnore()
//                .execute());
//    }

    public void addFields(final int fieldSourceId, final Collection<FieldInfo> fields) {
        JooqUtil.context(queryDatasourceDbConnProvider, context -> {
            var c = context.insertInto(FIELD_INFO,
                    FIELD_INFO.FK_FIELD_SOURCE_ID,
                    FIELD_INFO.FIELD_TYPE,
                    FIELD_INFO.FIELD_NAME);
            for (final FieldInfo field : fields) {
                c = c.values(fieldSourceId,
                        (byte) field.getFieldType().getIndex(),
                        field.getFieldName());
            }
            c.onDuplicateKeyUpdate()
                    .set(FIELD_INFO.FK_FIELD_SOURCE_ID, fieldSourceId)
                    .execute();
        });
    }

    @Override
    public ResultPage<FieldInfo> findFieldInfo(final FindFieldInfoCriteria criteria) {
        final Optional<Integer> optional = getFieldSource(criteria.getDataSourceRef());
        if (optional.isEmpty()) {
            return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
        }

        final Condition condition = getStringMatchCondition(
                FIELD_INFO.FIELD_NAME,
                criteria.getStringMatch());
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);

        final List<FieldInfo> fieldInfoList = JooqUtil
                .contextResult(queryDatasourceDbConnProvider, context -> context
                        .select(FIELD_INFO.FIELD_TYPE,
                                FIELD_INFO.FIELD_NAME)
                        .from(FIELD_INFO)
                        .where(FIELD_INFO.FK_FIELD_SOURCE_ID.eq(optional.get()))
                        .and(condition)
                        .orderBy(FIELD_INFO.FIELD_NAME)
                        .limit(offset, limit)
                        .fetch())
                .map(r -> {
                    final byte typeId = r.get(FIELD_INFO.FIELD_TYPE);
                    final String name = r.get(FIELD_INFO.FIELD_NAME);
                    final FieldType fieldType = FieldType.get(typeId);
                    final ConditionSet conditions = ConditionSet.getDefault(fieldType);
                    return new FieldInfo(fieldType, name, conditions, null, null);
                });
        return ResultPage.createCriterialBasedList(fieldInfoList, criteria);
    }

    private Condition getStringMatchCondition(final Field<String> field,
                                              final StringMatch stringMatch) {
        Condition condition = DSL.trueCondition();
        if (stringMatch != null) {
            switch (stringMatch.getMatchType()) {
                case ANY -> condition = DSL.trueCondition();
                case NULL -> condition = field.isNull();
                case NON_NULL -> condition = field.isNotNull();
                case BLANK -> condition = field.likeRegex("^[[:space:]]*$");
                case EMPTY -> condition = field.eq("");
                case NULL_OR_BLANK -> condition = field.isNull().or(field.likeRegex(
                        "^[[:space:]]*$"));
                case NULL_OR_EMPTY -> condition = field.isNull().or(field.eq(
                        ""));
                case CONTAINS -> {
                    if (stringMatch.isCaseSensitive()) {
                        condition = field.contains(stringMatch.getPattern());
                    } else {
                        condition = field.containsIgnoreCase(stringMatch.getPattern());
                    }
                }
                case EQUALS -> {
                    if (stringMatch.isCaseSensitive()) {
                        condition = field.equal(stringMatch.getPattern());
                    } else {
                        condition = field.equalIgnoreCase(stringMatch.getPattern());
                    }
                }
                case REGEX -> condition = field.likeRegex(stringMatch.getPattern());
            }
        }
        return condition;
    }
}
