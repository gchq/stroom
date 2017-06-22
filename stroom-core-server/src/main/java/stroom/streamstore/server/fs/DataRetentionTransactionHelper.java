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
import stroom.streamstore.server.ExpressionMatcher;
import stroom.streamstore.server.StreamFields;
import stroom.streamstore.shared.DataRetentionRule;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.streamtask.shared.StreamProcessor;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DataRetentionTransactionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataRetentionTransactionHelper.class);

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
    public void deleteMatching(final Period ageRange, final List<DataRetentionRule> rules, final Map<DataRetentionRule, Optional<Long>> ageMap, final TaskMonitor taskMonitor) {
        // Find out which fields are used by the expressions so we don't have to do unnecessary joins.
        final Set<String> fieldSet = new HashSet<>();
        fieldSet.add(StreamFields.STREAM_ID);
        fieldSet.add(StreamFields.CREATED_ON);

        // Also make sure we create a list of rules that are enabled and have at least one enabled term.
        final List<DataRetentionRule> activeRules = new ArrayList<>();
        rules.forEach(rule -> {
            if (rule.isEnabled() && rule.getExpression() != null && rule.getExpression().enabled()) {
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

        final long rowCount = getRowCount(ageRange, fieldSet);
        long rowNum = 1;

        final ExpressionMatcher expressionMatcher = new ExpressionMatcher(StreamFields.getFieldMap(), dictionaryService);

        final String sql = getSQL(ageRange, fieldSet, false);
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final Map<String, Object> attributeMap = createAttributeMap(resultSet, fieldSet);
                        final Long streamId = (Long) attributeMap.get(StreamFields.STREAM_ID);
                        try {
                            final String streamInfo = "stream " + rowNum++ + " of " + rowCount + " (stream id=" + streamId + ")";
                            info(taskMonitor, "Examining " + streamInfo);

                            final DataRetentionRule matchingRule = findMatchingRule(expressionMatcher, attributeMap, activeRules);
                            if (matchingRule != null) {
                                ageMap.get(matchingRule).ifPresent(age -> {
                                    final Long createMs = (Long) attributeMap.get(StreamFields.CREATED_ON);
                                    if (createMs < age) {
                                        info(taskMonitor, "Deleting " + streamInfo);
                                        fileSystemStreamStore.deleteStream(Stream.createStub(streamId));
                                    }
                                });
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

    private void info(final TaskMonitor taskMonitor, final String message) {
        LOGGER.debug(message);
        taskMonitor.info(message);
    }

    private long getRowCount(final Period ageRange, final Set<String> fieldSet) {
        long rowCount = 0;
        final String sql = getSQL(ageRange, fieldSet, true);
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        rowCount = resultSet.getLong(1);
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return rowCount;
    }

    private String getSQL(final Period ageRange, final Set<String> fieldSet, final boolean count) {
        final SQLBuilder sql = new SQLBuilder();
        sql.append("SELECT");

        final boolean includeStream = addFieldsToQuery(StreamFields.getStreamFields(), fieldSet, sql, "S");
        final boolean includeFeed = addFieldsToQuery(StreamFields.getFeedFields(), fieldSet, sql, "F");
        final boolean includeStreamType = addFieldsToQuery(StreamFields.getStreamTypeFields(), fieldSet, sql, "ST");
        final boolean includePipeline = addFieldsToQuery(StreamFields.getPipelineFields(), fieldSet, sql, "P");

        if (count) {
            sql.setLength(0);
            sql.append("SELECT COUNT(*)");
        } else {
            // Remove last comma from field list.
            sql.setLength(sql.length() - 1);
        }

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

        return sql.toString();
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
                final IndexField indexField = StreamFields.getFieldMap().get(fieldName);
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
        if (item.enabled()) {
            if (item instanceof ExpressionOperator) {
                final ExpressionOperator operator = (ExpressionOperator) item;
                operator.getChildren().forEach(i -> addChildren(i, fieldSet));
            } else if (item instanceof ExpressionTerm) {
                final ExpressionTerm term = (ExpressionTerm) item;
                fieldSet.add(term.getField());
            }
        }
    }
}
