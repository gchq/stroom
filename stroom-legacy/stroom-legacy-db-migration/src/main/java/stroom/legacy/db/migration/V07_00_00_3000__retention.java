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

package stroom.legacy.db.migration;

import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.TimeUnit;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.MetaFields;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Deprecated
public class V07_00_00_3000__retention extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V07_00_00_3000__retention.class);
    private static final String DATA_RETENTION = "Data Retention";

    private final Serialiser2<FeedDoc> feedSerialiser =
            new Serialiser2FactoryImpl().createSerialiser(FeedDoc.class);
    private final Serialiser2<DataRetentionRules> dataRetentionRulesSerialiser =
            new Serialiser2FactoryImpl().createSerialiser(DataRetentionRules.class);

    @Override
    public void migrate(final Context context) throws Exception {
        final List<DataRetentionRule> rules = new ArrayList<>();

        // Add feed based retention rules.
        final AtomicInteger ruleNo = new AtomicInteger();
        final Map<Integer, List<String>> mapByRetention = getFeedRetentionMap(context);
        mapByRetention.keySet().stream().sorted().forEach(days -> {
            final List<ExpressionTerm> expressionTerms = mapByRetention.get(days)
                    .stream()
                    .sorted()
                    .map(feedName -> ExpressionTerm
                            .builder()
                            .field(MetaFields.FIELD_FEED)
                            .condition(Condition.EQUALS)
                            .value(feedName)
                            .build())
                    .collect(Collectors.toList());
            final ExpressionOperator expressionOperator = ExpressionOperator
                    .builder()
                    .op(Op.OR)
                    .addTerms(expressionTerms)
                    .build();

            final int no = ruleNo.incrementAndGet();
            final DataRetentionRule dataRetentionRule = new DataRetentionRule(no,
                    Instant.now().toEpochMilli(),
                    "Rule " + no,
                    true,
                    expressionOperator,
                    days,
                    TimeUnit.DAYS,
                    false);

            rules.add(dataRetentionRule);
        });

        // Get existing rules or create new rules.
        final DataRetentionRules dataRetentionRules = fetchCurrentRules(context);
        if (dataRetentionRules.getRules() != null) {
            for (final DataRetentionRule rule : dataRetentionRules.getRules()) {
                final int no = ruleNo.incrementAndGet();
                final DataRetentionRule dataRetentionRule = new DataRetentionRule(
                        no,
                        rule.getCreationTime(),
                        rule.getName(),
                        rule.isEnabled(),
                        rule.getExpression(),
                        rule.getAge(),
                        rule.getTimeUnit(),
                        rule.isForever());
                rules.add(dataRetentionRule);
            }
        }

        // Set the new rules.
        dataRetentionRules.setRules(rules);

        // Delete the existing data retention rules from the DB.
        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement("" +
                "DELETE" +
                " FROM doc " +
                " WHERE name = ?" +
                " AND type = ?")) {
            preparedStatement.setString(1, DATA_RETENTION);
            preparedStatement.setString(2, DataRetentionRules.DOCUMENT_TYPE);
            preparedStatement.execute();
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        // Serialise the data retention rules and add them to the DB.
        final Map<String, byte[]> dataMap = dataRetentionRulesSerialiser.write(dataRetentionRules);
        dataMap.forEach((k, v) -> {
            try (final PreparedStatement ps = context.getConnection().prepareStatement(
                    "INSERT INTO doc (" +
                            "  type, " +
                            "  uuid, " +
                            "  name, " +
                            "  ext, " +
                            "  data) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, DataRetentionRules.DOCUMENT_TYPE);
                ps.setString(2, dataRetentionRules.getUuid());
                ps.setString(3, dataRetentionRules.getName());
                ps.setString(4, k);
                ps.setBytes(5, v);
                ps.executeUpdate();
            } catch (final SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private Map<Integer, List<String>> getFeedRetentionMap(final Context context) throws Exception {
        final Map<Integer, List<String>> mapByRetention = new HashMap<>();
        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement("" +
                "SELECT" +
                " uuid," +
                " name," +
                " data" +
                " FROM doc" +
                " WHERE type = ?")) {
            preparedStatement.setString(1, FeedDoc.DOCUMENT_TYPE);

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final String uuid = resultSet.getString(1);
                    final String name = resultSet.getString(2);
                    final String data = resultSet.getString(3);

                    final FeedDoc feedDoc = readFeedDoc(data);
                    if (feedDoc != null) {
                        final Integer retentionDayAge = feedDoc.getRetentionDayAge();
                        if (retentionDayAge != null) {
                            mapByRetention
                                    .computeIfAbsent(retentionDayAge, k -> new ArrayList<>())
                                    .add(feedDoc.getName());
                        }
                    }
                }
            }
        }
        return mapByRetention;
    }

    private FeedDoc readFeedDoc(final String data) {
        FeedDoc feedDoc = null;

        try {
            feedDoc = feedSerialiser.read(data.getBytes(StandardCharsets.UTF_8));
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return feedDoc;
    }

    private DataRetentionRules fetchCurrentRules(final Context context) {
        DataRetentionRules dataRetentionRules = null;

        try (final PreparedStatement preparedStatement = context.getConnection().prepareStatement("" +
                "SELECT" +
                " type," +
                " uuid," +
                " name," +
                " ext," +
                " data" +
                " FROM doc " +
                " WHERE name = ?" +
                " AND type = ?")) {
            preparedStatement.setString(1, DATA_RETENTION);
            preparedStatement.setString(2, DataRetentionRules.DOCUMENT_TYPE);

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final String type = resultSet.getString(1);
                    final String uuid = resultSet.getString(2);
                    final String name = resultSet.getString(3);
                    final String ext = resultSet.getString(4);
                    final String data = resultSet.getString(5);

                    if (data != null) {
                        try {
                            dataRetentionRules =
                                    dataRetentionRulesSerialiser.read(data.getBytes(StandardCharsets.UTF_8));
                        } catch (final IOException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        if (dataRetentionRules == null) {
            final long now = System.currentTimeMillis();
            dataRetentionRules = new DataRetentionRules();
            dataRetentionRules.setType(DataRetentionRules.DOCUMENT_TYPE);
            dataRetentionRules.setUuid(UUID.randomUUID().toString());
            dataRetentionRules.setName(DATA_RETENTION);
            dataRetentionRules.setVersion(UUID.randomUUID().toString());
            dataRetentionRules.setCreateTimeMs(now);
            dataRetentionRules.setUpdateTimeMs(now);
            dataRetentionRules.setCreateUser("admin");
            dataRetentionRules.setUpdateUser("admin");
            dataRetentionRules.setRules(null);
        }

        return dataRetentionRules;
    }
}
