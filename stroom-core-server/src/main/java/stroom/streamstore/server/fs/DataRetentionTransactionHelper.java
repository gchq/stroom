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

package stroom.streamstore.server.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import stroom.dictionary.shared.DictionaryService;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.SQLUtil;
import stroom.entity.shared.Period;
import stroom.feed.shared.Feed;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.shared.ExpressionItem;
import stroom.query.shared.ExpressionOperator;
import stroom.query.shared.ExpressionTerm;
import stroom.query.shared.IndexField;
import stroom.query.shared.IndexFields;
import stroom.streamstore.server.ExpressionMatcher;
import stroom.streamstore.shared.DataRetentionRule;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.StreamProcessor;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class DataRetentionTransactionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataRetentionTransactionHelper.class);

    private static final String STREAM_ID = "Stream Id";
    private static final String PARENT_STREAM_ID = "Parent Stream Id";
    private static final String CREATED_ON = "Created On";
    private static final String FEED = "Feed";
    private static final String STREAM_TYPE = "Stream Type";
    private static final String PIPELINE = "Pipeline";


    private static final Map<String, String> STREAM_FIELDS = new HashMap<>();
    private static final Map<String, String> FEED_FIELDS = new HashMap<>();
    private static final Map<String, String> STREAM_TYPE_FIELDS = new HashMap<>();
    private static final Map<String, String> PIPELINE_FIELDS = new HashMap<>();
    //    private static final Map<String, String> STREAM_ATTRIBUTE_FIELDS = new HashMap<>();
    private static final IndexFields FIELDS;
    private static final Map<String, IndexField> INDEX_FIELD_MAP;

    static {
        STREAM_FIELDS.put(STREAM_ID, Stream.ID);
        STREAM_FIELDS.put(PARENT_STREAM_ID, Stream.PARENT_STREAM_ID);
        STREAM_FIELDS.put(CREATED_ON, Stream.CREATE_MS);

        FEED_FIELDS.put(FEED, Feed.NAME);

        STREAM_TYPE_FIELDS.put(STREAM_TYPE, StreamType.NAME);

        PIPELINE_FIELDS.put(PIPELINE, PipelineEntity.NAME);

        // TODO : Don't include these fields for now as the processing required to fetch attributes for each stream will be slow.
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_READ, StreamAttributeConstants.REC_READ);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_WRITE, StreamAttributeConstants.REC_WRITE);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_INFO, StreamAttributeConstants.REC_INFO);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_WARN, StreamAttributeConstants.REC_WARN);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_ERROR, StreamAttributeConstants.REC_ERROR);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.REC_FATAL, StreamAttributeConstants.REC_FATAL);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.DURATION, StreamAttributeConstants.DURATION);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.FILE_SIZE, StreamAttributeConstants.FILE_SIZE);
//        STREAM_ATTRIBUTE_FIELDS.put(StreamAttributeConstants.STREAM_SIZE, StreamAttributeConstants.STREAM_SIZE);

        final Set<IndexField> set = new HashSet<>();

        set.add(IndexField.createNumericField(STREAM_ID));
        set.add(IndexField.createNumericField(PARENT_STREAM_ID));
        set.add(IndexField.createDateField(CREATED_ON));

        set.add(IndexField.createField(FEED));
        set.add(IndexField.createField(STREAM_TYPE));
        set.add(IndexField.createField(PIPELINE));

