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

package stroom.index.impl.db;

import stroom.datasource.api.v2.AnalyzerType;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FindIndexFieldCriteria;
import stroom.datasource.api.v2.IndexField;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.index.impl.IndexFieldDao;
import stroom.index.shared.IndexFieldImpl;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static stroom.index.impl.db.jooq.tables.IndexField.INDEX_FIELD;
import static stroom.index.impl.db.jooq.tables.IndexFieldSource.INDEX_FIELD_SOURCE;

public class IndexFieldDaoImpl implements IndexFieldDao {

    private final IndexDbConnProvider queryDatasourceDbConnProvider;

    @Inject
    IndexFieldDaoImpl(final IndexDbConnProvider queryDatasourceDbConnProvider) {
        this.queryDatasourceDbConnProvider = queryDatasourceDbConnProvider;
    }

    private int getOrCreateFieldSource(final DocRef docRef) {
        Optional<Integer> optional = getFieldSource(docRef);
        if (optional.isEmpty()) {
            createFieldSource(docRef);
            optional = getFieldSource(docRef);
        }
        return optional.orElseThrow();
    }

    private Optional<Integer> getFieldSource(final DocRef docRef) {
        return JooqUtil
                .contextResult(queryDatasourceDbConnProvider, context -> context
                        .select(INDEX_FIELD_SOURCE.ID)
                        .from(INDEX_FIELD_SOURCE)
                        .where(INDEX_FIELD_SOURCE.TYPE.eq(docRef.getType()))
                        .and(INDEX_FIELD_SOURCE.UUID.eq(docRef.getUuid()))
                        .fetchOptional(INDEX_FIELD_SOURCE.ID));
    }

    private void createFieldSource(final DocRef docRef) {
        JooqUtil.context(queryDatasourceDbConnProvider, context -> context
                .insertInto(INDEX_FIELD_SOURCE)
                .set(INDEX_FIELD_SOURCE.TYPE, docRef.getType())
                .set(INDEX_FIELD_SOURCE.UUID, docRef.getUuid())
                .set(INDEX_FIELD_SOURCE.NAME, docRef.getName())
                .onDuplicateKeyIgnore()
                .execute());
    }

    @Override
    public void addFields(final DocRef docRef, final Collection<IndexField> fields) {
        final int fieldSourceId = getOrCreateFieldSource(docRef);
        JooqUtil.context(queryDatasourceDbConnProvider, context -> {
            var c = context.insertInto(INDEX_FIELD,
                    INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID,
                    INDEX_FIELD.TYPE,
                    INDEX_FIELD.NAME,
                    INDEX_FIELD.ANALYZER,
                    INDEX_FIELD.INDEXED,
                    INDEX_FIELD.STORED,
                    INDEX_FIELD.TERM_POSITIONS,
                    INDEX_FIELD.CASE_SENSITIVE);
            for (final IndexField field : fields) {
                c = c.values(fieldSourceId,
                        (byte) field.getFldType().getIndex(),
                        field.getFldName(),
                        field.getAnalyzerType().getDisplayValue(),
                        field.isIndexed(),
                        field.isStored(),
                        field.isTermPositions(),
                        field.isCaseSensitive());
            }
            c.onDuplicateKeyUpdate()
                    .set(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID, fieldSourceId)
                    .execute();
        });
    }

    @Override
    public ResultPage<IndexField> findFields(final FindIndexFieldCriteria criteria) {
        final Optional<Integer> optional = getFieldSource(criteria.getDataSourceRef());
        if (optional.isEmpty()) {
            return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
        }

        final Condition condition = getStringMatchCondition(
                INDEX_FIELD.NAME,
                criteria.getStringMatch());
        final int offset = JooqUtil.getOffset(criteria.getPageRequest());
        final int limit = JooqUtil.getLimit(criteria.getPageRequest(), true);

        final List<IndexField> fieldInfoList = JooqUtil
                .contextResult(queryDatasourceDbConnProvider, context -> context
                        .select(INDEX_FIELD.TYPE,
                                INDEX_FIELD.NAME,
                                INDEX_FIELD.ANALYZER,
                                INDEX_FIELD.INDEXED,
                                INDEX_FIELD.STORED,
                                INDEX_FIELD.TERM_POSITIONS,
                                INDEX_FIELD.CASE_SENSITIVE)
                        .from(INDEX_FIELD)
                        .where(INDEX_FIELD.FK_INDEX_FIELD_SOURCE_ID.eq(optional.get()))
                        .and(condition)
                        .orderBy(INDEX_FIELD.NAME)
                        .limit(offset, limit)
                        .fetch())
                .map(r -> {
                    final byte typeId = r.get(INDEX_FIELD.TYPE);
                    final String name = r.get(INDEX_FIELD.NAME);
                    final String analyzer = r.get(INDEX_FIELD.ANALYZER);
                    final boolean indexed = r.get(INDEX_FIELD.INDEXED);
                    final boolean stored = r.get(INDEX_FIELD.STORED);
                    final boolean termPositions = r.get(INDEX_FIELD.TERM_POSITIONS);
                    final boolean caseSensitive = r.get(INDEX_FIELD.CASE_SENSITIVE);

                    final FieldType fieldType = FieldType.get(typeId);
                    final AnalyzerType analyzerType = AnalyzerType.fromDisplayValue(analyzer);
                    return IndexFieldImpl
                            .builder()
                            .fldName(name)
                            .fldType(fieldType)
                            .analyzerType(analyzerType)
                            .indexed(indexed)
                            .stored(stored)
                            .termPositions(termPositions)
                            .caseSensitive(caseSensitive)
                            .build();
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
                case NOT_EQUALS -> {
                    if (stringMatch.isCaseSensitive()) {
                        condition = field.notEqual(stringMatch.getPattern());
                    } else {
                        condition = field.notEqualIgnoreCase(stringMatch.getPattern());
                    }
                }
                case STARTS_WITH -> {
                    if (stringMatch.isCaseSensitive()) {
                        condition = field.startsWith(stringMatch.getPattern());
                    } else {
                        condition = field.startsWithIgnoreCase(stringMatch.getPattern());
                    }
                }
                case ENDS_WITH -> {
                    if (stringMatch.isCaseSensitive()) {
                        condition = field.endsWith(stringMatch.getPattern());
                    } else {
                        condition = field.endsWithIgnoreCase(stringMatch.getPattern());
                    }
                }
                case REGEX -> condition = field.likeRegex(stringMatch.getPattern());
            }
        }
        return condition;
    }
}
