/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.state.impl.dao;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.DateTimeSettings;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.state.impl.ScyllaDbExpressionUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.internal.querybuilder.schema.compaction.DefaultTimeWindowCompactionStrategy;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.createTable;

public class SessionDao extends AbstractStateDao<Session> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SessionDao.class);

    private static final CqlIdentifier COLUMN_KEY = CqlIdentifier.fromCql("key");
    private static final CqlIdentifier COLUMN_START = CqlIdentifier.fromCql("start");
    private static final CqlIdentifier COLUMN_END = CqlIdentifier.fromCql("end");
    private static final CqlIdentifier COLUMN_TERMINAL = CqlIdentifier.fromCql("terminal");

    private static final Map<String, ScyllaDbColumn> COLUMN_MAP = Map.of(
            SessionFields.KEY,
            new ScyllaDbColumn(SessionFields.KEY, DataTypes.TEXT, COLUMN_KEY),
            SessionFields.START,
            new ScyllaDbColumn(SessionFields.START, DataTypes.TIMESTAMP, COLUMN_START),
            SessionFields.END,
            new ScyllaDbColumn(SessionFields.END, DataTypes.TIMESTAMP, COLUMN_END),
            SessionFields.TERMINAL,
            new ScyllaDbColumn(SessionFields.TERMINAL, DataTypes.BOOLEAN, COLUMN_TERMINAL));

    public SessionDao(final Provider<CqlSession> sessionProvider, final String tableName) {
        super(sessionProvider, CqlIdentifier.fromCql(tableName));
    }

    @Override
    void createTables() {
        LOGGER.info("Creating table: " + table);
        LOGGER.logDurationIfInfoEnabled(() -> {
            final SimpleStatement statement = createTable(table)
                    .ifNotExists()
                    .withPartitionKey(COLUMN_KEY, DataTypes.TEXT)
                    .withClusteringColumn(COLUMN_START, DataTypes.TIMESTAMP)
                    .withClusteringColumn(COLUMN_END, DataTypes.TIMESTAMP)
                    .withClusteringColumn(COLUMN_TERMINAL, DataTypes.BOOLEAN)
                    .withClusteringOrder(COLUMN_START, ClusteringOrder.DESC)
                    .withClusteringOrder(COLUMN_START, ClusteringOrder.ASC)
                    .withCompaction(new DefaultTimeWindowCompactionStrategy())
                    .build();
            sessionProvider.get().execute(statement);
        }, "createTables()");
    }

    @Override
    public void insert(final List<Session> sessions) {
        Objects.requireNonNull(sessions, "Null sessions list");
        final SimpleStatement statement = insertInto(table)
                .value(COLUMN_KEY, bindMarker())
                .value(COLUMN_START, bindMarker())
                .value(COLUMN_END, bindMarker())
                .value(COLUMN_TERMINAL, bindMarker())
                .usingTimeout(TEN_SECONDS)
                .build();
        final PreparedStatement preparedStatement = prepare(statement);
        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            for (final Session session : sessions) {
                Objects.requireNonNull(session.key());
                Objects.requireNonNull(session.start());
                Objects.requireNonNull(session.end());

                executor.addStatement(preparedStatement.bind(
                        session.key(),
                        session.start(),
                        session.end(),
                        session.terminal()));
            }
        }
    }

    @Override
    public void delete(final List<Session> sessions) {
        final SimpleStatement statement = deleteFrom(table)
                .whereColumn(COLUMN_KEY).isEqualTo(bindMarker())
                .whereColumn(COLUMN_START).isEqualTo(bindMarker())
                .whereColumn(COLUMN_END).isEqualTo(bindMarker())
                .whereColumn(COLUMN_TERMINAL).isEqualTo(bindMarker())
                .build();
        doDelete(sessions, statement, session -> new Object[]{
                session.key(),
                session.start(),
                session.end(),
                session.terminal()});
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ValuesConsumer valuesConsumer) {
        final Consumer<Session> sessionConsumer = new SessionConsumer(fieldIndex, valuesConsumer);
        final List<Relation> relations = new ArrayList<>();
        ScyllaDbExpressionUtil.getRelations(
                COLUMN_MAP,
                criteria.getExpression(),
                relations,
                dateTimeSettings);
        findKeys(relations, key -> {
            final List<Relation> childRelations = new ArrayList<>(relations);
            childRelations.add(Relation.column(COLUMN_KEY).isEqualTo(literal(key)));

            final RowConsumer rowConsumer = new RowConsumer(key, sessionConsumer);
            // Find rows and turn them into sessions with the row consumer.
            findRows(childRelations, rowConsumer);

            // Add the final session.
            rowConsumer.flush();
        });
    }

