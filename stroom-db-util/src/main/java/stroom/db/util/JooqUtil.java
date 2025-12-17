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

import stroom.util.concurrent.ThreadUtil;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.AsciiTable.TableBuilder;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.DurationTimer.TimedResult;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Range;
import stroom.util.shared.Selection;
import stroom.util.shared.StringCriteria;
import stroom.util.string.PatternUtil;

import org.jooq.Condition;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UpdatableRecord;
import org.jooq.conf.Settings;
import org.jooq.exception.DataAccessException;
import org.jooq.exception.DataChangedException;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.SQLDataType;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransactionRollbackException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.sql.DataSource;

public final class JooqUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JooqUtil.class);
    private static final ThreadLocal<DataSource> DATA_SOURCE_THREAD_LOCAL = new ThreadLocal<>();

    private static final String DEFAULT_ID_FIELD_NAME = "id";
    private static final Boolean RENDER_SCHEMA = false;
    static final int MAX_DEADLOCK_RETRY_ATTEMPTS = 20;
    private static final long SLEEP_INCREMENT_MS = 10;

    private JooqUtil() {
        // Utility class.
    }

    public static void disableJooqLogoInLogs() {
        System.getProperties().setProperty("org.jooq.no-logo", "true");
    }

    private static Settings createSettings(final boolean isExecuteWithOptimisticLocking) {
        final Settings settings = new Settings();
        // Turn off fully qualified schemata.
        settings.withRenderSchema(RENDER_SCHEMA);

        // Stop jooq from trying to do 'SET @@group_concat_max_len = 4294967295;' before each
        // statement using group_concat. This means we are limited to the default of
        // 1024 (unless it has been set to something else)
        // https://blog.jooq.org/mysqls-allowmultiqueries-flag-with-jdbc-and-jooq/
        settings.withRenderGroupConcatMaxLenSessionVariable(false);

        if (isExecuteWithOptimisticLocking) {
            settings.withExecuteWithOptimisticLocking(true);
        }
        return settings;
    }

    public static Configuration createConfiguration(final Connection connection,
                                                    final boolean isExecuteWithOptimisticLocking) {
        final Settings settings = createSettings(isExecuteWithOptimisticLocking);

        final DefaultConfiguration configuration = new DefaultConfiguration();
        configuration.setSQLDialect(SQLDialect.MYSQL);
        configuration.setSettings(settings);
        configuration.setConnection(connection);

        // Only add listener for slow queries if its logger is enabled to save the overhead
        if (SlowQueryExecuteListener.LOGGER.isDebugEnabled()) {
            configuration.setExecuteListener(new SlowQueryExecuteListener());
        }

        return configuration;
    }

    public static DSLContext createContext(final Connection connection) {
        final Configuration configuration = createConfiguration(connection, false);
        return DSL.using(configuration);
    }

    private static DSLContext createContextWithOptimisticLocking(final Connection connection) {
        final Configuration configuration = createConfiguration(connection, true);
        return DSL.using(configuration);
    }

    public static void context(final DataSource dataSource, final Consumer<DSLContext> consumer) {
        try (final Connection connection = dataSource.getConnection()) {
            try {
                checkDataSource(dataSource);
                final DSLContext context = createContext(connection);
                consumer.accept(context);
            } finally {
                releaseDataSource();
            }
        } catch (final Exception e) {
            throw convertException(e);
        }
    }

    /**
     * If {@code isTimed} is {@code true} it will measure the duration of consumer,
     * else it will return a zero duration. {@code isTimed} can be set using
     * {@code LOGGER.isDebugEnabled()} for example.
     */
    public static Duration timedContext(final DataSource dataSource,
                                        final boolean isTimed,
                                        final Consumer<DSLContext> consumer) {
        final Duration duration;
        try (final Connection connection = dataSource.getConnection()) {
            try {
                checkDataSource(dataSource);
                final DSLContext context = createContext(connection);
                if (isTimed) {
                    duration = DurationTimer.measure(() ->
                            consumer.accept(context));
                } else {
                    consumer.accept(context);
                    duration = Duration.ZERO;
                }
            } finally {
                releaseDataSource();
            }
        } catch (final Exception e) {
            throw convertException(e);
        }
        return duration;
    }

    public static <R extends Record> void truncateTable(final DataSource dataSource,
                                                        final Table<R> table) {
        try (final Connection connection = dataSource.getConnection()) {
            try {
                checkDataSource(dataSource);
                final DSLContext context = createContext(connection);
                context
                        .batch(
                                "SET FOREIGN_KEY_CHECKS=0",
                                "truncate table " + table.getName(),
                                "SET FOREIGN_KEY_CHECKS=1")
                        .execute();
            } finally {
                releaseDataSource();
            }
        } catch (final Exception e) {
            throw convertException(e);
        }
    }

    public static <R extends Record> int getTableCount(final DataSource dataSource,
                                                       final Table<R> table) {

        return getTableCountWhen(dataSource, table, null);
    }

    public static <R extends Record> int getTableCountWhen(final DataSource dataSource,
                                                           final Table<R> table,
                                                           final Condition condition) {

        try (final Connection connection = dataSource.getConnection()) {
            try {
                checkDataSource(dataSource);
                final DSLContext context = createContext(connection);
                return context
                        .selectCount()
                        .from(table)
                        .where(Objects.requireNonNullElseGet(condition, DSL::trueCondition))
                        .fetchOne()
                        .value1();
            } finally {
                releaseDataSource();
            }
        } catch (final Exception e) {
            throw convertException(e);
        }
    }

    public static <R> R contextResult(final DataSource dataSource,
                                      final Function<DSLContext, R> function) {
        final R result;
        try (final Connection connection = dataSource.getConnection()) {
            try {
                checkDataSource(dataSource);
                final DSLContext context = createContext(connection);
                result = function.apply(context);
            } finally {
                releaseDataSource();
            }
        } catch (final Exception e) {
            throw convertException(e);
        }
        return result;
    }

    /**
     * If {@code isTimed} is {@code true} it will measure the duration of function,
     * else it will return a {@link TimedResult} containing a zero duration.
     * {@code isTimed} can be set using {@code LOGGER.isDebugEnabled()} for example.
     */
    public static <R> TimedResult<R> timedContextResult(final DataSource dataSource,
                                                        final boolean isTimed,
                                                        final Function<DSLContext, R> function) {
        final TimedResult<R> timedResult;
        try (final Connection connection = dataSource.getConnection()) {
            try {
                checkDataSource(dataSource);
                final DSLContext context = createContext(connection);
                if (isTimed) {
                    timedResult = DurationTimer.measure(() ->
                            function.apply(context));
                } else {
                    timedResult = TimedResult.zero(function.apply(context));
                }
            } finally {
                releaseDataSource();
            }
        } catch (final Exception e) {
            throw convertException(e);
        }
        return timedResult;
    }

