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

package stroom.db.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Range;
import stroom.util.shared.Selection;
import stroom.util.shared.StringCriteria;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;

public class JooqHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JooqHelper.class);

    private static final String DEFAULT_ID_FIELD_NAME = "id";
    private static final Boolean RENDER_SCHEMA = false;
    private static final ThreadLocal<CurrentDataSource> DATA_SOURCE_THREAD_LOCAL = new ThreadLocal<>();

    private final DataSource dataSource;
    private final SQLDialect sqlDialect;

    public JooqHelper(final DataSource dataSource) {
        this.dataSource = dataSource;
        this.sqlDialect = SQLDialect.MYSQL;
    }

    public JooqHelper(final DataSource dataSource,
                      final SQLDialect sqlDialect) {
        this.dataSource = dataSource;
        this.sqlDialect = sqlDialect;
    }

    public void disableJooqLogoInLogs() {
        System.getProperties().setProperty("org.jooq.no-logo", "true");
    }

    private DSLContext createContext(final Connection connection) {
        Settings settings = new Settings();
        // Turn off fully qualified schemata.
        settings = settings.withRenderSchema(RENDER_SCHEMA);
        return DSL.using(connection, sqlDialect, settings);
    }

    private DSLContext createContextWithOptimisticLocking(final Connection connection) {
        Settings settings = new Settings();
        // Turn off fully qualified schemata.
        settings = settings.withRenderSchema(RENDER_SCHEMA);
        settings = settings.withExecuteWithOptimisticLocking(true);
        return DSL.using(connection, sqlDialect, settings);
    }

    protected <R> R useConnectionResult(final Function<Connection, R> function) {
        try {
            checkDataSource(dataSource);
            try (final Connection connection = dataSource.getConnection()) {
                return function.apply(connection);
            } catch (final SQLException e) {
                LOGGER.error(e::getMessage, e);
                throw new RuntimeException(e.getMessage(), e);
            }
        } finally {
            releaseDataSource();
        }
    }

    private void useConnection(final Consumer<Connection> consumer) {
        useConnectionResult(connection -> {
            consumer.accept(connection);
            return null;
        });
    }

    public <R> R contextResult(final Function<DSLContext, R> function) {
        return useConnectionResult(connection -> {
            final DSLContext context = createContext(connection);
            return function.apply(context);
        });
    }

    public void context(final Consumer<DSLContext> consumer) {
        contextResult(context -> {
            consumer.accept(context);
            return null;
        });
    }

    public <R> R contextResultWithOptimisticLocking(final Function<DSLContext, R> function) {
        return useConnectionResult(connection -> {
            final DSLContext context = createContextWithOptimisticLocking(connection);
            return function.apply(context);
        });
    }

    public void contextWithOptimisticLocking(final Consumer<DSLContext> consumer) {
        contextResultWithOptimisticLocking(context -> {
            consumer.accept(context);
            return null;
        });
    }

    public <R extends Record> void truncateTable(final Table<R> table) {
        context(context -> context
                .batch(
                        "SET FOREIGN_KEY_CHECKS=0",
                        "truncate table " + table.getName(),
                        "SET FOREIGN_KEY_CHECKS=1")
                .execute());
    }

    public <R extends Record> int getTableCount(final Table<R> table) {
        return contextResult(context -> context
                .selectCount()
                .from(table)
                .fetchOptional())
                .map(Record1::value1)
                .orElse(0);
    }

    public void transaction(final Consumer<DSLContext> consumer) {
        context(context -> context.transaction(nested -> consumer.accept(DSL.using(nested))));
    }

    public <R> R transactionResult(final Function<DSLContext, R> function) {
        return contextResult(context -> context.transactionResult(nested -> function.apply(DSL.using(nested))));
    }

    /**
     * Delete all rows matching the passed id value
     *
     * @param field The field to match id against
     * @param id    The id value to match on
     * @return The number of deleted records
     */
    public <R extends Record> int deleteById(final Table<R> table,
                                             final Field<Integer> field,
                                             final int id) {
        return contextResult(context -> context
                .deleteFrom(table)
                .where(field.eq(id))
                .execute());
    }

    /**
     * Delete all rows matching the passed id value
     *
     * @param id The id value to match on
     * @return The number of deleted records
     */
    public <R extends Record> int deleteById(final Table<R> table,
                                             final int id) {
        final Field<Integer> idField = getIdField(table);
        return contextResult(context -> context
                .deleteFrom(table)
                .where(idField.eq(id))
                .execute());
    }

    /**
     * Fetch a single row using the passed id value. If the id matches zero rows or more than one row then
     * an exception will be thrown. Assumes the table's id field is named 'id'.
     *
     * @param type The type of record to return
     * @param id   The id to match on
     * @return An optional containing the record if it was found.
     */
    public <R extends Record, T> Optional<T> fetchById(final Table<R> table,
                                                       final Class<T> type,
                                                       final int id) {
        final Field<Integer> idField = getIdField(table);
        return contextResult(context -> context
                .fetchOptional(table, idField.eq(id)))
                .map(record ->
                        record.into(type));
    }

    private Field<Integer> getIdField(final Table<?> table) {
        final Field<Integer> idField = table.field(DEFAULT_ID_FIELD_NAME, Integer.class);
        if (idField == null) {
            throw new RuntimeException(LogUtil.message("Field [id] not found on table [{}]", table.getName()));
        }
        return idField;
    }

    public int getLimit(final PageRequest pageRequest,
                        final boolean oneLarger) {
        return getLimit(pageRequest, oneLarger, Integer.MAX_VALUE);
    }

    public int getLimit(final PageRequest pageRequest,
                        final boolean oneLarger,
                        final int defaultValue) {
        if (pageRequest != null) {
            if (pageRequest.getLength() != null) {
                if (oneLarger) {
                    return pageRequest.getLength() + 1;
                } else {
                    return pageRequest.getLength();
                }
            }
        }

        return defaultValue;
    }

    public int getOffset(final PageRequest pageRequest) {
        if (pageRequest != null) {
            if (pageRequest.getOffset() != null) {
                return pageRequest.getOffset();
            }
        }

        return 0;
    }

    @SafeVarargs
    @SuppressWarnings("varargs") // Creating a stream from an array is safe
    public final Collection<Condition> conditions(final Optional<Condition>... conditions) {
        return Stream.of(conditions)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    public int count(final Table<?> table) {
        return contextResult(context -> context
                .select(DSL.count())
                .from(table)
                .fetchOptional())
                .map(Record1::value1)
                .orElse(0);
    }

    public int deleteAll(final Table<?> table) {
        return contextResult(context -> context
                .deleteFrom(table)
                .execute());
    }

    public <T> Optional<T> getMinId(final Table<?> table, final Field<T> idField) {
        return contextResult(context -> context
                .select(DSL.min(idField))
                .from(table)
                .fetchOptional())
                .map(Record1::value1);
    }

    public <T> Optional<T> getMaxId(final Table<?> table, final Field<T> idField) {
        return contextResult(context -> context
                .select(DSL.max(idField))
                .from(table)
                .fetchOptional())
                .map(Record1::value1);
    }

    public void checkEmpty(final Table<?> table) {
        if (count(table) > 0) {
            throw new RuntimeException("Unexpected data");
        }
    }

    /**
     * Used to build JOOQ conditions from our Criteria Range
     *
     * @param field    The jOOQ field being range queried
     * @param criteria The criteria to apply
     * @param <T>      The type of the range
     * @return A condition that applies the given range.
     */
    public <T extends Number> Optional<Condition> getRangeCondition(
            final Field<T> field,
            final Range<T> criteria) {
        if (criteria == null || !criteria.isConstrained()) {
            return Optional.empty();
        }

        Boolean matchNull = null;
        if (criteria.isMatchNull()) {
            matchNull = Boolean.TRUE;
        }

        final Optional<Condition> fromCondition;
        if (criteria.getFrom() == null) {
            fromCondition = Optional.empty();
        } else {
            fromCondition = Optional.of(field.greaterOrEqual(criteria.getFrom()));
        }
        final Optional<Condition> toCondition;
        if (criteria.getTo() == null) {
            toCondition = Optional.empty();
        } else {
            toCondition = Optional.of(field.lessThan(criteria.getTo()));
        }

        // Combine conditions.
        final Optional<Condition> condition = fromCondition.map(c1 ->
                        toCondition.map(c1::and).orElse(c1))
                .or(() -> toCondition);
        return convertMatchNull(field, matchNull, condition);
    }

    /**
     * Used to build jOOQ conditions from criteria sets
     *
     * @param field    The jOOQ field being set queried
     * @param criteria The criteria to apply
     * @param <T>      The type of the range
     * @return A condition that applies the given set.
     */
    public <T> Optional<Condition> getSetCondition(
            final Field<T> field,
            final Selection<T> criteria) {
        if (criteria == null || criteria.isMatchAll()) {
            return Optional.empty();
        }
        return Optional.of(field.in(criteria.getSet()));
    }

    /**
     * Used to build jOOQ conditions from string criteria
     *
     * @param field    The jOOQ field being queried
     * @param criteria The criteria to apply
     * @return A condition that applies the given criteria
     */
    public Optional<Condition> getStringCondition(
            final Field<String> field,
            final StringCriteria criteria) {
        if (criteria == null || !criteria.isConstrained()) {
            return Optional.empty();
        }

        final Optional<Condition> valueCondition;
        if (criteria.getMatchString() != null) {
            if (criteria.getMatchStyle() == null) {
                if (criteria.isCaseInsensitive()) {
                    valueCondition = Optional.of(DSL.upper(field).eq(criteria.getMatchString()));
                } else {
                    valueCondition = Optional.of(field.eq(criteria.getMatchString()));
                }
            } else {
                if (criteria.isCaseInsensitive()) {
                    valueCondition = Optional.of(DSL.upper(field).like(criteria.getMatchString()));
                } else {
                    valueCondition = Optional.of(field.like(criteria.getMatchString()));
                }
            }
        } else {
            valueCondition = Optional.empty();
        }

        return convertMatchNull(field, criteria.getMatchNull(), valueCondition);
    }

    private Optional<Condition> convertMatchNull(final Field<?> field,
                                                 final Boolean matchNull,
                                                 final Optional<Condition> condition) {
        if (matchNull == null) {
            return condition;
        }
        if (matchNull) {
            return condition.map(c -> c.or(field.isNull())).or(() -> Optional.of(field.isNull()));
        }
        return condition.or(() -> Optional.of(field.isNotNull()));
    }

    public Collection<OrderField<?>> getOrderFields(final Map<String, Field<?>> fieldMap,
                                                    final BaseCriteria criteria) {
        if (criteria.getSortList() == null) {
            return Collections.emptyList();
        }

        return criteria.getSortList()
                .stream()
                .map(s -> getOrderField(fieldMap, s))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private Optional<OrderField<?>> getOrderField(final Map<String, Field<?>> fieldMap,
                                                  final CriteriaFieldSort sort) {
        final Field<?> field = fieldMap.get(sort.getId());

        if (field != null) {
            if (sort.isDesc()) {
                return Optional.of(field.desc());
            } else {
                return Optional.of(field.asc());
            }
        }

        return Optional.empty();
    }

    /**
     * Converts a time in millis since epoch to a {@link Timestamp}
     */
    public Field<Timestamp> epochMsToTimestamp(final Field<? extends Number> field) {
        return DSL.field("from_unixtime({0} / 1000)", SQLDataType.TIMESTAMP, field);
    }

    /**
     * Converts a time in millis since epoch to a {@link Date}
     */
    public Field<Date> epochMsToDate(final Field<? extends Number> field) {
        return DSL.field("from_unixtime({0} / 1000)", SQLDataType.DATE, field);
    }

    public Field<Integer> periodDiff(final Field<? extends Date> date1,
                                     final Field<? extends Date> date2) {
        return DSL.field("period_diff(extract(year_month from {0}), extract(year_month from {1}))",
                SQLDataType.INTEGER, date1, date2);
    }

    public Field<Integer> periodDiff(final Field<? extends Date> date1,
                                     final Date date2) {
        return DSL.field("period_diff(extract(year_month from {0}), extract(year_month from {1}))",
                SQLDataType.INTEGER, date1, date2);
    }

    private static void checkDataSource(final DataSource dataSource) {
        final CurrentDataSource currentDataSource;
        try {
            throw new RuntimeException();
        } catch (final RuntimeException e) {
            currentDataSource = new CurrentDataSource(dataSource, e.getStackTrace());
        }

        // Check thread usage
        final CurrentDataSource currentThreadDataSource = DATA_SOURCE_THREAD_LOCAL.get();
        if (currentThreadDataSource != null && currentThreadDataSource.dataSource.equals(dataSource)) {
            LOGGER.error(() -> "Data source already in use by this thread:\n\n" +
                    currentThreadDataSource +
                    "\n\n" +
                    currentDataSource);
        }
        DATA_SOURCE_THREAD_LOCAL.set(currentDataSource);
    }

    private static void releaseDataSource() {
        DATA_SOURCE_THREAD_LOCAL.set(null);
    }

    private static class CurrentDataSource {

        private final DataSource dataSource;
        private final StackTraceElement[] currentStack;

        public CurrentDataSource(final DataSource dataSource, final StackTraceElement[] currentStack) {
            this.dataSource = dataSource;
            this.currentStack = currentStack;
        }

        @Override
        public String toString() {
            return Arrays.stream(currentStack)
                    .map(StackTraceElement::getClassName)
                    .collect(Collectors.joining("\n"));
        }
    }
}