//    public long count(final ExpressionCriteria criteria) {
//        final AtomicLong count = new AtomicLong();
//        final List<Relation> relations = new ArrayList<>();
//        ScyllaDbExpressionUtil.getRelations(FIELD_MAP, criteria.getExpression(), relations);
//        findKeys(relations, key -> {
//            final List<Relation> childRelations = new ArrayList<>(relations);
//            childRelations.add(Relation.column(COLUMN_KEY).isEqualTo(literal(key)));
//
//            // Find rows and turn them into sessions with the row consumer.
//            findRows(childRelations, row -> {
//                count.incrementAndGet();
//            });
//        });
//        return count.get();
//    }


    private void findKeys(final List<Relation> relations,
                          final Consumer<String> consumer) {
        final SimpleStatement statement = selectFrom(table)
                .column(COLUMN_KEY)
                .where(relations)
                .groupByColumnIds(COLUMN_KEY)
                .build();
        for (final Row row : sessionProvider.get().execute(statement)) {
            final String key = row.getString(0);
            consumer.accept(key);
        }
    }

    private void findRows(final List<Relation> relations,
                          final Consumer<Row> consumer) {
        final SimpleStatement statement = selectFrom(table)
                .column(COLUMN_START)
                .column(COLUMN_END)
                .column(COLUMN_TERMINAL)
                .where(relations)
                .orderBy(COLUMN_START, ClusteringOrder.ASC)
                .build();
        for (final Row row : sessionProvider.get().execute(statement)) {
            consumer.accept(row);
        }
    }

    public boolean inSession(final TemporalStateRequest request) {
        final SimpleStatement statement = selectFrom(table)
                .column(COLUMN_START)
                .column(COLUMN_END)
                .column(COLUMN_TERMINAL)
                .whereColumn(COLUMN_KEY).isEqualTo(bindMarker())
                .whereColumn(COLUMN_START).isLessThanOrEqualTo(bindMarker())
                .orderBy(COLUMN_START, ClusteringOrder.DESC)
                .limit(1)
                .allowFiltering()
                .build();
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(statement);
        final BoundStatement bound = preparedStatement.bind(request.key(), request.effectiveTime());
        return Optional
                .ofNullable(sessionProvider.get().execute(bound).one())
                .map(row -> {
                    final Instant start = row.getInstant(0);
                    final Instant end = row.getInstant(1);
                    final boolean terminal = row.getBoolean(2);
                    if (terminal) {
                        return false;
                    }

                    Objects.requireNonNull(start);
                    Objects.requireNonNull(end);

                    return (start.equals(request.effectiveTime()) || start.isBefore(request.effectiveTime())) &&
                            (end.equals(request.effectiveTime()) || end.isAfter(request.effectiveTime()));
                })
                .orElse(false);
    }

    @Override
    public void condense(final Instant oldest) {
        new Condenser(this).condense(oldest);
    }

    @Override
    public void removeOldData(final Instant oldest) {
        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            findKeys(Collections.emptyList(), key -> {
                final SimpleStatement statement = deleteFrom(table)
                        .whereColumn(COLUMN_KEY).isEqualTo(literal(key))
                        .whereColumn(COLUMN_START).isLessThan(literal(oldest))
                        .build();
                executor.addStatement(statement);
            });
        }
    }

    private static class Condenser {

        private final SessionDao sessionDao;
        private final List<Session> currentSessions = new ArrayList<>();
        private final List<Session> insertList = new ArrayList<>();
        private final List<Session> deleteList = new ArrayList<>();
        private Session currentSession = null;

        public Condenser(final SessionDao sessionDao) {
            this.sessionDao = sessionDao;
        }

        public void condense(final Instant oldest) {
            sessionDao.findKeys(Collections.emptyList(), key -> {
                final List<Relation> childRelations = new ArrayList<>();
                childRelations.add(Relation.column(COLUMN_KEY).isEqualTo(literal(key)));
                sessionDao.findRows(childRelations, row -> {
                    final Instant start = row.getInstant(0);
                    final Instant end = row.getInstant(1);
                    final boolean terminal = row.getBoolean(2);

                    Objects.requireNonNull(start);
                    Objects.requireNonNull(end);

                    if (start.isBefore(oldest)) {
                        final Session session = new Session(key, start, end, terminal);
                        if (terminal) {
                            if (currentSession != null) {
                                // If this is a session end event then end the current session if it is still valid.
                                if (session.end().isBefore(currentSession.end())) {
                                    currentSession = new Session(
                                            currentSession.key(),
                                            currentSession.start(),
                                            session.end(),
                                            false);
                                    replace(currentSessions, currentSession);
                                }
                            }

                            // Delete end.
                            delete(session);

                            // We no longer have an active session so set to null.
                            currentSession = null;
                            currentSessions.clear();

                        } else if (currentSession == null) {
                            // Remember new values.
                            currentSession = session;
                            currentSessions.clear();
                            currentSessions.add(session);

                        } else {
                            // See if the sessions overlap. If so just extend the current session.
                            if (start.equals(currentSession.end()) || start.isBefore(currentSession.end())) {
                                // Extend the current session.
                                currentSession = new Session(
                                        currentSession.key(),
                                        currentSession.start(),
                                        end,
                                        false);
                                currentSessions.add(session);

                                // Prevent the current session list getting too large by condensing frequently.
                                if (currentSessions.size() > 1000) {
                                    replace(currentSessions, currentSession);
                                    currentSessions.clear();
                                    currentSessions.add(currentSession);
                                }

                            } else {
                                // Add the current session.
                                if (currentSessions.size() > 1) {
                                    replace(currentSessions, currentSession);
                                }

                                // Start a new session.
                                currentSession = session;
                                currentSessions.clear();
                                currentSessions.add(session);
                            }
                        }
                    }
                });
            });

            // Add the final session.
            if (currentSessions.size() > 1) {
                replace(currentSessions, currentSession);
            }

            // Flush final changes.
            flush();
        }

        private void replace(final List<Session> oldSessions,
                             final Session newSession) {
            insertList.add(newSession);
            deleteList.addAll(oldSessions);
            if (insertList.size() >= BatchStatementExecutor.MAX_BATCH_STATEMENTS ||
                    deleteList.size() >= BatchStatementExecutor.MAX_BATCH_STATEMENTS) {
                flush();
            }
        }

        private void delete(final Session session) {
            deleteList.add(session);
            if (deleteList.size() >= BatchStatementExecutor.MAX_BATCH_STATEMENTS) {
                flush();
            }
        }


        private void flush() {
            sessionDao.insert(insertList);
            sessionDao.delete(deleteList);
            insertList.clear();
            deleteList.clear();
        }
    }

    private static class RowConsumer implements Consumer<Row> {

        private final String key;
        private final Consumer<Session> sessionConsumer;
        private Session currentSession;

        public RowConsumer(final String key,
                           final Consumer<Session> sessionConsumer) {
            this.key = key;
            this.sessionConsumer = sessionConsumer;
        }

        @Override
        public void accept(final Row row) {
            final Instant start = row.getInstant(0);
            final Instant end = row.getInstant(1);
            final boolean terminal = row.getBoolean(2);

            Objects.requireNonNull(start);
            Objects.requireNonNull(end);

            final Session session = new Session(key, start, end, terminal);
            if (terminal) {
                if (currentSession != null) {
                    // If this is a session end event then end the current session if it is still valid.
                    if (session.end().isBefore(currentSession.end())) {
                        currentSession = new Session(
                                currentSession.key(),
                                currentSession.start(),
                                session.end(),
                                false);
                        sessionConsumer.accept(currentSession);
                    }
                }

                // We no longer have an active session so set to null.
                currentSession = null;

            } else if (currentSession == null) {
                // Remember new values.
                currentSession = session;

            } else {
                // See if the sessions overlap. If so just extend the current session.
                if (start.equals(currentSession.end()) || start.isBefore(currentSession.end())) {
                    // Extend the current session.
                    currentSession = new Session(
                            currentSession.key(),
                            currentSession.start(),
                            end,
                            false);

                } else {
                    // Add the current session.
                    sessionConsumer.accept(currentSession);

                    // Start a new session.
                    currentSession = session;
                }
            }
        }

        public void flush() {
            // Add the final session.
            if (currentSession != null) {
                sessionConsumer.accept(currentSession);
            }
            currentSession = null;
        }
    }

    private static class SessionConsumer implements Consumer<Session> {

        private final String[] fieldNames;
        private final ValuesConsumer consumer;

        public SessionConsumer(final FieldIndex fieldIndex, final ValuesConsumer consumer) {
            this.fieldNames = fieldIndex.getFields();
            this.consumer = consumer;
        }

        @Override
        public void accept(final Session session) {
            final Val[] values = new Val[fieldNames.length];
            for (int i = 0; i < values.length; i++) {
                final String fieldName = fieldNames[i];
                switch (fieldName) {
                    case SessionFields.KEY -> values[i] = ValString.create(session.key());
                    case SessionFields.START -> values[i] = ValDate.create(session.start());
                    case SessionFields.END -> values[i] = ValDate.create(session.end());
                    case SessionFields.TERMINAL -> values[i] = ValBoolean.create(session.terminal());
                    default -> values[i] = ValNull.INSTANCE;
                }
            }
            consumer.accept(Val.of(values));
        }
    }
}