//    public static void contextResultWithOptimisticLocking(
//    final DataSource dataSource, final Consumer<DSLContext> consumer) {
//        try (final Connection connection = dataSource.getConnection()) {
//            final DSLContext context = createContextWithOptimisticLocking(connection);
//            consumer.accept(context);
//        } catch (final Exception e) {
//            LOGGER.error(e.getMessage(), e);
//            throw new RuntimeException(e.getMessage(), e);
//        }
//    }

    /**
     * @param dataSource
     * @param function
     * @param <R>
     * @return
     * @throws stroom.util.exception.DataChangedException If another thread/node has already changed the data
     */
    public static <R> R contextResultWithOptimisticLocking(final DataSource dataSource,
                                                           final Function<DSLContext, R> function) {
        final R result;
        try (final Connection connection = dataSource.getConnection()) {
            try {
                checkDataSource(dataSource);
                final DSLContext context = createContextWithOptimisticLocking(connection);
                result = function.apply(context);
            } finally {
                releaseDataSource();
            }
        } catch (final DataChangedException e) {
            throw new stroom.util.exception.DataChangedException(e.getMessage(), e);
        } catch (final Exception e) {
            throw convertException(e);
        }
        return result;
    }

    public static void transaction(final DataSource dataSource, final Consumer<DSLContext> consumer) {
        context(dataSource, context -> context.transaction(nested -> consumer.accept(DSL.using(nested))));
    }

    public static <R> R transactionResult(final DataSource dataSource, final Function<DSLContext, R> function) {
        return contextResult(dataSource,
                context -> context.transactionResult(nested -> function.apply(DSL.using(nested))));
    }

    public static <R> R transactionResultWithOptimisticLocking(final DataSource dataSource,
                                                               final Function<DSLContext, R> function) {
        return contextResultWithOptimisticLocking(dataSource,
                context -> context.transactionResult(nested -> function.apply(DSL.using(nested))));
    }

    public static <R extends UpdatableRecord<R>> R create(final DataSource dataSource, final R record) {
        LOGGER.debug(() -> "Creating a " + record.getTable() + " record:\n" + record);
        try (final Connection connection = dataSource.getConnection()) {
            try {
                checkDataSource(dataSource);
                final DSLContext context = createContext(connection);
                record.attach(context.configuration());
                record.store();
            } finally {
                releaseDataSource();
            }
        } catch (final Exception e) {
            throw convertException(e);
        }
        return record;
    }

    /**
     * See {@link JooqUtil#tryCreate(DataSource, UpdatableRecord, TableField, TableField, TableField, Consumer)}
     */
    public static <R extends UpdatableRecord<R>, T> R tryCreate(final DataSource dataSource,
                                                                final R record,
                                                                final TableField<R, T> keyField) {
        return tryCreate(dataSource, record, keyField, null, null, null);
    }

    /**
     * See {@link JooqUtil#tryCreate(DataSource, UpdatableRecord, TableField, TableField, TableField, Consumer)}
     */
    public static <R extends UpdatableRecord<R>, T> R tryCreate(final DataSource dataSource,
                                                                final R record,
                                                                final TableField<R, T> keyField,
                                                                final Consumer<R> onCreateAction) {
        return tryCreate(dataSource, record, keyField, null, null, onCreateAction);
    }

    /**
     * See {@link JooqUtil#tryCreate(DataSource, UpdatableRecord, TableField, TableField, TableField, Consumer)}
     */
    public static <R extends UpdatableRecord<R>, T1, T2> R tryCreate(final DataSource dataSource,
                                                                     final R record,
                                                                     final TableField<R, T1> keyField1,
                                                                     final TableField<R, T2> keyField2) {
        return tryCreate(dataSource, record, keyField1, keyField2, null, null);
    }

    /**
     * See {@link JooqUtil#tryCreate(DataSource, UpdatableRecord, TableField, TableField, TableField, Consumer)}
     */
    public static <R extends UpdatableRecord<R>, T1, T2, T3> R tryCreate(final DataSource dataSource,
                                                                         final R record,
                                                                         final TableField<R, T1> keyField1,
                                                                         final TableField<R, T2> keyField2,
                                                                         final Consumer<R> onCreateAction) {
        return tryCreate(dataSource, record, keyField1, keyField2, null, onCreateAction);
    }

    /**
     * Tries to insert record into the database. If it doesn't already exist it will insert the
     * record and return the record updated with any auto-generated columns (e.g. ID).
     * If it already exists then the unique key violation will be swallowed and the keyField arguments
     * will be used to retrieve the existing record for return.
     * <p>
     * If you don't need the persisted state from the database then use
     * {@link JooqUtil#tryCreate(DSLContext, UpdatableRecord)} instead.
     * </p>
     *
     * @param onCreateAction Called if the record does not already exist and is actually inserted.
     * @return The record containing the latest state in the database.
     */
    public static <R extends UpdatableRecord<R>, T1, T2, T3> R tryCreate(final DataSource dataSource,
                                                                         final R record,
                                                                         final TableField<R, T1> keyField1,
                                                                         final TableField<R, T2> keyField2,
                                                                         final TableField<R, T3> keyField3,
                                                                         final Consumer<R> onCreateAction) {
        return contextResult(dataSource, context ->
                tryCreate(context, record, keyField1, keyField2, keyField3, onCreateAction));
    }

    /**
     * <p>
     * Tries to insert record into the database. If it doesn't already exist it will insert the
     * record and return the record updated with any auto-generated columns (e.g. ID).
     * If it already exists then the unique key violation will be swallowed and the keyField arguments
     * will be used to retrieve the existing record for return. The keyField arguments are one to three columns
     * that make up a unique key.
     * </p>
     * <p>
     * If you don't need the persisted state from the database then use
     * {@link JooqUtil#tryCreate(DSLContext, UpdatableRecord)} instead.
     * </p>
     *
     * @param onCreateAction Called if the record does not already exist and is actually inserted.
     * @return The record containing the latest state in the database.
     */
    public static <R extends UpdatableRecord<R>, T1, T2, T3> R tryCreate(final DSLContext context,
                                                                         final R record,
                                                                         final TableField<R, T1> keyField1,
                                                                         final TableField<R, T2> keyField2,
                                                                         final TableField<R, T3> keyField3,
                                                                         final Consumer<R> onCreateAction) {
        R persistedRecord;
        LOGGER.debug(() -> "Creating a " + record.getTable() + " record if it doesn't already exist:\n" + record);
        record.attach(context.configuration());
        try {
            // Attempt to write the record, which may already be there
            record.insert();
            persistedRecord = record;
            if (onCreateAction != null) {
                onCreateAction.accept(persistedRecord);
            }
        } catch (final RuntimeException e) {
            if (isDuplicateKeyException(e)) {
                LOGGER.debug(e::getMessage, e);

                // In theory, we could get the unique key fields from record.getTable().getKeys()
                // but this is a bit fragile if the table has multiple unique keys, so better to let
                // the caller make the decision as to which fields to use
                final List<Condition> conditionList = new ArrayList<>();
                // For now support up to three fields in a compound key
                if (keyField1 != null) {
                    conditionList.add(keyField1.eq(record.get(keyField1)));
                }
                if (keyField2 != null) {
                    conditionList.add(keyField2.eq(record.get(keyField2)));
                }
                if (keyField3 != null) {
                    conditionList.add(keyField3.eq(record.get(keyField3)));
                }
                if (conditionList.isEmpty()) {
                    throw new RuntimeException("You must supplied at least one key field so the persisted record can " +
                                               "be retrieved.");
                }

                final Table<R> table = record.getTable();
                LOGGER.debug("Re-fetching existing record from {} with conditions: {}", table, conditionList);
                // Now need to re-fetch the record using the key fields so we have the full record with ids
                persistedRecord = context.selectFrom(table)
                        .where(conditionList.toArray(new Condition[0]))
                        .fetchOne();
            } else {
                // Some other error so just re-throw
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        }
        return persistedRecord;
    }

    /**
     * Tries to insert record into the database. If it doesn't already exist it will insert the
     * record and return 1. If it already exists then the unique key violation will be swallowed
     * and will return 0;
     * <p>
     * If you need the persisted version of the record from the database then use
     * {@link JooqUtil#tryCreate(DataSource, UpdatableRecord, TableField, TableField, TableField, Consumer)}
     * instead.
     * </p>
     */
    public static <R extends UpdatableRecord<R>> int tryCreate(final DataSource dataSource,
                                                               final R record) {
        return contextResult(dataSource, context ->
                tryCreate(context, record));
    }

    /**
     * Tries to insert record into the database. If it doesn't already exist it will insert the
     * record and return 1. If it already exists then the unique key violation will be swallowed
     * and will return 0;
     * <p>
     * If you need the persisted version of the record from the database then use
     * {@link JooqUtil#tryCreate(DataSource, UpdatableRecord, TableField, TableField, TableField, Consumer)}
     * instead.
     * </p>
     */
    public static <R extends UpdatableRecord<R>> int tryCreate(final DSLContext context,
                                                               final R record) {
        LOGGER.debug(() -> "Creating a " + record.getTable() + " record if it doesn't already exist:\n" + record);
        record.attach(context.configuration());
        int result = 0;
        try {
            // Attempt to write the record, which may already be there
            result = record.insert();
        } catch (final RuntimeException e) {
            if (isDuplicateKeyException(e)) {
                LOGGER.debug(e::getMessage, e);
            } else {
                // Some other error so just re-throw
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        }
        return result;
    }

    public static <R extends UpdatableRecord<R>> R update(final DataSource dataSource, final R record) {
        LOGGER.debug(() -> "Updating a " + record.getTable() + " record:\n" + record);
        try (final Connection connection = dataSource.getConnection()) {
            try {
                checkDataSource(dataSource);
                final DSLContext context = createContext(connection);
                record.attach(context.configuration());
                record.update();
            } finally {
                releaseDataSource();
            }
        } catch (final Exception e) {
            throw convertException(e);
        }
        return record;
    }

    public static <R extends UpdatableRecord<R>> R updateWithOptimisticLocking(final DataSource dataSource,
                                                                               final R record) {
        LOGGER.debug(() -> "Updating a " + record.getTable() + " record:\n" + record);
        try (final Connection connection = dataSource.getConnection()) {
            try {
                checkDataSource(dataSource);
                final DSLContext context = createContextWithOptimisticLocking(connection);
                record.attach(context.configuration());
                record.update();
            } finally {
                releaseDataSource();
            }
        } catch (final DataChangedException e) {
            throw new stroom.util.exception.DataChangedException(e.getMessage(), e);
        } catch (final Exception e) {
            throw convertException(e);
        }
        return record;
    }

    /**
     * Delete all rows matching the passed id value
     *
     * @param field The field to match id against
     * @param id    The id value to match on
     * @return The number of deleted records
     */
    public static <R extends Record> int deleteById(final DataSource dataSource,
                                                    final Table<R> table,
                                                    final Field<Integer> field,
                                                    final int id) {

        return contextResult(dataSource, context ->
                context
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
    public static <R extends Record> int deleteById(final DataSource dataSource,
                                                    final Table<R> table,
                                                    final int id) {

        final Field<Integer> idField = getIdField(table);
        return contextResult(dataSource, context ->
                context
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
    public static <R extends Record, T> Optional<T> fetchById(final DataSource dataSource,
                                                              final Table<R> table,
                                                              final Class<T> type,
                                                              final int id) {

        final Field<Integer> idField = getIdField(table);
        return contextResult(dataSource, context ->
                context
                        .fetchOptional(table, idField.eq(id))
                        .map(record ->
                                record.into(type)));
    }

    /**
     * Runs runnable, but if it causes a SQL deadlock it will keep retrying it until it is successful
     * or the retry limit is reached. runnable should be a single operation or idempotent such that
     * there is no issue with part of it being run multiple times, i.e. runnable should be a
     * single txn.
     *
     * @param messageSupplier A message describing what runnable is doing for logging purposes.
     */
    public static void withDeadlockRetries(final Runnable runnable,
                                           final Supplier<String> messageSupplier) {
        withDeadlockRetries(() -> {
            runnable.run();
            return null;
        }, messageSupplier);
    }

    /**
     * Calls {@link Supplier#get()}, but if it causes a SQL deadlock it will keep retrying
     * {@link Supplier#get()} until it is successful or the retry limit is reached.
     * supplier should be a single operation or idempotent such that there is no issue with part
     * of it being run multiple times, i.e. supplier should be a single txn so the whole thing can
     * roll back on deadlock.
     *
     * @param messageSupplier A message describing what runnable is doing for logging purposes.
     */
    public static <T> T withDeadlockRetries(final Supplier<T> supplier,
                                            final Supplier<String> messageSupplier) {

        final AtomicInteger attempt = new AtomicInteger(0);
        final AtomicLong sleepMs = new AtomicLong();
        while (true) {
            try {
                attempt.incrementAndGet();
                final T result = supplier.get();
                if (attempt.get() >= 2) {
                    LOGGER.info("Ran '{}' successfully after {} deadlocks",
                            NullSafe.supply(messageSupplier),
                            attempt.get() - 1);
                }

                return result;
            } catch (final DataAccessException e) {
                if (e.getCause() instanceof final SQLTransactionRollbackException sqlTxnRollbackEx
                    && NullSafe.containsIgnoringCase(sqlTxnRollbackEx.getMessage(), "deadlock")) {

                    if (attempt.get() >= MAX_DEADLOCK_RETRY_ATTEMPTS) {
                        throw new RuntimeException(LogUtil.message("Gave up retrying '{}' after {} deadlocks",
                                NullSafe.supply(messageSupplier), attempt), e);
                    }

                    LOGGER.warn(() -> LogUtil.message(
                            "Deadlock trying to run '{}' on attempt {}. Will retry in {} ms. " +
                            "Enable DEBUG for full stacktrace.",
                            NullSafe.supply(messageSupplier),
                            attempt.get(),
                            sleepMs));
                    LOGGER.debug(e.getMessage(), e);
                    // Just ignore interrupts as are we are only sleeping for at most 200ms
                    if (sleepMs.get() > 0) {
                        ThreadUtil.sleepIgnoringInterrupts(sleepMs.get());
                    }
                    // Make the sleep a bit longer next time
                    sleepMs.addAndGet(SLEEP_INCREMENT_MS);
                } else {
                    throw e;
                }
            }
        }
    }

    private static Field<Integer> getIdField(final Table<?> table) {
        final Field<Integer> idField = table.field(DEFAULT_ID_FIELD_NAME, Integer.class);
        if (idField == null) {
            throw new RuntimeException(LogUtil.message("Field [id] not found on table [{}]", table.getName()));
        }
        return idField;
    }

    public static int getLimit(final PageRequest pageRequest,
                               final boolean oneLarger) {
        return getLimit(pageRequest, oneLarger, Integer.MAX_VALUE);
    }

    public static int getLimit(final PageRequest pageRequest,
                               final boolean oneLarger,
                               final int defaultValue) {
        if (pageRequest != null) {
            if (pageRequest.getLength() != null) {
                if (oneLarger && pageRequest.getLength() < Integer.MAX_VALUE) {
                    return pageRequest.getLength() + 1;
                } else {
                    return pageRequest.getLength();
                }
            }
        }

        return defaultValue;
    }

    public static int getOffset(final PageRequest pageRequest) {
        if (pageRequest != null) {
            if (pageRequest.getOffset() != null) {
                return pageRequest.getOffset();
            }
        }

        return 0;
    }

    public static int getOffset(final PageRequest pageRequest, final int limit, final int count) {
        if (pageRequest != null) {
            if (pageRequest.getOffset() != null) {
                if (pageRequest.getOffset() == -1 || count < pageRequest.getOffset()) {
                    return Math.max(0, count - limit);
                }
                return pageRequest.getOffset();
            }
        }

        return 0;
    }

    @SafeVarargs
    @SuppressWarnings("varargs") // Creating a stream from an array is safe
    public static Collection<Condition> conditions(final Optional<Condition>... conditions) {
        return Stream.of(conditions)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Used to build JOOQ conditions from our Criteria Range
     *
     * @param field    The jOOQ field being range queried
     * @param criteria The criteria to apply
     * @param <T>      The type of the range
     * @return A condition that applies the given range.
     */
    public static <T extends Number> Optional<Condition> getRangeCondition(
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
     * Combine multiple conditions in a null safe way
     *
     * @param conditions
     * @return A non-null condition
     */
    public static Condition andConditions(final Condition... conditions) {
        if (conditions == null || conditions.length == 0) {
            return DSL.noCondition();
        } else if (conditions.length == 1 && conditions[0] != null) {
            return conditions[0];
        } else {
            Condition condition = DSL.noCondition();
            for (final Condition condition2 : conditions) {
                if (condition2 != null) {
                    condition = condition.and(condition2);
                }
            }
            return condition;
        }
    }

    /**
     * Used to build jOOQ conditions from criteria sets
     *
     * @param field    The jOOQ field being set queried
     * @param criteria The criteria to apply
     * @param <T>      The type of the range
     * @return A condition that applies the given set.
     */
    public static <T> Optional<Condition> getSetCondition(
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
    public static Optional<Condition> getStringCondition(
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

    public static Optional<Condition> getBooleanCondition(final Field<Boolean> field,
                                                          final Boolean value) {
        return Optional.ofNullable(value)
                .map(field::eq);
    }

    private static Optional<Condition> convertMatchNull(final Field<?> field,
                                                        final Boolean matchNull,
                                                        final Optional<Condition> condition) {
        if (matchNull == null) {
            return condition;
        } else if (matchNull) {
            return condition.map(c -> c.or(field.isNull()))
                    .or(() -> Optional.of(field.isNull()));
        } else {
            return condition.or(() -> Optional.of(field.isNotNull()));
        }
    }

    public static Collection<OrderField<?>> getOrderFields(final Map<String, Field<?>> fieldMap,
                                                           final BaseCriteria criteria,
                                                           final OrderField<?>... defaultSortFields) {
        final List<OrderField<?>> defaults = NullSafe.asList(defaultSortFields);
        if (NullSafe.isEmptyCollection(criteria.getSortList())) {
            return defaults;
        } else {
            final List<OrderField<?>> defaultsNotSeen = new ArrayList<>(defaults);

            final ArrayList<OrderField<?>> sortList = criteria.getSortList()
                    .stream()
                    .map(s -> getOrderField(fieldMap, s))
                    .filter(Objects::nonNull)
                    .peek(defaultsNotSeen::remove)
                    .collect(Collectors.toCollection(ArrayList::new));

            // If the default sort fields are not in the sort list then add them at the end, so they act
            // as a secondary sort, e.g. if user is sorting on 'enabled' then it makes sense to secondary
            // sort on the default sort fields.
            if (!defaultsNotSeen.isEmpty()) {
                sortList.addAll(defaultsNotSeen);
            }

            return sortList;
        }
    }

    public static Collection<OrderField<?>> getOrderFields(final Map<String, Field<?>> fieldMap,
                                                           final BaseCriteria criteria) {
        return NullSafe.list(criteria.getSortList())
                .stream()
                .map(s -> getOrderField(fieldMap, s))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static OrderField<?> getOrderField(final Map<String, Field<?>> fieldMap,
                                               final CriteriaFieldSort sort) {
        final Field<?> field = NullSafe.map(fieldMap)
                .get(sort.getId());
        return NullSafe.get(
                field,
                fld -> (sort.isDesc()
                        ? fld.desc()
                        : fld.asc()));
    }

    /**
     * Converts a time in millis since epoch to a {@link java.sql.Timestamp}
     */
    public static Field<Timestamp> epochMsToTimestamp(final Field<? extends Number> field) {
        return DSL.field("from_unixtime({0} / 1000)", SQLDataType.TIMESTAMP, field);
    }

    /**
     * Converts a time in millis since epoch to a {@link java.sql.Date}
     */
    public static Field<Date> epochMsToDate(final Field<? extends Number> field) {
        return DSL.field("from_unixtime({0} / 1000)", SQLDataType.DATE, field);
    }

    public static Field<Integer> periodDiff(final Field<? extends Date> date1,
                                            final Field<? extends Date> date2) {
        return DSL.field("period_diff(extract(year_month from {0}), extract(year_month from {1}))",
                SQLDataType.INTEGER, date1, date2);
    }

    public static Field<Integer> periodDiff(final Field<? extends Date> date1,
                                            final Date date2) {
        return DSL.field("period_diff(extract(year_month from {0}), extract(year_month from {1}))",
                SQLDataType.INTEGER, date1, date2);
    }

    /**
     * If filter is wild carded (i.e. contains a '*') then it returns 'field.like(x)' where x has
     * '*' characters replaced by '%' and SQL wild cards '%' and '_' are escaped.
     * If filter is not wild carded it returns 'field.eq(filter)'
     */
    public static Condition createWildCardedStringCondition(final Field<String> field, final String filter) {
        return createWildCardedStringCondition(field, filter, true);
    }

    /**
     * If allowWildCards is true and filter is wild carded (i.e. contains a '*') then it
     * returns 'field.like(x)' where x has
     * '*' characters replaced by '%' and SQL wild cards '%' and '_' are escaped.
     * If filter is not wild carded it returns 'field.eq(filter)'
     */
    public static Condition createWildCardedStringCondition(final Field<String> field,
                                                            final String filter,
                                                            final boolean allowWildCards) {
        if (filter != null) {
            if (allowWildCards && filter.contains(PatternUtil.STROOM_WILD_CARD_CHAR)) {
                final String likeStr = PatternUtil.createSqlLikeStringFromWildCardFilter(filter);
                LOGGER.debug("field.like({})", likeStr);
                return field.like(likeStr);
            }
            LOGGER.debug("field.eq({})", filter);
            return field.eq(filter);
        } else {
            LOGGER.debug("field.isNull()");
            return field.isNull();
        }
    }

    /**
     * Creates a single {@link Condition} containing an OR/AND of all the {@link Condition}s for
     * each of the passed filters, e.g. {@code field like 'xxx%' OR field like '%yyy'}.
     * If allowWildCards is true and filter is wild carded (i.e. contains a '*') then it
     * returns 'field.like(x)' where x has
     * '*' characters replaced by '%' and SQL wild cards '%' and '_' are escaped.
     * If filter is not wild carded it returns 'field.eq(filter)'.
     */
    public static Condition createWildCardedStringsCondition(final Field<String> field,
                                                             final List<String> filters,
                                                             final boolean allowWildCards,
                                                             final BooleanOperator booleanOperator) {
        Objects.requireNonNull(booleanOperator);
        if (filters == null || filters.isEmpty()) {
            return DSL.noCondition();
        } else {
            final List<Condition> conditions = filters.stream()
                    .map(filter -> createWildCardedStringCondition(field, filter, allowWildCards))
                    .collect(Collectors.toList());

            return BooleanOperator.AND.equals(booleanOperator)
                    ? DSL.and(conditions)
                    : DSL.or(conditions);
        }
    }

    /**
     * Convert a checked exception into an unchecked one. Produce useful logging and handling of interrupted exceptions.
     *
     * @param e The exception to convert.
     * @return A runtime exception.
     */
    private static RuntimeException convertException(final Exception e) {
        return convertException(e, false);
    }

    private static RuntimeException convertException(final Exception e, final boolean logError) {
        if (e.getCause() instanceof InterruptedException) {
            // We expect interruption during searches so don't log the error.
            LOGGER.debug(e::getMessage, e);
            // Continue to interrupt the current thread.
            Thread.currentThread().interrupt();
            // Throw an unchecked form of the interrupted exception.
            return new UncheckedInterruptedException((InterruptedException) e.getCause());
        } else {
            if (logError) {
                LOGGER.error(e::getMessage, e);
            } else {
                LOGGER.debug(e::getMessage, e);
            }
            if (e instanceof RuntimeException) {
                return (RuntimeException) e;
            } else {
                return new RuntimeException(e.getMessage(), e);
            }
        }
    }

    /**
     * Dumps the collection of records to an ASCII table or "NO DATA" if the collection is empty/null
     */
    public static <T extends Record> String toAsciiTable(final Collection<T> collection,
                                                         final boolean qualifiedFields) {
        final TableBuilder<T> builder = AsciiTable.builder(collection);
        if (collection.isEmpty()) {
            return "NO DATA";
        } else {
            // Grab any record to figure out what fields we have
            final T aRecord = collection.stream()
                    .findAny()
                    .orElseThrow();

            for (int i = 0; i < aRecord.fields().length; i++) {
                final Field<?> field = aRecord.field(i);
                final int iCopy = i;
                final String fieldName = qualifiedFields
                        ? field.getName()
                        : field.getQualifiedName().toString();

                final Function<T, String> getter;

                if (field.getName().endsWith("_ms")) {
                    getter = rec -> {
                        final Object val = rec.get(iCopy);
                        if (val == null) {
                            return null;
                        } else if (val instanceof Long) {
                            return Instant.ofEpochMilli((Long) val).toString();
                        } else {
                            return val.toString();
                        }
                    };
                } else {
                    getter = rec -> NullSafe.get(rec.get(iCopy), Object::toString);
                }
                builder.withColumn(Column.of(fieldName, getter));
            }
        }
        return builder.build();
    }

    /**
     * Check that the datasource is not currently being used by the current thread. The main point of this check is to
     * ensure that operations on a datasource are not nested as this can lead to exhaustion of connections from a
     * datasource connection pool and produce a deadlock. It is generally ok for separate threads to get connections
     * from the datasource as a connection should become available once one thread has released a connection back to the
     * pool. However, nested calls, particularly recursive ones, will quickly starve a connection pool and lock the
     * system.
     *
     * @param dataSource The datasource to check.
     */
    private static void checkDataSource(final DataSource dataSource) {
        final DataSource currentDataSource = DATA_SOURCE_THREAD_LOCAL.get();
        if (currentDataSource != null && currentDataSource.equals(dataSource)) {
            try {
                throw new RuntimeException("Data source already in use");
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
        DATA_SOURCE_THREAD_LOCAL.set(dataSource);
    }

    private static void releaseDataSource() {
        DATA_SOURCE_THREAD_LOCAL.set(null);
    }

    public static <T> Optional<T> getMinId(final DSLContext context,
                                           final Table<?> table,
                                           final Field<T> idField) {
        return context
                .select(DSL.min(idField))
                .from(table)
                .fetchOptional()
                .map(Record1::value1);
    }

    public static <T> Optional<T> getMaxId(final DSLContext context,
                                           final Table<?> table,
                                           final Field<T> idField) {
        return context
                .select(DSL.max(idField))
                .from(table)
                .fetchOptional()
                .map(Record1::value1);
    }

    public static int count(final DSLContext context,
                            final Table<?> table) {
        return context
                .select(DSL.count())
                .from(table)
                .fetchOptional()
                .map(Record1::value1)
                .orElse(0);
    }

    public static int deleteAll(final DSLContext context,
                                final Table<?> table) {
        return context
                .deleteFrom(table)
                .execute();
    }

    public static void checkEmpty(final DSLContext context,
                                  final Table<?> table) {
        if (count(context, table) > 0) {
            throw new RuntimeException("Unexpected data");
        }
    }

    public static void mapAuditFields(final Record record, final HasAuditInfo hasAuditInfo) {
        Objects.requireNonNull(record);
        Objects.requireNonNull(hasAuditInfo);

        hasAuditInfo.setCreateTimeMs(record.get("create_time_ms", Long.class));
        hasAuditInfo.setUpdateTimeMs(record.get("update_time_ms", Long.class));
        hasAuditInfo.setCreateUser(record.get("create_user", String.class));
        hasAuditInfo.setCreateUser(record.get("update_user", String.class));
    }

    public static void onDuplicateKeyIgnore(final Runnable runnable) {
        try {
            runnable.run();
        } catch (final RuntimeException e) {
            if (isDuplicateKeyException(e)) {
                LOGGER.debug(e::getMessage, e);
            } else {
                // Some other error so just re-throw
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        }
    }

    public static <R> Optional<R> onDuplicateKeyIgnore(final Supplier<Optional<R>> supplier) {
        try {
            return supplier.get();
        } catch (final RuntimeException e) {
            if (isDuplicateKeyException(e)) {
                LOGGER.debug(e::getMessage, e);
                return Optional.empty();
            } else {
                // Some other error so just re-throw
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        }
    }

    public static boolean isDuplicateKeyException(final Throwable throwable) {
        // 1062 is a duplicate key exception so someone else has already inserted it
        return NullSafe.test(throwable, e ->
                e instanceof DataAccessException
                && e.getCause() instanceof final SQLIntegrityConstraintViolationException sqlEx
                && sqlEx.getErrorCode() == 1062);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public enum BooleanOperator {
        AND,
        OR;
    }
}