//        STREAM_ATTRIBUTE_FIELDS.forEach((k, v) -> {
//            final IndexField indexField = create(k, StreamAttributeConstants.SYSTEM_ATTRIBUTE_FIELD_TYPE_MAP.get(v));
//            if (indexField != null) {
//                set.add(indexField);
//            }
//        });

        final List<IndexField> list = set.stream().sorted(Comparator.comparing(IndexField::getFieldName)).collect(Collectors.toList());
        FIELDS = new IndexFields(list);
        INDEX_FIELD_MAP = list.stream().collect(Collectors.toMap(IndexField::getFieldName, Function.identity()));
    }

    private final DataSource dataSource;
    private final FileSystemStreamStore fileSystemStreamStore;
    private final DictionaryService dictionaryService;

    @Inject
    public DataRetentionTransactionHelper(final DataSource dataSource, final FileSystemStreamStore fileSystemStreamStore, final DictionaryService dictionaryService) {
        this.dataSource = dataSource;
        this.fileSystemStreamStore = fileSystemStreamStore;
        this.dictionaryService = dictionaryService;
    }

    @Transactional(propagation = Propagation.NEVER, readOnly = true)
    public void deleteMatching(final Period ageRange, final List<DataRetentionRule> rules, final Map<DataRetentionRule, Long> ageMap) {
        // Find out which fields are used by the expressions so we don't have to do unnecessary joins.
        final Set<String> fieldSet = new HashSet<>();
        fieldSet.add(STREAM_ID);
        fieldSet.add(CREATED_ON);

        // Also make sure we create a list of rules that are enabled and have at least one enabled term.
        final List<DataRetentionRule> activeRules = new ArrayList<>();
        rules.forEach(rule -> {
            if (rule.isEnabled() && rule.getExpression() != null && rule.getExpression().isEnabled()) {
                final Set<String> fields = new HashSet<>();
                addToFieldSet(rule, fields);
                if (fields.size() > 0) {
                    fieldSet.addAll(fields);
                    activeRules.add(rule);
                }
            }
        });

        // If there are no active rules then we aren't going to process anything.
        if (activeRules.size() == 0) {
            return;
        }

        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT");

        final boolean includeStream = addFieldsToQuery(STREAM_FIELDS, fieldSet, sql, "S");
        final boolean includeFeed = addFieldsToQuery(FEED_FIELDS, fieldSet, sql, "F");
        final boolean includeStreamType = addFieldsToQuery(STREAM_TYPE_FIELDS, fieldSet, sql, "ST");
        final boolean includePipeline = addFieldsToQuery(PIPELINE_FIELDS, fieldSet, sql, "P");

        // Remove last comma from field list.
        sql.setLength(sql.length() - 1);

        sql.append(" FROM ");
        sql.append(Stream.TABLE_NAME);
        sql.append(" S");

        if (includeFeed) {
            SQLUtil.join(sql, Feed.TABLE_NAME, "F", "S", Feed.FOREIGN_KEY, "F", Feed.ID);
        }
        if (includeStreamType) {
            SQLUtil.join(sql, StreamType.TABLE_NAME, "ST", "S", StreamType.FOREIGN_KEY, "ST", StreamType.ID);
        }
        if (includePipeline) {
            SQLUtil.leftOuterJoin(sql, StreamProcessor.TABLE_NAME, "SP", "S", StreamProcessor.FOREIGN_KEY, "SP", StreamProcessor.ID);
            SQLUtil.leftOuterJoin(sql, PipelineEntity.TABLE_NAME, "p", "SP", PipelineEntity.FOREIGN_KEY, "p", PipelineEntity.ID);
        }

        sql.append(" WHERE 1=1");
        SQLUtil.appendLongRangeQuery(sql, "S." + Stream.CREATE_MS, ageRange);

        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(INDEX_FIELD_MAP, dictionaryService);

        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql.toString())) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final Map<String, Object> attributeMap = createAttributeMap(resultSet, fieldSet);
                        final Long streamId = (Long) attributeMap.get(STREAM_ID);
                        try {
                            LOGGER.debug("Processing stream {}", streamId);

                            final DataRetentionRule matchingRule = findMatchingRule(expressionMatcher, attributeMap, activeRules);
                            if (matchingRule != null) {
                                final Long age = ageMap.get(matchingRule);
                                if (age != null) {
                                    final Long createMs = (Long) attributeMap.get(CREATED_ON);
                                    if (createMs < age) {
                                        LOGGER.debug("Deleting stream {}", streamId);
                                        fileSystemStreamStore.deleteStream(Stream.createStub(streamId));
                                    }
                                }
                            }
                        } catch (final Exception e) {
                            LOGGER.error("An error occurred processing stream " + streamId, e);
                        }
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private DataRetentionRule findMatchingRule(final ExpressionMatcher expressionMatcher, final Map<String, Object> attributeMap, final List<DataRetentionRule> activeRules) {
        for (final DataRetentionRule rule : activeRules) {
            if (expressionMatcher.match(attributeMap, rule.getExpression())) {
                return rule;
            }
        }
        return null;
    }

    private Map<String, Object> createAttributeMap(final ResultSet resultSet, final Set<String> fieldSet) {
        final Map<String, Object> attributeMap = new HashMap<>();
        fieldSet.forEach(fieldName -> {
            try {
                final IndexField indexField = INDEX_FIELD_MAP.get(fieldName);
                switch (indexField.getFieldType()) {
                    case FIELD:
                        final String string = resultSet.getString(fieldName);
                        attributeMap.put(fieldName, string);
                        break;
                    default:
                        final long number = resultSet.getLong(fieldName);
                        attributeMap.put(fieldName, number);
                        break;

                }
            } catch (final SQLException e) {
                LOGGER.error(e.getMessage(), e);
            }
        });
        return attributeMap;
    }

    private boolean addFieldsToQuery(final Map<String, String> fieldMap, final Set<String> fieldSet, final SQLBuilder sql, final String alias) {
        final AtomicBoolean used = new AtomicBoolean();

        fieldMap.forEach((k, v) -> {
            if (fieldSet.contains(k)) {
                sql.append(" ");
                sql.append(alias);
                sql.append(".");
                sql.append(v);
                sql.append(" AS ");
                sql.append("'" + k + "'");
                sql.append(",");

                used.set(true);
            }
        });

        return used.get();
    }

    private void addToFieldSet(final DataRetentionRule rule, final Set<String> fieldSet) {
        if (rule.isEnabled() && rule.getExpression() != null) {
            addChildren(rule.getExpression(), fieldSet);
        }
    }

    private void addChildren(final ExpressionItem item, final Set<String> fieldSet) {
        if (item.isEnabled()) {
            if (item instanceof ExpressionOperator) {
                final ExpressionOperator operator = (ExpressionOperator) item;
                operator.getChildren().forEach(i -> addChildren(i, fieldSet));
            } else if (item instanceof ExpressionTerm) {
                final ExpressionTerm term = (ExpressionTerm) item;
                fieldSet.add(term.getField());
            }
        }
    }

//    private static IndexField create(final String name, final StreamAttributeFieldUse streamAttributeFieldUse) {
//        switch (streamAttributeFieldUse) {
//            case FIELD:
//                return IndexField.createField(name);
//            case ID:
//                return IndexField.createIdField(name);
//            case DURATION_FIELD:
//                return IndexField.createNumericField(name);
//            case COUNT_IN_DURATION_FIELD:
//                return IndexField.createNumericField(name);
//            case NUMERIC_FIELD:
//                return IndexField.createNumericField(name);
//            case DATE_FIELD:
//                return IndexField.createDateField(name);
//            case SIZE_FIELD:
//                return IndexField.createNumericField(name);
//        }
//
//        return null;
//    }

    public static IndexFields getFields() {
        return FIELDS;
    }
}
